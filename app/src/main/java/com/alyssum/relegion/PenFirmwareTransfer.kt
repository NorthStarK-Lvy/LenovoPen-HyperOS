package com.alyssum.relegion

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.BatteryManager
import android.util.Log
import androidx.lifecycle.Lifecycle
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.nordicsemi.android.dfu.DfuBaseService
import no.nordicsemi.android.dfu.DfuProgressListenerAdapter
import no.nordicsemi.android.dfu.DfuServiceInitiator
import no.nordicsemi.android.dfu.DfuServiceListenerHelper

class PenDfuService : DfuBaseService() {
    override fun getNotificationTarget(): Class<out Activity> = MainActivity::class.java
}

/** One download/DFU session at a time, kept across the expected Bluetooth disconnection. */
internal object PenFirmwareTransfer {
    enum class Phase { IDLE, DOWNLOADING, READY, PREPARING, FLASHING, TRANSFERRED }

    private data class Target(
        val address: String,
        val pen: LenovoFirmwareClient.Pen,
        val update: LenovoFirmwareClient.Update,
    )

    data class Status(
        val phase: Phase = Phase.IDLE,
        val percent: Int = -1,
        val message: String = "",
        val address: String = "",
        val version: String = "",
    ) {
        val busy get() = phase == Phase.DOWNLOADING || preparingOrFlashing
        val preparingOrFlashing get() = phase == Phase.PREPARING || phase == Phase.FLASHING
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val current = MutableStateFlow(Status())
    val status = current.asStateFlow()
    private var target: Target? = null
    private var archive: File? = null

    fun downloadedUpdate(address: String, pen: LenovoFirmwareClient.Pen?): LenovoFirmwareClient.Update? =
        target?.takeIf { it.address == address && it.pen == pen && archive != null && current.value.phase == Phase.READY }?.update

    fun download(context: Context, address: String, pen: LenovoFirmwareClient.Pen, update: LenovoFirmwareClient.Update) {
        if (current.value.busy || address.isEmpty() || !LenovoFirmwarePackage.matches(pen, update)) return
        archive?.delete()
        archive = null
        target = Target(address, pen, update)
        current.value = Status(Phase.DOWNLOADING, 0, address = address, version = update.version())
        scope.launch {
            try {
                archive = withContext(Dispatchers.IO) {
                    LenovoFirmwarePackage.download(context.cacheDir, update) { percent ->
                        current.value = Status(Phase.DOWNLOADING, percent, address = address, version = update.version())
                    }
                }
                current.value = Status(Phase.READY, address = address, version = update.version())
            } catch (failure: IOException) {
                val message = if (failure is LenovoFirmwarePackage.InvalidPackage) failure.message.orEmpty()
                    else "下载固件失败，请重试"
                DiagnosticLog.write(Log.ERROR, "RELEGION-OTA", "Download: ${failure.javaClass.simpleName}")
                current.value = Status(message = message, address = address, version = update.version())
            }
        }
    }

    suspend fun install(context: Context, address: String, pen: LenovoFirmwareClient.Pen, lifecycle: Lifecycle) {
        val update = downloadedUpdate(address, pen) ?: return
        val file = archive ?: return
        current.value = Status(Phase.PREPARING, address = address, version = update.version())
        try {
            withContext(Dispatchers.IO) { LenovoFirmwarePackage.verify(file, update) }
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                throw IOException("请授予附近设备权限")
            }
            if (context.getSystemService(LocationManager::class.java)?.isLocationEnabled != true) {
                throw IOException("请开启系统定位，让蓝牙找到重启后的手写笔")
            }
            val tabletLevel = context.getSystemService(BatteryManager::class.java)
                ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            val info = PenFirmwareGatt.read(context, address, includeBattery = true)
            val (tablet, region) = withContext(Dispatchers.IO) { PenOtaTablet.identity }
            val connectedPen = LenovoFirmwareClient.fromGatt(info.serial, info.hardware, info.firmware,
                info.bluetoothName, info.ids, tablet, region)
            if (connectedPen != pen || !LenovoFirmwarePackage.matches(connectedPen, update)) {
                throw IOException("当前连接的手写笔或固件版本已变化，请重新检查更新")
            }
            if (tabletLevel !in 0..100 || info.battery !in 0..100) throw IOException("无法确认平板或手写笔电量")
            if (tabletLevel < 20 || info.battery < 20) throw IOException("平板和手写笔电量均需至少 20%")
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                throw IOException("请回到固件页面，再确认安装")
            }
            startDfu(context.applicationContext, address, info.bluetoothName, update.version(), file)
        } catch (cancelled: CancellationException) {
            current.value = Status(Phase.READY, address = address, version = update.version())
            throw cancelled
        } catch (failure: IOException) {
            val invalidPackage = failure is LenovoFirmwarePackage.InvalidPackage
            if (invalidPackage) {
                file.delete()
                archive = null
            }
            current.value = Status(if (invalidPackage) Phase.IDLE else Phase.READY,
                message = failure.message.orEmpty(), address = address, version = update.version())
            DiagnosticLog.write(Log.WARN, "RELEGION-OTA", "Install preflight: ${failure.javaClass.simpleName}")
        } catch (failure: RuntimeException) {
            current.value = Status(Phase.READY, message = "无法启动蓝牙固件升级", address = address, version = update.version())
            DiagnosticLog.write(Log.ERROR, "RELEGION-OTA", "DFU start: ${failure.javaClass.simpleName}")
        }
    }

    private fun startDfu(context: Context, address: String, name: String, version: String, file: File) {
        val listener = object : DfuProgressListenerAdapter() {
            override fun onProgressChanged(deviceAddress: String, percent: Int, speed: Float, avgSpeed: Float,
                                           currentPart: Int, partsTotal: Int) {
                current.value = Status(Phase.FLASHING, percent, address = address, version = version)
            }

            override fun onDfuCompleted(deviceAddress: String) {
                finish(context, this, Status(Phase.TRANSFERRED, 100, address = address, version = version))
            }

            override fun onDfuAborted(deviceAddress: String) {
                finish(context, this, Status(Phase.READY, message = "固件传输已中止", address = address, version = version))
            }

            override fun onError(deviceAddress: String, error: Int, errorType: Int, message: String) {
                DiagnosticLog.write(Log.ERROR, "RELEGION-OTA", "DFU error=$error type=$errorType")
                finish(context, this, Status(Phase.READY, message = "蓝牙刷写失败（$error）", address = address, version = version))
            }
        }
        DfuServiceInitiator.createDfuNotificationChannel(context)
        DfuServiceListenerHelper.registerProgressListener(context, listener, address)
        try {
            current.value = Status(Phase.FLASHING, address = address, version = version)
            // Transport options from Lenovo FirmwareUpgrader, applied only to the verified CD1 package.
            DfuServiceInitiator(address)
                .setDeviceName(name)
                .setKeepBond(true)
                .setForceDfu(false)
                .setPacketsReceiptNotificationsEnabled(true)
                .setPacketsReceiptNotificationsValue(12)
                .setPrepareDataObjectDelay(400)
                .setUnsafeExperimentalButtonlessServiceInSecureDfuEnabled(true)
                .setNumberOfRetries(1)
                .setForeground(true)
                .setZip(file.absolutePath)
                .start(context, PenDfuService::class.java)
        } catch (failure: RuntimeException) {
            DfuServiceListenerHelper.unregisterProgressListener(context, listener)
            throw failure
        }
    }

    private fun finish(context: Context, listener: DfuProgressListenerAdapter, next: Status) {
        DfuServiceListenerHelper.unregisterProgressListener(context, listener)
        if (next.phase == Phase.TRANSFERRED) {
            archive?.delete()
            archive = null
            target = null
        }
        current.value = next
    }
}
