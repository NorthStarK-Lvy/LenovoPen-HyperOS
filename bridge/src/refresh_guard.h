#pragma once
#include <array>
#include <cmath>
#include <functional>
#include <optional>
#include <sstream>

namespace penrefresh {
struct Snapshot {
    int user = -1;
    std::string peak, miui, choice, smart;
};
inline bool rate(const std::string& value, double target) {
    char* end = nullptr;
    const double parsed = strtod(value.c_str(), &end);
    return end != value.c_str() && *end == '\0' && std::isfinite(parsed) && std::abs(parsed - target) < 0.1;
}
inline bool rawValue(const std::string& value) {
    if (value == "null") return true;
    if (value.empty() || value.size() > 32) return false;
    char* end = nullptr;
    double parsed = strtod(value.c_str(), &end);
    return end != value.c_str() && *end == '\0' && std::isfinite(parsed);
}
struct Backend {
    virtual ~Backend() = default;
    virtual std::optional<int> currentUser() = 0;
    virtual std::optional<Snapshot> read(int user) = 0;
    virtual bool write(int user, bool peak, const std::string& value) = 0;
    virtual bool save(const Snapshot& snapshot) = 0;
    virtual bool clear() = 0;
    virtual void log(const std::string& message) = 0;
};

// This policy never changes user_refresh_rate (the user's preference), min rate,
// or smart-fps mode. Only the two effective caps are owned and journaled.
class Guard {
    Backend& io;
    std::optional<Snapshot> original;
    bool blocked = false;
    std::string blockedChoice;
public:
    explicit Guard(Backend& backend, std::optional<Snapshot> saved = {}) : io(backend), original(saved) {}
    bool active() const { return original.has_value(); }
    bool release() {
        if (!original) return true;
        auto now = io.read(original->user);
        if (!now) { io.log("REFRESH_RESTORE_PENDING read_failed"); return false; }
        bool ok = true;
        if (now->choice == original->choice && now->smart == original->smart) {
            // Compare-and-restore each owned key. Never overwrite a changed key.
            if (rate(now->peak, 120) && now->peak != original->peak)
                ok = io.write(original->user, true, original->peak) && ok;
            if (rate(now->miui, 120) && now->miui != original->miui)
                ok = io.write(original->user, false, original->miui) && ok;
        } else io.log("REFRESH_RESTORE_SKIPPED user_preference_changed");
        if (!ok) { io.log("REFRESH_RESTORE_PENDING write_failed"); return false; }
        if (!io.clear()) return false;
        io.log("REFRESH_RELEASED user=" + std::to_string(original->user));
        original.reset(); return true;
    }
    void tick(bool connected) {
        if (!connected) { release(); blocked = false; return; }
        auto user = io.currentUser();
        if (!user) return;
        if (original && original->user != *user && !release()) return;
        auto now = io.read(*user);
        if (!now) return;
        if (original) {
            if (now->choice != original->choice || now->smart != original->smart ||
                !rate(now->peak, 120) || !rate(now->miui, 120)) {
                // A user, system policy or another module took over. Do not fight
                // that change or restore stale values on later disconnection.
                if (!io.clear()) return;
                original.reset(); blocked = true; blockedChoice = now->choice;
                io.log("REFRESH_YIELD preference_or_effective_cap_changed");
            }
            return;
        }
        if (blocked) {
            if (now->choice == blockedChoice) return;
            blocked = false;
        }
        if (!rate(now->choice, 144)) return;
        if (rate(now->peak, 120) && rate(now->miui, 120)) return;
        if (!io.save(*now)) { io.log("REFRESH_NOT_APPLIED journal_failed"); return; }
        original = now;
        bool peakOk = rate(now->peak, 120) || io.write(*user, true, "120");
        bool miuiOk = peakOk && (rate(now->miui, 120) || io.write(*user, false, "120"));
        auto after = io.read(*user);
        if (!peakOk || !miuiOk || !after || !rate(after->peak, 120) || !rate(after->miui, 120) ||
            after->choice != now->choice || after->smart != now->smart) {
            io.log("REFRESH_APPLY_FAILED rolling_back");
            release(); blocked = true; blockedChoice = now->choice; return;
        }
        io.log("REFRESH_CAPPED user=" + std::to_string(*user) + " effective=120 preference=" + now->choice);
    }
};

// Android's native cmd frontend avoids repeatedly starting an app_process VM.
inline bool command(const std::vector<std::string>& args, std::string& output) {
    int ends[2]; if (pipe2(ends, O_CLOEXEC | O_NONBLOCK) != 0) return false;
    std::vector<char*> argv;
    argv.push_back(const_cast<char*>("/system/bin/cmd"));
    for (const auto& arg : args) argv.push_back(const_cast<char*>(arg.c_str()));
    argv.push_back(nullptr);
    pid_t child = fork();
    if (child == 0) {
        close(ends[0]);
        int flags = fcntl(ends[1], F_GETFL);
        fcntl(ends[1], F_SETFL, flags & ~O_NONBLOCK);
        dup2(ends[1], STDOUT_FILENO); dup2(ends[1], STDERR_FILENO); close(ends[1]);
        execv(argv[0], argv.data()); _exit(127);
    }
    close(ends[1]);
    if (child < 0) { close(ends[0]); return false; }
    const long long end = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count() + 3000;
    bool exited = false, overflow = false; int status = 0; output.clear();
    while (true) {
        char buffer[512]; ssize_t n;
        while ((n = ::read(ends[0], buffer, sizeof(buffer))) > 0) {
            if (output.size() + static_cast<size_t>(n) <= 2048) output.append(buffer, n);
            else overflow = true;
        }
        if (!exited && waitpid(child, &status, WNOHANG) == child) exited = true;
        if (exited && n == 0) break;
        long long now = std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count();
        if (now >= end) {
            if (!exited) { kill(child, SIGKILL); waitpid(child, &status, 0); }
            close(ends[0]); return false;
        }
        pollfd p{ends[0], POLLIN, 0}; poll(&p, 1, 20);
    }
    close(ends[0]);
    while (!output.empty() && (output.back() == '\n' || output.back() == '\r')) output.pop_back();
    return !overflow && WIFEXITED(status) && WEXITSTATUS(status) == 0;
}

class DeviceBackend final : public Backend {
    std::string path;
    std::function<void(const std::string&)> logger;
    bool syncDirectory() {
        std::string parent = path.substr(0, path.rfind('/'));
        int fd = open(parent.c_str(), O_RDONLY | O_DIRECTORY | O_CLOEXEC);
        if (fd < 0) return false;
        bool ok = fsync(fd) == 0; close(fd); return ok;
    }
    bool get(int user, const char* space, const char* key, std::string& out) {
        return command({"settings", "--user", std::to_string(user), "get", space, key}, out) && rawValue(out);
    }
    static std::string journalDirectory(const std::string& module) {
        // Keep recovery data across module upgrades. Temporary test copies use
        // only their own staging directory and never touch the installed state.
        return module == "/data/adb/modules/lenovo_pen_quicknote"
            ? "/data/adb/lenovo_pen_quicknote" : module + "/run";
    }
public:
    DeviceBackend(const std::string& dir, std::function<void(const std::string&)> callback)
        : path(journalDirectory(dir) + "/refresh-state"), logger(std::move(callback)) {
        mkdir(journalDirectory(dir).c_str(), 0700);
    }
    std::optional<int> currentUser() override {
        std::string raw;
        if (!command({"activity", "get-current-user"}, raw) || raw.empty() || raw.size() > 8 ||
            raw.find_first_not_of("0123456789") != std::string::npos) return {};
        return atoi(raw.c_str());
    }
    std::optional<Snapshot> read(int user) override {
        Snapshot s; s.user = user;
        if (!get(user, "system", "peak_refresh_rate", s.peak) ||
            !get(user, "secure", "miui_refresh_rate", s.miui) ||
            !get(user, "secure", "user_refresh_rate", s.choice) ||
            !get(user, "system", "is_smart_fps", s.smart)) return {};
        return s;
    }
    bool write(int user, bool peak, const std::string& value) override {
        if (!rawValue(value)) return false;
        std::string out;
        std::vector<std::string> args{"settings", "--user", std::to_string(user),
            value == "null" ? "delete" : "put", peak ? "system" : "secure",
            peak ? "peak_refresh_rate" : "miui_refresh_rate"};
        if (value != "null") args.push_back(value);
        bool ok = command(args, out) && (out.empty() || (value == "null" && out.rfind("Deleted ", 0) == 0));
        log(std::string("REFRESH_WRITE ") + (peak ? "peak=" : "miui=") + value + " ok=" + (ok ? "1" : "0"));
        return ok;
    }
    bool save(const Snapshot& s) override {
        std::string contents = "1\n" + std::to_string(s.user) + "\n" + s.peak + "\n" + s.miui + "\n" + s.choice + "\n" + s.smart + "\n";
        int fd = open((path + ".tmp").c_str(), O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC | O_NOFOLLOW, 0600);
        if (fd < 0) return false;
        bool ok = ::write(fd, contents.data(), contents.size()) == static_cast<ssize_t>(contents.size()) && fsync(fd) == 0;
        close(fd);
        return ok && rename((path + ".tmp").c_str(), path.c_str()) == 0 && syncDirectory();
    }
    bool clear() override { return (unlink(path.c_str()) == 0 || errno == ENOENT) && syncDirectory(); }
    void log(const std::string& message) override { logger(message); }
    std::optional<Snapshot> load() {
        int fd = open(path.c_str(), O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
        if (fd < 0) return {};
        char data[256]; ssize_t n = ::read(fd, data, sizeof(data)); close(fd);
        if (n <= 0 || n >= static_cast<ssize_t>(sizeof(data))) { log("REFRESH_INVALID_JOURNAL"); return {}; }
        std::istringstream in(std::string(data, n));
        std::string version, user; Snapshot s;
        if (!std::getline(in, version) || version != "1" || !std::getline(in, user) || user.empty() || user.size() > 8 ||
            user.find_first_not_of("0123456789") != std::string::npos ||
            !std::getline(in, s.peak) || !std::getline(in, s.miui) || !std::getline(in, s.choice) || !std::getline(in, s.smart) ||
            !rawValue(s.peak) || !rawValue(s.miui) || !rate(s.choice, 144) || !rawValue(s.smart)) {
            log("REFRESH_INVALID_JOURNAL"); return {};
        }
        s.user = atoi(user.c_str()); log("REFRESH_RECOVERY_LOADED user=" + user); return s;
    }
};
}
