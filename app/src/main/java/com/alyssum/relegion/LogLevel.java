package com.alyssum.relegion;

import android.content.SharedPreferences;
import android.util.Log;

public enum LogLevel {
    WARN(Log.WARN), ERROR(Log.ERROR), OFF(Integer.MAX_VALUE);

    public static final String GROUP = "diagnostics";
    public static final String KEY = "log_level";
    private final int minimum;

    LogLevel(int minimum) { this.minimum = minimum; }

    public boolean allows(int priority) {
        return this != OFF && priority >= minimum;
    }

    public static LogLevel read(SharedPreferences preferences) {
        if (preferences == null) return WARN;
        try {
            return valueOf(preferences.getString(KEY, WARN.name()));
        } catch (RuntimeException ignored) {
            return WARN;
        }
    }
}
