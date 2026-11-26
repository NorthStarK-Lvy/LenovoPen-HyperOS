package com.alyssum.relegion;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Local mirror of Lenovo pen metadata and settings used by the Xposed bridge. */
public final class PenOfficialState {
    public static final String PREFS = "pen_official_state";
    public static final String KEY_ADDRESS = "address";
    public static final String KEY_SERIAL = "serial";
    public static final String KEY_VERSION = "version";
    public static final String KEY_MODEL = "model";
    public static final String KEY_BATTERY = "battery";
    public static final String KEY_CHARGING = "charging";
    public static final String KEY_ATTACHED = "attached";
    public static final String KEY_CONNECTED = "connected";
    public static final String KEY_CLICK_ACTION = "click_action";
    public static final String KEY_DOUBLE_ACTION = "double_action";
    public static final String KEY_DOUBLE_PACKAGE = "double_package";
    public static final String KEY_OPEN_PACKAGE = "open_package";
    public static final String KEY_CONSUMER_PACKAGES = "consumer_packages";
    public static final String KEY_CONSUMERS_ENABLED = "consumers_enabled";
    public static final String KEY_ROOT_ENABLED = "root_bridge_enabled";
    public static final String DEFAULT_CLICK_ACTION = PenActions.CREATE_NOTE;
    public static final String DEFAULT_DOUBLE_ACTION = PenActions.SCREEN_RECOGNITION;
    public static final List<String> DEFAULT_CONSUMERS = PenActions.noteAppCandidates();
    private PenOfficialState() {}

    public static Bundle read(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Bundle b = new Bundle();
        b.putString("address", p.getString(KEY_ADDRESS, ""));
        b.putString("serial", p.getString(KEY_SERIAL, ""));
        b.putString("version", p.getString(KEY_VERSION, ""));
        b.putString("model", p.getString(KEY_MODEL, ""));
        b.putInt("battery", p.getInt(KEY_BATTERY, -1));
        b.putBoolean("charging", p.getBoolean(KEY_CHARGING, false));
        b.putBoolean("attached", p.getBoolean(KEY_ATTACHED, false));
        b.putBoolean("connected", p.getBoolean(KEY_CONNECTED, false));
        b.putString("clickAction", p.getString(KEY_CLICK_ACTION, DEFAULT_CLICK_ACTION));
        b.putString("doubleAction", p.getString(KEY_DOUBLE_ACTION, DEFAULT_DOUBLE_ACTION));
        b.putString("openPackage", p.getString(KEY_OPEN_PACKAGE, ""));
        b.putString("doublePackage", p.getString(KEY_DOUBLE_PACKAGE, ""));
        b.putStringArrayList("consumerPackages", new ArrayList<>(consumers(context)));
        b.putBoolean("consumersEnabled", p.getBoolean(KEY_CONSUMERS_ENABLED, true));
        b.putBoolean("rootBridgeEnabled", p.getBoolean(KEY_ROOT_ENABLED, false));
        b.putLong("sampled", p.getLong("sampled", 0));
        return b;
    }

    public static Set<String> consumers(Context context) {
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_CONSUMER_PACKAGES, "");
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (!raw.isBlank()) result.addAll(Arrays.asList(raw.split("\\n")));
        if (result.isEmpty()) result.addAll(DEFAULT_CONSUMERS);
        result.removeIf(s -> s == null || s.isBlank() || s.length() > 200 || s.indexOf('/') >= 0);
        return result;
    }

    public static boolean isConsumer(Context context, String packageName) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_CONSUMERS_ENABLED, true)
                && packageName != null && consumers(context).contains(packageName);
    }
    public static void setConsumersEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_CONSUMERS_ENABLED, enabled).apply();
    }
    public static void setRootEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ROOT_ENABLED, enabled).apply();
    }

    public static void update(Context context, String address, String serial, String version,
            String model, int battery, boolean charging, boolean attached, boolean connected) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_ADDRESS, safe(address)).putString(KEY_SERIAL, safe(serial))
                .putString(KEY_VERSION, safe(version)).putString(KEY_MODEL, safe(model))
                .putInt(KEY_BATTERY, battery >= 0 && battery <= 100 ? battery : -1)
                .putBoolean(KEY_CHARGING, charging).putBoolean(KEY_ATTACHED, attached)
                .putBoolean(KEY_CONNECTED, connected).putLong("sampled", SystemClock.elapsedRealtime())
                .apply();
    }

    public static void setClickAction(Context context, String action, String openPackage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_CLICK_ACTION, PenActions.normalize(action, DEFAULT_CLICK_ACTION))
                .putString(KEY_OPEN_PACKAGE, PenActions.isPackageName(openPackage) ? openPackage : "").apply();
    }
    public static void setDoubleAction(Context context, String action, String openPackage) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_DOUBLE_ACTION, PenActions.normalize(action, DEFAULT_DOUBLE_ACTION))
                .putString(KEY_DOUBLE_PACKAGE, PenActions.isPackageName(openPackage) ? openPackage : "").apply();
    }

    public static void setConsumers(Context context, Set<String> values) {
        LinkedHashSet<String> clean = new LinkedHashSet<>();
        if (values != null) for (String value : values) {
            if (value != null && value.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")) clean.add(value);
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_CONSUMER_PACKAGES, String.join("\n", clean)).apply();
    }

    private static String safe(String value) { return value == null ? "" : value.substring(0, Math.min(200, value.length())); }
}
