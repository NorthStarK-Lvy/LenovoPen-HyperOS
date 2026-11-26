package com.alyssum.relegion.hook;

import android.os.Binder;
import android.util.Log;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;

/** Records PowerKeeper requests and the resulting setting writes without changing either. */
final class RefreshRateProbe {
    private static final String KEY = "miui_refresh_rate";
    private static final String GAME = "com.netease.dwrg";

    private RefreshRateProbe() {}

    static void install(XposedModule api, ClassLoader loader) throws ReflectiveOperationException {
        installPowerKeeperProbe(api, loader);
        installGameDisplayProbe(api, loader);

        Class<?> secure = Class.forName("android.provider.Settings$Secure", false, loader);
        for (Method method : secure.getDeclaredMethods()) {
            String name = method.getName();
            if (!"putIntForUser".equals(name) && !"putStringForUser".equals(name)) continue;
            api.hook(method).intercept(chain -> {
                try {
                    Object key = chain.getArg(1);
                    if (KEY.equals(key)) {
                        logTrace(api, KEY + " <- " + chain.getArg(2));
                    }
                } catch (RuntimeException ignored) {
                    // Diagnostics must never break the write.
                }
                return chain.proceed();
            });
        }
        api.log(Log.INFO, "RELEGION", "RefreshRateProbe installed");
    }

    private static void installPowerKeeperProbe(XposedModule api, ClassLoader loader) {
        try {
            Class<?> policy = Class.forName("com.miui.server.mirim.policy.RefreshRatePolicy", false, loader);
            Class<?> parameter = Class.forName("miui.scenariorecognition.RimParameter", false, loader);
            Method getInt = parameter.getMethod("getInt", String.class);
            Method request = policy.getDeclaredMethod("methodPowerKeeperSetFps", Object[].class);
            api.hook(request).intercept(chain -> {
                try {
                    if (chain.getArg(0) instanceof Object[] args && args.length == 1
                            && parameter.isInstance(args[0])) {
                        Object fps = getInt.invoke(args[0], "fps");
                        logTrace(api, "PowerKeeper request fps=" + fps + " callingPid="
                                + Binder.getCallingPid() + " callingUid=" + Binder.getCallingUid());
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Diagnostics must never break the request.
                }
                return chain.proceed();
            });
            api.log(Log.INFO, "RELEGION", "RefreshRateProbe PowerKeeper request hook installed");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            api.log(Log.WARN, "RELEGION", "RefreshRateProbe PowerKeeper request hook unavailable", e);
        }
    }

    private static void installGameDisplayProbe(XposedModule api, ClassLoader loader) {
        try {
            Class<?> handler = Class.forName("com.android.server.ui.display.DisplayFrameSettingHandler", false, loader);
            Method setEffect = handler.getDeclaredMethod("setScreenEffect", String.class, int.class);
            api.hook(setEffect).intercept(chain -> {
                try {
                    if (GAME.equals(chain.getArg(0))) {
                        logTrace(api, "DisplayFrameSettingHandler pkg=" + GAME + " requestedFps=" + chain.getArg(1));
                    }
                } catch (RuntimeException ignored) {
                    // Diagnostics must never break the decision.
                }
                return chain.proceed();
            });
            api.log(Log.INFO, "RELEGION", "RefreshRateProbe game display hook installed");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            api.log(Log.WARN, "RELEGION", "RefreshRateProbe game display hook unavailable", e);
        }
    }

    private static void logTrace(XposedModule api, String message) {
        StringBuilder trace = new StringBuilder("RefreshRateProbe ").append(message);
        for (StackTraceElement frame : new Throwable().getStackTrace()) {
            String owner = frame.getClassName();
            if (owner.startsWith("android.provider.Settings") || owner.startsWith(RefreshRateProbe.class.getName())) {
                continue;
            }
            trace.append(" | ").append(owner).append('.').append(frame.getMethodName())
                    .append(':').append(frame.getLineNumber());
            if (trace.length() > 1600) break;
        }
        api.log(Log.INFO, "RELEGION", trace.toString());
    }
}
