package com.alyssum.relegion.hook;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.content.ComponentName;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Hooks the Lenovo service's audited pen-key boundary without replacing system input. */
final class LenovoPenKeyHook {
    private static final Set<Method> INSTALLED = new HashSet<>();
    private static XposedModule module;
    private LenovoPenKeyHook() {}

    static synchronized void install(XposedModule api, ClassLoader loader) {
        if (!INSTALLED.isEmpty()) return;
        module = api;
        try {
            Class<?> policy = Class.forName(
                    "com.zui.server.input.styluspen.pen.bluetooth.policy.BluetoothPenInputPolicy", false, loader);
            for (Method method : policy.getDeclaredMethods()) {
                if (!"processKeyEventKeyBeforeQueueing".equals(method.getName())) continue;
                method.setAccessible(true);
                api.hook(method).intercept(chain -> {
                    forwardIfNeeded(chain);
                    return chain.proceed();
                });
                api.deoptimize(method);
                INSTALLED.add(method);
            }
            HookLog.write(api, android.util.Log.INFO, "RELEGION", "Lenovo native pen-key hook installed=" + INSTALLED.size());
        } catch (ReflectiveOperationException | RuntimeException e) {
            HookLog.write(api, android.util.Log.WARN, "RELEGION", "Lenovo native pen-key hook unavailable", e);
        }
    }

    private static void forwardIfNeeded(XposedInterface.Chain chain) {
        KeyEvent event = null;
        for (Object arg : chain.getArgs()) if (arg instanceof KeyEvent) { event = (KeyEvent) arg; break; }
        if (event == null || event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() != 0
                || !isPenKey(event.getKeyCode())) return;
        Object owner = chain.getThisObject();
        Context context = readContext(owner);
        if (context == null) return;
        Bundle config = PenStateBridge.read(context);
        String foreground = PenStateBridge.foregroundPackage();
        List<String> consumers = config.getStringArrayList("consumerPackages");
        if (foreground != null && consumers != null && consumers.contains(foreground)) return;
        String action = event.getKeyCode() == com.alyssum.relegion.LenovoPenProtocol.KEY_TWO_CLICK
                ? config.getString("doubleAction", "screen_recognition")
                : config.getString("clickAction", "create_note");
        dispatch(context, action, config.getString("openPackage", ""), event.getKeyCode());
    }

    private static boolean isPenKey(int code) { return code >= 600 && code <= 604; }
    private static Context readContext(Object owner) {
        try {
            Field f = owner.getClass().getSuperclass().getDeclaredField("mContext");
            f.setAccessible(true); Object value = f.get(owner);
            return value instanceof Context ? (Context) value : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) { return null; }
    }
    private static void dispatch(Context context, String action, String packageName, int keyCode) {
        try {
            Intent intent;
            if ("create_note".equals(action)) {
                intent = new Intent("com.miui.creation.action.INSERT_CREATION").setPackage("com.miui.creation")
                        .setType("vnd.android.cursor.item/create_creation").putExtra("shortcut", "stylus")
                        .putExtra("scene", "app").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            } else if ("open_app".equals(action) && packageName != null && !packageName.isBlank()) {
                intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
                if (intent == null) return;
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            } else if ("screen_recognition".equals(action) || "screenshot".equals(action)) {
                intent = new Intent(Intent.ACTION_ASSIST).setComponent(new android.content.ComponentName(
                        "com.miui.voiceassist", "com.xiaomi.voiceassistant.VoiceService"))
                        .putExtra("voice_assist_start_from_key", "stylus_key_" + keyCode)
                        .putExtra("voice_assist_function_key", "start_screen_recognition")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            } else return;
            context.startActivity(intent);
        } catch (RuntimeException e) {
            if (module != null) HookLog.write(module, android.util.Log.WARN, "RELEGION", "Pen action failed", e);
        }
    }

    /** Small isolated bridge so the system-server hook does not depend on UI classes. */
    private static final class PenStateBridge {
        static Bundle read(Context context) {
            try {
                Bundle value = context.getContentResolver().call(
                        android.net.Uri.parse("content://com.alyssum.relegion.state"), "getPenConfig", null, Bundle.EMPTY);
                return value == null ? Bundle.EMPTY : value;
            } catch (RuntimeException e) { return Bundle.EMPTY; }
        }
        static String foregroundPackage() {
            try {
                Class<?> atm = Class.forName("android.app.ActivityTaskManager");
                Object service = atm.getMethod("getService").invoke(null);
                for (Method method : service.getClass().getMethods()) {
                    if (!"getFocusedRootTaskInfo".equals(method.getName()) || method.getParameterCount() != 0) continue;
                    Object info = method.invoke(service);
                    if (info == null) continue;
                    Field top = info.getClass().getField("topActivity");
                    Object value = top.get(info);
                    if (value instanceof ComponentName) return ((ComponentName) value).getPackageName();
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
            return null;
        }
    }
}
