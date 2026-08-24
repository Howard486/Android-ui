package com.foldspace.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.foldspace.launcher.ui.components.TextAction
import com.foldspace.launcher.ui.theme.FoldSpaceTheme

/**
 * What a long press on blank space should put there.
 *
 * It used to go straight to the widget picker, which was fine while a widget
 * was the only thing that could be placed. A quick-launch block is the second,
 * and silently keeping the old shortcut would make it unreachable.
 */
@Composable
fun PlacementChooser(
    onPickWidget: () -> Unit,
    onPickQuickLaunch: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val tokens = FoldSpaceTheme.tokens
    Box(
        modifier
            .fillMaxSize()
            .background(tokens.overlayScrim())
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(contentPadding)
                .padding(horizontal = 28.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "放什麼到這一格？",
                style = MaterialTheme.typography.headlineSmall,
                color = tokens.textPrimary,
            )

            Choice(
                title = "小工具",
                detail = "App 自己畫的內容，會自己更新。",
                onClick = onPickWidget,
            )
            Choice(
                title = "快速啟動",
                detail = "一個區塊裝好幾格：App、App 捷徑、網址。放得下四個圖示的地方可以放十六個。",
                onClick = onPickQuickLaunch,
            )

            Spacer(Modifier.height(4.dp))
            TextAction(text = "取消", onClick = onDismiss)
        }
    }
}

@Composable
private fun Choice(title: String, detail: String, onClick: () -> Unit) {
    val tokens = FoldSpaceTheme.tokens
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.cardRadius))
            .background(tokens.surfaceAlpha())
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = tokens.textPrimary,
        )
        Text(
            text = detail,
            style = MaterialTheme.typography.labelSmall,
            color = tokens.textMuted,
        )
    }
}
