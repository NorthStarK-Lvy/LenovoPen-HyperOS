package io.github.myrom.lenovopen.hook;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.widget.Toast;
import de.robv.android.xposed.XposedHelpers;
import io.github.myrom.lenovopen.Protocol;
import java.lang.reflect.Proxy;
import java.lang.reflect.Method;

/** Uses only Preference instances created by the target APK's own ClassLoader. */
final class NativeCard implements Runnable {
    private final Object page, root, section, status, battery, single, doubleClick;
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running, nativeOverride;

    NativeCard(Object owner, ClassLoader loader) throws Exception {
        page = owner;
        context = (Context) XposedHelpers.getObjectField(page, "l");
        root = XposedHelpers.callMethod(page, "getPreferenceScreen");
        if (context == null || root == null) throw new IllegalStateException("Preference page not ready");
        Class<?> categoryType = XposedHelpers.findClass("androidx.preference.PreferenceCategory", loader);
        Class<?> preferenceType = XposedHelpers.findClass("androidx.preference.Preference", loader);
        section = categoryType.getConstructor(Context.class, AttributeSet.class).newInstance(context, null);
        call(section, "setKey", "myrom_lenovo_pen_xposed");
        call(section, "setTitle", "手写笔");
        call(section, "setOrder", -1000);
        call(section, "setVisible", false);
        status = row(preferenceType, "连接状态", false);
        battery = row(preferenceType, "手写笔电量", false);
        single = row(preferenceType, "侧键单击", false);
        call(single, "setSummary", "小米创作速记");
        doubleClick = row(preferenceType, "侧键双击", false);
        call(doubleClick, "setSummary", "超级小爱识屏");
        Object details = row(preferenceType, "手写笔详情", true);
        call(details, "setSummary", "查看连接状态与功能说明");
        Method setter = null;
        for (Method method : preferenceType.getMethods())
            if (method.getName().equals("setOnPreferenceClickListener") && method.getParameterCount() == 1) setter = method;
        if (setter == null) throw new NoSuchMethodException("setOnPreferenceClickListener");
        // The actual interface is obfuscated to androidx.preference.p in this APK.
        Class<?> listener = setter.getParameterTypes()[0];
        Object click = Proxy.newProxyInstance(loader, new Class<?>[]{listener}, (proxy, method, args) -> {
            if (method.getReturnType() == boolean.class && method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == preferenceType) {
                try {
                    Intent intent = new Intent().setClassName(Protocol.MODULE, Protocol.MODULE + ".MainActivity");
                    intent.putExtra("from_stylus", true);
                    PenHook.publish(context, PenHook.read());
                    context.startActivity(intent);
                } catch (RuntimeException e) {
                    PenHook.log(e);
                    Toast.makeText(context, "无法打开手写笔详情，请确认模块仍已安装", Toast.LENGTH_LONG).show();
                }
                return true;
            }
            if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
            if ("equals".equals(method.getName())) return proxy == args[0];
            if ("toString".equals(method.getName())) return "LenovoPenDetailsListener";
            return null;
        });
        setter.invoke(details, click);
        // Attach only after construction succeeds, so failed setup leaves no empty category.
        call(root, "g", section);
    }
    private Object row(Class<?> type, String title, boolean selectable) throws Exception {
        Object item = type.getConstructor(Context.class, AttributeSet.class).newInstance(context, null);
        call(item, "setTitle", title);
        call(item, "setSelectable", selectable);
        call(section, "g", item);
        return item;
    }
    private static Object call(Object receiver, String method, Object... args) {
        return XposedHelpers.callMethod(receiver, method, args);
    }
    void start() { if (!running) { running = true; run(); } }
    void stop() {
        running = false;
        handler.removeCallbacks(this);
        restoreNative();
        call(section, "setVisible", false);
    }
    void dispose() { stop(); call(root, "k", section); }
    private void restoreNative() {
        if (!nativeOverride) return;
        nativeOverride = false;
        Object detector = XposedHelpers.getObjectField(page, "w");
        boolean xiaomi = detector != null && XposedHelpers.getBooleanField(detector, "b");
        XposedHelpers.setAdditionalInstanceField(page, PenHook.RESTORING, true);
        try {
            PenHook.restorePreferenceFlags(page);
            XposedHelpers.setBooleanField(page, "F", !xiaomi);
            call(page, "b", xiaomi);
        } finally { XposedHelpers.removeAdditionalInstanceField(page, PenHook.RESTORING); }
    }
    @Override public void run() {
        if (!running) return;
        try {
            Bundle state = PenHook.read();
            call(section, "setTitle", state.getString("name", "手写笔"));
            Object detector = XposedHelpers.getObjectField(page, "w");
            boolean xiaomi = detector != null && XposedHelpers.getBooleanField(detector, "b");
            boolean paired = state.getBoolean("paired");
            boolean online = state.getBoolean("connected");
            boolean useLenovo = io.github.myrom.lenovopen.PenIdentity.overrideNativeConnection(xiaomi, paired, !state.getString("error", "").isEmpty());
            if (useLenovo) {
                nativeOverride = true;
                call(page, "b", online);
            } else restoreNative();
            // Keep the details entry available even before first pairing.
            call(section, "setVisible", true);
            String error = state.getString("error", "");
            String summary = !error.isEmpty() ? error : !state.getBoolean("enabled") ? "蓝牙已关闭"
                    : !paired ? "未检测到已配对的联想笔" : online ? "已连接" : "已配对，未连接";
            call(status, "setSummary", summary);
            int level = state.getInt("battery", -1);
            call(battery, "setSummary", !online ? "连接后读取电量" : level >= 0 ? level + "%" : "电量暂不可用");
            call(single, "setEnabled", online);
            call(doubleClick, "setEnabled", online);
        } catch (Throwable e) {
            PenHook.log(e);
            try { restoreNative(); } catch (Throwable ignored) { }
            // A hook failure must leave the original settings page usable.
            running = false;
        }
        if (running) handler.postDelayed(this, 2000);
    }
}
