package com.alyssum.relegion;

import android.util.Log;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LogLevelTest {
    @Test public void filtersAppAndHookPrioritiesAtTheSelectedLevel() {
        assertFalse(LogLevel.WARN.allows(Log.INFO));
        assertTrue(LogLevel.WARN.allows(Log.WARN));
        assertTrue(LogLevel.WARN.allows(Log.ERROR));
        assertFalse(LogLevel.ERROR.allows(Log.WARN));
        assertTrue(LogLevel.ERROR.allows(Log.ERROR));
        assertFalse(LogLevel.OFF.allows(Log.ERROR));
        assertFalse(LogLevel.OFF.allows(Log.ASSERT));
    }
}
