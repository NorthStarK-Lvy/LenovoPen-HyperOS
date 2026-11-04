package io.github.myrom.lenovopen;

public final class Protocol {
    public static final String MODULE = "io.github.myrom.lenovopen";
    public static final String TARGET = "com.miui.securitycore";
    public static final String AUTHORITY = MODULE + ".state";
    public static final String REQUEST = MODULE + ".REQUEST_STATE";
    public static final String PERMISSION = MODULE + ".permission.REQUEST_STATE";
    public static final long FRESH_MS = 8000;
    private Protocol() {}
}
