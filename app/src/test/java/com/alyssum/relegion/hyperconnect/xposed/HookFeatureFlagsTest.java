// Adapted from HyperConnectToolkit v1.6.0, Apache-2.0.
package com.alyssum.relegion.hyperconnect.xposed;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class HookFeatureFlagsTest {
    @Test
    public void eachFeatureCanBeDisabledIndependently() {
        HookFeatureFlags flags = HookFeatureFlags.of(false, true, false);

        assertFalse(flags.crossDeviceNotification);
        assertTrue(flags.portraitStreaming);
        assertFalse(flags.miLinkMultiChannel);
    }

    @Test
    public void disabledFallbackInstallsNoFunctionalGroup() {
        HookFeatureFlags flags = HookFeatureFlags.disabled();

        assertFalse(flags.crossDeviceNotification);
        assertFalse(flags.portraitStreaming);
        assertFalse(flags.miLinkMultiChannel);
    }
}
