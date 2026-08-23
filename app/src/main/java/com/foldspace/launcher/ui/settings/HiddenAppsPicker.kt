package com.foldspace.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.foldspace.launcher.core.launcher.AppEntry
import com.foldspace.launcher.core.search.T9
import com.foldspace.launcher.ui.components.AppIcon
import com.foldspace.launcher.ui.components.TextAction
import com.foldspace.launcher.ui.theme.FoldSpaceTheme

/**
 * Chooses which apps do not appear.
 *
 * FoldSpace has no app drawer, so the home pages follow the installed list —
 * every app gets a cell whether it is wanted or not. That is what makes this
 * necessary: until now there was no way at all to say "I never open this".
 *
 * It is stated on the screen, not left to be discovered, that hiding is not
 * hiding *from* anyone. The app stays installed, stays running, and is still
 * one tap away in recents, in a notification, in the Play Store and in any
 * other launcher. What goes away is the icon.
 */
@Composable
fun HiddenAppsPicker(
    apps: List<AppEntry>,
    hidden: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val tokens = FoldSpaceTheme.tokens
    var query by remember { mutableStateOf("") }

    val sorted = remember(apps) { apps.sortedBy { it.searchLabel } }
    val trimmed = query.trim().lowercase()

    val shown = remember(sorted, trimmed) {
        if (trimmed.isEmpty()) {
            sorted
        } else {
            // The same rule the search overlay uses, so a keypad query behaves
            // identically here rather than being a second kind of search.
            val numeric = T9.isNumericQuery(trimmed)
            sorted.filter { entry ->
                if (numeric) {
                    T9.score(entry.searchLabel, trimmed) != T9.NO_MATCH ||
                        entry.searchLabel.contains(trimmed)
                } else {
                    entry.searchLabel.contains(trimmed) || entry.packageName.contains(trimmed)
                }
            }
        }
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
                text = "隱藏 App",
                style = MaterialTheme.typography.headlineSmall,
                color = tokens.textPrimary,
            )
            TextAction(text = "完成", onClick = onDismiss)
        }

        Text(
            text = "隱藏只是把圖示拿掉。App 仍然安裝著，也仍然可以從最近使用、通知或其他 " +
                "Launcher 打開 —— 這不是鎖，也不是隱私功能。",
            style = MaterialTheme.typography.labelSmall,
            color = tokens.textMuted,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "取消隱藏時，App 會回到第一個空格，不是原本的位置 —— 位置隨著格子一起讓出去了。",
            style = MaterialTheme.typography.labelSmall,
            color = tokens.textMuted,
        )

        Spacer(Modifier.height(12.dp))
        SettingsSearchField(query = query, onQueryChange = { query = it })
        Spacer(Modifier.height(4.dp))
        Text(
            text = "已隱藏 ${hidden.size} 個",
            style = MaterialTheme.typography.labelSmall,
            color = tokens.textMuted,
        )
        Spacer(Modifier.height(8.dp))

        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "找不到「$query」",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.textMuted,
                )
            }
            return@Column
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(shown, key = { it.key }) { entry ->
                val isHidden = entry.key in hidden
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onToggle(entry.key, !isHidden) }
                        .padding(vertical = 8.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppIcon(entry, 40.dp)
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isHidden) tokens.textMuted else tokens.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // A filled mark rather than a checkbox: the question here
                    // is "is this one hidden", and the answer should be legible
                    // at a glance down a list of three hundred rows.
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (isHidden) tokens.accent else tokens.surfaceElevated),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isHidden) {
                            Text(
                                text = "✕",
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.onAccent,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A plain search field for the settings overlays.
 *
 * Deliberately not shared with the drawer's: that one carries suggestions,
 * profile tabs and an IME action that launches the top result, none of which
 * belong on a list you are ticking.
 */
@Composable
internal fun SettingsSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "搜尋 App",
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
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = tokens.textPrimary),
            cursorBrush = SolidColor(tokens.accent),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (query.isEmpty()) {
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
