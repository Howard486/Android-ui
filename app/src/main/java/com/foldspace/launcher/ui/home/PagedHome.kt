package com.foldspace.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.foldspace.launcher.home.HomeItem
import com.foldspace.launcher.home.HomeItemType
import com.foldspace.launcher.home.HomeLayout
import com.foldspace.launcher.notifications.NotificationSummary
import com.foldspace.launcher.spaces.SpaceDensity
import com.foldspace.launcher.ui.components.AppIcon
import com.foldspace.launcher.ui.theme.FoldSpaceTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The editable home surface: pages of a fixed grid, apps placed by the user.
 *
 * This replaces V0.1's computed home. There is no app drawer behind it — every
 * installed app lives on a page — so the swipe-up search is the only other way
 * to reach an app, and it matters more than it did before.
 */
@Composable
fun PagedHome(
    layout: HomeLayout,
    notifications: NotificationSummary,
    density: SpaceDensity,
    onLaunch: (HomeItem) -> Unit,
    onLongPress: (HomeItem) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val tokens = FoldSpaceTheme.tokens
    val pageCount = layout.pages.size.coerceAtLeast(1)
    val pagerState = rememberPagerState(pageCount = { pageCount })

    Column(
        modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        StatusStrip(
            notifications = notifications,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            pageSpacing = 8.dp,
        ) { pageIndex ->
            val page = layout.pages.getOrNull(pageIndex)
            if (page == null || page.items.isEmpty()) {
                EmptyPage()
            } else {
                CellGrid(
                    layout = layout,
                    items = page.items,
                    notifications = notifications,
                    density = density,
                    onLaunch = onLaunch,
                    onLongPress = onLongPress,
                )
            }
        }

        if (pageCount > 1) {
            PageDots(
                count = pageCount,
                selected = pagerState.currentPage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
            )
        } else {
            Spacer(Modifier.height(20.dp))
        }
    }
}

/**
 * Fixed grid: every cell is the same size and items sit where they were put.
 *
 * Built as nested rows rather than a lazy grid because the page is bounded and
 * fully visible — laziness would buy nothing and would break absolute cell
 * addressing, which is the whole point of a placed layout.
 */
@Composable
private fun CellGrid(
    layout: HomeLayout,
    items: List<HomeItem>,
    notifications: NotificationSummary,
    density: SpaceDensity,
    onLaunch: (HomeItem) -> Unit,
    onLongPress: (HomeItem) -> Unit,
) {
    val byCell = items.associateBy { it.cellX to it.cellY }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(layout.grid.rows) { y ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                repeat(layout.grid.columns) { x ->
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        byCell[x to y]?.let { item ->
                            HomeCell(
                                item = item,
                                badgeCount = item.app
                                    ?.let { notifications.countFor(it.packageName) }
                                    ?: 0,
                                density = density,
                                onClick = { onLaunch(item) },
                                onLongClick = { onLongPress(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeCell(
    item: HomeItem,
    badgeCount: Int,
    density: SpaceDensity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val tokens = FoldSpaceTheme.tokens

    when (item.type) {
        HomeItemType.App -> {
            val app = item.app
            if (app == null) {
                // §14.1 — a paused work profile or an app on unmounted storage
                // keeps its cell; deleting it would lose the arrangement over
                // something temporary.
                UnavailableCell()
            } else {
                com.foldspace.launcher.ui.components.AppTile(
                    entry = app,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    iconSize = density.iconSizeDp.dp,
                    badgeCount = badgeCount,
                )
            }
        }

        HomeItemType.Folder -> FolderCell(item, density, onClick, onLongClick)

        // Widgets arrive in phase 4; a placed-but-unrendered widget would be
        // an invisible hole, so it is drawn as an explicit placeholder.
        HomeItemType.Widget -> UnavailableCell()
    }
}

@Composable
private fun FolderCell(
    item: HomeItem,
    density: SpaceDensity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val tokens = FoldSpaceTheme.tokens
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(density.iconSizeDp.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(tokens.iconCornerRadius))
                .background(tokens.surfaceElevated)
                .padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            // A 2×2 peek of the first four members, which is what makes a
            // folder recognisable at icon size.
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                item.folderContents.chunked(2).take(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        pair.forEach { member ->
                            member.app?.let { AppIcon(it, (density.iconSizeDp / 3).dp) }
                        }
                    }
                }
            }
        }
        Text(
            text = item.folderTitle.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = tokens.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun UnavailableCell() {
    val tokens = FoldSpaceTheme.tokens
    Box(
        Modifier
            .size(40.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(tokens.surfaceElevated.copy(alpha = 0.5f)),
    )
}

@Composable
private fun EmptyPage() {
    val tokens = FoldSpaceTheme.tokens
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "這一頁還是空的",
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.textMuted,
        )
    }
}

/**
 * A slim clock and unread count above the grid.
 *
 * Deliberately not a card: with a fixed 5×7 grid, anything that occupies cells
 * costs the user app slots, and the clock is the one thing they should not
 * have to spend a slot on.
 */
@Composable
private fun StatusStrip(
    notifications: NotificationSummary,
    modifier: Modifier = Modifier,
) {
    val tokens = FoldSpaceTheme.tokens
    val now by produceState(initialValue = Date()) {
        while (true) {
            value = Date()
            val calendar = Calendar.getInstance()
            delay(
                60_000L - (calendar.get(Calendar.SECOND) * 1000L +
                    calendar.get(Calendar.MILLISECOND)),
            )
        }
    }

    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = statusFormat.format(now),
            style = MaterialTheme.typography.titleMedium,
            color = tokens.textPrimary,
        )
        val unread = notifications.items.size
        if (unread > 0) {
            Text(
                text = "$unread 則通知",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.accent,
            )
        }
    }
}

@Composable
private fun PageDots(count: Int, selected: Int, modifier: Modifier = Modifier) {
    val tokens = FoldSpaceTheme.tokens
    Row(
        modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (index == selected) 7.dp else 5.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == selected) tokens.accent else tokens.textMuted.copy(alpha = 0.5f),
                    ),
            )
        }
    }
}

private val statusFormat = SimpleDateFormat("HH:mm  EEE M/d", Locale.getDefault())
