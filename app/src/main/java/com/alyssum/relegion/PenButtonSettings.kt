package com.alyssum.relegion

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alyssum.relegion.CompactCard as Card
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

private val clickActions = listOf(
    PenActions.CREATE_NOTE to "创建速记",
    PenActions.SCREEN_RECOGNITION to "超级小爱识屏",
    PenActions.SCREENSHOT to "截屏",
    PenActions.VIDEO to "短视频（抖音、快手）",
    PenActions.OPEN_APP to "打开指定应用",
    PenActions.NONE to "不执行动作",
)

/** Same artwork as Lenovo's own pen app, chosen from the identified pen family. */
@Composable
internal fun PenPicture(model: String, modifier: Modifier = Modifier) {
    val picture = when (model) {
        PenModel.PARKER -> R.drawable.pen_parker
        PenModel.OMAS -> R.drawable.pen_omas
        PenModel.SHEAFFER -> R.drawable.pen_sheaffer
        else -> R.drawable.pen_picasso
    }
    // Keep the artwork aspect ratio; never stretch it.
    Image(painterResource(picture), contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
}

private fun appLabel(context: Context, packageName: String): String = try {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
} catch (_: PackageManager.NameNotFoundException) { packageName }

/** Entry row on the pen page; the editor itself is a second page. */
@Composable
internal fun PenButtonEntry(onClick: () -> Unit) {
    val context = LocalContext.current
    val click = clickActions.firstOrNull { it.first == PenActionStore.action(context) }?.second ?: "创建速记"
    val double = clickActions.firstOrNull { it.first == PenActionStore.doubleAction(context) }?.second ?: "超级小爱识屏"
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
        ArrowPreference(title = "笔按键设置", summary = "单击：$click　双击：$double", onClick = onClick)
    }
}

