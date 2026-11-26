// Adapted from silverpoetry/HyperConnectToolkit v1.6.0 (Apache-2.0).
// RELEGION: namespace, API 102 integration and activation evidence.
package com.alyssum.relegion.hyperconnect.xposed;

import android.content.SharedPreferences;

import com.alyssum.relegion.hyperconnect.config.FeaturePreferences;

/** Immutable package-load snapshot; disabled features never register their hooks. */
final class HookFeatureFlags {
    final boolean crossDeviceNotification;
    final boolean portraitStreaming;
    final boolean miLinkMultiChannel;

    private HookFeatureFlags(
            boolean crossDeviceNotification,
            boolean portraitStreaming,
            boolean miLinkMultiChannel) {
        this.crossDeviceNotification = crossDeviceNotification;
        this.portraitStreaming = portraitStreaming;
        this.miLinkMultiChannel = miLinkMultiChannel;
    }

    static HookFeatureFlags read(SharedPreferences preferences) {
        return of(
                preferences.getBoolean(
                        FeaturePreferences.KEY_CROSS_DEVICE_NOTIFICATION,
                        FeaturePreferences.DEFAULT_CROSS_DEVICE_NOTIFICATION_ENABLED),
                preferences.getBoolean(
                        FeaturePreferences.KEY_PORTRAIT_STREAMING,
                        FeaturePreferences.DEFAULT_PORTRAIT_STREAMING_ENABLED),
                preferences.getBoolean(
                        FeaturePreferences.KEY_MILINK_MULTI_CHANNEL,
                        FeaturePreferences.DEFAULT_MILINK_MULTI_CHANNEL_ENABLED));
    }

    static HookFeatureFlags of(
            boolean crossDeviceNotification,
            boolean portraitStreaming,
            boolean miLinkMultiChannel) {
        return new HookFeatureFlags(
                crossDeviceNotification,
                portraitStreaming,
                miLinkMultiChannel);
    }

    static HookFeatureFlags disabled() {
        return of(false, false, false);
    }

}
