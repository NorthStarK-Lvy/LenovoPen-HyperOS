package com.alyssum.relegion.hook;

import java.util.Map;

final class GameRefreshPolicy {
    private GameRefreshPolicy() {}

    static int targetRate(int selected, int[] supported) {
        if (supported == null) return 0;
        int target = 0;
        for (int rate : supported) {
            if (rate > 60 && rate <= selected && rate > target) target = rate;
        }
        return target;
    }

    static float gameDefaultRate(float rate) {
        return rate == 60f ? 0f : rate;
    }

    /** Owns a single absent entry during a synchronous policy calculation on the host handler. */
    static final class Profile implements AutoCloseable {
        private final Map<String, Integer> profiles;
        private final String pkg;
        private final Integer value;
        private boolean closed;

        private Profile(Map<String, Integer> profiles, String pkg, int rate) {
            this.profiles = profiles;
            this.pkg = pkg;
            value = rate;
            profiles.put(pkg, value);
        }

        static Profile open(Map<String, Integer> profiles, String pkg, int rate) {
            return profiles.containsKey(pkg) ? null : new Profile(profiles, pkg, rate);
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            if (value.equals(profiles.get(pkg))) profiles.remove(pkg);
        }
    }
}
