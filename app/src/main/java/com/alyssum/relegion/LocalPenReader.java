package com.alyssum.relegion;

import android.Manifest;
import android.bluetooth.*;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.content.Intent;

/** Companion fallback: an actual Lenovo HID device, never the permanent touch panel. */
final class LocalPenReader {
    private static final PenBatteryCache BATTERY = new PenBatteryCache();
    private static Object cached(Object object, String method) {
        try { return object.getClass().getMethod(method).invoke(object); }
        catch (ReflectiveOperationException | SecurityException e) { return null; }
    }
    static void onBluetoothEvent(Intent intent) {
        if (!"android.bluetooth.device.action.BATTERY_LEVEL_CHANGED".equals(intent.getAction())) return;
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
        if (device == null) return;
        try {
            if (!PenIdentity.matchesBond(device.getAddress(), null, null, device.getName())) return;
            long now = SystemClock.elapsedRealtime();
            BATTERY.update(device.getAddress(), true, intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1), now, now);
        } catch (SecurityException ignored) { }
    }
    static Bundle read(Context context, Bundle nativeState) {
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return nativeState;
        Bundle state = new Bundle();
        try {
            BluetoothManager manager = context.getSystemService(BluetoothManager.class);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            if (adapter == null) return nativeState;
            boolean enabled = adapter.isEnabled();
            boolean inputPresent = false;
            String inputName = null;
            int inputProduct = 0;
            for (int id : InputDevice.getDeviceIds()) {
                InputDevice input = InputDevice.getDevice(id);
                if (input != null && PenIdentity.matchesInput(input.getVendorId(), input.getProductId())) {
                    inputPresent = true; inputName = input.getName(); inputProduct = input.getProductId();
                    break;
                }
            }
            BluetoothDevice candidate = null;
            for (BluetoothDevice device : adapter.getBondedDevices()) {
                if (!PenModel.fromName(device.getName()).isEmpty()
                        || (inputPresent && inputName != null && inputName.equals(device.getName()))) {
                    candidate = device; break;
                }
            }
            // Prefer a fresh host read if HID registration is still catching up.
            long now = SystemClock.elapsedRealtime();
            if (enabled && !inputPresent && PenIdentity.fresh(now, nativeState.getLong("sampled"))
                    && nativeState.getBoolean("connected") && nativeState.getString("error", "").isEmpty()) return nativeState;
            state.putLong("sampled", now);
            state.putBoolean("enabled", enabled);
            state.putBoolean("paired", candidate != null);
            boolean online = enabled && candidate != null && inputPresent;
            state.putBoolean("connected", online);
            String name = candidate == null ? nativeState.getString("name", "手写笔") : candidate.getAlias();
            if (name == null || name.isBlank()) name = candidate == null ? "手写笔" : candidate.getName();
            state.putString("name", name == null ? "手写笔" : name);
            state.putString("error", "");
            String address = candidate == null ? "" : candidate.getAddress();
            state.putString("device", address);
            // Keep the last known family while the HID node is briefly absent.
            String family = PenModel.of(candidate == null ? null : candidate.getName(), inputProduct);
            state.putString("penModel", family.isEmpty() ? nativeState.getString("penModel", "") : family);
            if (address.equals(nativeState.getString("device", ""))) {
                BATTERY.update(address, online, nativeState.getInt("battery", -1), nativeState.getLong("batterySampled"), now);
                // Device info is read by the host-side hook over GATT; keep it when the app rebuilds the snapshot.
                for (String key : new String[]{"firmware", "version", "serial", "hardware", "model"})
                    state.putString(key, nativeState.getString(key, ""));
            }
            Object hostLevel = online && candidate != null ? cached(candidate, "getBatteryLevel") : null;
            int reported = hostLevel instanceof Integer ? (Integer) hostLevel : -1;
            int battery = BATTERY.update(address, online, reported, now, now);
            state.putInt("battery", battery);
            state.putLong("batterySampled", BATTERY.sampled(address));
            return state;
        } catch (RuntimeException e) { return nativeState; }
    }
}
