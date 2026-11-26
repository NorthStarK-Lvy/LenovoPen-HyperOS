package com.alyssum.relegion;

import org.junit.Test;
import static org.junit.Assert.*;

public class PenModelTest {
    @Test public void productIdsMatchTheOfficialModelTable() {
        assertEquals(PenModel.PICASSO, PenModel.fromProduct(0x617f));
        assertEquals(PenModel.PARKER, PenModel.fromProduct(0x61a1));
        assertEquals(PenModel.OMAS, PenModel.fromProduct(0x622e));
        assertEquals(PenModel.SHEAFFER, PenModel.fromProduct(0x612b));
        assertEquals(PenModel.UNKNOWN, PenModel.fromProduct(0));
        assertEquals(PenModel.UNKNOWN, PenModel.fromProduct(0x617e));
    }
    @Test public void bluetoothNamesCoverEverySeries() {
        assertEquals(PenModel.PICASSO, PenModel.fromName("Lenovo Tab Pen Plus"));
        assertEquals(PenModel.PARKER, PenModel.fromName("Lenovo Tab Pen Pro"));
        assertEquals(PenModel.PARKER, PenModel.fromName("Lenovo Tab Pen Pro 2"));
        assertEquals(PenModel.SHEAFFER, PenModel.fromName("Lenovo Precision Pen"));
        assertEquals(PenModel.SHEAFFER, PenModel.fromName("Lenovo Precision Pen 3"));
        assertEquals(PenModel.UNKNOWN, PenModel.fromName("Bluetooth Mouse"));
        assertEquals(PenModel.UNKNOWN, PenModel.fromName(null));
    }
    @Test public void productIdWinsOverAnAliasLikeName() {
        assertEquals(PenModel.PICASSO, PenModel.of("My renamed pen", 0x617f));
        assertEquals(PenModel.PARKER, PenModel.of("Lenovo Tab Pen Plus", 0x61a1));
        assertEquals(PenModel.PICASSO, PenModel.of("Lenovo Tab Pen Plus", 0));
        assertEquals(PenModel.UNKNOWN, PenModel.of("My renamed pen", 0));
    }
    @Test public void onlyParkerHasBodyGesturesAndHaptics() {
        assertTrue(PenModel.hasSlideGesture(PenModel.PARKER));
        assertTrue(PenModel.hasTailEraser(PenModel.PARKER));
        assertTrue(PenModel.hasHapticFeedback(PenModel.PARKER));
        assertTrue(PenModel.hasDualTapKeys(PenModel.PARKER));
        for (String model : new String[]{PenModel.PICASSO, PenModel.OMAS, PenModel.SHEAFFER, PenModel.UNKNOWN}) {
            assertFalse(model, PenModel.hasSlideGesture(model));
            assertFalse(model, PenModel.hasTailEraser(model));
            assertFalse(model, PenModel.hasHapticFeedback(model));
            assertFalse(model, PenModel.hasDualTapKeys(model));
        }
    }
}
