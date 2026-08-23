package com.foldspace.launcher.ui.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.foldspace.launcher.core.launcher.AppEntry
import com.foldspace.launcher.core.launcher.ProfileType
import com.foldspace.launcher.notifications.NotificationSummary
import com.foldspace.launcher.spaces.SpaceDensity
import com.foldspace.launcher.ui.components.AppTile
import com.foldspace.launcher.ui.theme.FoldSpaceTheme

/** §5.3 — the drawer's category tabs. */
private enum class DrawerTab(val label: String, val profile: ProfileType?) {
    All("全部", null),
    Personal("個人", ProfileType.Personal),
    Work("工作", ProfileType.Work),
    Private("私人", ProfileType.Private),
}

/**
 * §5.5 Universal Search.
 *
 * This is deliberately *not* an app drawer. Apps live on the home pages now,
 * so listing them all again here would be a second, competing home for every
 * app. What it does instead is the thing pages are bad at: finding one app
 * among several hundred without scrolling.
 *
 * Search matches on a pre-lowercased label (see [AppEntry.searchLabel]) and on
 * the package name, so "設定" and "com.android.settings" both find Settings.
 */
@Composable
fun AppSearchOverlay(
    apps: List<AppEntry>,
    notifications: NotificationSummary,
    suggested: List<AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    density: SpaceDensity = SpaceDensity.Standard,
) {
    val tokens = FoldSpaceTheme.tokens
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(DrawerTab.All) }

    // Only offer profile tabs that actually exist on this device — an empty
    // "Work" tab on a personal phone is noise.
    val availableTabs = remember(apps) {
        val present = apps.mapTo(mutableSetOf()) { it.profile }
        DrawerTab.entries.filter { it.profile == null || it.profile in present }
    }

    val trimmedQuery = query.trim().lowercase()

    val results = remember(apps, trimmedQuery, tab) {
        if (trimmedQuery.isEmpty()) {
            emptyList()
        } else {
            apps.asSequence()
                .filter { tab.profile == null || it.profile == tab.profile }
                .filter {
                    it.searchLabel.contains(trimmedQuery) ||
                        it.packageName.contains(trimmedQuery)
                }
                // Prefix matches first: typing "ch" should reach Chrome before
                // it reaches anything that merely contains "ch".
                .sortedByDescending { it.searchLabel.startsWith(trimmedQuery) }
                .toList()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.overlayScrim())
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        SearchField(
            query = query,
            onQueryChange = { query = it },
            onSubmit = { results.firstOrNull()?.let(onLaunch) },
        )

        Spacer(Modifier.height(12.dp))

        if (availableTabs.size > 1 && trimmedQuery.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                availableTabs.forEach { entry ->
                    TabChip(
                        label = entry.label,
                        selected = tab == entry,
                        onClick = { tab = entry },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // §5.3 "Suggested apps" — hidden while searching, where it would just
        // push the results the user is aiming at further down.
        if (trimmedQuery.isEmpty() && suggested.isNotEmpty()) {
            SectionHeader("建議")
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                suggested.take(SUGGESTED_COUNT).forEach { entry ->
                    AppTile(
                        entry = entry,
                        onClick = { onLaunch(entry) },
                        onLongClick = { onLongPress(entry) },
                        modifier = Modifier.weight(1f),
                        iconSize = density.iconSizeDp.dp,
                        badgeCount = notifications.countFor(entry.packageName),
                    )
                }
                // Keep the row from stretching a single suggestion full-width.
                repeat(SUGGESTED_COUNT - suggested.size.coerceAtMost(SUGGESTED_COUNT)) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (trimmedQuery.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "輸入名稱來尋找 App",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textMuted,
                )
            }
            return@Column
        }

        if (results.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "找不到「$query」",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.textMuted,
                )
            }
            return@Column
        }

        SectionHeader("搜尋結果 · ${results.size}")

        LazyVerticalGrid(
            columns = GridCells.Adaptive(density.drawerCellDp.dp),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(results, key = { it.key }) { entry ->
                AppTile(
                    entry = entry,
                    onClick = { onLaunch(entry) },
                    onLongClick = { onLongPress(entry) },
                    iconSize = density.iconSizeDp.dp,
                    badgeCount = notifications.countFor(entry.packageName),
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val tokens = FoldSpaceTheme.tokens
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(tokens.cardRadius))
            .background(tokens.surfaceElevated)
            .border(1.dp, tokens.outline, RoundedCornerShape(tokens.cardRadius))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        if (query.isEmpty()) {
            Text(
                text = "搜尋 App、設定、指令",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textMuted,
            )
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = tokens.textPrimary),
            cursorBrush = SolidColor(tokens.accent),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = ImeAction.Go,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onGo = { onSubmit() },
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val tokens = FoldSpaceTheme.tokens
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) tokens.accent.copy(alpha = 0.18f) else tokens.surfaceAlpha())
            .border(
                1.dp,
                if (selected) tokens.accent.copy(alpha = 0.45f) else tokens.outline,
                RoundedCornerShape(50),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) tokens.accent else tokens.textSecondary,
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = FoldSpaceTheme.tokens.textMuted,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

private const val SUGGESTED_COUNT = 5
