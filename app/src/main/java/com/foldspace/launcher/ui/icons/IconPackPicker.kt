package com.foldspace.launcher.ui.icons

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.foldspace.launcher.ui.components.FoldCard
import com.foldspace.launcher.ui.theme.FoldSpaceTheme

/**
 * Chooses an installed icon pack.
 *
 * Packs are found by the intent filter every pack has declared for years;
 * there is no official API. A device with none installed gets told that,
 * along with where packs come from — an empty list with no explanation reads
 * as a broken feature.
 */
@Composable
fun IconPackPicker(
    packs: List<IconPackInfo>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val tokens = FoldSpaceTheme.tokens

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
            Text(
                text = "圖示包",
                style = MaterialTheme.typography.headlineSmall,
                color = tokens.textPrimary,
            )
            Text(
                text = "完成",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.accent,
                modifier = Modifier.clickable(onClick = onDismiss).padding(6.dp),
            )
        }
        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                PackRow(
                    label = "系統圖示",
                    detail = "使用每個 App 自己的圖示",
                    isSelected = selected == null,
                    onClick = { onSelect(null) },
                )
            }

            items(packs, key = { it.packageName }) { pack ->
                PackRow(
                    label = pack.label,
                    detail = pack.packageName,
                    isSelected = pack.packageName == selected,
                    onClick = { onSelect(pack.packageName) },
                )
            }

            if (packs.isEmpty()) {
                item {
                    Text(
                        text = "這台裝置沒有安裝圖示包。從 Play 商店安裝任一款支援 " +
                            "Nova / ADW 格式的圖示包後就會出現在這裡。",
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textMuted,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun PackRow(
    label: String,
    detail: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = FoldSpaceTheme.tokens
    FoldCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = tokens.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isSelected) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "✓",
                        style = MaterialTheme.typography.titleMedium,
                        color = tokens.accent,
                    )
                }
            }
        }
    }
}
