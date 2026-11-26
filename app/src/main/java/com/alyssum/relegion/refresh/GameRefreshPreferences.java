package com.alyssum.relegion.refresh;

import android.content.SharedPreferences;

/** Shared by the companion UI, PowerKeeper and system_server. */
public final class GameRefreshPreferences {
    public static final String GROUP = "game_refresh";
    public static final String KEY_ENABLED = "enabled";

    private GameRefreshPreferences() {}

    public static boolean isEnabled(SharedPreferences preferences) {
        // Preserve the behavior of installations made before the switch was added.
        return preferences.getBoolean(KEY_ENABLED, true);
    }
}
