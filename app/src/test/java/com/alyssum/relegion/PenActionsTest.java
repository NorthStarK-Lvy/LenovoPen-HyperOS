package com.alyssum.relegion;

import org.junit.Test;
import static org.junit.Assert.*;

public class PenActionsTest {
    @Test public void onlyActionsTheBridgeCanPerformAreAccepted() {
        for (String action : PenActions.ALL) assertEquals(action, PenActions.normalize(action, "x"));
        assertEquals("x", PenActions.normalize("remote_control", "x"));
        assertEquals("x", PenActions.normalize("show_hide_menu", "x"));
        assertEquals("x", PenActions.normalize(null, "x"));
        assertEquals(PenActions.NONE, PenActions.normalize("do_nothing", "x"));
    }
    @Test public void videoActionIsAcceptedAndOnlyNamesShortVideoApps() {
        assertEquals(PenActions.VIDEO, PenActions.normalize("video", "x"));
        assertTrue(PenActions.VIDEO_PACKAGES.contains("com.ss.android.ugc.aweme"));
        assertTrue(PenActions.VIDEO_PACKAGES.contains("com.smile.gifmaker"));
        assertTrue(PenActions.VIDEO_PACKAGES.contains("com.kwai.video"));
        assertFalse(PenActions.VIDEO_PACKAGES.contains("com.onyx.galaxy.note"));
        for (String pkg : PenActions.VIDEO_PACKAGES) assertTrue(PenActions.isPackageName(pkg));
    }
    @Test public void noteCandidatesAreValidAndUnique() {
        java.util.List<String> list = PenActions.noteAppCandidates();
        assertEquals(15, list.size());
        assertEquals(list.size(), new java.util.HashSet<>(list).size());
        for (String pkg : list) assertTrue(pkg, PenActions.isPackageName(pkg));
        assertTrue(list.contains("com.fiistudio.fiinote"));
        assertTrue(list.contains("com.google.android.keep"));
    }
    @Test public void packageNamesCannotCarryShellSyntax() {
        assertTrue(PenActions.isPackageName("com.onyx.galaxy.note"));
        assertTrue(PenActions.isPackageName("a_b.c1"));
        assertFalse(PenActions.isPackageName(""));
        assertFalse(PenActions.isPackageName(null));
        assertFalse(PenActions.isPackageName("single"));
        assertFalse(PenActions.isPackageName("com.a; reboot"));
        assertFalse(PenActions.isPackageName("com.a\ncom.b"));
        assertFalse(PenActions.isPackageName("com.a b"));
        assertFalse(PenActions.isPackageName("com.a/.Main"));
        assertFalse(PenActions.isPackageName("a.".repeat(120) + "b"));
    }
}
