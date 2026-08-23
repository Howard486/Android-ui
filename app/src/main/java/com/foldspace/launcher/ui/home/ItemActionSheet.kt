package com.foldspace.launcher.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.foldspace.launcher.core.shortcuts.AppShortcut
import com.foldspace.launcher.home.HomeItem
import com.foldspace.launcher.home.HomeItemType
import com.foldspace.launcher.ui.components.AppIcon
import com.foldspace.launcher.ui.components.FoldCard
import com.foldspace.launcher.ui.icons.IconShaper
import com.foldspace.launcher.ui.theme.FoldSpaceTheme

/**
 * §5.3 — what long-pressing an icon offers.
 *
 * Long-press used to go straight to App Info, which is the least useful thing
 * available and the one nobody presses for. The app's own shortcuts come
 * first — "New message", "Search", a saved conversation — because those are
 * the actions the app itself thinks are worth reaching in one gesture.
 *
 * An empty shortcut list is ambiguous: the app may publish none, or FoldSpace
 * may not be the default home app, which is what gates the query. The sheet
 * distinguishes the two rather than showing the same blank either way.
 */
@Composable
fun ItemActionSheet(
    item: HomeItem,
    shortcuts: List<AppShortcut>,
    shortcutsAvailable: Boolean,
    isPinned: Boolean,
    onLaunchShortcut: (AppShortcut) -> Unit,
    onOpenAppInfo: () -> Unit,
    onTogglePin: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val tokens = FoldSpaceTheme.tokens

    Box(
        modifier
            .fillMaxSize()
            .background(tokens.scrim.copy(alpha = 0.7f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        FoldCard(
            Modifier
                .padding(contentPadding)
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
                // Swallows the tap so pressing the sheet does not dismiss it.
                .clickable(enabled = false) {},
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                item.app?.let { AppIcon(it, 44.dp) }
                Spacer(Modifier.size(12.dp))
                Text(
                    text = item.label.ifBlank { item.app?.label.orEmpty() },
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(14.dp))

            shortcuts.forEach { shortcut ->
                ShortcutRow(shortcut = shortcut, onClick = { onLaunchShortcut(shortcut) })
            }

            if (shortcuts.isEmpty()) {
                Text(
                    text = if (shortcutsAvailable) {
                        "這個 App 沒有提供捷徑"
                    } else {
                        "FoldSpace 還不是預設主畫面，因此讀不到 App 捷徑"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textMuted,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                if (item.type != HomeItemType.Folder) {
                    Text(
                        text = if (isPinned) "取消釘選" else "釘選到 Dock",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.accent,
                        modifier = Modifier.clickable(onClick = onTogglePin).padding(4.dp),
                    )
                    Text(
                        text = "App 資訊",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textSecondary,
                        modifier = Modifier.clickable(onClick = onOpenAppInfo).padding(4.dp),
                    )
                }
                Text(
                    text = "從桌面移除",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted,
                    modifier = Modifier.clickable(onClick = onRemove).padding(4.dp),
                )
            }
        }
    }
}

@Composable
private fun ShortcutRow(shortcut: AppShortcut, onClick: () -> Unit) {
    val tokens = FoldSpaceTheme.tokens
    // The shortcut's own icon, shaped like every other icon on the screen.
    val bitmap = remember(shortcut.id, tokens.surfaceElevated) {
        IconShaper.render(
            drawable = shortcut.icon,
            sizePx = SHORTCUT_ICON_PX,
            key = "shortcut:" + shortcut.packageName + "/" + shortcut.id,
            tile = tokens.surfaceElevated,
        )
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.size(28.dp))
        } else {
            Spacer(Modifier.size(28.dp))
        }
        Text(
            text = shortcut.label,
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Rendered once and cached; the sheet is small and short-lived. */
private const val SHORTCUT_ICON_PX = 96
