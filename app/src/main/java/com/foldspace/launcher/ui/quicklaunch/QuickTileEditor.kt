package com.foldspace.launcher.ui.quicklaunch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.foldspace.launcher.core.launcher.AppEntry
import com.foldspace.launcher.core.shortcuts.AppShortcut
import com.foldspace.launcher.home.QuickLaunchGrid
import com.foldspace.launcher.home.QuickTile
import com.foldspace.launcher.home.QuickTileKind
import com.foldspace.launcher.ui.components.AppIcon
import com.foldspace.launcher.ui.components.TextAction
import com.foldspace.launcher.ui.theme.FoldSpaceTheme

/** Which list the editor is showing. */
private sealed interface EditorStep {
    data object Tiles : EditorStep
    data object PickApp : EditorStep
    data object PickShortcutApp : EditorStep
    data class PickShortcut(val app: AppEntry) : EditorStep
    data object AddUrl : EditorStep
}

/**
 * Fills a quick-launch block.
 *
 * The block is one grid item, so this is the only place its contents can be
 * edited — there is nothing on the home screen to long-press per tile. Adding,
 * removing and reordering all happen here and are written in one go, which
 * also means backing out of the editor changes nothing.
 */
@Composable
fun QuickTileEditor(
    tiles: List<QuickTile>,
    spanX: Int,
    spanY: Int,
    apps: List<AppEntry>,
    shortcutsFor: (AppEntry) -> List<AppShortcut>,
    shortcutsAvailable: Boolean,
    onSave: (List<QuickTile>) -> Unit,
    onRemoveBlock: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val tokens = FoldSpaceTheme.tokens
    val working = remember(tiles) { mutableStateListOf<QuickTile>().apply { addAll(tiles) } }
    var step by remember { mutableStateOf<EditorStep>(EditorStep.Tiles) }

    val capacity = QuickLaunchGrid.capacity(spanX, spanY)
    val overflow = (working.size - capacity).coerceAtLeast(0)

    fun add(tile: QuickTile) {
        working.add(tile)
        step = EditorStep.Tiles
    }

    Column(
        modifier
            .fillMaxSize()
            .background(tokens.overlayScrim())
            .padding(contentPadding)
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when (step) {
                    EditorStep.Tiles -> "快速啟動"
                    EditorStep.PickApp -> "選一個 App"
                    EditorStep.PickShortcutApp -> "選捷徑來自哪個 App"
                    is EditorStep.PickShortcut -> "選一個捷徑"
                    EditorStep.AddUrl -> "加入網址"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = tokens.textPrimary,
            )
            if (step == EditorStep.Tiles) {
                TextAction(
                    text = "完成",
                    onClick = {
                        onSave(working.toList())
                        onDismiss()
                    },
                )
            } else {
                TextAction(text = "返回", onClick = { step = EditorStep.Tiles })
            }
        }

        Spacer(Modifier.height(8.dp))

        when (val current = step) {
            EditorStep.Tiles -> TileList(
                tiles = working,
                capacity = capacity,
                overflow = overflow,
                appFor = { key -> apps.firstOrNull { it.key == key || it.packageName == key } },
                shortcutsAvailable = shortcutsAvailable,
                onMove = { from, to ->
                    if (to in working.indices) working.add(to, working.removeAt(from))
                },
                onRemove = { index -> working.removeAt(index) },
                onAddApp = { step = EditorStep.PickApp },
                onAddShortcut = { step = EditorStep.PickShortcutApp },
                onAddUrl = { step = EditorStep.AddUrl },
                onRemoveBlock = {
                    onRemoveBlock()
                    onDismiss()
                },
            )

            EditorStep.PickApp -> AppList(apps) { entry ->
                add(QuickTile(QuickTileKind.App, entry.key, entry.label))
            }

            EditorStep.PickShortcutApp -> AppList(apps) { entry ->
                step = EditorStep.PickShortcut(entry)
            }

            is EditorStep.PickShortcut -> {
                val found = remember(current.app.key) { shortcutsFor(current.app) }
                if (found.isEmpty()) {
                    Hint(
                        if (shortcutsAvailable) {
                            "${current.app.label} 沒有提供捷徑。"
                        } else {
                            "要讀取 App 捷徑，FoldSpace 必須是預設主畫面。"
                        },
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        items(found, key = { it.id }) { shortcut ->
                            RowButton(
                                label = shortcut.label,
                                onClick = {
                                    add(
                                        QuickTile(
                                            kind = QuickTileKind.Shortcut,
                                            target = "${shortcut.packageName}/${shortcut.id}",
                                            label = shortcut.label,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }

            EditorStep.AddUrl -> UrlForm { url, label ->
                add(QuickTile(QuickTileKind.Url, url, label))
            }
        }
    }
}

@Composable
private fun ColumnScope.TileList(
    tiles: List<QuickTile>,
    capacity: Int,
    overflow: Int,
    appFor: (String) -> AppEntry?,
    shortcutsAvailable: Boolean,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (Int) -> Unit,
    onAddApp: () -> Unit,
    onAddShortcut: () -> Unit,
    onAddUrl: () -> Unit,
    onRemoveBlock: () -> Unit,
) {
    val tokens = FoldSpaceTheme.tokens

    Text(
        text = "這個區塊放得下 $capacity 個。放大區塊就放得下更多。",
        style = MaterialTheme.typography.labelSmall,
        color = tokens.textMuted,
    )
    if (overflow > 0) {
        Text(
            text = "後面 $overflow 個目前不會顯示。",
            style = MaterialTheme.typography.labelSmall,
            color = tokens.accent,
        )
    }

    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AddButton("App", onAddApp, Modifier.weight(1f))
        AddButton("捷徑", onAddShortcut, Modifier.weight(1f))
        AddButton("網址", onAddUrl, Modifier.weight(1f))
    }
    if (!shortcutsAvailable) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = "捷徑需要 FoldSpace 是預設主畫面才讀得到。",
            style = MaterialTheme.typography.labelSmall,
            color = tokens.textMuted,
        )
    }
    Spacer(Modifier.height(10.dp))

    if (tiles.isEmpty()) {
        Hint("還沒有任何項目。")
    } else {
        LazyColumn(
            Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Indexed rather than keyed by content: two tiles may legitimately
            // point at the same target, and a duplicate key crashes the list.
            items(tiles.size) { index ->
                val tile = tiles[index]
                val app = tile.packageName?.let(appFor)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(tokens.surfaceAlpha())
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (app != null && tile.kind != QuickTileKind.Url) {
                        AppIcon(app, 28.dp)
                    } else {
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(tokens.surfaceElevated),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (tile.kind == QuickTileKind.Url) "🌐" else "?",
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.textSecondary,
                            )
                        }
                    }

                    Column(Modifier.weight(1f)) {
                        Text(
                            text = tile.displayLabel(app?.label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (index < capacity) tokens.textPrimary else tokens.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = when (tile.kind) {
                                QuickTileKind.App -> "App"
                                QuickTileKind.Shortcut -> "捷徑"
                                QuickTileKind.Url -> tile.target
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    SmallButton("↑") { onMove(index, index - 1) }
                    SmallButton("↓") { onMove(index, index + 1) }
                    SmallButton("✕") { onRemove(index) }
                }
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    TextAction(text = "刪除整個區塊", onClick = onRemoveBlock)
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun AppList(apps: List<AppEntry>, onPick: (AppEntry) -> Unit) {
    var query by remember { mutableStateOf("") }
    val trimmed = query.trim().lowercase()
    val shown = remember(apps, trimmed) {
        val sorted = apps.sortedBy { it.searchLabel }
        if (trimmed.isEmpty()) sorted else sorted.filter { it.searchLabel.contains(trimmed) }
    }

    EditorField(value = query, onValueChange = { query = it }, placeholder = "搜尋 App")
    Spacer(Modifier.height(8.dp))

    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(shown, key = { it.key }) { entry ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onPick(entry) }
                    .padding(vertical = 8.dp, horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppIcon(entry, 36.dp)
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = FoldSpaceTheme.tokens.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun UrlForm(onAdd: (url: String, label: String) -> Unit) {
    val tokens = FoldSpaceTheme.tokens
    var url by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }

    Text(
        text = "一格可以直接開一個網頁，不必先開瀏覽器再找書籤。",
        style = MaterialTheme.typography.labelSmall,
        color = tokens.textMuted,
    )
    Spacer(Modifier.height(10.dp))
    EditorField(
        value = url,
        onValueChange = { url = it },
        placeholder = "https://",
        keyboardType = KeyboardType.Uri,
    )
    Spacer(Modifier.height(8.dp))
    EditorField(value = label, onValueChange = { label = it }, placeholder = "名稱（可留空）")
    Spacer(Modifier.height(12.dp))

    val normalised = url.trim().let {
        // A bare host typed without a scheme is the common case, and an
        // ACTION_VIEW with no scheme resolves to nothing at all.
        when {
            it.isEmpty() -> ""
            it.startsWith("http://") || it.startsWith("https://") -> it
            else -> "https://$it"
        }
    }

    TextAction(
        text = "加入",
        onClick = { if (normalised.isNotEmpty()) onAdd(normalised, label.trim()) },
    )
}

@Composable
private fun EditorField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val tokens = FoldSpaceTheme.tokens
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.cardRadius))
            .background(tokens.surfaceElevated)
            .border(1.dp, tokens.outline, RoundedCornerShape(tokens.cardRadius))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = tokens.textPrimary),
            cursorBrush = SolidColor(tokens.accent),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textMuted,
                    )
                }
                inner()
            },
        )
    }
}

@Composable
private fun AddButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = FoldSpaceTheme.tokens
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(tokens.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "＋ $label",
            style = MaterialTheme.typography.labelLarge,
            color = tokens.textPrimary,
        )
    }
}

@Composable
private fun SmallButton(glyph: String, onClick: () -> Unit) {
    val tokens = FoldSpaceTheme.tokens
    Box(
        Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(tokens.surfaceElevated)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.labelMedium,
            color = tokens.textPrimary,
        )
    }
}

@Composable
private fun RowButton(label: String, onClick: () -> Unit) {
    val tokens = FoldSpaceTheme.tokens
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tokens.surfaceAlpha())
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = FoldSpaceTheme.tokens.textMuted,
    )
}
