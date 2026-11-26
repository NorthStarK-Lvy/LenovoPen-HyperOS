// Adapted from silverpoetry/HyperConnectToolkit v1.6.0 (Apache-2.0).
// RELEGION: namespace, API 102 integration and activation evidence.
package com.alyssum.relegion.hyperconnect.xposed;

/** Reads the one stable HyperOS form-factor flag needed to scope tablet-only behavior. */
final class DeviceFormFactor {
    private DeviceFormFactor() {
    }

    static boolean isTablet(ClassLoader classLoader) throws ReflectiveOperationException {
        Class<?> miuiBuild = Class.forName("miui.os.Build", false, classLoader);
        return miuiBuild.getField("IS_TABLET").getBoolean(null);
    }
}
