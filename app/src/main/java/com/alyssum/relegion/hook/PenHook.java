package com.alyssum.relegion.hook;

import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.util.Log;
import io.github.libxposed.api.XposedModule;
import com.alyssum.relegion.Protocol;
import com.alyssum.relegion.PenIdentity;

public final class PenHook {
    private static final String CARD = Protocol.MODULE + ".card";
    static final String RESTORING = Protocol.MODULE + ".restoring";
    private static final String MASK = Protocol.MODULE + ".preference_mask";
    private static final PenReader READER = new PenReader();
    private static Context appContext;
    private static XposedModule framework;
    private static boolean bridgeRegistered;
    static void log(Throwable e) { framework.log(Log.ERROR, "RELEGION", "Pen hook", e); }
    static void info(String message) { if (framework != null) framework.log(Log.INFO, "RELEGION", message); }
    public static Bundle read() {
        Bundle state = READER.read();
        try {
            Bundle official = appContext == null ? null : com.alyssum.relegion.PenOfficialState.read(appContext);
            if (official == null) return state;
            // The official service does not exist on this ROM; its empty values must not erase GATT-read ones.
            for (String key : new String[]{"serial", "version", "model", "clickAction", "openPackage"}) {
                String value = official.getString(key, "");
                if (!value.isEmpty()) state.putString(key, value);
            }
            state.putInt("officialBattery", official.getInt("battery", -1));
            state.putBoolean("charging", official.getBoolean("charging"));
            state.putBoolean("attached", official.getBoolean("attached"));
            state.putStringArrayList("consumerPackages", official.getStringArrayList("consumerPackages"));
        } catch (RuntimeException ignored) { }
        return state;
    }
    public static void publish(Context context, Bundle state) {
        try { context.getContentResolver().call(Uri.parse("content://" + Protocol.AUTHORITY), "publish", null, state); }
        catch (RuntimeException e) { log(e); }
    }
    private static synchronized void registerBridge(Context context) {
        if (bridgeRegistered) return;
        // install() runs before the Application exists, so the GATT reader gets its context here.
        appContext = context;
        READER.setContext(context);
        try {
            Handler events = new Handler(Looper.getMainLooper());
            context.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent intent) { publish(c, read()); }
            }, new IntentFilter(Protocol.REQUEST), Protocol.PERMISSION, events, Context.RECEIVER_EXPORTED);
            IntentFilter filter = new IntentFilter();
            for (String action : new String[]{BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED,
                    BluetoothAdapter.ACTION_STATE_CHANGED, BluetoothDevice.ACTION_BOND_STATE_CHANGED,
                    BluetoothDevice.ACTION_NAME_CHANGED, "android.bluetooth.device.action.ALIAS_CHANGED",
                    "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED", "android.bluetooth.input.profile.action.CONNECTION_STATE_CHANGED"}) filter.addAction(action);
            context.registerReceiver(new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent intent) {
                    String action = intent.getAction();
                    if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) { publish(c, read()); return; }
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
                    if (!READER.matchesDevice(device)) return;
                    READER.onBluetoothEvent(intent);
                    boolean connected = BluetoothDevice.ACTION_ACL_CONNECTED.equals(action);
                    if (!connected && !BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) { publish(c, read()); return; }
                    Runnable sample = () -> {
                        Bundle state = read();
                        if (connected && state.getBoolean("connected")) state.putString("event", "connected");
                        publish(c, state);
                    };
                    events.postDelayed(sample, connected ? 800 : 500);
                    if (connected) events.postDelayed(sample, 2500);
                }
            }, filter, null, events, Context.RECEIVER_EXPORTED);
            bridgeRegistered = true;
            events.post(() -> publish(context, read()));
        } catch (RuntimeException e) { log(e); }
    }
    static void install(XposedModule api, ClassLoader loader) throws ReflectiveOperationException {
        framework = api;
        try { appContext = (Context) Class.forName("android.app.ActivityThread", false, loader)
                .getMethod("currentApplication").invoke(null); } catch (ReflectiveOperationException ignored) { }
        if (appContext != null) READER.setContext(appContext);

        Class<?> page = Reflect.findClass("t2.A", loader);
        if (!"q1.o".equals(page.getSuperclass().getName()) || page.getDeclaredField("l").getType() != Context.class
                || !"t2.l".equals(page.getDeclaredField("w").getType().getName())) throw new IllegalStateException("Unsupported SecurityCore layout");
        HostCalls.install(api, loader, Protocol.PEN_AUTHORITY, Protocol.PEN_CALL, args -> {
            Bundle state = read();
            state.putInt("bridgeVersion", 2);
            return state;
        });
        api.hook(Application.class.getDeclaredMethod("attach", Context.class)).intercept(chain -> {
            Object result = chain.proceed(); registerBridge((Application) chain.getThisObject()); return result;
        });
        api.hook(Reflect.findClass("t2.l", loader).getDeclaredConstructor(Context.class)).intercept(chain -> {
            Object result = chain.proceed();
            try {
                Object owner = chain.getArg(0);
                Bundle state = read();
                if (owner != null && PenIdentity.allowHandwritingEntrance(owner.getClass().getName(), state.getBoolean("connected"), !state.getString("error", "").isEmpty()))
                    Reflect.setBooleanField(chain.getThisObject(), "b", true);
            } catch (RuntimeException e) { log(e); }
            return result;
        });
        api.hook(page.getDeclaredMethod("b", boolean.class)).intercept(chain -> {
            Object owner = chain.getThisObject();
            if (Boolean.TRUE.equals(Reflect.getAdditionalInstanceField(owner, RESTORING))) return chain.proceed();
            Bundle state = null;
            boolean override = false;
            try {
                Object detector = Reflect.getObjectField(owner, "w");
                boolean xiaomi = detector != null && Reflect.getBooleanField(detector, "b");
                state = read();
                if (Reflect.getAdditionalInstanceField(owner, CARD) == null) {
                    Context context = (Context) Reflect.getObjectField(owner, "l");
                    if (context != null) publish(context, state);
                }
                override = PenIdentity.overrideNativeConnection(xiaomi, state.getBoolean("paired"), !state.getString("error", "").isEmpty());
                if (!override) restorePreferenceFlags(owner);
            } catch (RuntimeException e) { log(e); }
            Object result = override ? chain.proceed(new Object[]{state.getBoolean("connected")}) : chain.proceed();
            if (override) try { restrictUnsupported(owner, state); } catch (RuntimeException e) { log(e); }
            return result;
        });
        api.hook(page.getDeclaredMethod("onResume")).intercept(chain -> {
            Object result = chain.proceed(); Object owner = chain.getThisObject();
            try {
                NativeCard card = (NativeCard) Reflect.getAdditionalInstanceField(owner, CARD);
                if (card == null) { card = new NativeCard(owner, loader); Reflect.setAdditionalInstanceField(owner, CARD, card); }
                card.start();
            } catch (Exception e) { log(e); }
            return result;
        });
        api.hook(page.getMethod("onPause")).intercept(chain -> {
            if (page.isInstance(chain.getThisObject())) stop(chain.getThisObject(), false);
            return chain.proceed();
        });
        api.hook(page.getMethod("onDestroyView")).intercept(chain -> {
            if (page.isInstance(chain.getThisObject())) stop(chain.getThisObject(), true);
            return chain.proceed();
        });
        api.hook(page.getDeclaredMethod("onDestroy")).intercept(chain -> { stop(chain.getThisObject(), true); return chain.proceed(); });
        api.log(Log.INFO, "RELEGION", "Pen hooks installed via API 102");
    }
    private static void restrictUnsupported(Object page, Bundle state) {
        if (Reflect.getAdditionalInstanceField(page, MASK) == null) {
            java.util.List<Object[]> saved = new java.util.ArrayList<>();
            for (String name : new String[]{"k", "e", "f", "g", "i", "j", "z", "A", "B", "C", "D", "x", "y"}) {
                Object item = Reflect.getObjectField(page, name);
                if (item != null) saved.add(new Object[]{item, Reflect.callMethod(item, "isEnabled"),
                        Reflect.callMethod(item, "isVisible"),
                        name.equals("x") || name.equals("y") ? Reflect.getBooleanField(item, "g") : null});
            }
            Object ota = Reflect.callMethod(page, "findPreference", "firmware_update");
            if (ota != null) saved.add(new Object[]{ota, Reflect.callMethod(ota, "isEnabled"), Reflect.callMethod(ota, "isVisible"), null});
            Reflect.setAdditionalInstanceField(page, MASK, saved);
        }
        boolean online = state.getBoolean("connected");
        // User confirmed native handwriting works. Both variants are retained;
        // the original APK chooses which one is visible for this device.
        for (String name : new String[]{"c", "d"}) {
            Object item = Reflect.getObjectField(page, name);
            if (item != null) Reflect.callMethod(item, "setEnabled", online);
        }
        for (String name : new String[]{"k", "e", "f", "g", "i", "j", "z", "A", "B", "C", "D"}) {
            Object item = Reflect.getObjectField(page, name);
            if (item != null) Reflect.callMethod(item, "setEnabled", false);
        }
        for (String name : new String[]{"x", "y"}) {
            Object item = Reflect.getObjectField(page, name);
            if (item != null) {
                Reflect.callMethod(item, "setEnabled", false);
                Reflect.setBooleanField(item, "g", false);
            }
        }
        Object firmware = Reflect.callMethod(page, "findPreference", "firmware_update");
        if (firmware != null) {
            Reflect.callMethod(firmware, "setEnabled", false);
            Reflect.callMethod(firmware, "setVisible", false);
        }
    }
    @SuppressWarnings("unchecked")
    static void restorePreferenceFlags(Object page) {
        Object saved = Reflect.removeAdditionalInstanceField(page, MASK);
        if (!(saved instanceof java.util.List)) return;
        for (Object[] flags : (java.util.List<Object[]>) saved) {
            Reflect.callMethod(flags[0], "setEnabled", flags[1]);
            Reflect.callMethod(flags[0], "setVisible", flags[2]);
            if (flags[3] instanceof Boolean) Reflect.setBooleanField(flags[0], "g", (Boolean) flags[3]);
        }
    }
    private static void stop(Object page, boolean remove) {
        try {
            NativeCard card = (NativeCard) Reflect.getAdditionalInstanceField(page, CARD);
            if (card != null) {
                if (remove) card.dispose(); else card.stop();
            }
            if (remove) Reflect.removeAdditionalInstanceField(page, CARD);
        } catch (Throwable e) { log(e); }
    }
}
