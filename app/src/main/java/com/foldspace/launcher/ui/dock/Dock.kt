package com.foldspace.launcher.ui.dock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import com.foldspace.launcher.core.launcher.AppEntry
import com.foldspace.launcher.notifications.NotificationSummary
import com.foldspace.launcher.ui.components.AppTile
import com.foldspace.launcher.ui.theme.FoldSpaceTheme

/**
 * §5.4 Dynamic Dock — fixed pins plus smart slots.
 *
 * The dock is drawn *above* the Samsung Wallet gesture zone, never inside it
 * (§6.1): the reserved strip is added by the caller as bottom padding, so no
 * dock item can ever land on the swipe-up target.
 */
@Composable
fun Dock(
    apps: List<AppEntry>,
    notifications: NotificationSummary,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = FoldSpaceTheme.tokens

    if (apps.isEmpty()) {
        EmptyDockHint(modifier)
        return
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.cardRadius))
            .background(tokens.surfaceAlpha())
            .border(1.dp, tokens.outline, RoundedCornerShape(tokens.cardRadius))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        apps.forEach { entry ->
            AppTile(
                entry = entry,
                onClick = { onLaunch(entry) },
                onLongClick = { onLongPress(entry) },
                iconSize = 46.dp,
                showLabel = false,
                badgeCount = notifications.countFor(entry.packageName),
            )
        }
    }
}

/**
 * With no pins and no usage data the dock would otherwise render as an empty
 * bar, which reads as broken. Saying why is better than showing nothing.
 */
@Composable
private fun EmptyDockHint(modifier: Modifier = Modifier) {
    val tokens = FoldSpaceTheme.tokens
    Box(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(tokens.cardRadius))
            .background(tokens.surfaceAlpha())
            .border(1.dp, tokens.outline, RoundedCornerShape(tokens.cardRadius)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "長按 App 可釘選到 Dock",
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textMuted,
        )
    }
}
