package com.alyssum.relegion;

public final class PenIdentity {
    private PenIdentity() {}
    public static boolean matchesInput(int vendor, int product) {
        return vendor == 0x17ef && !PenModel.fromProduct(product).isEmpty();
    }
    public static boolean matchesBond(String address, String inputAddress, String lastAddress, String name) {
        return address != null && (address.equals(inputAddress) || address.equals(lastAddress)
                || !PenModel.fromName(name).isEmpty());
    }
    public static int visibleBattery(boolean connected, int reported) {
        return connected && reported >= 0 && reported <= 100 ? reported : -1;
    }
    public static boolean fresh(long now, long sampled) {
        return sampled > 0 && now >= sampled && now - sampled <= Protocol.FRESH_MS;
    }
    public static boolean overrideNativeConnection(boolean xiaomiConnected, boolean lenovoPaired, boolean readFailed) {
        return !xiaomiConnected && lenovoPaired && !readFailed;
    }
    public static boolean allowHandwritingEntrance(String activityClass, boolean connected, boolean readFailed) {
        return "com.miui.miinput.stylus.MiuiHandwritingSettingsActivity".equals(activityClass) && connected && !readFailed;
    }
}
