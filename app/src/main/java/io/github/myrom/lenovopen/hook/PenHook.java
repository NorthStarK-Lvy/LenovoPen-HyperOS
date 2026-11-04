package io.github.myrom.lenovopen.hook;

import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.myrom.lenovopen.Protocol;
import io.github.myrom.lenovopen.PenIdentity;

public final class PenHook implements IXposedHookLoadPackage {
    private static final String CARD = Protocol.MODULE + ".card";
    static final String RESTORING = Protocol.MODULE + ".restoring";
    private static final String MASK = Protocol.MODULE + ".preference_mask";
    private static boolean bridgeRegistered;
    private static final PenReader READER = new PenReader();
    static void log(Throwable error) { XposedBridge.log("[LenovoPenUI] " + error); }
    static Bundle read() { return READER.read(); }
    static void publish(Context context, Bundle snapshot) {
        try { context.getContentResolver().call(Uri.parse("content://" + Protocol.AUTHORITY), "publish", null, snapshot); }
        catch (RuntimeException e) { log(e); }
    }
    private static synchronized void registerBridge(Context context) {
        if (bridgeRegistered) return;
        try {
            context.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent intent) { publish(c, read()); }
            }, new IntentFilter(Protocol.REQUEST), Protocol.PERMISSION, new Handler(Looper.getMainLooper()), Context.RECEIVER_EXPORTED);
            Handler events = new Handler(Looper.getMainLooper());
            IntentFilter connections = new IntentFilter();
            connections.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
            connections.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
            connections.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
            context.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent intent) {
                    String action = intent.getAction();
                    if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) { publish(c, read()); return; }
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
                    if (!READER.matchesDevice(device)) return;
                    boolean connected = BluetoothDevice.ACTION_ACL_CONNECTED.equals(action);
                    Runnable sample = () -> {
                        Bundle state = read();
                        if (connected && state.getBoolean("connected")) state.putString("event", "connected");
                        publish(c, state);
                    };
                    // HID/cache registration may lag behind ACL; sample again without another alert.
                    events.postDelayed(sample, connected ? 800 : 500);
                    if (connected) events.postDelayed(sample, 2500);
                }
            }, connections, null, events, Context.RECEIVER_EXPORTED);
            bridgeRegistered = true;
            XposedBridge.log("[LenovoPenUI] state bridge ready");
        } catch (RuntimeException e) { log(e); }
    }
    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam load) {
        if (!Protocol.TARGET.equals(load.packageName)) return;
        try {
            XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) { registerBridge((Application) p.thisObject); }
            });
            // Resolve the descriptor through this APK's class loader; never bundle host AndroidX.
            Class<?> page = XposedHelpers.findClass("t2.A", load.classLoader);
            if (!"q1.o".equals(page.getSuperclass().getName())
                    || page.getDeclaredField("l").getType() != Context.class
                    || !"t2.l".equals(page.getDeclaredField("w").getType().getName())) {
                throw new IllegalStateException("Unsupported SecurityCore page layout");
            }
            XposedHelpers.findAndHookConstructor("t2.l", load.classLoader, Context.class, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.hasThrowable() || p.args[0] == null) return;
                    try {
                        String owner = p.args[0].getClass().getName();
                        if (!"com.miui.miinput.stylus.MiuiHandwritingSettingsActivity".equals(owner)) return;
                        Bundle state = read();
                        if (PenIdentity.allowHandwritingEntrance(owner, state.getBoolean("connected"), !state.getString("error", "").isEmpty())) {
                            // This one-off detector is created only for this Activity's entrance guard.
                            // Keep its version field and every main-page/global detector unchanged.
                            XposedHelpers.setBooleanField(p.thisObject, "b", true);
                        }
                    } catch (Throwable e) { log(e); }
                }
            });
            XposedHelpers.findAndHookMethod(page, "b", boolean.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (Boolean.TRUE.equals(XposedHelpers.getAdditionalInstanceField(p.thisObject, RESTORING))) return;
                    try {
                        Object detector = XposedHelpers.getObjectField(p.thisObject, "w");
                        boolean xiaomi = detector != null && XposedHelpers.getBooleanField(detector, "b");
                        Bundle state = read();
                        if (!PenIdentity.overrideNativeConnection(xiaomi, state.getBoolean("paired"), !state.getString("error", "").isEmpty())) {
                            restorePreferenceFlags(p.thisObject);
                            return;
                        }
                        // Only this page's render argument changes. Do not write its detector.b,
                        // detector.c, setting_stylus_version, or any global Xiaomi model state.
                        p.args[0] = state.getBoolean("connected");
                        p.setObjectExtra("lenovo_state", state);
                    } catch (Throwable e) { log(e); }
                }
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.hasThrowable() || !(p.getObjectExtra("lenovo_state") instanceof Bundle)) return;
                    try { restrictUnsupported(p.thisObject, (Bundle) p.getObjectExtra("lenovo_state")); }
                    catch (Throwable e) { log(e); }
                }
            });
            XposedHelpers.findAndHookMethod(page, "onResume", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (p.hasThrowable()) return;
                    try {
                        NativeCard card = (NativeCard) XposedHelpers.getAdditionalInstanceField(p.thisObject, CARD);
                        if (card == null) {
                            card = new NativeCard(p.thisObject, load.classLoader);
                            XposedHelpers.setAdditionalInstanceField(p.thisObject, CARD, card);
                        }
                        card.start();
                    } catch (Throwable e) { log(e); }
                }
            });
            // onPause is inherited in this exact APK. Hook its real declaring method,
            // and filter by the page type so keyboard and other fragments are unaffected.
            XposedBridge.hookMethod(page.getMethod("onPause"), new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (!page.isInstance(p.thisObject)) return;
                    stop(p.thisObject, false);
                }
            });
            XposedBridge.hookMethod(page.getMethod("onDestroyView"), new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (page.isInstance(p.thisObject)) stop(p.thisObject, true);
                }
            });
            XposedHelpers.findAndHookMethod(page, "onDestroy", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) { stop(p.thisObject, true); }
            });
            XposedBridge.log("[LenovoPenUI] hooks installed for t2.A");
        } catch (Throwable e) { log(e); }
    }
    private static void restrictUnsupported(Object page, Bundle state) {
        if (XposedHelpers.getAdditionalInstanceField(page, MASK) == null) {
            java.util.List<Object[]> saved = new java.util.ArrayList<>();
            for (String name : new String[]{"k", "e", "f", "g", "i", "j", "z", "A", "B", "C", "D", "x", "y"}) {
                Object item = XposedHelpers.getObjectField(page, name);
                if (item != null) saved.add(new Object[]{item, XposedHelpers.callMethod(item, "isEnabled"),
                        XposedHelpers.callMethod(item, "isVisible"),
                        name.equals("x") || name.equals("y") ? XposedHelpers.getBooleanField(item, "g") : null});
            }
            Object ota = XposedHelpers.callMethod(page, "findPreference", "firmware_update");
            if (ota != null) saved.add(new Object[]{ota, XposedHelpers.callMethod(ota, "isEnabled"), XposedHelpers.callMethod(ota, "isVisible"), null});
            XposedHelpers.setAdditionalInstanceField(page, MASK, saved);
        }
        boolean online = state.getBoolean("connected");
        // User confirmed native handwriting works. Both variants are retained;
        // the original APK chooses which one is visible for this device.
        for (String name : new String[]{"c", "d"}) {
            Object item = XposedHelpers.getObjectField(page, name);
            if (item != null) XposedHelpers.callMethod(item, "setEnabled", online);
        }
        for (String name : new String[]{"k", "e", "f", "g", "i", "j", "z", "A", "B", "C", "D"}) {
            Object item = XposedHelpers.getObjectField(page, name);
            if (item != null) XposedHelpers.callMethod(item, "setEnabled", false);
        }
        for (String name : new String[]{"x", "y"}) {
            Object item = XposedHelpers.getObjectField(page, name);
            if (item != null) {
                XposedHelpers.callMethod(item, "setEnabled", false);
                XposedHelpers.setBooleanField(item, "g", false);
            }
        }
        Object firmware = XposedHelpers.callMethod(page, "findPreference", "firmware_update");
        if (firmware != null) {
            XposedHelpers.callMethod(firmware, "setEnabled", false);
            XposedHelpers.callMethod(firmware, "setVisible", false);
        }
    }
    @SuppressWarnings("unchecked")
    static void restorePreferenceFlags(Object page) {
        Object saved = XposedHelpers.removeAdditionalInstanceField(page, MASK);
        if (!(saved instanceof java.util.List)) return;
        for (Object[] flags : (java.util.List<Object[]>) saved) {
            XposedHelpers.callMethod(flags[0], "setEnabled", flags[1]);
            XposedHelpers.callMethod(flags[0], "setVisible", flags[2]);
            if (flags[3] instanceof Boolean) XposedHelpers.setBooleanField(flags[0], "g", (Boolean) flags[3]);
        }
    }
    private static void stop(Object page, boolean remove) {
        try {
            NativeCard card = (NativeCard) XposedHelpers.getAdditionalInstanceField(page, CARD);
            if (card != null) {
                if (remove) card.dispose(); else card.stop();
            }
            if (remove) XposedHelpers.removeAdditionalInstanceField(page, CARD);
        } catch (Throwable e) { log(e); }
    }
}
