package io.github.myrom.lenovopen.hook;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import io.github.myrom.lenovopen.PenIdentity;

/** Runs only inside SecurityCore, using its existing Bluetooth permissions. */
@SuppressLint("MissingPermission") // Host-process permissions, not the module APK's manifest; SecurityException is handled.
final class PenReader {
    private String lastAddress;
    private String lastName = "手写笔";
    private static Object cached(Object object, String method) {
        try { return object.getClass().getMethod(method).invoke(object); }
        catch (ReflectiveOperationException | SecurityException e) { return null; }
    }
    private static boolean connected(BluetoothDevice device) {
        return Boolean.TRUE.equals(cached(device, "isConnected"));
    }
    Bundle read() {
        Bundle state = new Bundle();
        state.putLong("sampled", SystemClock.elapsedRealtime());
        state.putInt("battery", -1);
        state.putString("name", lastName);
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) { state.putString("error", "本机蓝牙服务不可用"); return state; }
            boolean enabled = adapter.isEnabled();
            state.putBoolean("enabled", enabled);
            String inputAddress = null;
            for (int id : InputDevice.getDeviceIds()) {
                InputDevice input = InputDevice.getDevice(id);
                if (input == null || !PenIdentity.matchesInput(input.getVendorId(), input.getProductId())) continue;
                Object address = cached(input, "getBluetoothAddress");
                if (address instanceof String && BluetoothAdapter.checkBluetoothAddress((String) address)) {
                    inputAddress = (String) address; break;
                }
            }
            BluetoothDevice candidate = null;
            // Query only the existing bond/cache; never scan, pair or open GATT.
            for (BluetoothDevice device : adapter.getBondedDevices()) {
                if (!PenIdentity.matchesBond(device.getAddress(), inputAddress, lastAddress, device.getName())) continue;
                if (candidate == null) candidate = device;
                if (enabled && connected(device)) { candidate = device; break; }
            }
            if (candidate == null) return state;
            lastAddress = candidate.getAddress();
            Object alias = cached(candidate, "getAlias");
            String remoteName = candidate.getName();
            lastName = alias instanceof String && !((String) alias).trim().isEmpty() ? ((String) alias).trim()
                    : remoteName != null && !remoteName.trim().isEmpty() ? remoteName.trim() : "手写笔";
            state.putString("name", lastName);
            state.putBoolean("paired", true);
            boolean online = enabled && connected(candidate);
            state.putBoolean("connected", online);
            Object level = online ? cached(candidate, "getBatteryLevel") : null;
            state.putInt("battery", PenIdentity.visibleBattery(online, level instanceof Integer ? (Integer) level : -1));
        } catch (SecurityException e) {
            state.putString("error", "系统功能组件缺少蓝牙连接权限");
        } catch (RuntimeException e) {
            state.putString("error", "蓝牙状态读取失败：" + e.getClass().getSimpleName());
        }
        return state;
    }
    boolean matchesDevice(BluetoothDevice device) {
        try {
            return device != null && PenIdentity.matchesBond(device.getAddress(), null, lastAddress, device.getName());
        } catch (SecurityException e) { return false; }
    }
}
