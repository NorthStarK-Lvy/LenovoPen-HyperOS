package com.alyssum.relegion

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alyssum.relegion.tablet.ModelSettingsStore
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

private const val PROJECT = "https://github.com/NorthStarK-Lvy/LenovoPen-HyperOS"

/** One fixed viewport backdrop, behind the scaffold, bars and scrolling cards. */
@Composable
internal fun AboutBackdrop(scene: AboutScene, active: Boolean, modifier: Modifier = Modifier) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val dark = isSystemInDarkTheme()
    val base = MiuixTheme.colorScheme.surface.toArgb()
    AndroidView(
        factory = { AboutAtmosphereView(it, scene) },
        modifier = modifier,
        onRelease = { it.dispose() },
        update = {
            it.configure(base, dark)
            it.setMotionAllowed(active && resumed)
        }
    )
}

@Composable
internal fun AboutScreen(scene: AboutScene, scroll: ScrollState, headerHeight: Dp, motion: Boolean, onMotionChanged: (Boolean) -> Unit) {
    var showModel by rememberSaveable { mutableStateOf(false) }
    var showCredits by rememberSaveable { mutableStateOf(false) }
    val model by ModelSettingsStore.configuration.collectAsStateWithLifecycle()
    val preferences: @Composable () -> Unit = {
        AboutGroup("偏好设置") {
            ArrowPreference(
                title = "关于平板机型",
                summary = model.name.ifEmpty { "跟随系统" },
                onClick = { showModel = true },
            )
            SwitchPreference(
                title = "动态背景",
                summary = "仅在关于页播放",
                checked = motion,
                onCheckedChange = onMotionChanged,
            )
        }
    }
    val project: @Composable () -> Unit = {
        AboutGroup("项目与支持") {
            AboutLink("源码仓库", "LenovoPen-HyperOS", PROJECT)
            AboutLink("问题反馈", null, "$PROJECT/issues")
            AboutLink("开发者", "NorthStarK-Lvy", "https://github.com/NorthStarK-Lvy")
            ArrowPreference(title = "开源许可与致谢", onClick = { showCredits = true })
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).padding(bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AboutHeader(scene, scroll, headerHeight)
        Column(
            Modifier.widthIn(max = 788.dp).fillMaxWidth().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            CompactCard(Modifier.fillMaxWidth(), insideMargin = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AboutFact("Android", Build.VERSION.RELEASE, Modifier.weight(1f))
                    AboutFact("libxposed API", "102", Modifier.weight(1f))
                    AboutFact("构建号", BuildConfig.VERSION_CODE.toString(), Modifier.weight(1f))
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth >= 620.dp) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(Modifier.weight(1f)) { project() }
                        Box(Modifier.weight(1f)) { preferences() }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        project()
                        preferences()
                    }
                }
            }
        }
    }

    WindowDialog(show = showModel, title = "关于平板机型", onDismissRequest = { showModel = false }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
            TabletModelEditor()
            TextButton("关闭", modifier = Modifier.align(Alignment.End), onClick = { showModel = false })
        }
    }
    WindowDialog(show = showCredits, title = "开源许可与致谢", onDismissRequest = { showCredits = false }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                AboutLink("RELEGION", "MIT License", "$PROJECT/blob/main/LICENSE")
                AboutLink("miuix", "界面组件", "https://github.com/YuKongA/miuix")
                AboutLink("HyperCeiler", "关于页与流动色雾参考", "https://github.com/ReChronoRain/HyperCeiler")
                AboutLink("HyperConnectToolkit", "互联适配 · Apache-2.0", "https://github.com/silverpoetry/HyperConnectToolkit")
                AboutLink("KernelSU", "折叠侧栏与排版参考", "https://github.com/tiann/KernelSU")
                AboutLink("Vector · libxposed", "模块框架与接口", "https://github.com/JingMatrix/Vector")
            }
            TextButton("完成", modifier = Modifier.align(Alignment.End), onClick = { showCredits = false })
        }
    }
}

@Composable
private fun AboutHeader(scene: AboutScene, scroll: ScrollState, headerHeight: Dp) {
    val headerPx = with(LocalDensity.current) { headerHeight.toPx() }
    val colors = MiuixTheme.colorScheme
    Box(
        Modifier.fillMaxWidth().heightIn(min = headerHeight).clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(horizontal = 24.dp, vertical = 16.dp).graphicsLayer {
                val progress = (scroll.value / (headerPx * 0.75f)).coerceIn(0f, 1f)
                alpha = 1f - progress * progress
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(painterResource(R.drawable.ic_relegion), contentDescription = null, modifier = Modifier.size(68.dp))
            Spacer(Modifier.height(6.dp))
            AndroidView(
                factory = { AboutWordmarkView(it, scene) },
                modifier = Modifier.widthIn(max = 350.dp).fillMaxWidth().height(48.dp),
                onRelease = { it.dispose() },
                update = { it.configure(colors.onSurface.toArgb()) },
            )
            Text("我[新]澎湃", fontSize = 15.sp, textAlign = TextAlign.Center,
                color = colors.onSurface.copy(alpha = 0.86f))
            Text(
                "v${BuildConfig.VERSION_NAME}",
                modifier = Modifier.padding(top = 10.dp)
                    .background(colors.surface.copy(alpha = 0.64f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                fontSize = 12.sp,
                color = colors.onSurface,
            )
        }
    }
}

@Composable
private fun AboutFact(label: String, value: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(3.dp))
        Text(label, fontSize = 12.sp, textAlign = TextAlign.Center, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun AboutGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, modifier = Modifier.padding(horizontal = 8.dp).semantics { heading() },
            fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurface)
        CompactCard(Modifier.fillMaxWidth(), insideMargin = PaddingValues(0.dp), content = content)
    }
}

@Composable
private fun AboutLink(title: String, summary: String?, url: String) {
    val context = LocalContext.current
    ArrowPreference(title = title, summary = summary, onClick = {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: RuntimeException) {
            Toast.makeText(context, "未找到可打开链接的应用", Toast.LENGTH_SHORT).show()
        }
    })
}
