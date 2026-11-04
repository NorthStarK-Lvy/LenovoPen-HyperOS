package io.github.myrom.lenovopen;

import org.junit.Test;
import static org.junit.Assert.*;

public class PenIdentityTest {
    @Test public void permanentTouchControllerIsNotBluetoothPen() {
        assertFalse(PenIdentity.matchesInput(0, 0));
        assertTrue(PenIdentity.matchesInput(0x17ef, 0x617f));
        assertFalse(PenIdentity.matchesInput(0x17ef, 0x617e));
    }
    @Test public void bondingAloneDoesNotMatchOtherDevices() {
        assertFalse(PenIdentity.matchesBond("AA", null, null, "Bluetooth Mouse"));
        assertFalse(PenIdentity.matchesBond(null, null, null, "Lenovo Tab Pen Plus"));
        assertTrue(PenIdentity.matchesBond("AA", null, null, "Lenovo Tab Pen Plus"));
        assertTrue(PenIdentity.matchesBond("AA", "AA", null, "Custom pen name"));
        assertTrue(PenIdentity.matchesBond("AA", null, "AA", "Custom pen name"));
    }
    @Test public void disconnectDoesNotDisplayOldPercentage() {
        assertEquals(-1, PenIdentity.visibleBattery(false, 100));
        assertEquals(-1, PenIdentity.visibleBattery(true, -1));
        assertEquals(-1, PenIdentity.visibleBattery(true, 101));
        assertEquals(0, PenIdentity.visibleBattery(true, 0));
        assertEquals(100, PenIdentity.visibleBattery(true, 100));
    }
    @Test public void deadBridgeCannotShowAStaleConnectionAsLive() {
        assertTrue(PenIdentity.fresh(10000, 9000));
        assertFalse(PenIdentity.fresh(20000, 9000));
        assertFalse(PenIdentity.fresh(10000, 11000));
        assertFalse(PenIdentity.fresh(10000, 0));
    }
    @Test public void nativeOverrideOnlyAppliesToIdentifiedLenovoPens() {
        assertTrue(PenIdentity.overrideNativeConnection(false, true, false));
        assertFalse(PenIdentity.overrideNativeConnection(true, true, false));
        assertFalse(PenIdentity.overrideNativeConnection(false, false, false));
        assertFalse(PenIdentity.overrideNativeConnection(false, true, true));
    }
    @Test public void handwritingGateDoesNotAffectOtherDetectorInstances() {
        String page = "com.miui.miinput.stylus.MiuiHandwritingSettingsActivity";
        assertTrue(PenIdentity.allowHandwritingEntrance(page, true, false));
        assertFalse(PenIdentity.allowHandwritingEntrance(page, false, false));
        assertFalse(PenIdentity.allowHandwritingEntrance(page, true, true));
        assertFalse(PenIdentity.allowHandwritingEntrance("com.miui.miinput.stylus.KeyBoardAndStylusSettingsActivity", true, false));
    }
}
