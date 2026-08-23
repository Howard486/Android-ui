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
import com.foldspace.launcher.ui.components.TextAction
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
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
    onRename: (String?) -> Unit,
    onPickIcon: () -> Unit,
    onClearIcon: () -> Unit,
    hasCustomIcon: Boolean,
    isLocked: Boolean,
    onToggleLock: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val tokens = FoldSpaceTheme.tokens
    var renaming by remember(item.id) { mutableStateOf(false) }
    var draft by remember(item.id) { mutableStateOf(item.label.ifBlank { item.app?.label.orEmpty() }) }

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

            if (renaming) {
                RenameField(
                    value = draft,
                    onValueChange = { draft = it },
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextAction(
                        text = "儲存",
                        onClick = {
                            onRename(draft.trim().takeIf { it.isNotBlank() })
                            renaming = false
                        },
                    )
                    // Clearing restores the app's real name rather than storing
                    // an empty one, so the app is still findable by it.
                    TextAction(
                        text = "還原原名",
                        onClick = {
                            onRename(null)
                            renaming = false
                        },
                        color = tokens.textMuted,
                    )
                    TextAction(
                        text = "取消",
                        onClick = { renaming = false },
                        color = tokens.textSecondary,
                    )
                }
                return@FoldCard
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (item.type != HomeItemType.Folder) {
                    TextAction(
                        text = if (isPinned) "取消釘選" else "釘選到 Dock",
                        onClick = onTogglePin,
                    )
                    TextAction(
                        text = "App 資訊",
                        onClick = onOpenAppInfo,
                        color = tokens.textSecondary,
                    )
                }
                TextAction(
                    text = "從桌面移除",
                    onClick = onRemove,
                    color = tokens.textMuted,
                )
            }

            if (item.type != HomeItemType.Folder && item.app != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextAction(text = "重新命名", onClick = { renaming = true })
                    TextAction(
                        text = if (hasCustomIcon) "更換圖示" else "自訂圖示",
                        onClick = onPickIcon,
                    )
                    if (hasCustomIcon) {
                        TextAction(
                            text = "還原圖示",
                            onClick = onClearIcon,
                            color = tokens.textMuted,
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextAction(
                        text = if (isLocked) "取消 App 鎖" else "加上 App 鎖",
                        onClick = onToggleLock,
                        color = if (isLocked) tokens.textMuted else tokens.accent,
                    )
                }
                // Said here rather than in a settings page nobody opens: this
                // only stops the app being launched *from FoldSpace*. Recents,
                // notifications and any other launcher walk straight past it.
                Text(
                    text = "App 鎖只擋從 FoldSpace 開啟。從最近使用、通知或其他 Launcher 仍然打得開 —— " +
                        "這是防止別人隨手拿起手機，不是安全機制。",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted,
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

/**
 * The rename field.
 *
 * A plain field rather than a dialog: the sheet is already a modal surface,
 * and stacking a second one over it to type six characters is the kind of
 * ceremony that makes renaming feel like a settings task rather than an edit.
 */
@Composable
private fun RenameField(value: String, onValueChange: (String) -> Unit) {
    val tokens = FoldSpaceTheme.tokens
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.cardRadius))
            .background(tokens.surfaceElevated)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = tokens.textPrimary),
            cursorBrush = SolidColor(tokens.accent),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
