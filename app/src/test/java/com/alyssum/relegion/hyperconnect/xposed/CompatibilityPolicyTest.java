package com.alyssum.relegion.hyperconnect.xposed;

import org.junit.Test;
import static org.junit.Assert.*;

public class CompatibilityPolicyTest {
    @Test public void swapsInitialLandscapeBoundsOnlyForPortraitPinApp() {
        assertTrue(CompatibilityPolicy.shouldRestorePortraitInitialBounds(1, 9, 2000, 1200));
        assertFalse(CompatibilityPolicy.shouldRestorePortraitInitialBounds(2, 9, 2000, 1200));
        assertFalse(CompatibilityPolicy.shouldRestorePortraitInitialBounds(1, 8, 2000, 1200));
        assertFalse(CompatibilityPolicy.shouldRestorePortraitInitialBounds(1, 9, 1200, 2000));
        assertFalse(CompatibilityPolicy.shouldRestorePortraitInitialBounds(1, 9, 2000, 0));
    }
    @Test public void orientationOverrideIsLimitedToAuditedSinkAndSensorLandscape() {
        assertEquals(10, CompatibilityPolicy.resolveRequestedOrientation(true, 6));
        assertEquals(6, CompatibilityPolicy.resolveRequestedOrientation(false, 6));
        assertEquals(0, CompatibilityPolicy.resolveRequestedOrientation(true, 0));
        assertEquals(1, CompatibilityPolicy.resolveRequestedOrientation(true, 1));
    }
}
