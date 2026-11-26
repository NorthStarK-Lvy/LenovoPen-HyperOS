package com.alyssum.relegion;

import java.util.List;
import java.util.regex.Pattern;

/** The side-key actions the root bridge can really perform, and input validation for them. */
public final class PenActions {
    public static final String CREATE_NOTE = "create_note";
    public static final String SCREEN_RECOGNITION = "screen_recognition";
    public static final String SCREENSHOT = "screenshot";
    public static final String OPEN_APP = "open_app";
    public static final String VIDEO = "video";
    public static final String NONE = "none";
    public static final List<String> ALL = List.of(CREATE_NOTE, SCREEN_RECOGNITION, SCREENSHOT, OPEN_APP, VIDEO, NONE);
    /** Short-video apps the video action may control; the official app matches these same packages. */
    public static final List<String> VIDEO_PACKAGES = List.of(
            "com.ss.android.ugc.aweme", "com.smile.gifmaker", "com.kwai.video");
    /** Note apps the official whitelist pre-fills; the picker only ever queries these fixed packages. */
    private static final List<String> NOTE_APP_CANDIDATES = List.of(
            "com.tophatch.concepts.china", "com.tophatch.concepts", "com.lenovo.pendemo",
            "com.fiistudio.fiinote", "com.wacom.bamboopapertab", "com.medibang.android.paint.tablet",
            "jp.ne.ibis.ibispaintx.app.hms", "com.zui.notes", "com.microsoft.office.onenote",
            "com.myscript.nebo", "com.google.android.keep", "com.flexcil.flexcilnote",
            "com.topstack.kilonotes.pad", "com.orion.notein", "com.lenovo.styluspen");
    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+");
    private PenActions() {}

    /** Fixed candidate list for the note-app picker; never a query over all installed apps. */
    public static List<String> noteAppCandidates() { return NOTE_APP_CANDIDATES; }

    /** Unknown values fall back; the official "do nothing" spelling maps to none. */
    public static String normalize(String action, String fallback) {
        if (action != null && ALL.contains(action)) return action;
        if ("do_nothing".equals(action)) return NONE;
        return fallback;
    }

    public static boolean isPackageName(String value) {
        return value != null && value.length() <= 200 && PACKAGE.matcher(value).matches();
    }
}
