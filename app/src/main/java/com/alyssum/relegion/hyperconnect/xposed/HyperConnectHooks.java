// Adapted from silverpoetry/HyperConnectToolkit v1.6.0, Apache-2.0.
// RELEGION: single API 102 entry, configuration-matched activation reports.
package com.alyssum.relegion.hyperconnect.xposed;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Log;

import com.alyssum.relegion.hyperconnect.config.FeaturePreferences;
import com.alyssum.relegion.hyperconnect.platform.HookStatusContract;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;

/** Lightweight LSPosed entry point: exact process routing, configuration gating and lifecycle. */
public final class HyperConnectHooks {
    private final XposedModule module;

    public HyperConnectHooks(XposedModule module) { this.module = module; }
    private static final String TAG = "HyperConnectToolkit";

    private PortraitStreamingHooks portraitHooks;
    private TargetProcessRuntime mirrorRuntime;
    private boolean mirrorReporterInstalled;
    private boolean mirrorReadyHandled;
    private boolean loaded;
    private int snapshot;

    public void onPackageLoaded(PackageLoadedParam param, String processName) {
        if (loaded || !param.isFirstPackage()) return;

        TargetProcess target = TargetProcess.resolve(
                param.getPackageName(),
                processName);
        if (target == null) return;
        loaded = true;

        HookFeatureFlags features = readFeatureFlags();
        snapshot = FeaturePreferences.snapshot(features.crossDeviceNotification,
                features.portraitStreaming, features.miLinkMultiChannel);
        module.log(Log.INFO, TAG,
                "Feature snapshot for " + target.processName
                        + ": notification=" + features.crossDeviceNotification
                        + ", portrait=" + features.portraitStreaming
                        + ", multiChannel=" + features.miLinkMultiChannel);

        switch (target) {
            case MILINK_NOTIFICATION_DISCOVERY -> installNotificationDiscoveryFeature(
                    param.getDefaultClassLoader(),
                    features);
            case MILINK_NOTIFICATION_FLOW -> installNotificationFlowFeature(
                    param.getDefaultClassLoader(),
                    features);
            case MILINK_CORE -> installMultiChannelFeature(
                    param.getDefaultClassLoader(),
                    features);
            case XIAOMI_MIRROR -> installPortraitFeature(
                    param.getDefaultClassLoader(),
                    features);
        }
    }

    public void onPackageReady(PackageReadyParam param, String processName) {
        if (mirrorReadyHandled || portraitHooks == null || !param.isFirstPackage()) return;
        if (!TargetProcess.XIAOMI_MIRROR.matches(
                param.getPackageName(),
                processName)) {
            return;
        }

        mirrorReadyHandled = true;
        if (portraitHooks.installOrientation(param.getClassLoader())
                && portraitHooks.isComplete()
                && mirrorReporterInstalled) {
            mirrorRuntime.recordInstalledCapability(
                    HookStatusContract.FEATURE_PORTRAIT_STREAMING);
        }
    }

    private void installNotificationDiscoveryFeature(
            ClassLoader classLoader,
            HookFeatureFlags features) {
        if (!features.crossDeviceNotification) return;
        if (!isTablet(classLoader)) {
            module.log(Log.INFO, TAG, "Skipped tablet notification compatibility on a phone");
            return;
        }

        boolean installed = new MiLinkDiscoveryHooks(module).install(classLoader);
        reportInstalledCapability(
                installed,
                TargetProcess.MILINK_NOTIFICATION_DISCOVERY,
                HookStatusContract.COMPONENT_NOTIFICATION_DISCOVERY);
    }

    private void installNotificationFlowFeature(
            ClassLoader classLoader,
            HookFeatureFlags features) {
        if (!features.crossDeviceNotification) return;
        if (!isTablet(classLoader)) {
            module.log(Log.INFO, TAG, "Skipped tablet notification compatibility on a phone");
            return;
        }

        boolean installed = new MiLinkNotificationFlowHooks(module).install(classLoader);
        reportInstalledCapability(
                installed,
                TargetProcess.MILINK_NOTIFICATION_FLOW,
                HookStatusContract.COMPONENT_NOTIFICATION_FLOW);
    }

    private void installMultiChannelFeature(
            ClassLoader classLoader,
            HookFeatureFlags features) {
        if (!features.miLinkMultiChannel) return;
        boolean installed = new MiLinkMultiChannelHooks(module).install(classLoader);
        reportInstalledCapability(
                installed,
                TargetProcess.MILINK_CORE,
                HookStatusContract.FEATURE_MILINK_MULTI_CHANNEL);
    }

    private void installPortraitFeature(
            ClassLoader classLoader,
            HookFeatureFlags features) {
        if (!features.portraitStreaming) return;
        if (!isTablet(classLoader)) {
            module.log(Log.INFO, TAG, "Skipped tablet portrait compatibility on a phone");
            return;
        }

        mirrorRuntime = new TargetProcessRuntime(
                module,
                TargetProcess.XIAOMI_MIRROR.packageName,
                TargetProcess.XIAOMI_MIRROR.processName, snapshot);
        portraitHooks = new PortraitStreamingHooks(
                module,
                mirrorRuntime::applicationContext);
        if (portraitHooks.installGeometry(classLoader)) {
            mirrorReporterInstalled = mirrorRuntime.installLifecycleHook();
        }
    }

    private void reportInstalledCapability(
            boolean installed,
            TargetProcess target,
            int capability) {
        if (!installed) return;
        TargetProcessRuntime runtime = new TargetProcessRuntime(
                module,
                target.packageName,
                target.processName, snapshot);
        if (runtime.installLifecycleHook()) {
            runtime.recordInstalledCapability(capability);
        }
    }

    private HookFeatureFlags readFeatureFlags() {
        try {
            SharedPreferences preferences = module.getRemotePreferences(FeaturePreferences.GROUP);
            return HookFeatureFlags.read(preferences);
        } catch (Throwable error) {
            module.log(Log.ERROR, TAG,
                    "Remote configuration unavailable; skipped all functional hooks",
                    error);
            return HookFeatureFlags.disabled();
        }
    }

    private boolean isTablet(ClassLoader classLoader) {
        try {
            return DeviceFormFactor.isTablet(classLoader);
        } catch (Throwable error) {
            module.log(Log.WARN, TAG, "Could not read HyperOS tablet form factor", error);
            return false;
        }
    }
}
