package com.alyssum.relegion;

public final class Protocol {
    public static final String MODULE = "com.alyssum.relegion";
    public static final String TARGET = "com.miui.securitycore";
    public static final String PEN_CALL = MODULE + ".pen.v2";
    public static final String PEN_AUTHORITY = "com.miui.securityspace.provider.search.SecSettingsSearchProvider";
    public static final String AUTHORITY = MODULE + ".state";
    public static final String REQUEST = MODULE + ".REQUEST_STATE";
    public static final String PERMISSION = MODULE + ".permission.REQUEST_STATE";
    public static final long REQUEST_INTERVAL_MS = 2000;
    public static final long FRESH_MS = 8000;
    private Protocol() {}
}
