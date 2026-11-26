package com.alyssum.relegion

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alyssum.relegion.hyperconnect.config.Feature
import com.alyssum.relegion.hyperconnect.config.FeatureSettingsStore
import com.alyssum.relegion.hyperconnect.platform.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.alyssum.relegion.CompactCard as Card
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun HyperConnectScreen() {
    val context = LocalContext.current
    val configuration by FeatureSettingsStore.configuration.collectAsStateWithLifecycle()
    val runtime by HookRuntimeStore.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val root = remember { RootShell() }
    val launcher = remember { HiddenSettingsLauncher(context, root) }
    var busy by remember { mutableStateOf(false) }
    var restartNeeded by remember { mutableStateOf(false) }
    val snapshotMatches = runtime.configSnapshot == configuration.settings.snapshot

    fun show(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    fun set(feature: Feature, enabled: Boolean) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val saved = withContext(Dispatchers.IO) {
                    val success = FeatureSettingsStore.setEnabled(feature, enabled)
                    if (success) HookRuntimeStore.clear(context)
                    success
                }
                if (saved) restartNeeded = true else show("设置未能保存，请重试")
            } finally { busy = false }
        }
    }
    fun status(feature: Feature): String = when {
        !configuration.isRemoteAvailable -> "等待 Vector 配置服务"
        configuration.hasPendingRemoteWrite -> "已保存，等待同步"
        snapshotMatches && runtime.isApplied(feature) -> "Hook 已加载"
        else -> "等待目标进程加载"
    }

    CompactPage {
        Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
            Text("设备互联", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(if (restartNeeded) "设置已保存，点右上角重启图标重新加载互联服务。" else
                "在 Vector 中勾选小米互联服务与小米妙享。切换选项后，点右上角重启图标重新加载。",
                fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        InterconnectToggle("跨设备通知", "发现附近手机，并用系统原有链路打开通知对应的应用。",
            configuration.settings.crossDeviceNotification,
            status(Feature.CROSS_DEVICE_NOTIFICATION), !busy) { set(Feature.CROSS_DEVICE_NOTIFICATION, it) }
        InterconnectToggle("竖屏应用流转", "平板竖屏时保留应用初始比例，允许流转窗口随屏幕旋转。",
            configuration.settings.portraitStreaming,
            status(Feature.PORTRAIT_STREAMING), !busy) { set(Feature.PORTRAIT_STREAMING, it) }
        InterconnectToggle("MiLink 双通道", "将平板共享通道上限从一个增至两个，补走系统原有的设备绑定流程。",
            configuration.settings.miLinkMultiChannel,
            status(Feature.MILINK_MULTI_CHANNEL), !busy) { set(Feature.MILINK_MULTI_CHANNEL, it) }
        Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
            Text("系统设置", fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Text("账号、网络、通知权限和接收设备仍在系统页面配置。首次打开隐藏入口可能需要 root 授权。",
                fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Spacer(Modifier.height(8.dp))
            TextButton(text = "打开跨设备通知", enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    try {
                        when (val result = launcher.openCrossDeviceNotification()) {
                            is LaunchResult.Success -> Unit
                            is LaunchResult.Failure -> show(result.reason)
                        }
                    } finally { busy = false }
                }
            })
        }
        Text("基于 HyperConnectToolkit v1.6.0。Hook 加载状态不代表对端设备已完成连接。",
            fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun InterconnectToggle(title: String, summary: String, checked: Boolean,
    state: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text(summary, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
        }
        Spacer(Modifier.height(10.dp))
        Text(if (checked) state else "已关闭 · 重启目标进程后生效", fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}
