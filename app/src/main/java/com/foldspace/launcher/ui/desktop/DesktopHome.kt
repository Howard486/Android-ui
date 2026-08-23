package com.foldspace.launcher.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.foldspace.launcher.core.launcher.AppEntry
import com.foldspace.launcher.desktop.FreeformState
import com.foldspace.launcher.notifications.NotificationSummary
import com.foldspace.launcher.ui.components.AppTile
import com.foldspace.launcher.ui.components.LabelOnWallpaper
import com.foldspace.launcher.ui.components.TextAction
import com.foldspace.launcher.ui.theme.FoldSpaceTheme

/**
 * The unfolded desktop: the user's own pages, with a taskbar under them.
 *
 * What this borrows from a PC is the *shell* — a taskbar, a start button, a
 * clock in the corner, and apps that open in windows rather than filling the
 * screen. What it cannot borrow is window management. A launcher may say where
 * a window opens ([com.foldspace.launcher.desktop.DesktopWindows]); it may not
 * move one, resize one, decorate one, or switch between them. Those belong to
 * the system, and no public API lends them out.
 *
 * The pages are the same pages. Desktop mode is a different shell around one
 * arrangement, not a second arrangement to keep in step — the same decision
 * the three contexts already made.
 */
@Composable
fun DesktopHome(
    taskbarApps: List<AppEntry>,
    notifications: NotificationSummary,
    freeform: FreeformState,
    clock: String,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    onOpenStart: () -> Unit,
    onLeaveDesktop: () -> Unit,
    onOpenFreeformSettings: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    desktopContent: @Composable () -> Unit,
) {
    Column(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(1f)) { desktopContent() }

        FreeformNotice(
            state = freeform,
            onOpenSettings = onOpenFreeformSettings,
            onLeaveDesktop = onLeaveDesktop,
        )

        Taskbar(
            apps = taskbarApps,
            notifications = notifications,
            clock = clock,
            onLaunch = onLaunch,
            onLongPress = onLongPress,
            onOpenStart = onOpenStart,
            onLeaveDesktop = onLeaveDesktop,
            contentPadding = contentPadding,
        )
    }
}

/**
 * Says out loud when windows will not actually open as windows.
 *
 * Without freeform enabled `setLaunchBounds` is ignored and every app opens
 * full screen with no error of any kind, so silence here would read as the
 * feature being broken rather than as a switch being off.
 */
@Composable
private fun FreeformNotice(
    state: FreeformState,
    onOpenSettings: () -> Unit,
    onLeaveDesktop: () -> Unit,
) {
    val tokens = FoldSpaceTheme.tokens
    if (state == FreeformState.Available) return

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tokens.surfaceElevated)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = when (state) {
                FreeformState.NeedsDeveloperOption ->
                    "目前 App 會全螢幕開啟。要開成視窗，請到「開發人員選項 → 啟用自由形式視窗」" +
                        "打開並重新開機 —— 這個開關只有裝置本身能設定。"
                FreeformState.DeskModeActive ->
                    "Samsung DeX 正在執行，而它有真正的視窗管理（移動、縮放、標題列），" +
                        "FoldSpace 沒有。這個工作交給 DeX 比較好。"
                FreeformState.Available -> ""
            },
            style = MaterialTheme.typography.labelSmall,
            color = tokens.textSecondary,
            modifier = Modifier.weight(1f),
        )
        when (state) {
            FreeformState.NeedsDeveloperOption ->
                TextAction(text = "開啟設定", onClick = onOpenSettings)
            FreeformState.DeskModeActive ->
                TextAction(text = "離開桌面模式", onClick = onLeaveDesktop)
            FreeformState.Available -> Unit
        }
    }
}

@Composable
private fun Taskbar(
    apps: List<AppEntry>,
    notifications: NotificationSummary,
    clock: String,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    onOpenStart: () -> Unit,
    onLeaveDesktop: () -> Unit,
    contentPadding: PaddingValues,
) {
    val tokens = FoldSpaceTheme.tokens

    Row(
        Modifier
            .fillMaxWidth()
            .background(tokens.surfaceAlpha())
            .padding(contentPadding)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextAction(text = "⊞  開始", onClick = onOpenStart)
        Spacer(Modifier.width(6.dp))

        // Scrollable rather than capped: a taskbar's whole point is that it
        // holds more than a dock, and dropping the overflow silently would
        // make pinning an app look like it failed.
        LazyRow(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(apps, key = { it.key }) { entry ->
                AppTile(
                    entry = entry,
                    onClick = { onLaunch(entry) },
                    onLongClick = { onLongPress(entry) },
                    iconSize = TASKBAR_ICON,
                    showLabel = false,
                    badgeCount = notifications.countFor(entry.packageName),
                )
            }
        }

        Text(
            text = clock,
            style = MaterialTheme.typography.labelSmall.merge(LabelOnWallpaper),
            color = tokens.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        TextAction(text = "手機版面", onClick = onLeaveDesktop)
    }
}

/** Small enough that a row of them reads as a taskbar rather than as a dock. */
private val TASKBAR_ICON = 34.dp

/** What the taskbar reserves, for the window cascade to stay clear of. */
val TASKBAR_HEIGHT = 52.dp