/** Second page: single click, double click and apps that keep the button for themselves. */
@Composable
internal fun PenButtonSettings(liveState: Bundle) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val root = remember { RootPenBridge(context) }
    var click by remember { mutableStateOf(PenActions.normalize(PenActionStore.action(context), PenActions.CREATE_NOTE)) }
    var clickPackage by remember { mutableStateOf(PenActionStore.openPackage(context)) }
    var double by remember { mutableStateOf(PenActions.normalize(PenActionStore.doubleAction(context), PenActions.SCREEN_RECOGNITION)) }
    var doublePackage by remember { mutableStateOf(PenActionStore.doublePackage(context)) }
    var guard by remember { mutableStateOf(PenActionStore.consumersEnabled(context)) }
    var extra by remember { mutableStateOf(PenActionStore.consumers(context)) }
    var picking by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) {
        // Only ask root when the user already enabled the key service.
        if (PenOfficialState.read(context).getBoolean("rootBridgeEnabled")) {
            running = root.isRunning()
            if (running == false) running = root.startKeys().let { it.success && it.output.isNotBlank() }
        }
    }
    // Every change is saved at once; the root launcher reads the file on the next click.
    fun persist() {
        PenActionStore.save(context, click, clickPackage)
        PenActionStore.saveDoubleAction(context, double, doublePackage)
        PenActionStore.setConsumersEnabled(context, guard)
        PenActionStore.saveConsumers(context, extra)
        root.writeConfig()
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val penModel = liveState.getString("penModel", "")
        Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
            PenPicture(penModel, Modifier.fillMaxWidth().height(96.dp))
            Spacer(Modifier.height(8.dp))
            Text(liveState.getString("name", "手写笔").ifBlank { "手写笔" }, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            // Features that only the Parker family has; Plus and other families do not show them.
            if (PenModel.hasSlideGesture(penModel))
                Text("支持笔身手势与笔尾橡皮", fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        Text("单击", Modifier.padding(start = 8.dp), fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        ActionCard("单击侧键", click, clickPackage, onAction = { click = it; persist() },
            onPackage = { clickPackage = it; persist() })
        Text("双击", Modifier.padding(start = 8.dp), fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        ActionCard("双击侧键", double, doublePackage, onAction = { double = it; persist() },
            onPackage = { doublePackage = it; persist() })
        Text("应用内按键", Modifier.padding(start = 8.dp), fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("笔记应用优先", fontSize = 16.sp)
                    Text("已选应用在前台时，侧键交给应用自己处理，不触发上面的全局动作。", fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
                Switch(checked = guard, onCheckedChange = { guard = it; persist() })
            }
            if (guard) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("已选 ${extra.size} 个应用", Modifier.weight(1f), fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    TextButton(text = "管理", onClick = { picking = true })
                }
            }
        }
        ConsumerPicker(show = picking, selected = extra, onDismiss = { picking = false },
            onChange = { chosen -> extra = chosen; persist() })
        Text("侧键服务", Modifier.padding(start = 8.dp), fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
            Text("状态：" + when (running) { true -> "运行中"; false -> "未运行"; null -> "未启用" }, fontSize = 15.sp)
            Spacer(Modifier.height(4.dp))
            Text("侧键由 root 服务读取笔的输入事件，需要授予 root 权限。", fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            TextButton(text = if (running == true) "停止侧键服务" else "启动侧键服务", onClick = {
                scope.launch {
                    if (running == true) {
                        root.stopKeys(); PenOfficialState.setRootEnabled(context, false); running = false
                    } else {
                        persist()
                        val result = root.startKeys()
                        val up = result.success && result.output.isNotBlank()
                        PenOfficialState.setRootEnabled(context, up); running = up
                        Toast.makeText(context, if (up) "侧键服务已启动" else result.output.ifBlank { "启动失败，请确认已授予 root" },
                            Toast.LENGTH_LONG).show()
                    }
                }
            })
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ActionCard(title: String, action: String, pkg: String, onAction: (String) -> Unit, onPackage: (String) -> Unit) {
    var text by remember(pkg) { mutableStateOf(pkg) }
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
        WindowDropdownPreference(
            items = clickActions.map { it.second },
            selectedIndex = clickActions.indexOfFirst { it.first == action }.coerceAtLeast(0),
            title = title,
            onSelectedIndexChange = { index -> if (index in clickActions.indices) onAction(clickActions[index].first) },
        )
        if (action == PenActions.OPEN_APP) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, label = "应用包名")
                val valid = PenActions.isPackageName(text.trim())
                Text(if (text.isBlank()) "填写要打开的应用包名" else if (valid) appLabel(LocalContext.current, text.trim()) else "包名格式不正确",
                    fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                TextButton(text = "保存应用", enabled = valid, onClick = { onPackage(text.trim()) })
            }
        }
    }
}

/** Picker for apps that keep the pen button. Only the fixed note-app candidates are checked, never the whole app list. */
@Composable
private fun ConsumerPicker(show: Boolean, selected: Set<String>, onDismiss: () -> Unit, onChange: (Set<String>) -> Unit) {
    val context = LocalContext.current
    val candidates = remember { PenActions.noteAppCandidates() }
    val installed = remember(show) { if (show) candidates.filter { isInstalled(context, it) }.toSet() else emptySet() }
    WindowDialog(show = show, title = "笔记应用", summary = "勾选的应用在前台时，侧键交给应用自己处理。",
        onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            candidates.forEach { pkg ->
                val available = pkg in installed
                val checked = pkg in selected
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    .clickable(enabled = available) { onChange(if (checked) selected - pkg else selected + pkg) }
                    .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(appLabel(context, pkg), fontSize = 16.sp,
                            color = if (available) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        Text(if (available) pkg else "未安装", fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    }
                    if (available) Checkbox(state = if (checked) ToggleableState.On else ToggleableState.Off,
                        onClick = { onChange(if (checked) selected - pkg else selected + pkg) })
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            TextButton(text = "完成", modifier = Modifier.fillMaxWidth(), onClick = onDismiss)
        }
    }
}

private fun isInstalled(context: Context, packageName: String): Boolean = try {
    context.packageManager.getPackageInfo(packageName, 0)
    true
} catch (_: PackageManager.NameNotFoundException) { false }
