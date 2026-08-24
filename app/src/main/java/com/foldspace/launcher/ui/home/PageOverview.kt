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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.foldspace.launcher.home.GridSpec
import com.foldspace.launcher.home.HomePage
import com.foldspace.launcher.home.PageKind
import com.foldspace.launcher.ui.theme.FoldSpaceTheme

/**
 * The zoomed-out view of the pages, for reordering and adding or removing them.
 *
 * Each card is a *schematic* — a miniature grid with a dot for every occupied
 * cell — rather than a live render. Composing every page at thumbnail size to
 * show what amounts to a pattern of dots would cost far more than it tells
 * anyone, and the pattern is what people actually recognise a page by.
 *
 * Reordering is a long-press drag over the row: the card follows the finger by
 * whole card widths, which is the only granularity that means anything when
 * the result is an integer page index.
 */
@Composable
fun PageOverview(
    pages: List<HomePage>,
    grid: GridSpec,
    currentPage: Int,
    onMovePage: (from: Int, to: Int) -> Unit,
    onDeletePage: (pageIndex: Int) -> Unit,
    onAddPage: () -> Unit,
    onOpenPage: (position: Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val tokens = FoldSpaceTheme.tokens

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
            Column {
                Text(
                    text = "頁面",
                    style = MaterialTheme.typography.headlineSmall,
                    color = tokens.textPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "長按拖曳可換順序；空白的頁面才能刪除",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted,
                )
            }
            Text(
                text = "完成",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.accent,
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 10.dp, vertical = 14.dp),
            )
        }

        Spacer(Modifier.height(20.dp))

        var dragging by remember { mutableStateOf<Int?>(null) }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(CARD_GAP),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(pages, key = { _, page -> page.index }) { position, page ->
                PageCard(
                    page = page,
                    grid = grid,
                    position = position,
                    isCurrent = position == currentPage,
                    isDragging = dragging == position,
                    onOpen = { onOpenPage(position) },
                    onDelete = { onDeletePage(page.index) },
                    onDragStart = { dragging = position },
                    onDragStep = { steps ->
                        val from = dragging ?: return@PageCard
                        val to = (from + steps).coerceIn(0, pages.lastIndex)
                        if (to != from) {
                            onMovePage(from, to)
                            dragging = to
                        }
                    },
                    onDragEnd = { dragging = null },
                    canMoveLeft = position > 0,
                    canMoveRight = position < pages.lastIndex,
                    onMoveLeft = { onMovePage(position, position - 1) },
                    onMoveRight = { onMovePage(position, position + 1) },
                )
            }

            item {
                AddPageCard(onClick = onAddPage)
            }
        }
    }
}

@Composable
private fun PageCard(
    page: HomePage,
    grid: GridSpec,
    position: Int,
    isCurrent: Boolean,
    isDragging: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onDragStart: () -> Unit,
    onDragStep: (Int) -> Unit,
    onDragEnd: () -> Unit,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
) {
    val tokens = FoldSpaceTheme.tokens
    val density = LocalDensity.current
    // The gesture outlives recompositions, so it must not hold the callbacks
    // it was installed with — they close over this card's position.
    val startDrag by rememberUpdatedState(onDragStart)
    val stepDrag by rememberUpdatedState(onDragStep)
    val endDrag by rememberUpdatedState(onDragEnd)

    val occupied = remember(page) {
        buildSet {
            page.items.forEach { item ->
                for (y in item.cellY until item.cellY + item.spanY.coerceAtLeast(1)) {
                    for (x in item.cellX until item.cellX + item.spanX.coerceAtLeast(1)) {
                        add(x to y)
                    }
                }
            }
        }
    }

    Box {
        Column(
            Modifier
                .width(CARD_WIDTH)
                .height(CARD_HEIGHT)
                .alpha(if (isDragging) 0.5f else 1f)
                .clip(RoundedCornerShape(14.dp))
                .background(tokens.surfaceAlpha())
                .border(
                    width = if (isCurrent) 2.dp else 1.dp,
                    color = if (isCurrent) tokens.accent else tokens.outline,
                    shape = RoundedCornerShape(14.dp),
                )
                .clickable(onClick = onOpen)
                // Keyed on the grid alone. It used to be keyed on `position`
                // as well — the very thing a reorder changes — so the first
                // step restarted the pointer input and cancelled the drag
                // that caused it. One step and then nothing, every time.
                .pointerInput(grid) {
                    var travel = 0f
                    val step = with(density) { CARD_WIDTH.toPx() + CARD_GAP.toPx() }
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            travel = 0f
                            startDrag()
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            travel += amount.x
                            val steps = (travel / step).toInt()
                            if (steps != 0) {
                                travel -= steps * step
                                stepDrag(steps)
                            }
                        },
                        onDragEnd = { endDrag() },
                        onDragCancel = { endDrag() },
                    )
                }
                .padding(8.dp),
        ) {
            MiniGrid(
                grid = grid,
                occupied = occupied,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = page.kind.label(),
                style = MaterialTheme.typography.labelSmall,
                color = if (isCurrent) tokens.accent else tokens.textMuted,
            )
        }

        // Explicit move buttons, and they are the guarantee rather than the
        // garnish. Reordering by drag has now failed on the device twice, and
        // a page order that can only be changed by a gesture is a page order
        // that cannot be changed when the gesture breaks. These cannot be
        // stolen by anything in the pointer stream.
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MoveButton(label = "‹", enabled = canMoveLeft, onClick = onMoveLeft)
            MoveButton(label = "›", enabled = canMoveRight, onClick = onMoveRight)
        }

        if (occupied.isEmpty()) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(tokens.surfaceElevated)
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "✕",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textPrimary,
                )
            }
        }
    }
}

/** A dot per occupied cell — the pattern is what identifies a page. */
@Composable
private fun MiniGrid(
    grid: GridSpec,
    occupied: Set<Pair<Int, Int>>,
    modifier: Modifier = Modifier,
) {
    val tokens = FoldSpaceTheme.tokens
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(grid.rows) { y ->
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                repeat(grid.columns) { x ->
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if ((x to y) in occupied) {
                                    tokens.textSecondary.copy(alpha = 0.75f)
                                } else {
                                    tokens.textMuted.copy(alpha = 0.15f)
                                },
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun AddPageCard(onClick: () -> Unit) {
    val tokens = FoldSpaceTheme.tokens
    Box(
        Modifier
            .width(CARD_WIDTH)
            .height(CARD_HEIGHT)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, tokens.outline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "＋",
            style = MaterialTheme.typography.headlineSmall,
            color = tokens.accent,
        )
    }
}

private fun PageKind.label(): String = when (this) {
    PageKind.Grid -> "頁面"
    PageKind.Hub -> "摘要"
    PageKind.Feed -> "新聞"
    PageKind.Widgets -> "小工具"
    PageKind.Work -> "工項"
    PageKind.Library -> "資料庫"
}

private val CARD_WIDTH = 104.dp
private val CARD_HEIGHT = 168.dp
private val CARD_GAP = 12.dp


/**
 * One nudge, one position.
 *
 * A 32dp target rather than the 48dp minimum because two of them sit on a
 * card barely wider than that, and the alternative — no button at all — is
 * what has left page order unchangeable for three builds.
 */
@Composable
private fun MoveButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val tokens = FoldSpaceTheme.tokens
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (enabled) tokens.surfaceElevated else tokens.surfaceElevated.copy(alpha = 0.3f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) tokens.textPrimary else tokens.textMuted,
        )
    }
}
