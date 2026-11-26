// Adapted from silverpoetry/HyperConnectToolkit v1.6.0 (Apache-2.0).
// RELEGION: namespace, API 102 integration and activation evidence.
package com.alyssum.relegion.hyperconnect.xposed;

import com.alyssum.relegion.hook.HookLog;

import android.annotation.SuppressLint;
import android.app.Application;
import android.app.BroadcastOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.alyssum.relegion.BuildConfig;
import com.alyssum.relegion.hyperconnect.platform.HookStatusContract;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedModule;

/** Captures one target process context and reports only successfully installed feature groups. */
final class TargetProcessRuntime {
    private static final String TAG = "HyperConnectToolkit";
    private static final String MODULE_PACKAGE = "com.alyssum.relegion";
    private static final String RECEIVER_CLASS =
            "com.alyssum.relegion.hyperconnect.platform.HookStatusReceiver";

    private final XposedModule module;
    private final String sourcePackage;
    private final String sourceProcess;
    private final int snapshot;
    private volatile Context applicationContext;
    private int installedCapabilities;
    private int reportedCapabilities;

    TargetProcessRuntime(XposedModule module, String sourcePackage, String sourceProcess, int snapshot) {
        this.module = module;
        this.sourcePackage = sourcePackage;
        this.sourceProcess = sourceProcess;
        this.snapshot = snapshot;
    }

    @SuppressLint("DiscouragedPrivateApi")
    boolean installLifecycleHook() {
        try {
            Method attach = Application.class.getDeclaredMethod("attach", Context.class);
            attach.setAccessible(true);
            module.hook(attach).intercept(chain -> {
                Object result = chain.proceed();
                if (chain.getArg(0) instanceof Context context) {
                    Context appContext = context.getApplicationContext();
                    applicationContext = appContext != null ? appContext : context;
                    reportPendingFeatures();
                }
                return result;
            });
            // API 102 may also call PackageReady after attach on a vendor build.
            // Use the existing Application if available; never create a host context.
            try {
                Class<?> thread = Class.forName("android.app.ActivityThread");
                Method current = thread.getDeclaredMethod("currentApplication");
                current.setAccessible(true);
                Object app = current.invoke(null);
                if (app instanceof Application application) applicationContext = application;
            } catch (ReflectiveOperationException | RuntimeException ignored) { }
            HookLog.write(module, Log.INFO, TAG, "Installed target-process lifecycle reporter");
            return true;
        } catch (Throwable error) {
            HookLog.write(module, Log.ERROR, TAG, "Target-process lifecycle reporter is incompatible", error);
            return false;
        }
    }

    Context applicationContext() {
        return applicationContext;
    }

    synchronized void recordInstalledCapability(int capability) {
        installedCapabilities |= capability & HookStatusContract.ALL_REPORT_BITS;
        reportPendingFeatures();
    }

    private synchronized void reportPendingFeatures() {
        if (Build.VERSION.SDK_INT < 34) return; // Sender-verified reports need API 34.
        Context context = applicationContext;
        int pendingFeatures = installedCapabilities & ~reportedCapabilities;
        if (context == null || pendingFeatures == 0) {
            return;
        }

        try {
            Intent report = new Intent(HookStatusContract.ACTION_HOOKS_ACTIVE)
                    .setComponent(new ComponentName(MODULE_PACKAGE, RECEIVER_CLASS))
                    .putExtra(HookStatusContract.EXTRA_SOURCE_PACKAGE, sourcePackage)
                    .putExtra(HookStatusContract.EXTRA_SOURCE_PROCESS, sourceProcess)
                    .putExtra(HookStatusContract.EXTRA_MODULE_VERSION, BuildConfig.VERSION_CODE)
                    .putExtra(HookStatusContract.EXTRA_ACTIVE_FEATURES, pendingFeatures)
                    .putExtra(HookStatusContract.EXTRA_CONFIG_SNAPSHOT, snapshot)
                    .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES
                            | Intent.FLAG_RECEIVER_FOREGROUND);
            BroadcastOptions options = BroadcastOptions.makeBasic();
            options.setShareIdentityEnabled(true);
            context.sendBroadcast(report, null, options.toBundle());
            reportedCapabilities |= pendingFeatures;
            HookLog.write(module, Log.INFO, TAG,
                    "Reported Hook capabilities=" + pendingFeatures + " from " + sourceProcess);
        } catch (Throwable error) {
            HookLog.write(module, Log.WARN, TAG, "Could not report installed Hook features", error);
        }
    }
}
