package com.alyssum.relegion;

import org.junit.Test;
import static org.junit.Assert.*;

public class PenBatteryCacheTest {
    @Test public void sparseReportsSurviveConnectionFreshnessWindow() {
        PenBatteryCache cache = new PenBatteryCache();
        assertEquals(73, cache.update("pen", true, 73, 1000, 1000));
        assertEquals(73, cache.update("pen", true, -1, 0, 600000));
        assertEquals(1000, cache.sampled("pen"));
    }
    @Test public void disconnectedAndDifferentDevicesDoNotInheritPercentage() {
        PenBatteryCache cache = new PenBatteryCache();
        cache.update("penA", true, 73, 1000, 1000);
        assertEquals(-1, cache.update("penA", false, -1, 0, 2000));
        assertEquals(-1, cache.update("penB", true, -1, 0, 2000));
        assertEquals(-1, cache.update("", true, 99, 2000, 2000));
        assertEquals(73, cache.update("penA", true, -1, 0, 3000));
    }
    @Test public void invalidOrOldReportsCannotOverwriteNewerBattery() {
        PenBatteryCache cache = new PenBatteryCache();
        cache.update("pen", true, 60, 2000, 2000);
        assertEquals(60, cache.update("pen", true, 90, 1000, 3000));
        assertEquals(60, cache.update("pen", true, 101, 3000, 3000));
        assertEquals(0, cache.update("pen", true, 0, 4000, 4000));
    }
    @Test public void expiredOrFutureReportsAreNotShown() {
        PenBatteryCache cache = new PenBatteryCache();
        cache.update("pen", true, 60, 1000, 1000);
        assertEquals(-1, cache.update("pen", true, -1, 0, 1001 + PenBatteryCache.MAX_AGE_MS));
        assertEquals(-1, cache.update("pen", true, 60, 10000, 1000));
    }
}
