package com.alyssum.relegion.hook;

import android.content.SharedPreferences;
import android.util.Log;
import com.alyssum.relegion.LogLevel;
import io.github.libxposed.api.XposedModule;

public final class HookLog {
    private static volatile SharedPreferences preferences;

    private HookLog() {}

    static void initialize(XposedModule module) {
        try {
            preferences = module.getRemotePreferences(LogLevel.GROUP);
        } catch (RuntimeException ignored) {
            preferences = null;
        }
    }

    public static void write(XposedModule module, int priority, String tag, String message) {
        write(module, priority, tag, message, null);
    }

    public static void write(XposedModule module, int priority, String tag, String message, Throwable error) {
        if (priority < Log.WARN || !LogLevel.read(preferences).allows(priority)) return;
        if (error == null) module.log(priority, tag, message);
        else module.log(priority, tag, message, error);
    }
}
