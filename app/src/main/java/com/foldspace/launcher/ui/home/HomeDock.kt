package com.foldspace.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.foldspace.launcher.settings.DockShape
import com.foldspace.launcher.spaces.SpaceDensity
import com.foldspace.launcher.ui.components.AppTile
import com.foldspace.launcher.ui.theme.FoldSpaceTheme

/**
 * The dock that sits under every page.
 *
 * `ui/dock/Dock.kt` has existed since V0.1 but only the tabletop, book and
 * charging layouts ever drew it — the paged home, the screen people actually
 * use, had no dock at all. This is that dock, shaped for a page: a single
 * translucent tray, labels off, pinned apps first.
 *
 * Drawn *above* the Samsung Wallet gesture zone, never inside it (§6.1): the
 * reserved strip arrives as bottom padding from the caller, so no dock item
 * can land on the swipe-up target.
 */
@Composable
fun HomeDock(
    apps: List<AppEntry>,
    notifications: NotificationSummary,
    density: SpaceDensity,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
    shape: DockShape = DockShape.Default,
) {
    val tokens = FoldSpaceTheme.tokens
    val rows = DockSlots.arrange(apps, shape.rows, shape.columns)

    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(tokens.surfaceAlpha())
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (rows.isEmpty()) {
            // An empty tray reads as broken. Saying why costs one line and
            // tells the user the one thing that fills it.
            Box(Modifier.height(density.dockIconSizeDp.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "長按 App 可釘選到 Dock",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textMuted,
                )
            }
            return@Box
        }

        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            rows.forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    row.forEach { entry ->
                        AppTile(
                            entry = entry,
                            onClick = { onLaunch(entry) },
                            onLongClick = { onLongPress(entry) },
                            modifier = Modifier.weight(1f),
                            iconSize = density.dockIconSizeDp.dp,
                            showLabel = false,
                            badgeCount = notifications.countFor(entry.packageName),
                        )
                    }
                    // A short last row must not stretch its icons to fill the
                    // width, or the tray stops looking like a grid.
                    repeat(shape.columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
