package com.foldspace.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.foldspace.launcher.home.HomeItem
import com.foldspace.launcher.home.HomeItemType
import com.foldspace.launcher.home.HomeLayout
import com.foldspace.launcher.home.HomePage
import com.foldspace.launcher.home.PageKind
import com.foldspace.launcher.notifications.NotificationSummary
import com.foldspace.launcher.spaces.SpaceDensity
import com.foldspace.launcher.ui.components.AppIcon
import com.foldspace.launcher.ui.components.AppTile
import com.foldspace.launcher.ui.theme.FoldSpaceTheme
import com.foldspace.launcher.ui.widgets.WidgetCell
import com.foldspace.launcher.widgets.WidgetHostController
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Where a drag currently is, and what it would do if released there. */
private data class DragState(
    val item: HomeItem,
    val pointer: Offset,
    val targetPage: Int,
    val targetCell: Pair<Int, Int>?,
    val targetItem: HomeItem?,
)

/**
 * The editable home surface: pages of a fixed grid, apps placed by the user.
 *
 * There is no app drawer behind this — every installed app lives on a page —
 * so the swipe-up search is the only other way to reach one, and it matters
 * more than it did before.
 */
@Composable
fun PagedHome(
    layout: HomeLayout,
    notifications: NotificationSummary,
    density: SpaceDensity,
    widgetHost: WidgetHostController?,
    editing: Boolean,
    onLaunch: (HomeItem) -> Unit,
    onLongPress: (HomeItem) -> Unit,
    onOpenFolder: (HomeItem) -> Unit,
    onMove: (item: HomeItem, page: Int, cellX: Int, cellY: Int) -> Unit,
    onDropOnto: (moving: HomeItem, target: HomeItem) -> Unit,
    onLongPressEmpty: (page: Int, cellX: Int, cellY: Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    feedContent: (@Composable () -> Unit)? = null,
    workContent: (@Composable () -> Unit)? = null,
) {
    val gridPages = layout.pages.ifEmpty { listOf(HomePage(0, emptyList())) }
    val leadingCount = if (layout.leading != null) 1 else 0
    val pagerState = rememberPagerState(
        // Land on the first grid page, not on the feed. Opening the launcher
        // into a news feed rather than your apps would be the wrong default
        // however much the feed is worth having one swipe away.
        initialPage = leadingCount,
        pageCount = { gridPages.size + leadingCount },
    )

    var drag by remember { mutableStateOf<DragState?>(null) }

    Column(
        modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 8.dp,
                // A drag owns the pointer; letting the pager also read it means
                // a diagonal drag flings the page out from under the icon.
                userScrollEnabled = drag == null,
            ) { pagerIndex ->
                val leading = layout.leading.takeIf { pagerIndex < leadingCount }
                val page = gridPages.getOrNull(pagerIndex - leadingCount)
                when {
                    leading == PageKind.Feed ->
                        feedContent?.invoke() ?: EmptyPage("尚未設定新聞來源")

                    leading == PageKind.Work ->
                        workContent?.invoke() ?: EmptyPage("尚無工項")

                    page == null -> EmptyPage("這一頁還是空的")

                    else -> CellGrid(
                        layout = layout,
                        page = page,
                        notifications = notifications,
                        density = density,
                        widgetHost = widgetHost,
                        editing = editing,
                        drag = drag,
                        pageIndex = page.index,
                        pagerIndex = pagerIndex,
                        pagerState = pagerState,
                        leadingCount = leadingCount,
                        onLaunch = onLaunch,
                        onLongPress = onLongPress,
                        onOpenFolder = onOpenFolder,
                        onLongPressEmpty = onLongPressEmpty,
                        onDragUpdate = { drag = it },
                        onDragEnd = {
                            val current = drag
                            drag = null
                            if (current != null) {
                                val cell = current.targetCell
                                val target = current.targetItem
                                when {
                                    target != null && target.id != current.item.id ->
                                        onDropOnto(current.item, target)

                                    cell != null ->
                                        onMove(
                                            current.item,
                                            current.targetPage,
                                            cell.first,
                                            cell.second,
                                        )
                                }
                            }
                        },
                    )
                }
            }

            // The dragged icon rides above everything, including the pager.
            drag?.let { state ->
                DragGhost(item = state.item, pointer = state.pointer, density = density)
            }
        }

        if (gridPages.size + leadingCount > 1) {
            PageDots(
                leading = layout.leading,
                gridPages = gridPages,
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
    page: HomePage,
    notifications: NotificationSummary,
    density: SpaceDensity,
    widgetHost: WidgetHostController?,
    editing: Boolean,
    drag: DragState?,
    pageIndex: Int,
    pagerIndex: Int,
    pagerState: PagerState,
    leadingCount: Int,
    onLaunch: (HomeItem) -> Unit,
    onLongPress: (HomeItem) -> Unit,
    onOpenFolder: (HomeItem) -> Unit,
    onLongPressEmpty: (page: Int, cellX: Int, cellY: Int) -> Unit,
    onDragUpdate: (DragState?) -> Unit,
    onDragEnd: () -> Unit,
) {
    val byCell = remember(page) { page.items.associateBy { it.cellX to it.cellY } }
    var gridSize by remember { mutableStateOf(IntSize.Zero) }
    val localDensity = LocalDensity.current
    val activeDrag = drag?.takeIf { it.targetPage == pageIndex }

    /** Pager positions include the leading page; stored page indices do not. */
    fun gridPageAt(pagerPosition: Int) = pagerPosition - leadingCount

    // Dragging to an edge turns the page, which is the only practical way to
    // move an app across five pages.
    LaunchedEffect(activeDrag?.pointer?.x, gridSize.width) {
        val pointerX = activeDrag?.pointer?.x ?: return@LaunchedEffect
        if (gridSize.width == 0) return@LaunchedEffect
        val edge = gridSize.width * EDGE_FRACTION
        val next = when {
            pointerX < edge -> pagerState.currentPage - 1
            pointerX > gridSize.width - edge -> pagerState.currentPage + 1
            else -> return@LaunchedEffect
        }
        // Never drag onto the leading page — it holds no cells.
        if (next < leadingCount || next >= pagerState.pageCount) return@LaunchedEffect
        delay(EDGE_DWELL_MS)
        pagerState.animateScrollToPage(next)
    }

    val dragModifier = Modifier.pointerInput(page.index, layout.grid, editing) {
            var position = Offset.Zero

            fun cellAt(offset: Offset): Pair<Int, Int>? {
                if (size.width == 0 || size.height == 0) return null
                val x = (offset.x / (size.width.toFloat() / layout.grid.columns)).toInt()
                val y = (offset.y / (size.height.toFloat() / layout.grid.rows)).toInt()
                return if (layout.grid.contains(x, y)) x to y else null
            }

            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    position = offset
                    val cell = cellAt(offset)
                    val item = cell?.let(byCell::get)
                    when {
                        // Long-pressing an icon picks it up. Doing so outside
                        // edit mode is what people expect, so it enters edit
                        // mode rather than refusing.
                        item != null -> onDragUpdate(DragState(item, offset, pageIndex, cell, null))

                        // Long-pressing blank space is the only discoverable
                        // way to add a widget.
                        cell != null -> onLongPressEmpty(pageIndex, cell.first, cell.second)
                    }
                },
                onDrag = { change, amount ->
                    change.consume()
                    position += amount
                    val current = drag ?: return@detectDragGesturesAfterLongPress
                    val cell = cellAt(position)
                    onDragUpdate(
                        current.copy(
                            pointer = position,
                            targetPage = gridPageAt(pagerState.currentPage)
                                .coerceAtLeast(0),
                            targetCell = cell,
                            targetItem = cell?.let(byCell::get)
                                ?.takeIf { it.id != current.item.id },
                        ),
                    )
                },
                onDragEnd = onDragEnd,
                onDragCancel = onDragEnd,
            )
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .onSizeChanged { gridSize = it }
            .then(dragModifier),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val cellWidthDp = if (gridSize.width == 0) 0 else with(localDensity) {
            (gridSize.width / layout.grid.columns).toDp().value.toInt()
        }
        val cellHeightDp = if (gridSize.height == 0) 0 else with(localDensity) {
            (gridSize.height / layout.grid.rows).toDp().value.toInt()
        }

        repeat(layout.grid.rows) { y ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                repeat(layout.grid.columns) { x ->
                    val item = byCell[x to y]
                    val isDropTarget = activeDrag?.targetCell == (x to y)
                    val isBeingDragged = activeDrag != null && activeDrag.item.id == item?.id

                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(
                                if (isDropTarget) {
                                    Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .border(
                                            1.dp,
                                            FoldSpaceTheme.tokens.accent,
                                            RoundedCornerShape(16.dp),
                                        )
                                } else {
                                    Modifier
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (item != null) {
                            Box(Modifier.alpha(if (isBeingDragged) 0.25f else 1f)) {
                                HomeCell(
                                    item = item,
                                    badgeCount = item.app
                                        ?.let { app -> notifications.countFor(app.packageName) }
                                        ?: 0,
                                    density = density,
                                    widgetHost = widgetHost,
                                    cellWidthDp = cellWidthDp,
                                    cellHeightDp = cellHeightDp,
                                    onClick = {
                                        if (item.type == HomeItemType.Folder) {
                                            onOpenFolder(item)
                                        } else {
                                            onLaunch(item)
                                        }
                                    },
                                    onLongClick = { onLongPress(item) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DragGhost(item: HomeItem, pointer: Offset, density: SpaceDensity) {
    val localDensity = LocalDensity.current
    val size = density.iconSizeDp.dp
    val halfPx = with(localDensity) { (size / 2).toPx() }

    Box(
        Modifier
            .offset {
                IntOffset(
                    (pointer.x - halfPx).roundToInt(),
                    (pointer.y - halfPx).roundToInt(),
                )
            }
            .size(size)
            .alpha(0.9f),
    ) {
        item.app?.let { AppIcon(it, size) }
    }
}

@Composable
private fun HomeCell(
    item: HomeItem,
    badgeCount: Int,
    density: SpaceDensity,
    widgetHost: WidgetHostController?,
    cellWidthDp: Int,
    cellHeightDp: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    when (item.type) {
        HomeItemType.App -> {
            val app = item.app
            if (app == null) {
                // §14.1 — a paused work profile or an app on unmounted storage
                // keeps its cell; deleting it would lose the arrangement over
                // something temporary.
                UnavailableCell("暫停")
            } else {
                AppTile(
                    entry = app,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    iconSize = density.iconSizeDp.dp,
                    badgeCount = badgeCount,
                )
            }
        }

        HomeItemType.Folder -> FolderCell(item, density, onClick)

        HomeItemType.Widget -> {
            val id = item.appWidgetId
            if (widgetHost == null || id == null) {
                UnavailableCell("工具")
            } else {
                WidgetCell(
                    controller = widgetHost,
                    appWidgetId = id,
                    widthDp = cellWidthDp * item.spanX,
                    heightDp = cellHeightDp * item.spanY,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun FolderCell(item: HomeItem, density: SpaceDensity, onClick: () -> Unit) {
    val tokens = FoldSpaceTheme.tokens
    Column(
        Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(density.iconSizeDp.dp)
                .clip(RoundedCornerShape(tokens.iconCornerRadius))
                .background(tokens.surfaceElevated)
                .padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            // A 2x2 peek of the first four members, which is what makes a
            // folder recognisable at icon size.
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                item.folderContents.take(4).chunked(2).forEach { pair ->
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
private fun UnavailableCell(label: String) {
    val tokens = FoldSpaceTheme.tokens
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tokens.surfaceElevated.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tokens.textMuted,
        )
    }
}

@Composable
private fun EmptyPage(message: String) {
    val tokens = FoldSpaceTheme.tokens
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.textMuted,
        )
    }
}

@Composable
private fun PageDots(
    leading: PageKind?,
    gridPages: List<HomePage>,
    selected: Int,
    modifier: Modifier = Modifier,
) {
    val tokens = FoldSpaceTheme.tokens
    val leadingCount = if (leading != null) 1 else 0

    Row(
        modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(leadingCount + gridPages.size) { index ->
            val isSelected = index == selected
            val isLeading = index < leadingCount
            // The leading page gets a wider marker so it is findable by its dot
            // rather than by swiping to see what is over there.
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(
                        width = if (isLeading) 14.dp else if (isSelected) 7.dp else 5.dp,
                        height = if (isSelected) 7.dp else 5.dp,
                    )
                    .clip(CircleShape)
                    .background(
                        if (isSelected) tokens.accent else tokens.textMuted.copy(alpha = 0.5f),
                    ),
            )
        }
    }
}

/** How close to the edge a drag has to get before the page turns. */
private const val EDGE_FRACTION = 0.12f
private const val EDGE_DWELL_MS = 500L
