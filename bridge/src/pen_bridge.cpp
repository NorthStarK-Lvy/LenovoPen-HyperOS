#include <android/log.h>
#include <linux/input.h>
#include <sys/file.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <poll.h>
#include <dirent.h>
#include <fcntl.h>
#include <signal.h>
#include <unistd.h>
#include <cerrno>
#include <chrono>
#include <cstdarg>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>
#include <memory>
#include "refresh_guard.h"
#include "refresh_guard_tests.h"

namespace {
volatile sig_atomic_t stopping = 0;
void onSignal(int) { stopping = 1; }
long long nowMs() {
    return std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
}
constexpr unsigned kUsage = 0x000c0600; // Six isolated single clicks captured on this pen.
constexpr unsigned kDoubleUsage = 0x000c0601; // Three isolated double clicks captured on this pen.
struct Decoder {
    unsigned usage = 0;
    bool dropping = false;
    unsigned feed(unsigned type, unsigned code, int value) {
        if (type == EV_SYN && code == SYN_DROPPED) { dropping = true; usage = 0; return false; }
        if (type == EV_SYN && code == SYN_REPORT) { dropping = false; usage = 0; return false; }
        if (dropping) return false;
        if (type == EV_MSC && code == MSC_SCAN) usage = static_cast<unsigned>(value);
        return type == EV_KEY && code == KEY_UNKNOWN && value == 1 &&
            (usage == kUsage || usage == kDoubleUsage) ? usage : 0;
    }
};
struct Gate {
    long long last = -1;
    bool accept(long long stamp, bool busy) {
        if (busy || (last >= 0 && stamp - last < 650)) return false;
        last = stamp; return true;
    }
};
std::string base;
void logLine(const char* fmt, ...) {
    char message[768]; va_list ap; va_start(ap, fmt); vsnprintf(message, sizeof(message), fmt, ap); va_end(ap);
    __android_log_print(ANDROID_LOG_INFO, "LenovoPenQuickNote", "%s", message);
    fprintf(stdout, "%s\n", message); fflush(stdout);
    if (base.empty()) return;
    const std::string path = base + "/run/bridge.log";
    struct stat st{};
    if (stat(path.c_str(), &st) == 0 && st.st_size > 512 * 1024) rename(path.c_str(), (path + ".1").c_str());
    FILE* f = fopen(path.c_str(), "a");
    if (f) { fprintf(f, "%lld %s\n", nowMs(), message); fclose(f); }
}
bool present(const char* flag) { return access((base + "/" + flag).c_str(), F_OK) == 0; }
bool shouldStop() {
    return stopping || present("disable") || present("remove") || present("run/stop") || access(base.c_str(), F_OK) != 0;
}
int findPen(std::string& found) {
    DIR* dir = opendir("/dev/input"); if (!dir) return -1;
    int result = -1;
    while (dirent* entry = readdir(dir)) {
        if (strncmp(entry->d_name, "event", 5) != 0) continue;
        const char* n = entry->d_name + 5;
        if (!*n || strspn(n, "0123456789") != strlen(n)) continue;
        std::string path = std::string("/dev/input/") + entry->d_name;
        int fd = open(path.c_str(), O_RDONLY | O_NONBLOCK | O_CLOEXEC | O_NOFOLLOW);
        if (fd < 0) continue;
        input_id id{}; char name[256]{};
        bool match = ioctl(fd, EVIOCGID, &id) >= 0 && ioctl(fd, EVIOCGNAME(sizeof(name)), name) >= 0 &&
            id.bustype == BUS_BLUETOOTH && id.vendor == 0x17ef && id.product == 0x617f &&
            strcmp(name, "Lenovo Tab Pen Plus Consumer Control") == 0;
        if (match) { found = path; result = fd; break; }
        close(fd);
    }
    closedir(dir); return result;
}
pid_t launch(unsigned usage) {
    pid_t pid = fork();
    if (pid == 0) {
        const std::string logfile = base + "/run/launcher.log";
        int out = open(logfile.c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_NOFOLLOW, 0600);
        if (out >= 0) { dup2(out, STDOUT_FILENO); dup2(out, STDERR_FILENO); close(out); }
        const std::string script = base + "/launch-note.sh";
        execl("/system/bin/sh", "sh", script.c_str(), usage == kDoubleUsage ? "--screen" : "--trigger", static_cast<char*>(nullptr));
        _exit(127);
    }
    return pid;
}
int selftest() {
    int checks = 0;
    auto check = [&](bool b) { ++checks; if (!b) { fprintf(stderr, "FAIL %d\n", checks); exit(1); } };
    Decoder d;
    check(!d.feed(EV_KEY, KEY_UNKNOWN, 1));
    check(!d.feed(EV_MSC, MSC_SCAN, kUsage));
    check(d.feed(EV_KEY, KEY_UNKNOWN, 1));
    check(!d.feed(EV_KEY, KEY_UNKNOWN, 0));
    check(!d.feed(EV_KEY, KEY_UNKNOWN, 2));
    d.feed(EV_SYN, SYN_REPORT, 0);
    check(!d.feed(EV_KEY, KEY_UNKNOWN, 1));
    d.feed(EV_MSC, MSC_SCAN, 0x000c0602);
    check(!d.feed(EV_KEY, KEY_UNKNOWN, 1));
    d.feed(EV_SYN, SYN_REPORT, 0); d.feed(EV_MSC, MSC_SCAN, kDoubleUsage);
    check(d.feed(EV_KEY, KEY_UNKNOWN, 1) == kDoubleUsage);
    check(!d.feed(EV_KEY, KEY_UNKNOWN, 0));
    check(!d.feed(EV_KEY, KEY_UNKNOWN, 2));
    d.feed(EV_SYN, SYN_REPORT, 0);
    check(!d.feed(EV_KEY, KEY_UNKNOWN, 1));
    d.feed(EV_MSC, MSC_SCAN, kUsage); d.feed(EV_SYN, SYN_DROPPED, 0);
    check(!d.feed(EV_KEY, KEY_UNKNOWN, 1));
    d.feed(EV_SYN, SYN_REPORT, 0); d.feed(EV_MSC, MSC_SCAN, kUsage);
    check(d.feed(EV_KEY, KEY_UNKNOWN, 1));
    Gate g; check(g.accept(1000, false)); check(!g.accept(1100, false));
    check(!g.accept(2000, true)); check(g.accept(2000, false));
    printf("SELFTEST_OK checks=%d; no input devices opened, no activities launched\n", checks); return 0;
}
}
int main(int argc, char** argv) {
    if (argc == 2 && strcmp(argv[1], "--self-test") == 0) return selftest();
    if (argc == 2 && strcmp(argv[1], "--refresh-self-test") == 0) return penrefresh::selftest();
    if (argc == 3 && strcmp(argv[1], "--replay") == 0) {
        FILE* file = fopen(argv[2], "rb"); if (!file) return 3;
        Decoder decoder; Gate gate; input_event e{}; unsigned clicks = 0, doubles = 0, count = 0;
        while (fread(&e, sizeof(e), 1, file) == 1) {
            ++count;
            unsigned usage = decoder.feed(e.type, e.code, e.value);
            if (usage && gate.accept(e.time.tv_sec * 1000LL + e.time.tv_usec / 1000, false)) {
                if (usage == kDoubleUsage) ++doubles; else ++clicks;
            }
        }
        bool ok = feof(file) && !ferror(file); fclose(file);
        printf("REPLAY_OK events=%u clicks=%u doubles=%u input_devices_opened=0 activities_started=0\n", count, clicks, doubles);
        return ok ? 0 : 1;
    }
    if (argc < 3) { fprintf(stderr, "usage: pen-bridge MODDIR --daemon|--observe|--once|--refresh-test|--restore-refresh [seconds]\n"); return 2; }
    char resolved[4096]; if (!realpath(argv[1], resolved)) return 3;
    base = resolved;
    bool refreshTest = strcmp(argv[2], "--refresh-test") == 0;
    bool restoreOnly = strcmp(argv[2], "--restore-refresh") == 0;
    bool daemon = strcmp(argv[2], "--daemon") == 0;
    bool observe = strcmp(argv[2], "--observe") == 0 || refreshTest;
    bool once = strcmp(argv[2], "--once") == 0;
    if (!observe && !once && !daemon && !restoreOnly) return 2;
    const int duration = argc > 3 ? atoi(argv[3]) : (once || observe ? 90 : 0);
    if (duration < 0 || duration > 3600) return 2;
    const long long deadline = duration ? nowMs() + duration * 1000LL : 0;
    umask(0077); mkdir((base + "/run").c_str(), 0700);
    int lock = open((base + "/run/daemon.lock").c_str(), O_CREAT | O_RDWR | O_CLOEXEC | O_NOFOLLOW, 0600);
    if (lock < 0 || flock(lock, LOCK_EX | LOCK_NB) != 0) { fprintf(stderr, "ALREADY_RUNNING_OR_LOCK_FAILED\n"); return 4; }
    signal(SIGTERM, onSignal); signal(SIGINT, onSignal);
    std::unique_ptr<penrefresh::DeviceBackend> refreshIo;
    std::unique_ptr<penrefresh::Guard> refresh;
    if (daemon || refreshTest || restoreOnly) {
        refreshIo = std::make_unique<penrefresh::DeviceBackend>(base, [](const std::string& msg) { logLine("%s", msg.c_str()); });
        refresh = std::make_unique<penrefresh::Guard>(*refreshIo, refreshIo->load());
    }
    if (restoreOnly) { bool ok = refresh->release(); close(lock); return ok ? 0 : 1; }
    logLine("START mode=%s single=0x%08x double=0x%08x uid=%d", argv[2], kUsage, kDoubleUsage, getuid());
    int fd = -1; Decoder decoder; Gate gate; long long nextScan = 0, nextRefresh = 0, launchedAt = 0;
    pid_t child = -1; bool fired = false; int actionExit = 0;
    while (!shouldStop() && (!deadline || nowMs() < deadline || child > 0)) {
        if (once && fired && child < 0) break;
        long long now = nowMs();
        if (child > 0) {
            int status = 0; pid_t done = waitpid(child, &status, WNOHANG);
            if (done == child) {
                actionExit = WIFEXITED(status) ? WEXITSTATUS(status) : 128 + WTERMSIG(status);
                logLine("LAUNCH_FINISHED exit=%d", actionExit); child = -1;
                if (once && fired) break;
            } else if (now - launchedAt > 15000) {
                kill(child, SIGKILL); waitpid(child, &status, 0); child = -1; actionExit = 124;
                logLine("LAUNCH_TIMEOUT"); if (once) break;
            }
        }
        if (fd < 0 && now >= nextScan) {
            std::string path; fd = findPen(path); nextScan = now + 2000;
            if (fd >= 0) { decoder = Decoder{}; logLine("CONNECTED %s", path.c_str()); }
        }
        if (refresh && now >= nextRefresh) { refresh->tick(fd >= 0); nextRefresh = nowMs() + 2000; }
        pollfd pfd{fd, POLLIN, 0};
        int ready = poll(fd >= 0 ? &pfd : nullptr, fd >= 0 ? 1 : 0, 400);
        if (ready < 0 && errno == EINTR) continue;
        if (ready < 0 || (fd >= 0 && pfd.revents & (POLLERR | POLLHUP | POLLNVAL))) {
            if (fd >= 0) close(fd); fd = -1; decoder = Decoder{}; logLine("DISCONNECTED"); continue;
        }
        if (fd < 0 || !(pfd.revents & POLLIN)) continue;
        input_event events[128]; ssize_t count = read(fd, events, sizeof(events));
        if (count < 0 && (errno == EAGAIN || errno == EINTR)) continue;
        if (count <= 0 || count % sizeof(input_event) != 0) { close(fd); fd = -1; decoder = Decoder{}; continue; }
        for (size_t i = 0; i < static_cast<size_t>(count) / sizeof(input_event); ++i) {
            const auto& e = events[i];
            if (e.type == EV_MSC && e.code == MSC_SCAN) logLine("SCAN usage=0x%08x", static_cast<unsigned>(e.value));
            unsigned hit = decoder.feed(e.type, e.code, e.value);
            if (!hit || !gate.accept(nowMs(), child > 0)) continue;
            logLine("%s%s", hit == kDoubleUsage ? "DOUBLE_CLICK" : "SINGLE_CLICK", observe ? " observe_only" : "");
            if (!observe) { child = launch(hit); launchedAt = nowMs(); fired = true; if (child < 0) { actionExit = 125; logLine("FORK_FAILED"); } }
        }
    }
    if (child > 0) { kill(child, SIGKILL); int status; waitpid(child, &status, 0); }
    if (refresh) refresh->release();
    if (fd >= 0) close(fd); close(lock); logLine("STOP");
    if (once && !fired) return 5;
    return actionExit;
}
