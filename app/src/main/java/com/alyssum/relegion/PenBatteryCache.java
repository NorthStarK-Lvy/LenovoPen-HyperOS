package com.alyssum.relegion;

import java.util.HashMap;
import java.util.Map;

/** Battery reports are sparse; connection freshness must not erase a valid report. */
public final class PenBatteryCache {
    public static final long MAX_AGE_MS = 24 * 60 * 60 * 1000L;
    private record Reading(int level, long sampled) {}
    private final Map<String, Reading> readings = new HashMap<>();
    public synchronized int update(String device, boolean connected, int level, long sampled, long now) {
        if (device == null || device.isBlank()) return -1;
        readings.entrySet().removeIf(entry -> !validAge(now, entry.getValue().sampled));
        Reading previous = readings.get(device);
        if (level >= 0 && level <= 100 && validAge(now, sampled)
                && (previous == null || sampled >= previous.sampled)) {
            previous = new Reading(level, sampled);
            readings.put(device, previous);
        }
        return connected && previous != null ? previous.level : -1;
    }
    public synchronized long sampled(String device) {
        Reading value = readings.get(device);
        return value == null ? 0 : value.sampled;
    }
    private static boolean validAge(long now, long sampled) {
        return sampled > 0 && now >= sampled && now - sampled <= MAX_AGE_MS;
    }
}
