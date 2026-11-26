package com.alyssum.relegion

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Reads device information over a short-lived client connected to the existing bond. */
@SuppressLint("MissingPermission") // The firmware screen checks BLUETOOTH_CONNECT before reading.
internal object PenFirmwareGatt {
    private fun uuid(id: String) = UUID.fromString("0000$id-0000-1000-8000-00805f9b34fb")

    private val deviceInfo = uuid("180a")
    private val serialNumber = uuid("2a25")
    private val firmwareRevision = uuid("2a26")
    private val pnpId = uuid("2a50")
    private val hardwareModel = uuid("2a27")
    private val batteryService = uuid("180f")
    private val batteryLevel = uuid("2a19")
    private val fields = listOf(serialNumber, firmwareRevision, pnpId, hardwareModel)
    private val reader = Mutex()

    data class Info(
        val serial: String,
        val firmware: String,
        val hardware: String,
        val bluetoothName: String,
        val ids: LenovoFirmwareClient.DeviceIds?,
        val battery: Int,
    )

    suspend fun read(context: Context, address: String, includeBattery: Boolean = false): Info =
        reader.withLock { readGatt(context, address, includeBattery) }

    private suspend fun readGatt(context: Context, address: String, includeBattery: Boolean): Info = withContext(Dispatchers.Main.immediate) {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        val device = adapter?.bondedDevices?.firstOrNull { it.address == address }
            ?: throw IOException("缺少手写笔蓝牙配对")
        val deviceName = device.name.orEmpty()
        val values = mutableMapOf<UUID, ByteArray>()
        val queue = ArrayDeque<BluetoothGattCharacteristic>()
        var connection: BluetoothGatt? = null
        try {
            val completed = withTimeoutOrNull(12_000) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    val callback = object : BluetoothGattCallback() {
                        private fun next(gatt: BluetoothGatt) {
                            while (queue.isNotEmpty()) {
                                if (gatt.readCharacteristic(queue.removeFirst())) return
                            }
                            continuation.resume(Unit)
                        }

                        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                            if (!continuation.isActive) return
                            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                                if (!gatt.discoverServices()) continuation.resumeWithException(IOException("无法发现手写笔蓝牙服务"))
                            } else if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                                if (values.isNotEmpty()) continuation.resume(Unit)
                                else continuation.resumeWithException(IOException("无法连接手写笔蓝牙服务（$status）"))
                            }
                        }

                        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                            if (!continuation.isActive) return
                            val service = if (status == BluetoothGatt.GATT_SUCCESS) gatt.getService(deviceInfo) else null
                            if (service == null) {
                                continuation.resumeWithException(IOException("手写笔未提供设备信息服务"))
                                return
                            }
                            fields.mapNotNull(service::getCharacteristic).forEach(queue::addLast)
                            if (includeBattery) gatt.getService(batteryService)
                                ?.getCharacteristic(batteryLevel)?.let(queue::addLast)
                            next(gatt)
                        }

                        override fun onCharacteristicRead(
                            gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int,
                        ) {
                            if (!continuation.isActive) return
                            if (status == BluetoothGatt.GATT_SUCCESS && value.isNotEmpty()) {
                                values[characteristic.uuid] = value.copyOf()
                            }
                            next(gatt)
                        }
                    }
                    connection = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE,
                        BluetoothDevice.PHY_LE_1M_MASK, Handler(Looper.getMainLooper()))
                    if (connection == null) continuation.resumeWithException(IOException("无法启动手写笔蓝牙读取"))
                }
            }
            if (completed == null && values.isEmpty()) throw IOException("读取手写笔固件信息超时")
            fun text(id: UUID) =
                values[id]?.toString(Charsets.UTF_8)?.substringBefore('\u0000')?.trim().orEmpty()
            Info(text(serialNumber), text(firmwareRevision), text(hardwareModel), deviceName,
                values[pnpId]?.let { LenovoFirmwareClient.DeviceIds.fromPnpId(it) },
                values[batteryLevel]?.singleOrNull()?.toInt()?.and(0xff)?.takeIf { it in 0..100 } ?: -1)
        } finally {
            connection?.let { gatt ->
                runCatching { gatt.disconnect() }
                runCatching { gatt.close() }
            }
        }
    }
}

/** Uses the Lenovo vendor identity when HyperOS supplies a Xiaomi model in Build.MODEL. */
internal object PenOtaTablet {
    val identity: Pair<String, String> by lazy {
        val vendorModel = property("ro.product.vendor.model")
        val vendorBrand = property("ro.product.vendor.brand")
        val model = when {
            vendorBrand.equals("Lenovo", ignoreCase = true) -> vendorModel
            Build.MANUFACTURER.equals("Lenovo", ignoreCase = true) -> Build.MODEL
            Build.MODEL == "LAVIE Tab T12 12QHD1" -> Build.MODEL
            else -> ""
        }
        val configuredRegion = sequenceOf("ro.config.lgsi.region", "ro.lenovo.region", "ro.odm.lenovo.region")
            .map(::property).firstOrNull { it.isNotBlank() && it != "unknown" }
        val vendorBuild = if (configuredRegion == null && model == "TB710FU") {
            property("ro.vendor.build.version.incremental")
        } else ""
        val region = LenovoFirmwareClient.resolveRegion(configuredRegion, model, vendorBuild)
        model to region
    }

    private fun property(name: String): String = try {
        val process = ProcessBuilder("/system/bin/getprop", name).redirectErrorStream(true).start()
        try {
            process.inputStream.bufferedReader().use { it.readLine().orEmpty().trim() }
        } finally {
            process.destroy()
        }
    } catch (_: IOException) {
        ""
    } catch (_: SecurityException) {
        ""
    }
}
