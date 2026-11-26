package com.alyssum.relegion.hook;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.InputDevice;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.UUID;
import com.alyssum.relegion.PenIdentity;
import com.alyssum.relegion.PenModel;
import com.alyssum.relegion.PenBatteryCache;
import android.content.Intent;

/** Runs only inside SecurityCore, using its existing Bluetooth permissions. */
@SuppressLint("MissingPermission") // Host-process permissions, not the module APK's manifest; SecurityException is handled.
final class PenReader {
    private String lastAddress;
    private String lastName = "手写笔";
    private String lastFirmware = "";
    private String lastSerial = "";
    private String lastHardware = "";
    private String lastModel = "";
    private int gattBattery = -1;
    private final PenBatteryCache battery = new PenBatteryCache();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayDeque<BluetoothGattCharacteristic> pending = new ArrayDeque<>();
    private BluetoothGatt infoGatt;
    private android.content.Context hostContext;
    private boolean infoDone, gotFirmware;
    private int attempts;
    private long lastAttempt;
    private static final int MAX_ATTEMPTS = 4;
    private static final long RETRY_MS = 6000, GATT_TIMEOUT_MS = 12000;
    // Standard GATT services present in this pen's GATT cache: 180A Device Information, 180F Battery.
    private static final UUID DEVICE_INFO = standard(0x180A), BATTERY_SERVICE = standard(0x180F);
    private static final UUID FIRMWARE_REVISION = standard(0x2A26), SERIAL_NUMBER = standard(0x2A25),
            HARDWARE_REVISION = standard(0x2A27), BATTERY_LEVEL = standard(0x2A19);
    private final Runnable infoTimeout = () -> finishInfo("timeout");
    private static UUID standard(int id) { return UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", id)); }
    void setContext(android.content.Context context) { if (context != null) hostContext = context; }
    synchronized void onBluetoothEvent(Intent intent) {
        String action = intent.getAction();
        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) { infoDone = false; attempts = 0; lastAttempt = 0; }
        if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) finishInfo("disconnected");
        if (!"android.bluetooth.device.action.BATTERY_LEVEL_CHANGED".equals(action)) return;
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
        if (!matchesDevice(device)) return;
        long now = SystemClock.elapsedRealtime();
        battery.update(device.getAddress(), true, intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1), now, now);
    }
    private synchronized void finishInfo(String why) {
        handler.removeCallbacks(infoTimeout);
        pending.clear();
        if (infoGatt == null) return;
        try { infoGatt.disconnect(); } catch (RuntimeException ignored) { }
        try { infoGatt.close(); } catch (RuntimeException ignored) { }
        infoGatt = null;
        infoDone = gotFirmware;
        PenHook.info("Pen GATT info closed: " + why + ", firmware=" + (gotFirmware ? lastFirmware : "none"));
    }
    private synchronized void requestInfo(BluetoothDevice device) {
        long now = SystemClock.elapsedRealtime();
        if (hostContext == null || infoGatt != null || infoDone || attempts >= MAX_ATTEMPTS
                || now - lastAttempt < RETRY_MS) return;
        attempts++; lastAttempt = now; gotFirmware = false;
        try {
            infoGatt = device.connectGatt(hostContext, false, infoCallback, BluetoothDevice.TRANSPORT_LE);
            if (infoGatt != null) handler.postDelayed(infoTimeout, GATT_TIMEOUT_MS);
            PenHook.info("Pen GATT info attempt " + attempts + ", started=" + (infoGatt != null));
        } catch (RuntimeException e) {
            infoGatt = null;
            PenHook.info("Pen GATT info attempt failed: " + e.getClass().getSimpleName());
        }
    }
    private void queue(BluetoothGatt gatt, UUID service, UUID characteristic) {
        BluetoothGattService found = gatt.getService(service);
        BluetoothGattCharacteristic item = found == null ? null : found.getCharacteristic(characteristic);
        if (item != null) pending.add(item);
    }
    private void store(UUID id, byte[] value) {
        if (BATTERY_LEVEL.equals(id)) { int level = value[0] & 0xFF; if (level <= 100) gattBattery = level; return; }
        String text = new String(value, StandardCharsets.UTF_8).replaceAll("\\p{Cntrl}", "").trim();
        if (text.isEmpty()) return;
        if (text.length() > 64) text = text.substring(0, 64);
        if (FIRMWARE_REVISION.equals(id)) { lastFirmware = text; gotFirmware = true; }
        else if (SERIAL_NUMBER.equals(id)) lastSerial = text;
        else if (HARDWARE_REVISION.equals(id)) lastHardware = text;
    }
    // One characteristic read may be outstanding at a time, so the queue is drained from the read callback.
    private void readNext(BluetoothGatt gatt) {
        BluetoothGattCharacteristic next;
        while ((next = pending.poll()) != null) if (gatt.readCharacteristic(next)) return;
        finishInfo("complete");
    }
    private final BluetoothGattCallback infoCallback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            synchronized (PenReader.this) {
                if (gatt != infoGatt) { gatt.close(); return; }
                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) gatt.discoverServices();
                else finishInfo("link status=" + status + " state=" + newState);
            }
        }
        @Override public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            synchronized (PenReader.this) {
                if (gatt != infoGatt) return;
                if (status != BluetoothGatt.GATT_SUCCESS) { finishInfo("discover status=" + status); return; }
                pending.clear();
                queue(gatt, DEVICE_INFO, FIRMWARE_REVISION);
                queue(gatt, DEVICE_INFO, SERIAL_NUMBER);
                queue(gatt, DEVICE_INFO, HARDWARE_REVISION);
                queue(gatt, BATTERY_SERVICE, BATTERY_LEVEL);
                readNext(gatt);
            }
        }
        @Override public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            synchronized (PenReader.this) {
                if (gatt != infoGatt) return;
                byte[] value = characteristic.getValue();
                if (status == BluetoothGatt.GATT_SUCCESS && value != null && value.length > 0)
                    store(characteristic.getUuid(), value);
                readNext(gatt);
            }
        }
    };
    private static Object cached(Object object, String method) {
        try { return object.getClass().getMethod(method).invoke(object); }
        catch (ReflectiveOperationException | SecurityException e) { return null; }
    }
    private static boolean connected(BluetoothDevice device) {
        return Boolean.TRUE.equals(cached(device, "isConnected"));
    }
    synchronized Bundle read() {
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
            int inputProduct = 0;
            for (int id : InputDevice.getDeviceIds()) {
                InputDevice input = InputDevice.getDevice(id);
                if (input == null || !PenIdentity.matchesInput(input.getVendorId(), input.getProductId())) continue;
                Object address = cached(input, "getBluetoothAddress");
                if (address instanceof String && BluetoothAdapter.checkBluetoothAddress((String) address)) {
                    inputAddress = (String) address; inputProduct = input.getProductId(); break;
                }
            }
            BluetoothDevice candidate = null;
            // Query only the existing bond; never scan or pair. GATT is opened only to read device info.
            for (BluetoothDevice device : adapter.getBondedDevices()) {
                if (!PenIdentity.matchesBond(device.getAddress(), inputAddress, lastAddress, device.getName())) continue;
                if (candidate == null) candidate = device;
                if (enabled && connected(device)) { candidate = device; break; }
            }
            if (candidate == null) return state;
            String address = candidate.getAddress();
            if (!address.equals(lastAddress)) {
                finishInfo("pen changed");
                lastAddress = address;
                lastFirmware = lastSerial = lastHardware = lastModel = "";
                gattBattery = -1;
                infoDone = false;
                attempts = 0;
                lastAttempt = 0;
            }
            Object alias = cached(candidate, "getAlias");
            String remoteName = candidate.getName();
            lastName = alias instanceof String && !((String) alias).trim().isEmpty() ? ((String) alias).trim()
                    : remoteName != null && !remoteName.trim().isEmpty() ? remoteName.trim() : "手写笔";
            state.putString("name", lastName);
            state.putString("bluetoothName", remoteName == null ? "" : remoteName);
            // The alias is user-editable, so the family comes from the HID product id or the bonded name.
            if (inputProduct != 0) lastModel = PenModel.fromProduct(inputProduct);
            else if (lastModel.isEmpty()) lastModel = PenModel.fromName(remoteName);
            state.putString("penModel", lastModel);
            state.putString("firmware", lastFirmware);
            state.putString("version", lastFirmware);
            state.putString("serial", lastSerial);
            // The OTA identity must stay bound to this GATT address even if a legacy broadcast supplies a serial.
            state.putString("otaSerial", lastSerial);
            state.putString("hardware", lastHardware);
            state.putBoolean("paired", true);
            boolean online = enabled && connected(candidate);
            state.putBoolean("connected", online);
            state.putBoolean("otaInfoReady", online && infoDone);
            Object level = online ? cached(candidate, "getBatteryLevel") : null;
            int reported = level instanceof Integer ? (Integer) level : -1;
            // The stack's cached level is preferred; the pen's own 2A19 read is the fallback.
            if (reported < 0 || reported > 100) reported = online ? gattBattery : -1;
            long now = SystemClock.elapsedRealtime();
            state.putString("device", lastAddress);
            state.putInt("battery", battery.update(lastAddress, online, reported, now, now));
            state.putLong("batterySampled", battery.sampled(lastAddress));
            if (online) requestInfo(candidate);
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
