#pragma once
#include <map>
namespace penrefresh {
inline int selftest() {
    struct Fake final : Backend {
        int user = 0, writes = 0, failWrite = 0;
        bool failRead = false, failSave = false;
        std::map<int, Snapshot> values{{0, {0, "144", "144", "144", "0"}}, {10, {10, "144", "144", "144", "0"}}};
        std::optional<Snapshot> saved;
        std::optional<int> currentUser() override { return user; }
        std::optional<Snapshot> read(int id) override { return failRead ? std::nullopt : std::optional(values.at(id)); }
        bool write(int id, bool peak, const std::string& v) override {
            if (++writes == failWrite) return false;
            (peak ? values.at(id).peak : values.at(id).miui) = v; return true;
        }
        bool save(const Snapshot& s) override { if (failSave) return false; saved = s; return true; }
        bool clear() override { saved.reset(); return true; }
        void log(const std::string&) override {}
    };
    int checks = 0;
    auto check = [&](bool ok) { if (!ok) { fprintf(stderr, "REFRESH_TEST_FAIL %d\n", checks + 1); exit(1); } ++checks; };
    {
        Fake f; Guard g(f); g.tick(false); check(f.writes == 0);
        g.tick(true); check(g.active() && f.saved.has_value());
        check(f.values[0].peak == "120" && f.values[0].miui == "120" && f.values[0].choice == "144");
        g.tick(true); check(f.writes == 2);
        g.tick(false); check(!g.active() && !f.saved && f.values[0].peak == "144" && f.values[0].miui == "144");
    }
    for (const char* choice : {"60", "120"}) {
        Fake f; f.values[0] = {0, choice, choice, choice, "0"}; Guard g(f); g.tick(true);
        check(!g.active() && f.writes == 0);
    }
    {
        Fake f; Guard g(f); g.tick(true);
        f.values[0] = {0, "60", "60", "60", "0"}; g.tick(true); g.tick(false);
        check(!g.active() && !f.saved && f.values[0].peak == "60" && f.writes == 2);
    }
    {
        Fake f; Guard g(f); g.tick(true); f.values[0].choice = "120";
        g.tick(false); check(!g.active() && f.values[0].peak == "120" && f.values[0].choice == "120");
    }
    {
        Fake f; f.failWrite = 2; Guard g(f); g.tick(true);
        check(!g.active() && !f.saved && f.values[0].peak == "144" && f.values[0].miui == "144");
    }
    {
        Fake f; f.failSave = true; Guard g(f); g.tick(true); check(f.writes == 0 && !g.active());
    }
    {
        Fake f; Snapshot saved = f.values[0]; f.saved = saved; f.values[0].peak = f.values[0].miui = "120";
        Guard g(f, saved); g.tick(false); check(f.values[0].peak == "144" && f.values[0].miui == "144" && !f.saved);
    }
    {
        Fake f; Snapshot saved = f.values[0]; f.values[0] = {0, "60", "60", "60", "0"};
        Guard g(f, saved); g.tick(false); check(f.writes == 0 && f.values[0].choice == "60");
    }
    {
        Fake f; Guard g(f); g.tick(true); f.user = 10; g.tick(true);
        check(f.values[0].peak == "144" && f.values[10].peak == "120" && f.saved->user == 10);
        g.tick(false); check(f.values[10].peak == "144");
    }
    {
        Fake f; Guard g(f); g.tick(true); f.failRead = true; g.tick(false); check(g.active() && f.saved.has_value());
        f.failRead = false; g.tick(false); check(!g.active() && f.values[0].peak == "144");
    }
    {
        Fake f; Guard g(f); g.tick(true); f.values[0].miui = "90"; g.tick(false);
        check(f.values[0].miui == "90" && f.values[0].peak == "144");
    }
    {
        Fake f; Guard g(f); g.tick(true); f.values[0].smart = "1"; g.tick(true); g.tick(false);
        check(!g.active() && f.writes == 2 && f.values[0].smart == "1");
    }
    {
        Fake f; Guard g(f); g.tick(true); f.values[0].peak = "90"; g.tick(true); g.tick(true);
        check(!g.active() && f.values[0].peak == "90" && f.writes == 2);
        f.values[0] = {0, "120", "120", "120", "0"}; g.tick(true);
        f.values[0] = {0, "144", "144", "144", "0"}; g.tick(true); check(g.active() && f.values[0].peak == "120");
    }
    check(rate("144.0", 144) && !rate("nan", 144) && !rawValue("144;bad") && rawValue("null"));
    printf("REFRESH_SELFTEST_OK checks=%d real_settings_writes=0 input_devices_opened=0\n", checks);
    return 0;
}
}
