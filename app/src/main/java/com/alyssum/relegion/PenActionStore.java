package com.alyssum.relegion;

import android.content.Context;
import android.os.Bundle;
import java.util.Set;

/** Local editor for the official Lenovo pen actions bridged by the Xposed host. */
public final class PenActionStore {
    private PenActionStore() {}
    public static String action(Context c) { return c.getSharedPreferences(PenOfficialState.PREFS, 0)
            .getString(PenOfficialState.KEY_CLICK_ACTION, PenOfficialState.DEFAULT_CLICK_ACTION); }
    public static String openPackage(Context c) { return c.getSharedPreferences(PenOfficialState.PREFS, 0)
            .getString(PenOfficialState.KEY_OPEN_PACKAGE, ""); }
    public static String doubleAction(Context c) { return c.getSharedPreferences(PenOfficialState.PREFS, 0)
            .getString(PenOfficialState.KEY_DOUBLE_ACTION, PenOfficialState.DEFAULT_DOUBLE_ACTION); }
    public static String doublePackage(Context c) { return c.getSharedPreferences(PenOfficialState.PREFS, 0)
            .getString(PenOfficialState.KEY_DOUBLE_PACKAGE, ""); }
    public static void saveDoubleAction(Context c, String action, String pkg) { PenOfficialState.setDoubleAction(c, action, pkg); }
    public static Set<String> consumers(Context c) { return PenOfficialState.consumers(c); }
    public static void save(Context c, String action, String pkg) { PenOfficialState.setClickAction(c, action, pkg); }
    public static void saveConsumers(Context c, Set<String> packages) { PenOfficialState.setConsumers(c, packages); }
    public static boolean consumersEnabled(Context c) { return PenOfficialState.read(c).getBoolean("consumersEnabled", true); }
    public static void setConsumersEnabled(Context c, boolean enabled) { PenOfficialState.setConsumersEnabled(c, enabled); }
    public static Bundle state(Context c) { return PenOfficialState.read(c); }
}
