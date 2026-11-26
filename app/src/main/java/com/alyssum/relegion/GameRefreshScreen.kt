package com.alyssum.relegion

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alyssum.relegion.refresh.GameRefreshSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GameRefreshScreen() {
    val enabled by GameRefreshSettingsStore.enabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }

    CompactPage {
        CompactCard(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
            SwitchPreference(
                title = "游戏高刷",
                summary = when {
                    saving -> "正在保存到 Vector"
                    enabled == null -> "等待 Vector 配置服务"
                    else -> "修改后重启平板生效"
                },
                checked = enabled == true,
                enabled = enabled != null && !saving,
                onCheckedChange = { checked ->
                    if (!saving) {
                        saving = true
                        scope.launch {
                            try {
                                val saved = withContext(Dispatchers.IO) {
                                    GameRefreshSettingsStore.setEnabled(checked)
                                }
                                if (!saved) Toast.makeText(context, "设置未能保存，请重试", Toast.LENGTH_SHORT).show()
                            } finally {
                                saving = false
                            }
                        }
                    }
                },
            )
        }
        CompactCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GameRefreshDetail("HyperOS 游戏刷新率",
                    "放宽 PowerKeeper 对默认档位游戏的 60 Hz 限制，跟随屏幕设置，保留系统温控、省电和游戏专属档位。")
                GameRefreshDetail("Android 默认帧率",
                    "解除 GameManager 的默认 60 FPS 限制。游戏实际帧率取决于游戏自身支持和运行状态。")
            }
        }
        Text(
            text = "使用前请在 Vector 作用域中启用「系统框架」和 PowerKeeper（com.miui.powerkeeper）。",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun GameRefreshDetail(title: String, summary: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MiuixTheme.textStyles.title4)
        Text(summary, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}
