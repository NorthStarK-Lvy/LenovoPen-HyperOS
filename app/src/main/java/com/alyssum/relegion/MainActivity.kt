package com.alyssum.relegion

import android.Manifest
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.hardware.input.InputManager
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.NavigationRailValue
import top.yukonga.miuix.kmp.basic.rememberNavigationRailState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import com.alyssum.relegion.CompactCard as Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.defaultTextStyles

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val bluetoothPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val controller = remember { ThemeController(ColorSchemeMode.System) }
            val textStyles = remember { defaultTextStyles().let { base -> base.copy(
                main = base.main.copy(fontSize = 15.sp),
                paragraph = base.paragraph.copy(fontSize = 15.sp),
                body1 = base.body1.copy(fontSize = 15.sp),
                body2 = base.body2.copy(fontSize = 14.sp),
                button = base.button.copy(fontSize = 15.sp),
                headline1 = base.headline1.copy(fontSize = 16.sp),
                headline2 = base.headline2.copy(fontSize = 15.sp),
                title1 = base.title1.copy(fontSize = 28.sp),
                title2 = base.title2.copy(fontSize = 21.sp),
                title3 = base.title3.copy(fontSize = 19.sp),
                title4 = base.title4.copy(fontSize = 17.sp),
            ) } }
            MiuixTheme(controller = controller, textStyles = textStyles) {
                RelegionScreen(onOpenSettings = ::openSettings,
                    onAllowBluetooth = { bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT) },
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

internal enum class RelegionPage(val label: String, val icon: Int) {
    PEN("手写笔", R.drawable.ic_pen_status),
    INTERCONNECT("互联", R.drawable.ic_hyperconnect),
    GAME_REFRESH("游戏高刷", R.drawable.ic_game_refresh),
    ABOUT("关于", R.drawable.ic_about),
}

private enum class DetailPage(val title: String) {
    BUTTONS("笔按键设置"),
    FIRMWARE("固件升级"),
    SETTINGS("设置"),
}

@Composable
private fun RelegionScreen(onOpenSettings: () -> Unit, onAllowBluetooth: () -> Unit, onAllowNotifications: () -> Unit) {
    val pages = RelegionPage.entries
    val pager = key(pages.size) { rememberPagerState(pageCount = { pages.size }) }
    val navigation = rememberCoroutineScope()
    val selected = pager.targetPage
    val settledPage = pages[pager.settledPage]
    val context = androidx.compose.ui.platform.LocalContext.current
    val aboutScene = remember(context) { AboutScene(context) }
    val preferences = remember { context.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE) }
    val railState = rememberNavigationRailState(initialValue =
        if (preferences.getBoolean("navigation_rail_expanded", false)) NavigationRailValue.Expanded
        else NavigationRailValue.Collapsed)
    LaunchedEffect(railState.currentValue) {
        preferences.edit().putBoolean("navigation_rail_expanded", railState.isExpanded).apply()
    }
    var aboutMotion by remember { mutableStateOf(preferences.getBoolean("about_motion", true)) }
    val transfer by PenFirmwareTransfer.status.collectAsStateWithLifecycle()
    var detailPage by rememberSaveable {
        mutableStateOf<DetailPage?>(DetailPage.FIRMWARE.takeIf {
            transfer.phase == PenFirmwareTransfer.Phase.FLASHING
        })
    }
    val onPenPage = settledPage == RelegionPage.PEN && !pager.isScrollInProgress
    val penSnapshot = rememberPenSnapshot(
        transfer.phase != PenFirmwareTransfer.Phase.FLASHING &&
            (detailPage == DetailPage.FIRMWARE || detailPage == null && onPenPage),
        firmwarePage = detailPage == DetailPage.FIRMWARE,
    )
    BackHandler(enabled = detailPage != null) { detailPage = null }
    val penScroll = rememberScrollState()
    val aboutScroll = rememberScrollState()
    val aboutHeaderHeight = 218.dp
    val headerPx = with(androidx.compose.ui.platform.LocalDensity.current) { aboutHeaderHeight.toPx() }
    val aboutBlend by remember { derivedStateOf {
        (1f - abs(RelegionPage.ABOUT.ordinal - pager.currentPage - pager.currentPageOffsetFraction)).coerceIn(0f, 1f)
    } }
    val titleProgress by remember(headerPx) { derivedStateOf {
        val progress = ((aboutScroll.value / headerPx - 0.25f) / 0.55f).coerceIn(0f, 1f)
        progress * progress * (3f - 2f * progress)
    } }
    fun selectPage(index: Int) {
        navigation.launch {
            pager.animateScrollToPage(index, animationSpec = tween(300, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)))
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.surface)) {
        val rail = maxWidth >= 840.dp
        // One continuous background in window coordinates, independent of scrolling.
        AboutBackdrop(
            scene = aboutScene,
            active = aboutMotion && detailPage == null && settledPage == RelegionPage.ABOUT && !pager.isScrollInProgress,
            modifier = Modifier.matchParentSize().graphicsLayer { alpha = aboutBlend }
        )
        AnimatedContent(
            targetState = detailPage,
            modifier = Modifier.fillMaxSize().clipToBounds(),
            transitionSpec = {
                val opening = targetState != null
                val direction = if (opening) SlideDirection.Start else SlideDirection.End
                // Miuix navigation moves the detail page a full width and the covered page a quarter width.
                val easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
                val enter = slideIntoContainer(direction, tween(500, easing = easing)) { offset ->
                    if (opening) offset else offset / 4
                }
                val exit = slideOutOfContainer(direction, tween(500, easing = easing)) { offset ->
                    if (opening) offset / 4 else offset
                }
                val coveredEnter = if (opening) enter else enter + fadeIn(tween(500, easing = easing), initialAlpha = 0.9f)
                val coveredExit = if (opening) exit + fadeOut(tween(500, easing = easing), targetAlpha = 0.9f) else exit
                (coveredEnter togetherWith coveredExit).apply {
                    targetContentZIndex = if (opening) 1f else 0f
                }
            },
            label = "Detail page navigation",
        ) { shownDetail ->
            val topBarBlend = if (shownDetail == null) aboutBlend else 0f
            Scaffold(
                containerColor = if (shownDetail == null) Color.Transparent else MiuixTheme.colorScheme.surface,
                topBar = { SmallTopAppBar(title = shownDetail?.title ?: "RELEGION", titlePadding = 24.dp,
                    navigationIcon = { if (shownDetail != null) IconButton(onClick = { detailPage = null }, modifier = Modifier.size(48.dp)) {
                        Text("‹", fontSize = 30.sp, color = MiuixTheme.colorScheme.onSurface) } },
                    actions = {
                        when {
                            shownDetail == DetailPage.SETTINGS -> RestartAppsAction(RestartTarget.SETTINGS)
                            shownDetail == null && settledPage == RelegionPage.PEN -> RestartAppsAction(RestartTarget.PEN)
                            shownDetail == null && settledPage == RelegionPage.INTERCONNECT ->
                                RestartAppsAction(RestartTarget.MILINK, RestartTarget.MIRROR)
                        }
                        if (shownDetail == null) IconButton(
                            onClick = { if (detailPage == null) detailPage = DetailPage.SETTINGS },
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(ImageVector.vectorResource(R.drawable.ic_settings), contentDescription = "设置",
                                tint = MiuixTheme.colorScheme.onSurface)
                        }
                    },
                    color = MiuixTheme.colorScheme.surface.copy(alpha = 1f - topBarBlend),
                    titleColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 1f - topBarBlend * (1f - titleProgress))) },
                bottomBar = {
                    if (!rail && shownDetail == null) NavigationBar(color = MiuixTheme.colorScheme.surface.copy(alpha = 1f - aboutBlend), showDivider = aboutBlend == 0f) {
                        pages.forEachIndexed { index, page ->
                            NavigationBarItem(selected = selected == index, onClick = { selectPage(index) },
                                icon = ImageVector.vectorResource(page.icon), label = page.label)
                        }
                    }
                }
            ) { padding ->
                Row(Modifier.fillMaxSize().padding(padding)) {
                    if (rail && shownDetail == null) NavigationRail(state = railState, modifier = Modifier.fillMaxHeight(),
                        expandContentDescription = "展开侧栏", collapseContentDescription = "收起侧栏",
                        color = MiuixTheme.colorScheme.surface.copy(alpha = 1f - aboutBlend), showDivider = aboutBlend == 0f) {
                        pages.forEachIndexed { index, page ->
                            NavigationRailItem(selected = selected == index, onClick = { selectPage(index) },
                                icon = ImageVector.vectorResource(page.icon), label = page.label)
                        }
                    }
                    if (shownDetail == DetailPage.FIRMWARE) Box(Modifier.weight(1f).fillMaxHeight()) {
                        key(penSnapshot.getString("device"), penSnapshot.getBoolean("connected")) {
                            PenFirmwareScreen(penSnapshot, active = detailPage == DetailPage.FIRMWARE)
                        }
                    } else if (shownDetail == DetailPage.BUTTONS) Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
                        Column(Modifier.widthIn(max = 840.dp).fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp)) { PenButtonSettings(penSnapshot) }
                    } else if (shownDetail == DetailPage.SETTINGS) Box(Modifier.weight(1f).fillMaxHeight()) {
                        SettingsScreen(aboutMotion) {
                            aboutMotion = it
                            preferences.edit().putBoolean("about_motion", it).apply()
                        }
                    } else HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxHeight(), beyondViewportPageCount = 1) { index ->
                        when (pages[index]) {
                            RelegionPage.PEN -> PenScreen(penSnapshot, penScroll, onOpenSettings, onAllowBluetooth,
                                onAllowNotifications, { if (detailPage == null) detailPage = DetailPage.BUTTONS },
                                { if (detailPage == null) detailPage = DetailPage.FIRMWARE })
                            RelegionPage.INTERCONNECT -> HyperConnectScreen()
                            RelegionPage.GAME_REFRESH -> GameRefreshScreen()
                            RelegionPage.ABOUT -> AboutScreen(aboutScene, aboutScroll, aboutHeaderHeight)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberPenSnapshot(active: Boolean, firmwarePage: Boolean): Bundle {
    val lifecycle = LocalLifecycleOwner.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var snapshot by remember { mutableStateOf(StateProvider.peek()) }
    LaunchedEffect(lifecycle, active, firmwarePage) {
        if (!active) return@LaunchedEffect
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            fun acceptSnapshot() {
                snapshot = LocalPenReader.read(context, StateProvider.peek(), firmwarePage)
            }
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = acceptSnapshot()
            }
            val resolver = context.contentResolver
            resolver.registerContentObserver(Uri.parse("content://" + Protocol.AUTHORITY + "/pen"), false, observer)
            val inputs = context.getSystemService(InputManager::class.java)
            val inputListener = object : InputManager.InputDeviceListener {
                override fun onInputDeviceAdded(deviceId: Int) = acceptSnapshot()
                override fun onInputDeviceRemoved(deviceId: Int) = acceptSnapshot()
                override fun onInputDeviceChanged(deviceId: Int) = acceptSnapshot()
            }
            inputs.registerInputDeviceListener(inputListener, Handler(Looper.getMainLooper()))
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: android.content.Context, intent: Intent) {
                    LocalPenReader.onBluetoothEvent(intent)
                    acceptSnapshot()
                }
            }
            val filter = IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothDevice.ACTION_NAME_CHANGED)
                addAction("android.bluetooth.device.action.ALIAS_CHANGED")
                addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED")
                addAction("android.bluetooth.input.profile.action.CONNECTION_STATE_CHANGED")
            }
            context.registerReceiver(receiver, filter, null, Handler(Looper.getMainLooper()), android.content.Context.RECEIVER_EXPORTED)
            try {
                while (true) {
                    // Read before requesting: a settings-page snapshot may already
                    // have arrived. Replies update the UI immediately via observer.
                    acceptSnapshot()
                    // Cold-start the existing host provider if its process was
                    // reclaimed. Read its Bluetooth cache without opening settings.
                    PenStateBridge.refresh(context)
                    delay(Protocol.REQUEST_INTERVAL_MS)
                }
            } finally {
                resolver.unregisterContentObserver(observer)
                inputs.unregisterInputDeviceListener(inputListener)
                context.unregisterReceiver(receiver)
            }
        }
    }
    return snapshot
}

