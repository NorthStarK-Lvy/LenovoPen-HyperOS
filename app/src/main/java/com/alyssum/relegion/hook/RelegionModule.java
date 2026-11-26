package com.alyssum.relegion.hook;

import android.util.Log;
import com.alyssum.relegion.BuildConfig;
import com.alyssum.relegion.Protocol;
import com.alyssum.relegion.LenovoPenProtocol;
import com.alyssum.relegion.refresh.GameRefreshPreferences;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam;
import com.alyssum.relegion.hyperconnect.xposed.HyperConnectHooks;

/** Single modern API 102 entry. Host processes never load the Compose UI. */
public final class RelegionModule extends XposedModule {
    private String processName;
    private boolean installed;
    private boolean penProtocolInstalled;
    private HyperConnectHooks hyperConnect;
    @Override public void onModuleLoaded(ModuleLoadedParam param) {
        processName = param.getProcessName();
        hyperConnect = new HyperConnectHooks(this);
        log(Log.INFO, "RELEGION", "API 102 loaded in " + processName);
    }
    @Override public void onPackageLoaded(PackageLoadedParam param) {
        if (param.isFirstPackage() && PowerKeeperGameRefreshHook.PACKAGE.equals(processName)
                && PowerKeeperGameRefreshHook.PACKAGE.equals(param.getPackageName()) && gameRefreshEnabled()) {
            try {
                PowerKeeperGameRefreshHook.install(this, param.getDefaultClassLoader());
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                log(Log.WARN, "RELEGION", "PowerKeeper game profile unavailable", e);
            }
        }
        hyperConnect.onPackageLoaded(param, processName);
    }
    @Override public void onSystemServerStarting(SystemServerStartingParam param) {
        // The Lenovo-only policy is optional on the port; absent classes must be a no-op.
        LenovoPenKeyHook.install(this, param.getClassLoader());
        if (gameRefreshEnabled()) {
            try { GameDefaultFrameRateHook.install(this, param.getClassLoader()); }
            catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                log(Log.WARN, "RELEGION", "GameManager default-rate hook unavailable", e);
            }
        }
        if (BuildConfig.DEBUG) {
            try { RefreshRateProbe.install(this, param.getClassLoader()); }
            catch (ReflectiveOperationException | RuntimeException e) {
                log(Log.ERROR, "RELEGION", "RefreshRateProbe unavailable", e);
            }
        }
    }

    private boolean gameRefreshEnabled() {
        try {
            return GameRefreshPreferences.isEnabled(getRemotePreferences(GameRefreshPreferences.GROUP));
        } catch (RuntimeException e) {
            log(Log.WARN, "RELEGION", "Could not read game refresh setting; keeping stock policy", e);
            return false;
        }
    }

    @Override public void onPackageReady(PackageReadyParam param) {
        hyperConnect.onPackageReady(param, processName);
        if (!penProtocolInstalled && "com.miui.securitycore".equals(param.getPackageName())
                && "com.miui.securitycore".equals(processName)) {
            try {
                android.content.Context context = (android.content.Context) param.getClassLoader()
                        .loadClass("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
                if (context != null) {
                    LenovoPenProtocol.install(this, context);
                    penProtocolInstalled = true;
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                log(Log.ERROR, "RELEGION", "Lenovo pen protocol unavailable", e);
            }
        }
        if (installed || !param.getPackageName().equals(processName)) return;
        try {
            if (Protocol.TARGET.equals(param.getPackageName())) {
                PenHook.install(this, param.getClassLoader());
                installed = true;
            } else if ("com.android.settings".equals(param.getPackageName())) {
                TabletModelHook.install(this, param.getClassLoader());
                installed = true;
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            log(Log.ERROR, "RELEGION", "Host layout is unsupported; leaving original behavior", e);
        }
    }
}
