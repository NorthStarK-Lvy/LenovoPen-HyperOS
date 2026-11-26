package com.alyssum.relegion

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alyssum.relegion.tablet.ModelNamePolicy
import com.alyssum.relegion.tablet.ModelSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun TabletModelEditor() {
    val context = LocalContext.current
    val configuration by ModelSettingsStore.configuration.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var draft by rememberSaveable { mutableStateOf(configuration.name) }
    var edited by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(configuration.name) { if (!edited) draft = configuration.name }
    fun save(value: String) {
        if (saving) return
        saving = true
        scope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { ModelSettingsStore.setName(value) }
                if (saved) { edited = false; draft = ModelNamePolicy.normalize(value) }
                else Toast.makeText(context, "保存失败，请重试", Toast.LENGTH_SHORT).show()
            } finally { saving = false }
        }
    }
    Text("修改参数卡片上方的机型标题。留空可恢复系统显示。", fontSize = 14.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    Spacer(Modifier.height(12.dp))
    TextField(value = draft, onValueChange = {
        if (it.codePointCount(0, it.length) <= ModelNamePolicy.MAX_CODE_POINTS) { draft = it; edited = true }
    }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !saving)
    Spacer(Modifier.height(8.dp))
    Text(when {
        configuration.pending -> "已保存在本机，等待 Vector 同步"
        !configuration.remoteAvailable -> "等待 Vector 配置服务"
        configuration.name.isEmpty() -> "已同步 · 跟随系统"
        else -> "已同步 · ${configuration.name}"
    }, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton("保存", enabled = !saving && edited, onClick = { save(draft) })
        TextButton("恢复默认", enabled = !saving && (draft.isNotEmpty() || configuration.name.isNotEmpty()), onClick = { save("") })
    }
    Spacer(Modifier.height(8.dp))
    Text("首次使用需在 Vector 勾选“设置”并重启；后续保存可直接更新标题。", fontSize = 12.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
}
