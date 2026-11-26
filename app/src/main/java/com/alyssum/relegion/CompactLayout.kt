package com.alyssum.relegion

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun compactCardColors(): CardColors = CardDefaults.defaultColors(
    color = if (isSystemInDarkTheme()) MiuixTheme.colorScheme.surfaceContainer else Color.White,
)

@Composable
internal fun CompactCard(
    modifier: Modifier = Modifier,
    insideMargin: PaddingValues = PaddingValues(16.dp),
    colors: CardColors = compactCardColors(),
    content: @Composable ColumnScope.() -> Unit,
) {
    MiuixCard(modifier = modifier, cornerRadius = 16.dp, insideMargin = insideMargin, colors = colors, content = content)
}

@Composable
internal fun CompactPage(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 900.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
