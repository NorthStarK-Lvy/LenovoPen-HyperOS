package com.alyssum.relegion.hook;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

/** Supplies a per-game profile only for the duration of PowerKeeper's native decision. */
final class PowerKeeperGameRefreshHook {
    static final String PACKAGE = "com.miui.powerkeeper";
    private static final String POLICY = PACKAGE + ".statemachine.DisplayFrameSetting";

    private PowerKeeperGameRefreshHook() {}

    static void install(XposedModule api, ClassLoader loader) throws ReflectiveOperationException {
        Host host = new Host(loader);
        Controller[] controller = new Controller[1];
        boolean[] unavailable = new boolean[1];
        api.hook(host.onForeground).intercept(chain -> {
            if (unavailable[0] || !(chain.getThisObject() instanceof Handler handler)
                    || Looper.myLooper() != handler.getLooper()) return chain.proceed();

            GameRefreshPolicy.Profile profile = null;
            try {
                if (controller[0] == null) controller[0] = new Controller(api, host, handler);
                if (controller[0].owner == handler) profile = controller[0].prepare(chain.getArg(0));
            } catch (ReflectiveOperationException | RuntimeException e) {
                unavailable[0] = true;
                HookLog.write(api, Log.WARN, "RELEGION", "Game refresh preparation failed; using stock policy", e);
            }

            try {
                return chain.proceed();
            } finally {
                if (profile != null) profile.close();
            }
        });
        HookLog.write(api, Log.INFO, "RELEGION", "Event-driven PowerKeeper game profile hook installed");
    }

    private static final class Host {
        final Method onForeground;
        final Method supportedRates;
        final Field context;
        final Field userRate;
        final Field defaults;
        final Field overrides;
        final Field foreground;
        final Field foregroundPackage;
        final Field foregroundDisplay;

        Host(ClassLoader loader) throws ReflectiveOperationException {
            Class<?> policy = Class.forName(POLICY, false, loader);
            if (!Handler.class.isAssignableFrom(policy)) throw new NoSuchMethodException("Policy handler");
            Class<?> info = Class.forName("miui.process.ForegroundInfo", false, loader);
            onForeground = policy.getDeclaredMethod("onForegroundChanged", info);
            supportedRates = policy.getDeclaredMethod("getSupportFps");
            if (onForeground.getReturnType() != void.class || supportedRates.getReturnType() != int[].class) {
                throw new NoSuchMethodException("Game refresh signatures");
            }
            onForeground.setAccessible(true);
            supportedRates.setAccessible(true);
            context = field(policy, "mContext", Context.class);
            userRate = field(policy, "mUserFps", int.class);
            defaults = field(policy, "mDefaultGameApp", Set.class);
            overrides = field(policy, "mPrivGames", Map.class);
            foreground = field(policy, "mCurrentFgInfo", info);
            foregroundPackage = field(info, "mForegroundPackageName", String.class);
            foregroundDisplay = field(info, "mForegroundDisplayId", int.class);
        }
    }

    private static final class Controller {
        final Handler owner;
        private final XposedModule api;
        private final Host host;
        private final PowerManager power;
        private boolean thermallyLimited;
        private Object lastForeground;
        private int lastTarget;
        private boolean refreshQueued;
        private boolean warned;

        Controller(XposedModule api, Host host, Handler owner) throws IllegalAccessException {
            this.api = api;
            this.host = host;
            this.owner = owner;
            Context context = (Context) host.context.get(owner);
            if (context == null) throw new IllegalStateException("PowerKeeper context unavailable");
            power = context.getSystemService(PowerManager.class);
            if (power == null) throw new IllegalStateException("PowerManager unavailable");
            thermallyLimited = power.getCurrentThermalStatus() != PowerManager.THERMAL_STATUS_NONE;
            PowerManager.OnThermalStatusChangedListener listener = status -> {
                boolean limited = status != PowerManager.THERMAL_STATUS_NONE;
                if (thermallyLimited != limited) {
                    thermallyLimited = limited;
                    queueRefresh();
                }
            };
            power.addThermalStatusListener(owner::post, listener);
            try {
                context.registerReceiver(new BroadcastReceiver() {
                    @Override public void onReceive(Context ignored, Intent intent) { queueRefresh(); }
                }, new IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
                        null, owner, Context.RECEIVER_NOT_EXPORTED);
            } catch (RuntimeException e) {
                power.removeThermalStatusListener(listener);
                throw e;
            }
        }

        GameRefreshPolicy.Profile prepare(Object info) throws ReflectiveOperationException {
            lastForeground = info;
            if (info == null) {
                lastTarget = 0;
                return null;
            }
            String pkg = (String) host.foregroundPackage.get(info);
            Map<String, Integer> profiles = profiles();
            int target = target(info, pkg, profiles);
            GameRefreshPolicy.Profile profile = target == 0 ? null
                    : GameRefreshPolicy.Profile.open(profiles, pkg, target);
            if (profile == null) target = 0;
            if (target != lastTarget) {
                HookLog.write(api, Log.INFO, "RELEGION", "Game profile " + pkg + ": "
                        + (target == 0 ? "stock" : target + " Hz"));
            }
            lastTarget = target;
            return profile;
        }

        private int target(Object info, String pkg, Map<String, Integer> profiles)
                throws ReflectiveOperationException {
            if (pkg == null || pkg.isEmpty() || profiles == null || profiles.containsKey(pkg)
                    || host.foregroundDisplay.getInt(info) != 0) return 0;
            Object games = host.defaults.get(owner);
            if (!(games instanceof Set<?> defaults) || !defaults.contains(pkg)
                    || thermallyLimited || power.isPowerSaveMode() || !power.isInteractive()) return 0;
            int selected = host.userRate.getInt(owner);
            if (selected <= 60) return 0;
            return GameRefreshPolicy.targetRate(selected, (int[]) host.supportedRates.invoke(owner));
        }

        @SuppressWarnings("unchecked")
        private Map<String, Integer> profiles() throws IllegalAccessException {
            return (Map<String, Integer>) host.overrides.get(owner);
        }

        private void queueRefresh() {
            if (refreshQueued || lastForeground == null) return;
            refreshQueued = true;
            if (!owner.post(() -> {
                refreshQueued = false;
                try {
                    Object info = host.foreground.get(owner);
                    // Some branches retain the old foreground object. Never replay that object.
                    if (info == null || info != lastForeground || !power.isInteractive()) return;
                    String pkg = (String) host.foregroundPackage.get(info);
                    if (target(info, pkg, profiles()) != lastTarget) host.onForeground.invoke(owner, info);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    if (!warned) {
                        warned = true;
                        HookLog.write(api, Log.WARN, "RELEGION", "Game refresh event failed; keeping native policy", e);
                    }
                }
            })) refreshQueued = false;
        }
    }

    private static Field field(Class<?> owner, String name, Class<?> type) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        if (!type.isAssignableFrom(field.getType())) throw new NoSuchFieldException(name);
        field.setAccessible(true);
        return field;
    }
}
