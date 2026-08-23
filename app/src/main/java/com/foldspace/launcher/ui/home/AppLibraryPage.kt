package com.foldspace.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.foldspace.launcher.core.launcher.AppEntry
import com.foldspace.launcher.core.launcher.ProfileType
import com.foldspace.launcher.home.AppCategory
import com.foldspace.launcher.notifications.NotificationSummary
import com.foldspace.launcher.spaces.SpaceDensity
import com.foldspace.launcher.ui.components.AppTile
import com.foldspace.launcher.ui.theme.FoldSpaceTheme

/**
 * The App Library: every installed app, grouped by category.
 *
 * The important word is *view*. One-tap organise moves the user's real icons
 * into folders, which is why it needs an undo and why it was alarming the
 * first time it ran. Reading the same categorisation onto its own page costs
 * the arrangement nothing — the desktop stays exactly as it was left, and
 * this is where you go when you cannot remember which page something is on.
 *
 * Private Space apps are excluded (§14.2): they have their own container and
 * do not appear in a general listing.
 */
@Composable
fun AppLibraryPage(
    apps: List<AppEntry>,
    categories: Map<String, AppCategory>,
    notifications: NotificationSummary,
    density: SpaceDensity,
    columns: Int,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val tokens = FoldSpaceTheme.tokens

    val grouped = remember(apps, categories) {
        apps
            .filter { it.profile != ProfileType.Private }
            .groupBy { categories[it.packageName] ?: AppCategory.Other }
            .toList()
            .sortedBy { (category, _) -> category.ordinal }
            .map { (category, members) -> category to members.sortedBy { it.searchLabel } }
    }

    Column(
        modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 12.dp),
    ) {
        Text(
            text = "App 資料庫",
            style = MaterialTheme.typography.titleMedium,
            color = tokens.textPrimary,
            modifier = Modifier.padding(start = 6.dp, top = 8.dp, bottom = 10.dp),
        )

        if (grouped.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "還沒有可以顯示的 App",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.textMuted,
                )
            }
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns.coerceAtLeast(2)),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            grouped.forEach { (category, members) ->
                item(
                    key = "header-${category.key}",
                    span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) },
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(tokens.surfaceElevated.copy(alpha = 0.35f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "${category.displayName} · ${members.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.textSecondary,
                        )
                    }
                }

                items(members, key = { it.key }) { entry ->
                    AppTile(
                        entry = entry,
                        onClick = { onLaunch(entry) },
                        onLongClick = { onLongPress(entry) },
                        iconSize = density.iconSizeDp.dp,
                        badgeCount = notifications.countFor(entry.packageName),
                    )
                }
            }

            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                Box(Modifier.height(12.dp))
            }
        }
    }
}