@Composable
private fun PenScreen(snapshot: Bundle, scroll: ScrollState, onOpenSettings: () -> Unit, onAllowBluetooth: () -> Unit,
                      onAllowNotifications: () -> Unit, onOpenButtons: () -> Unit, onOpenFirmware: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember { context.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE) }
    var reminders by remember { mutableStateOf(preferences.getBoolean("connection_reminders", true)) }
    val notificationAllowed = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val bluetoothAllowed = context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    val fresh = PenIdentity.fresh(SystemClock.elapsedRealtime(), snapshot.getLong("sampled"))
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
        if (!bluetoothAllowed) Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
            Text("直接读取手写笔连接", fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Text("允许附近设备权限后，设置进程退出时也能读取真实连接状态。不会扫描或配对设备。", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            TextButton(text = "允许读取蓝牙状态", onClick = onAllowBluetooth)
        }
        ReminderCard(reminders, notificationAllowed, onToggle = {
            reminders = it
            preferences.edit().putBoolean("connection_reminders", reminders).apply()
        }, onAllowNotifications)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 760.dp
            val page = Modifier.widthIn(max = 1040.dp).fillMaxSize().align(Alignment.TopCenter).verticalScroll(scroll)
                .padding(horizontal = 16.dp, vertical = 12.dp)
            if (wide) {
                Row(page, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HeroCard(penName, state, connected, battery, snapshot.getString("penModel", ""))
                        reminderContent()
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PenInfoCard(snapshot, connected)
                        FirmwareEntry(onOpenFirmware)
                        PenButtonEntry(onOpenButtons)
                        if (!fresh) SetupCard(onOpenSettings)
                        DetailCard(fresh, error)
                    }
                }
            } else {
                Column(page, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HeroCard(penName, state, connected, battery, snapshot.getString("penModel", ""))
                    PenInfoCard(snapshot, connected)
                    FirmwareEntry(onOpenFirmware)
                    PenButtonEntry(onOpenButtons)
                    if (!fresh) SetupCard(onOpenSettings)
                    reminderContent()
                    DetailCard(fresh, error)
                    Spacer(Modifier.height(12.dp))
                }
            }
    }
}

