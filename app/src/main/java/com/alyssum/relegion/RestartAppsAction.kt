package com.alyssum.relegion

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.state.ToggleableState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alyssum.relegion.hyperconnect.config.FeatureSettingsStore
import com.alyssum.relegion.hyperconnect.platform.HookRuntimeStore
import com.alyssum.relegion.hyperconnect.platform.RootCommand
import com.alyssum.relegion.hyperconnect.platform.RootShell
import com.alyssum.relegion.tablet.ModelSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

private enum class RestartTarget(val label: String, val detail: String, val command: RootCommand) {
    SETTINGS("设置", "关于平板机型", RootCommand.STOP_SETTINGS),
    PEN("系统功能组件", "手写笔设置", RootCommand.STOP_SECURITY_CORE),
    MILINK("小米互联服务", "通知与双通道", RootCommand.STOP_MILINK),
    MIRROR("小米妙享", "应用流转", RootCommand.STOP_MIRROR),
}

/** Explicit user action only. No commands run while the dialog is merely shown. */
@Composable
internal fun RestartAppsAction(page: RelegionPage) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val root = remember { RootShell() }
    val features by FeatureSettingsStore.configuration.collectAsStateWithLifecycle()
    val model by ModelSettingsStore.configuration.collectAsStateWithLifecycle()
    var show by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<RestartTarget>()) }
    val synchronized = (!selected.any { it == RestartTarget.MILINK || it == RestartTarget.MIRROR } ||
        features.isRemoteAvailable && !features.hasPendingRemoteWrite) &&
        (RestartTarget.SETTINGS !in selected || model.remoteAvailable && !model.pending)

    IconButton(onClick = {
        selected = when (page) {
            RelegionPage.PEN -> setOf(RestartTarget.PEN)
            RelegionPage.INTERCONNECT -> setOf(RestartTarget.MILINK, RestartTarget.MIRROR)
            RelegionPage.ABOUT -> setOf(RestartTarget.SETTINGS)
            RelegionPage.GAME_REFRESH -> emptySet()
        }
        show = true
    }, enabled = !busy, modifier = Modifier.size(48.dp)) {
        Icon(ImageVector.vectorResource(R.drawable.ic_restart_apps), contentDescription = "重启应用",
            modifier = Modifier.size(24.dp), tint = MiuixTheme.colorScheme.onSurface)
    }
    WindowDialog(show = show, title = "重启应用", summary = "需要 root，停止后请重新打开对应应用。",
        onDismissRequest = { if (!busy) show = false }) {
      Column(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            RestartTarget.entries.forEach { target ->
                Row(Modifier.fillMaxWidth().clickable(enabled = !busy) {
                    selected = if (target in selected) selected - target else selected + target
                }.heightIn(min = 48.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(target.label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(target.detail, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                    Checkbox(state = if (target in selected) ToggleableState.On else ToggleableState.Off, enabled = !busy, onClick = {
                        selected = if (target in selected) selected - target else selected + target
                    })
                }
            }
        }
        if (!synchronized) Text("配置尚未同步到 Vector，请稍候。", fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(text = "取消", modifier = Modifier.weight(1f), enabled = !busy, onClick = { show = false })
            TextButton(text = if (busy) "正在重启…" else "确认重启", modifier = Modifier.weight(1f),
                colors = ButtonDefaults.textButtonColorsPrimary(),
                enabled = !busy && selected.isNotEmpty() && synchronized, onClick = {
                    busy = true
                    val requested = selected.toList()
                    scope.launch {
                        try {
                            val failed = mutableListOf<String>()
                            var stoppedInterconnect = false
                            for (target in requested) {
                                val result = root.execute(target.command)
                                if (!result.isSuccess) failed += target.label
                                else if (target == RestartTarget.MILINK || target == RestartTarget.MIRROR) stoppedInterconnect = true
                            }
                            if (stoppedInterconnect) withContext(Dispatchers.IO) { HookRuntimeStore.clear(context) }
                            show = false
                            Toast.makeText(context, if (failed.isEmpty()) "已停止所选应用，请重新打开" else
                                "未能停止：${failed.joinToString("、")}。请检查 root 授权。", Toast.LENGTH_LONG).show()
                        } finally { busy = false }
                    }
                })
        }
      }
    }
}
