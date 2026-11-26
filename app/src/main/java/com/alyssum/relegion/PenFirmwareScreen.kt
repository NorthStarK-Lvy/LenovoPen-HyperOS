package com.alyssum.relegion

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.text.format.Formatter
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun PenFirmwareScreen(snapshot: Bundle, active: Boolean) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val transfer by PenFirmwareTransfer.status.collectAsStateWithLifecycle()
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<LenovoFirmwareClient.Update?>(null) }
    var checkedPen by remember { mutableStateOf<LenovoFirmwareClient.Pen?>(null) }
    var error by remember { mutableStateOf("") }
    val online = snapshot.getBoolean("connected") && snapshot.getString("error", "").isEmpty() &&
        PenIdentity.fresh(SystemClock.elapsedRealtime(), snapshot.getLong("sampled"))
    val address = snapshot.getString("device", "")
    var hasPermission by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
    }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    var confirmInstall by remember { mutableStateOf(false) }
    val requestScan = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) confirmInstall = true
    }
    var retry by remember { mutableStateOf(0) }
    var reading by remember { mutableStateOf(false) }
    var readError by remember { mutableStateOf("") }
    var firmware by remember { mutableStateOf("") }
    var pen by remember { mutableStateOf<LenovoFirmwareClient.Pen?>(null) }
    LaunchedEffect(active, online, address, hasPermission, retry, lifecycle, transfer.preparingOrFlashing) {
        if (!active || !online || address.isEmpty() || !hasPermission || transfer.preparingOrFlashing) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            reading = true
            readError = ""
            pen = null
            try {
                val info = PenFirmwareGatt.read(context, address)
                firmware = LenovoFirmwareClient.firmwareVersion(info.firmware).ifBlank { info.firmware }
                val (tabletModel, region) = withContext(Dispatchers.IO) { PenOtaTablet.identity }
                pen = LenovoFirmwareClient.fromGatt(
                    info.serial, info.hardware, info.firmware, info.bluetoothName, info.ids, tabletModel, region,
                )
                if (pen == null) readError = when {
                    info.firmware.isBlank() -> "手写笔未返回固件版本"
                    LenovoFirmwareClient.firmwareVersion(info.firmware).isEmpty() -> "手写笔返回了无法识别的固件版本"
                    info.serial.isBlank() -> "手写笔未返回序列号"
                    info.hardware.isBlank() -> "手写笔未返回硬件型号"
                    info.ids == null -> "手写笔未返回完整的蓝牙 VID/PID"
                    !info.ids.supportsQuery() -> "这支笔未使用联想固件查询接口"
                    tabletModel.isBlank() -> "无法识别联想平板的原厂型号"
                    else -> "手写笔的固件查询信息不完整"
                }
                if (readError.isNotBlank()) DiagnosticLog.write(Log.WARN, "RELEGION-OTA", "GATT: $readError")
            } catch (_: SecurityException) {
                hasPermission = false
                DiagnosticLog.write(Log.WARN, "RELEGION-OTA", "GATT: nearby devices permission denied")
            } catch (failure: IOException) {
                readError = failure.message ?: "无法读取手写笔固件信息"
                DiagnosticLog.write(Log.WARN, "RELEGION-OTA", "GATT: $readError")
            } finally {
                reading = false
            }
        }
    }
    val downloaded = PenFirmwareTransfer.downloadedUpdate(address, pen)
    val latest = result.takeIf { checkedPen == pen } ?: downloaded
    val downloadIssues = remember(latest) {
        if (latest?.available() == true) LenovoFirmwarePackage.downloadIssues(latest) else emptyList()
    }
    val shownVersion = firmware.ifBlank { snapshot.getString("firmware", "") }
    val supportsUpdate = LenovoFirmwarePackage.matches(pen, latest)
    val transferForPen = transfer.address == address
    val retryVersionRead = transferForPen && transfer.phase == PenFirmwareTransfer.Phase.TRANSFERRED &&
        transfer.version != firmware && online && !reading

    CompactPage {
        CompactCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(ImageVector.vectorResource(R.drawable.ic_pen_status), contentDescription = null,
                    modifier = Modifier.size(32.dp), tint = MiuixTheme.colorScheme.primary)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(snapshot.getString("name", "").ifBlank { "联想手写笔" }, style = MiuixTheme.textStyles.title4)
                    Text(
                        when {
                            !online -> "未连接手写笔"
                            shownVersion.isNotBlank() -> "当前固件 $shownVersion"
                            reading -> "正在读取当前固件"
                            else -> "固件版本未知"
                        },
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
        CompactCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val status = when {
                    transfer.busy -> when (transfer.phase) {
                        PenFirmwareTransfer.Phase.DOWNLOADING ->
                            if (transfer.percent < 100) "正在下载固件 ${transfer.percent}%" else "正在校验固件"
                        PenFirmwareTransfer.Phase.PREPARING -> "正在核对手写笔和固件"
                        else -> if (transfer.percent < 0) "正在连接手写笔升级模式" else "正在写入固件 ${transfer.percent}%"
                    }
                    transferForPen && transfer.phase == PenFirmwareTransfer.Phase.TRANSFERRED ->
                        if (online && firmware == transfer.version) "已确认更新至 ${transfer.version}"
                        else "传输完成，等待手写笔重连并确认固件版本"
                    !hasPermission -> "允许访问附近设备后可读取固件信息"
                    !online -> "连接手写笔后可检查更新"
                    reading -> "正在读取手写笔的固件信息"
                    readError.isNotBlank() -> readError
                    pen == null -> "等待手写笔返回完整的固件信息"
                    checking -> "正在检查更新…"
                    error.isNotBlank() -> error
                    downloaded != null -> "固件已下载并通过校验"
                    latest?.available() == true -> "发现新版本 ${latest.version()}"
                    latest != null -> "已是最新版本"
                    else -> "检查更新"
                }
                Text(status, style = MiuixTheme.textStyles.title4)
                if (online && latest?.available() == true && latest.sizeBytes() > 0) {
                    Text("固件大小 ${Formatter.formatShortFileSize(context, latest.sizeBytes())}",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
                if (transferForPen && transfer.message.isNotBlank()) {
                    Text(transfer.message, style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
                if (supportsUpdate && online && !transfer.busy) {
                    TextButton(
                        text = if (downloaded != null) "安装到手写笔" else "下载固件",
                        modifier = Modifier.fillMaxWidth(),
                        enabled = active && !reading && !checking,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        onClick = {
                            val target = pen ?: return@TextButton
                            val update = latest ?: return@TextButton
                            if (downloaded == null) {
                                PenFirmwareTransfer.download(context.applicationContext, address, target, update)
                            } else if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                                == PackageManager.PERMISSION_GRANTED) {
                                confirmInstall = true
                            } else {
                                requestScan.launch(Manifest.permission.BLUETOOTH_SCAN)
                            }
                        },
                    )
                } else if (online && latest?.available() == true && !transfer.busy) {
                    val reason = if (downloadIssues.isEmpty())
                        "此型号的固件匹配规则尚未验证，暂不可安装。"
                    else downloadIssues.joinToString("。", postfix = "。") { it.message }
                    Text(reason,
                        style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
                TextButton(
                    text = when {
                        !hasPermission -> "允许蓝牙连接"
                        retryVersionRead -> "重新读取固件版本"
                        readError.isNotBlank() && pen == null -> "重新读取"
                        checking -> "正在检查…"
                        else -> "检查更新"
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = active && !transfer.busy && (!hasPermission || online && !reading && !checking &&
                        (pen != null || readError.isNotBlank() || retryVersionRead)),
                    onClick = {
                        if (!hasPermission) {
                            requestPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                            return@TextButton
                        }
                        if (retryVersionRead || pen == null && readError.isNotBlank()) {
                            retry++
                            return@TextButton
                        }
                        val target = pen ?: return@TextButton
                        if (checking) return@TextButton
                        checking = true
                        result = null
                        checkedPen = target
                        error = ""
                        scope.launch {
                            try {
                                val checked = withContext(Dispatchers.IO) { LenovoFirmwareClient.check(target) }
                                if (checked.available()) {
                                    val issues = LenovoFirmwarePackage.downloadIssues(checked)
                                    if (issues.isNotEmpty()) {
                                        DiagnosticLog.write(Log.ERROR, "RELEGION-OTA",
                                            "Manifest ${checked.version()} download blocked ${issues.joinToString { it.name }}")
                                    }
                                }
                                result = checked
                            } catch (_: LenovoFirmwareClient.PackageMismatch) {
                                DiagnosticLog.write(Log.ERROR, "RELEGION-OTA", "Check: package identity mismatch")
                                error = "联想返回的固件包与当前手写笔不匹配"
                            } catch (failure: LenovoFirmwareClient.Failure) {
                                DiagnosticLog.write(Log.ERROR, "RELEGION-OTA", failure.message.orEmpty())
                                error = firmwareCheckError(failure)
                            } catch (failure: IOException) {
                                DiagnosticLog.write(Log.ERROR, "RELEGION-OTA", "Check failed: ${failure.javaClass.simpleName}")
                                error = "无法完成固件检查"
                            } finally {
                                checking = false
                            }
                        }
                    },
                )
                Text("检查时会将手写笔型号和序列号发送给联想。",
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
        if (online && latest?.available() == true) {
            CompactCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("更新日志", style = MiuixTheme.textStyles.title4)
                    Text("联想未提供此版本的更新说明。",
                        style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
        }
    }
    WindowDialog(
        show = active && online && downloaded != null && confirmInstall,
        title = "安装手写笔固件",
        summary = "将把 ${downloaded?.version().orEmpty()} 写入当前手写笔。请保持蓝牙开启并让手写笔靠近平板。",
        onDismissRequest = { confirmInstall = false },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton("取消", modifier = Modifier.weight(1f), onClick = { confirmInstall = false })
            TextButton("确认安装", modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary(),
                onClick = {
                    confirmInstall = false
                    val target = pen ?: return@TextButton
                    if (active && online) scope.launch { PenFirmwareTransfer.install(context, address, target, lifecycle) }
                },
            )
        }
    }
}

private fun firmwareCheckError(failure: LenovoFirmwareClient.Failure): String {
    val stage = when (failure.stage) {
        LenovoFirmwareClient.Stage.CONFIG -> "查询固件"
        LenovoFirmwareClient.Stage.MANIFEST -> "读取固件清单"
    }
    val reason = when (failure.reason) {
        LenovoFirmwareClient.Reason.DNS -> "无法解析联想服务器地址"
        LenovoFirmwareClient.Reason.CONNECTION -> "无法连接联想服务器"
        LenovoFirmwareClient.Reason.TIMEOUT -> "联想服务器响应超时"
        LenovoFirmwareClient.Reason.TLS -> "安全连接失败"
        LenovoFirmwareClient.Reason.HTTP -> "HTTP ${failure.detail}"
        LenovoFirmwareClient.Reason.SERVER -> when (failure.detail) {
            "FUS-0112" -> "联想服务器未识别设备型号（FUS-0112）"
            "" -> "联想服务器拒绝了请求"
            else -> "联想服务器返回 ${failure.detail}"
        }
        LenovoFirmwareClient.Reason.RESPONSE -> "服务器响应格式异常"
        LenovoFirmwareClient.Reason.IO -> "读取服务器响应失败"
    }
    return "$stage 失败 · $reason"
}