@Composable
private fun FirmwareEntry(onOpenFirmware: () -> Unit) {
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
        ArrowPreference(title = "固件升级", summary = "查看当前版本并检查更新", onClick = onOpenFirmware)
    }
}

@Composable
private fun ReminderCard(enabled: Boolean, allowed: Boolean, onToggle: (Boolean) -> Unit, onAllow: () -> Unit) {
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("连接提醒", fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text(if (enabled && !allowed) "等待通知授权" else "连接时提醒，附带可用电量", fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
        if (enabled && !allowed) {
            Spacer(Modifier.height(8.dp))
            TextButton(text = "允许通知", onClick = onAllow)
        }
    }
}

@Composable
private fun HeroCard(name: String, state: String, connected: Boolean, battery: Int, model: String) {
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(8.dp).background(if (connected) Color(0xFF30A46C) else MiuixTheme.colorScheme.onSurfaceVariantSummary, CircleShape))
            Text(state, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        Spacer(Modifier.height(10.dp))
        Text(name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        PenPicture(model, Modifier.fillMaxWidth().height(80.dp).padding(vertical = 12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("手写笔电量", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, fontSize = 14.sp)
                Text(if (battery >= 0) "$battery%" else "—", fontSize = 30.sp, fontWeight = FontWeight.Medium)
            }
            Text(if (battery >= 0) "蓝牙上报" else if (connected) "电量暂不可用" else "连接后读取", fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}

@Composable
private fun PenInfoCard(state: Bundle, connected: Boolean) {
    val serial = state.getString("serial", "")
    val firmware = state.getString("firmware", "")
    val hardware = state.getString("hardware", "")
    val waiting = if (connected) "读取中" else "连接后读取"
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp)) {
        PenFact("固件版本", firmware.ifBlank { waiting })
        PenFact("序列号", serial.ifBlank { waiting })
        if (hardware.isNotBlank()) PenFact("硬件版本", hardware)
    }
}

@Composable
private fun PenFact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 16.sp)
        Text(value, fontSize = 15.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun SetupCard(onOpenSettings: () -> Unit) {
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
        Text("连接系统手写笔页", fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text("首次使用，请在 Xposed 管理器启用本模块，作用域选择“系统功能组件”，重启后打开系统手写笔页。",
            fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Spacer(Modifier.height(12.dp))
        TextButton(text = "打开系统手写笔设置", onClick = onOpenSettings)
    }
}

@Composable
private fun DetailCard(fresh: Boolean, error: String) {
    Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
        Text(if (fresh) "状态已同步" else "尚未同步", fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(if (error.isNotEmpty()) error else "电量来自蓝牙最近上报值。侧键和刷新率由原有 KSU 模块执行，此处显示功能说明。",
            fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}
