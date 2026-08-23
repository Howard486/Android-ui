package com.foldspace.launcher.ui.pairs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.foldspace.launcher.core.launcher.AppEntry
import com.foldspace.launcher.core.launcher.ProfileType
import com.foldspace.launcher.pairs.AppPair
import com.foldspace.launcher.pairs.SplitLauncher
import com.foldspace.launcher.ui.components.AppIcon
import com.foldspace.launcher.ui.components.FoldCard
import com.foldspace.launcher.ui.theme.FoldSpaceTheme

/**
 * Saved two-app pairs.
 *
 * The support level is stated at the top rather than left to be discovered:
 * on a device that is not already in multi-window, "App Pair" opens two apps
 * in sequence and the second covers the first. That is a real limit of what
 * a third-party launcher may do, and hiding it would make the feature look
 * broken instead of constrained.
 */
@Composable
fun PairEditor(
    pairs: List<AppPair>,
    apps: List<AppEntry>,
    support: SplitLauncher.Support,
    onSave: (AppPair) -> Unit,
    onDelete: (String) -> Unit,
    onLaunch: (AppPair) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val tokens = FoldSpaceTheme.tokens
    var picking by remember { mutableStateOf(false) }
    var first by remember { mutableStateOf<AppEntry?>(null) }

    val byKey = remember(apps) { apps.associateBy { it.key } }
    val selectable = remember(apps) {
        apps.filter { it.profile != ProfileType.Private }.sortedBy { it.searchLabel }
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
                text = if (picking) "選第 " + (if (first == null) "1" else "2") + " 個 App" else "App 配對",
                style = MaterialTheme.typography.headlineSmall,
                color = tokens.textPrimary,
            )
            Text(
                text = if (picking) "取消" else "完成",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.accent,
                modifier = Modifier
                    .clickable {
                        if (picking) {
                            picking = false
                            first = null
                        } else {
                            onDismiss()
                        }
                    }
                    .padding(6.dp),
            )
        }
        Spacer(Modifier.height(12.dp))

        if (picking) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(selectable, key = { it.key }) { entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                val current = first
                                if (current == null) {
                                    first = entry
                                } else {
                                    onSave(
                                        AppPair(
                                            id = "pair-" + System.currentTimeMillis(),
                                            firstKey = current.key,
                                            secondKey = entry.key,
                                            label = current.label + " + " + entry.label,
                                        ),
                                    )
                                    first = null
                                    picking = false
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
                        )
                    }
                }
            }
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                FoldCard(Modifier.fillMaxWidth()) {
                    Text(
                        text = when (support) {
                            SplitLauncher.Support.Adjacent -> "目前可以左右並排開啟。"
                            SplitLauncher.Support.Sequential ->
                                "目前不是分割畫面，兩個 App 會依序開啟，第二個會蓋住第一個。" +
                                    "先進入分割畫面再開配對，就會並排。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary,
                    )
                }
            }

            items(pairs, key = { it.id }) { pair ->
                val missing = pair.firstKey !in byKey || pair.secondKey !in byKey
                FoldCard(Modifier.fillMaxWidth()) {
                    Text(
                        text = pair.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (missing) tokens.textMuted else tokens.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (missing) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "其中一個 App 已移除",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textMuted,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        if (!missing) {
                            Text(
                                text = "開啟",
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.accent,
                                modifier = Modifier.clickable { onLaunch(pair) }.padding(4.dp),
                            )
                        }
                        Text(
                            text = "刪除",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textMuted,
                            modifier = Modifier.clickable { onDelete(pair.id) }.padding(4.dp),
                        )
                    }
                }
            }

            item {
                Text(
                    text = "＋ 新增配對",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.accent,
                    modifier = Modifier
                        .clickable {
                            first = null
                            picking = true
                        }
                        .padding(vertical = 10.dp),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
