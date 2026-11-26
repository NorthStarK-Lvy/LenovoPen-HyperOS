package com.alyssum.relegion.tablet;

/** A display-only name. It never changes Build fields or system properties. */
public final class ModelNamePolicy {
    public static final String GROUP = "tablet_model_ui";
    public static final String KEY = "about_tablet_model";
    public static final int MAX_CODE_POINTS = 64;

    public static String normalize(String value) {
        if (value == null) return "";
        StringBuilder name = new StringBuilder();
        boolean space = false;
        int count = 0;
        for (int offset = 0; offset < value.length();) {
            int point = value.codePointAt(offset);
            offset += Character.charCount(point);
            if (Character.isWhitespace(point) || Character.isSpaceChar(point) || Character.isISOControl(point)) {
                space = name.length() > 0;
                continue;
            }
            if (space && count < MAX_CODE_POINTS - 1) { name.append(' '); count++; }
            space = false;
            if (count >= MAX_CODE_POINTS) break;
            name.appendCodePoint(point);
            count++;
        }
        return name.toString();
    }

    /** Audited Settings contract: only adapter type 2's model card (index 6). */
    public static boolean isModelTitle(int adapterType, int cardIndex) {
        return adapterType == 2 && cardIndex == 6;
    }

    private ModelNamePolicy() { }
}
