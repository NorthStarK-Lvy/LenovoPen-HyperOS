package com.alyssum.relegion

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alyssum.relegion.tablet.ModelSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun SettingsScreen(motion: Boolean, onMotionChanged: (Boolean) -> Unit) {
    var showModel by rememberSaveable { mutableStateOf(false) }
    val model by ModelSettingsStore.configuration.collectAsStateWithLifecycle()
    val level by DiagnosticLog.level.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var saving by remember { mutableStateOf(false) }

    CompactPage {
        CompactCard(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
            ArrowPreference(title = "关于平板机型", summary = model.name.ifEmpty { "跟随系统" },
                onClick = { showModel = true })
            SwitchPreference(title = "动态背景", summary = "仅在关于页播放",
                checked = motion, onCheckedChange = onMotionChanged)
        }
        CompactCard(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
            WindowDropdownPreference(
                title = "日志输出等级",
                items = listOf("Warn", "Error", "不输出"),
                selectedIndex = level.ordinal,
                enabled = !saving,
                onSelectedIndexChange = { index ->
                    val next = LogLevel.values().getOrNull(index) ?: return@WindowDropdownPreference
                    if (!saving && next != level) {
                        saving = true
                        scope.launch {
                            try {
                                val synced = withContext(Dispatchers.IO) { DiagnosticLog.select(next) }
                                if (!synced) Toast.makeText(context, "已保存在本机，等待 Vector 同步", Toast.LENGTH_SHORT).show()
                            } finally {
                                saving = false
                            }
                        }
                    }
                },
            )
        }
    }

    WindowDialog(show = showModel, title = "关于平板机型", onDismissRequest = { showModel = false }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
            TabletModelEditor()
            TextButton("关闭", modifier = Modifier.align(Alignment.End), onClick = { showModel = false })
        }
    }
}
