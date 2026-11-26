package com.alyssum.relegion;

import com.alyssum.relegion.tablet.ModelNamePolicy;
import org.junit.Test;
import static org.junit.Assert.*;

public class ModelNamePolicyTest {
    @Test public void onlyTheModelHeadingIsEligible() {
        assertTrue(ModelNamePolicy.isModelTitle(2, 6));
        assertFalse(ModelNamePolicy.isModelTitle(1, 6));
        assertFalse(ModelNamePolicy.isModelTitle(0, 6));
        for (int card : new int[]{0, 1, 2, 3, 4, 5, 7, 8}) assertFalse(ModelNamePolicy.isModelTitle(2, card));
    }
    @Test public void blankRestoresStockAndPastedWhitespaceStaysOneLine() {
        assertEquals("", ModelNamePolicy.normalize(null));
        assertEquals("", ModelNamePolicy.normalize(" \n\t\u3000"));
        assertEquals("联想 拯救者 Y700", ModelNamePolicy.normalize("\t联想\n拯救者  Y700 \r"));
    }
    @Test public void lengthLimitDoesNotSplitSupplementaryCharacters() {
        String longName = "😀".repeat(70);
        String name = ModelNamePolicy.normalize(longName);
        assertEquals(64, name.codePointCount(0, name.length()));
        assertEquals("😀".repeat(64), name);
    }
}
