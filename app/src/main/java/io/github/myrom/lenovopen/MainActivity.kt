package io.github.myrom.lenovopen

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val controller = remember { ThemeController(ColorSchemeMode.System) }
            MiuixTheme(controller = controller) {
                PenScreen(onBack = { finish() }, onOpenSettings = ::openSettings,
                    onAllowNotifications = { notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) })
            }
        }
    }
    private fun openSettings() {
        try {
            startActivity(Intent("com.miui.miinput.action.KEYBOARD_AND_STYLUS_SETTINGS").setPackage(Protocol.TARGET))
        } catch (_: RuntimeException) {
            Toast.makeText(this, "手写笔组件无法打开，请先恢复原版系统功能组件", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
private fun PenScreen(onBack: () -> Unit, onOpenSettings: () -> Unit, onAllowNotifications: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var snapshot by remember { mutableStateOf(StateProvider.peek()) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val preferences = remember { context.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE) }
    var reminders by remember { mutableStateOf(preferences.getBoolean("connection_reminders", true)) }
    var notificationAllowed by remember { mutableStateOf(false) }
    LaunchedEffect(lifecycle) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                context.sendBroadcast(Intent(Protocol.REQUEST).setPackage(Protocol.TARGET))
                delay(400)
                snapshot = StateProvider.peek()
                now = SystemClock.elapsedRealtime()
                notificationAllowed = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                delay(1600)
            }
        }
    }
    val fresh = PenIdentity.fresh(now, snapshot.getLong("sampled"))
    val error = snapshot.getString("error", "")
    val connected = fresh && snapshot.getBoolean("connected") && error.isEmpty()
    val battery = PenIdentity.visibleBattery(connected, snapshot.getInt("battery", -1))
    val penName = snapshot.getString("name", "手写笔").ifBlank { "手写笔" }
    val state = when {
        !fresh -> "等待系统页面回应"
        error.isNotEmpty() -> "暂时无法读取"
        !snapshot.getBoolean("enabled") -> "蓝牙已关闭"
        connected -> "已连接"
        snapshot.getBoolean("paired") -> "已配对，未连接"
        else -> "尚未配对"
    }
    val reminderContent: @Composable () -> Unit = {
        ReminderCard(reminders, notificationAllowed, onToggle = {
            reminders = !reminders
            preferences.edit().putBoolean("connection_reminders", reminders).apply()
        }, onAllowNotifications)
    }
    Scaffold(
        topBar = {
            TopAppBar(title = "手写笔", navigationIcon = {
                TextButton(text = "返回", onClick = onBack)
            })
        },
    ) { insets ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(insets)) {
            val wide = maxWidth >= 760.dp
            val page = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = if (wide) 36.dp else 20.dp, vertical = 16.dp)
            if (wide) {
                Row(page, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        HeroCard(penName, state, connected, battery)
                        if (!fresh) SetupCard(onOpenSettings)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        FunctionCards()
                        reminderContent()
                        DetailCard(fresh, error)
                    }
                }
            } else {
                Column(page, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    HeroCard(penName, state, connected, battery)
                    if (!fresh) SetupCard(onOpenSettings)
                    FunctionCards()
                    reminderContent()
                    DetailCard(fresh, error)
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun ReminderCard(enabled: Boolean, allowed: Boolean, onToggle: () -> Unit, onAllow: () -> Unit) {
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(24.dp)) {
        InfoRow("连接提醒", if (!enabled) "已关闭" else if (allowed) "已开启" else "等待通知授权",
            "手写笔连接时提示一次，电量可用时一并显示。点击通知可打开详情。")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(text = if (enabled) "关闭提醒" else "开启提醒", onClick = onToggle)
            if (enabled && !allowed) TextButton(text = "允许通知", onClick = onAllow)
        }
    }
}

@Composable
private fun HeroCard(name: String, state: String, connected: Boolean, battery: Int) {
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).background(if (connected) Color(0xFF30A46C) else MiuixTheme.colorScheme.onSurfaceVariantSummary, CircleShape))
            Text(state, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        Spacer(Modifier.height(16.dp))
        Text(name, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        PenIllustration(Modifier.fillMaxWidth().height(180.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("手写笔电量", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 14.sp)
                Text(if (battery >= 0) "$battery%" else "—", fontSize = 40.sp, fontWeight = FontWeight.Medium)
            }
            Text(if (battery >= 0) "蓝牙上报" else if (connected) "电量暂不可用" else "连接后读取", fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}

@Composable
private fun PenIllustration(modifier: Modifier) {
    Canvas(modifier) {
        val start = Offset(size.width * .18f, size.height * .76f)
        val end = Offset(size.width * .82f, size.height * .24f)
        drawLine(Color.Black.copy(alpha = .06f), start + Offset(3.dp.toPx(), 12.dp.toPx()), end + Offset(3.dp.toPx(), 12.dp.toPx()), 17.dp.toPx(), StrokeCap.Round)
        drawLine(Brush.linearGradient(listOf(Color(0xFFB7BDC8), Color(0xFFF4F5F7), Color(0xFFB8C0CB)), start, end), start, end, 14.dp.toPx(), StrokeCap.Round)
        val buttonStart = Offset(start.x + (end.x - start.x) * .58f, start.y + (end.y - start.y) * .58f)
        val buttonEnd = Offset(start.x + (end.x - start.x) * .69f, start.y + (end.y - start.y) * .69f)
        drawLine(Color(0xFF8C95A3), buttonStart, buttonEnd, 4.dp.toPx(), StrokeCap.Round)
        drawLine(Color(0xFF737D8B), start, start - Offset(9.dp.toPx(), -7.dp.toPx()), 5.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
private fun FunctionCards() {
    Text("侧键功能", Modifier.padding(start = 8.dp), fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(24.dp)) {
        InfoRow("单击侧键", "小米创作速记", "快速打开速记小窗")
        Spacer(Modifier.height(24.dp))
        InfoRow("双击侧键", "超级小爱识屏", "识别当前屏幕内容")
    }
}

@Composable
private fun InfoRow(label: String, title: String, description: String) {
    Text(label, fontSize = 13.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    Spacer(Modifier.height(6.dp))
    Text(title, fontSize = 19.sp, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(6.dp))
    Text(description, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
}

@Composable
private fun SetupCard(onOpenSettings: () -> Unit) {
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(24.dp)) {
        Text("连接系统手写笔页", fontSize = 18.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text("首次使用，请在 Xposed 管理器启用本模块，作用域选择“系统功能组件”，重启后打开系统手写笔页。",
            fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Spacer(Modifier.height(12.dp))
        TextButton(text = "打开系统手写笔设置", onClick = onOpenSettings)
    }
}

@Composable
private fun DetailCard(fresh: Boolean, error: String) {
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(24.dp)) {
        Text(if (fresh) "状态已同步" else "尚未同步", fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(if (error.isNotEmpty()) error else "电量来自蓝牙最近上报值。侧键和刷新率由原有 KSU 模块执行，此处显示功能说明。",
            fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}
