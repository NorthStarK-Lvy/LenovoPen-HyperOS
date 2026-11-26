package com.alyssum.relegion.hook;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

public class GameRefreshPolicyTest {
    @Test public void selectsAnAdvertisedRateWithinTheUserLimit() {
        int[] modes = {144, 60, 90, 120};
        assertEquals(120, GameRefreshPolicy.targetRate(120, modes));
        assertEquals(144, GameRefreshPolicy.targetRate(144, modes));
        assertEquals(90, GameRefreshPolicy.targetRate(100, modes));
        assertEquals(0, GameRefreshPolicy.targetRate(60, modes));
        assertEquals(0, GameRefreshPolicy.targetRate(120, new int[]{30, 60}));
    }

    @Test public void removesOnlyTheGenericSixtyDefault() {
        assertEquals(0f, GameRefreshPolicy.gameDefaultRate(60f), 0f);
        assertEquals(0f, GameRefreshPolicy.gameDefaultRate(0f), 0f);
        assertEquals(90f, GameRefreshPolicy.gameDefaultRate(90f), 0f);
    }

    @Test public void explicitSixtyProfileTakesPriority() {
        Map<String, Integer> profiles = new HashMap<>();
        profiles.put("game", 60);
        assertNull(GameRefreshPolicy.Profile.open(profiles, "game", 120));
        assertEquals(Integer.valueOf(60), profiles.get("game"));
    }

    @Test public void temporaryEntryIsRemovedEvenWhenTheNativeCallFails() {
        Map<String, Integer> profiles = new HashMap<>();
        try (GameRefreshPolicy.Profile profile = GameRefreshPolicy.Profile.open(profiles, "game", 120)) {
            assertEquals(Integer.valueOf(120), profiles.get("game"));
            throw new IllegalStateException("native failure");
        } catch (IllegalStateException expected) {
            assertTrue(profiles.isEmpty());
        }
    }

    @Test public void nativeWritesTakeOwnershipOfTheEntry() {
        Map<String, Integer> profiles = new HashMap<>();
        try (GameRefreshPolicy.Profile profile = GameRefreshPolicy.Profile.open(profiles, "game", 120)) {
            profiles.put("game", 90);
        }
        assertEquals(Integer.valueOf(90), profiles.get("game"));
    }
}
