package com.foldspace.launcher.ui.home

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.foldspace.launcher.core.launcher.AppEntry
import com.foldspace.launcher.core.launcher.ProfileType
import com.foldspace.launcher.ui.components.AppIcon
import com.foldspace.launcher.ui.theme.FoldSpaceTheme

/**
 * Picks the four apps 簡易 shows.
 *
 * `setSimpleApps` has existed since the three-mode change and nothing ever
 * called it, so 簡易 has been showing the first four apps alphabetically with
 * no way to change them — which contradicts the whole premise of a mode built
 * around "only the apps you actually need".
 *
 * Order is the order you tap, not the order of the list: on a screen with
 * four slots, which corner a thing sits in is most of the layout.
 */
@Composable
fun SimpleAppPicker(
    apps: List<AppEntry>,
    current: List<AppEntry>,
    slots: Int,
    onConfirm: (List<AppEntry>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val tokens = FoldSpaceTheme.tokens
    var chosen by remember(current) { mutableStateOf(current.map { it.key }) }

    val selectable = remember(apps) {
        apps.filter { it.profile != ProfileType.Private }.sortedBy { it.searchLabel }
    }
    val byKey = remember(apps) { apps.associateBy { it.key } }

    Column(
        modifier
            .fillMaxSize()
            .background(tokens.scrim.copy(alpha = 0.96f))
            .padding(contentPadding)
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "簡易模式的 App",
                    style = MaterialTheme.typography.headlineSmall,
                    color = tokens.textPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "已選 ${chosen.size} / $slots，依點選順序排列",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted,
                )
            }
            Text(
                text = "取消",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted,
                modifier = Modifier.clickable(onClick = onDismiss).padding(6.dp),
            )
            Spacer(Modifier.size(12.dp))
            Text(
                text = "儲存",
                style = MaterialTheme.typography.labelSmall,
                color = if (chosen.isEmpty()) tokens.textMuted else tokens.accent,
                modifier = Modifier
                    .clickable(enabled = chosen.isNotEmpty()) {
                        onConfirm(chosen.mapNotNull(byKey::get))
                    }
                    .padding(6.dp),
            )
        }
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(selectable, key = { it.key }) { entry ->
                val position = chosen.indexOf(entry.key)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            chosen = when {
                                entry.key in chosen -> chosen - entry.key
                                chosen.size >= slots -> chosen.drop(1) + entry.key
                                else -> chosen + entry.key
                            }
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppIcon(entry, 40.dp)
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (position >= 0) {
                        // The number, not a tick: position is the whole point
                        // on a four-slot screen.
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(tokens.accent),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = (position + 1).toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.scrim.copy(alpha = 1f),
                            )
                        }
                    }
                }
            }
        }
    }
}
