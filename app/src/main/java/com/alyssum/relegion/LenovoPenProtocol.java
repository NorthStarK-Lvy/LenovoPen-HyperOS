package com.alyssum.relegion;

import com.alyssum.relegion.hook.HookLog;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import com.alyssum.relegion.hook.PenHook;
import io.github.libxposed.api.XposedModule;
import java.util.ArrayList;

/** Receives Lenovo's pen service protocol inside the host process with host privileges. */
public final class LenovoPenProtocol {
    static final String ACTION_BT = "lenovo.intent.action.PEN_BT_CHANGED";
    static final String ACTION_BATTERY = "lenovo.intent.action.PEN_BATTERY_CHANGED";
    static final String ACTION_SERIAL = "lenovo.intent.action.PEN_SN";
    static final String ACTION_VERSION = "lenovo.intent.action.PEN_VERSION";
    static final String ACTION_ATTACH = "lenovo.intent.action.PEN_ATTACH_CHANGED";
    static final String ACTION_KEY = "lenovo.intent.action.PEN_KEYEVENT";
    static final String ACTION_CLICK = "lenovo.intent.action.INPUT_DEVICE_CLICK_STATE_CHANGED";
    static final String EXTRA_TYPE = "type";
    static final int KEY_ONE_CLICK = 600;
    public static final int KEY_TWO_CLICK = 601;
    static final int KEY_THREE_CLICK = 602;
    static final int KEY_LONG_CLICK = 603;
    public static final int KEY_PRESS_CLICK = 604;
    private static XposedModule module;
    private static boolean registered;

    private LenovoPenProtocol() {}
    public static void install(XposedModule api, Context context) {
        module = api;
        if (registered) return;
        try {
            context.registerReceiver(new android.content.BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent intent) { receive(c, intent); }
            }, filter(), null, null, Context.RECEIVER_EXPORTED);
            registered = true;
            HookLog.write(api, Log.INFO, "RELEGION", "Lenovo pen protocol receiver installed");
        } catch (RuntimeException e) { HookLog.write(api, Log.ERROR, "RELEGION", "Pen protocol receiver failed", e); }
    }
    private static android.content.IntentFilter filter() {
        android.content.IntentFilter f = new android.content.IntentFilter();
        for (String a : new String[]{ACTION_BT, ACTION_BATTERY, ACTION_SERIAL, ACTION_VERSION, ACTION_ATTACH}) f.addAction(a);
        return f;
    }
    private static void receive(Context context, Intent intent) {
        String action = intent.getAction();
        if (ACTION_BT.equals(action)) {
            boolean connected = intent.getBooleanExtra("connected", intent.getIntExtra("state", 0) == 1);
            String address = string(intent, "address", "mac");
            Bundle old = PenOfficialState.read(context);
            String model = intent.getStringExtra("name");
            String serial = intent.getStringExtra("sn");
            int level = intent.getIntExtra("level", -1);
            if (level < 0) level = intent.getIntExtra("battery", -1);
            PenOfficialState.update(context, address,
                    serial == null ? old.getString("serial", "") : serial,
                    old.getString("version", ""), model == null ? old.getString("model", "") : model,
                    level < 0 ? old.getInt("battery", -1) : level, old.getBoolean("charging"),
                    old.getBoolean("attached"), connected);
        } else if (ACTION_BATTERY.equals(action)) {
            int battery = intent.getIntExtra("level", intent.getIntExtra("battery", -1));
            boolean charging = intent.getIntExtra("charging_state", intent.getIntExtra("charging", 0)) != 0;
            boolean attached = intent.getIntExtra("attached", intent.getIntExtra("status", 0)) != 0;
            Bundle old = PenOfficialState.read(context);
            PenOfficialState.update(context, old.getString("address", ""), old.getString("serial", ""),
                    old.getString("version", ""), old.getString("model", ""), battery, charging, attached,
                    old.getBoolean("connected"));
        } else if (ACTION_SERIAL.equals(action)) {
            Bundle old = PenOfficialState.read(context);
            PenOfficialState.update(context, old.getString("address", ""), string(intent, "sn", "serial"),
                    old.getString("version", ""), old.getString("model", ""), old.getInt("battery", -1),
                    old.getBoolean("charging"), old.getBoolean("attached"), old.getBoolean("connected"));
        } else if (ACTION_VERSION.equals(action)) {
            Bundle old = PenOfficialState.read(context);
            PenOfficialState.update(context, old.getString("address", ""), old.getString("serial", ""),
                    string(intent, "version", "firmware_version"), old.getString("model", ""),
                    old.getInt("battery", -1), old.getBoolean("charging"), old.getBoolean("attached"),
                    old.getBoolean("connected"));
        } else if (ACTION_ATTACH.equals(action)) {
            Bundle old = PenOfficialState.read(context);
            PenOfficialState.update(context, old.getString("address", ""), old.getString("serial", ""),
                    old.getString("version", ""), old.getString("model", ""), old.getInt("battery", -1),
                    old.getBoolean("charging"), intent.getIntExtra("status", 0) != 0,
                    old.getBoolean("connected"));
        }
        PenHook.publish(context, PenHook.read());
    }
    private static String string(Intent i, String first, String second) {
        String value = i.getStringExtra(first); return value == null ? i.getStringExtra(second) : value;
    }
    static boolean isConsumer(Context context, String packageName) { return PenOfficialState.isConsumer(context, packageName); }
    static boolean isPenKey(int keyCode) { return keyCode >= KEY_ONE_CLICK && keyCode <= KEY_PRESS_CLICK; }
    static boolean consumeForApp(Context context, String packageName) { return isConsumer(context, packageName); }
}
