package com.alyssum.relegion.hook;

import android.util.Log;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;

/** Removes the generic 60 FPS default at GameManager's own decision point. */
final class GameDefaultFrameRateHook {
    private GameDefaultFrameRateHook() {}

    static void install(XposedModule api, ClassLoader loader) throws ReflectiveOperationException {
        Class<?> service = Class.forName("com.android.server.app.GameManagerService", false, loader);
        Method getter = service.getDeclaredMethod("getGameDefaultFrameRate", boolean.class);
        Method bridge = service.getDeclaredMethod("-$$Nest$mgetGameDefaultFrameRate", service, boolean.class);
        Method toggle = service.getDeclaredMethod("toggleGameDefaultFrameRateUnchecked", boolean.class);
        Class<?> observer = Class.forName(service.getName() + "$MyUidObserver", false, loader);
        Method movedToTop = observer.getDeclaredMethod("handleUidMovedToTop", int.class);
        if (getter.getReturnType() != float.class || bridge.getReturnType() != float.class
                || toggle.getReturnType() != void.class || movedToTop.getReturnType() != void.class) {
            throw new NoSuchMethodException("GameManager default-rate signatures");
        }
        // Its seven-code-unit getter and synthetic bridge can be inlined in these callers.
        api.deoptimize(bridge);
        api.deoptimize(toggle);
        api.deoptimize(movedToTop);
        api.hook(getter).intercept(chain -> GameRefreshPolicy.gameDefaultRate((Float) chain.proceed()));
        HookLog.write(api, Log.INFO, "RELEGION", "GameManager generic 60 FPS default hook installed");
    }
}
