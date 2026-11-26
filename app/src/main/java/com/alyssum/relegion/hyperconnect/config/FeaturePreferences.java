// Adapted from silverpoetry/HyperConnectToolkit v1.6.0 (Apache-2.0).
// RELEGION: namespace, API 102 integration and activation evidence.
package com.alyssum.relegion.hyperconnect.config;

/** Stable RemotePreferences contract shared by the companion app and injected processes. */
public final class FeaturePreferences {
    public static final String GROUP = "hyper_connect_features";
    public static final String KEY_CROSS_DEVICE_NOTIFICATION =
            "cross_device_notification";
    public static final String KEY_PORTRAIT_STREAMING = "portrait_streaming";
    public static final String KEY_MILINK_MULTI_CHANNEL = "milink_multi_channel";
    public static final boolean DEFAULT_CROSS_DEVICE_NOTIFICATION_ENABLED = true;
    public static final boolean DEFAULT_PORTRAIT_STREAMING_ENABLED = false;
    public static final boolean DEFAULT_MILINK_MULTI_CHANNEL_ENABLED = false;

    public static int snapshot(boolean notification, boolean portrait, boolean multiChannel) {
        return (notification ? 1 : 0) | (portrait ? 2 : 0) | (multiChannel ? 4 : 0);
    }

    private FeaturePreferences() {
    }
}
