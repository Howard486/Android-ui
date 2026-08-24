package com.foldspace.launcher.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.foldspace.launcher.ui.theme.FoldSpaceTheme
import kotlin.math.roundToInt

/**
 * The edit-mode overlay on a placed item: a frame, two resize handles, a
 * remove button.
 *
 * Widgets need it because an `AndroidView` swallows touches, so the grid's own
 * long-press-to-drag never reaches one and there is otherwise no way at all to
 * act on a placed widget. Apps need only the resize half — the grid drags them
 * perfectly well — which is what [bodyDraggable] selects. Installing the body
 * drag for an app would put two drag handlers on the same pixels.
 *
 * Handles are on the right and bottom edges only. Growing from the top or left
 * would have to move the anchor cell as well as the span, which is a different
 * operation on a fixed grid, and one the user can get by moving the item and
 * resizing again.
 */
@Composable
fun ItemEditFrame(
    spanX: Int,
    spanY: Int,
    cellWidthPx: Float,
    cellHeightPx: Float,
    onProposeSpan: (spanX: Int, spanY: Int) -> Unit,
    onCommitSpan: () -> Unit,
    onMoveBy: (cellsX: Int, cellsY: Int) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    bodyDraggable: Boolean = true,
) {
    val tokens = FoldSpaceTheme.tokens

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .border(1.5.dp, tokens.accent, RoundedCornerShape(12.dp))
                .background(tokens.accent.copy(alpha = 0.06f))
                .then(if (bodyDraggable) Modifier.bodyDrag(cellWidthPx, cellHeightPx, onMoveBy) else Modifier),
        )

        ResizeHandle(
            alignment = Alignment.CenterEnd,
            cellPx = cellWidthPx,
            horizontal = true,
            currentSpan = { spanX },
            onPropose = { onProposeSpan(it, spanY) },
            onCommit = onCommitSpan,
        )

        ResizeHandle(
            alignment = Alignment.BottomCenter,
            cellPx = cellHeightPx,
            horizontal = false,
            currentSpan = { spanY },
            onPropose = { onProposeSpan(spanX, it) },
            onCommit = onCommitSpan,
        )

        Box(
            Modifier
                .align(Alignment.TopStart)
                .size(26.dp)
                .background(tokens.surfaceElevated, CircleShape)
                .pointerInput(Unit) { detectTapGestures { onRemove() } },
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

/**
 * Moving by dragging the frame itself.
 *
 * Only widgets get this. An `AndroidView` swallows touches before the grid's
 * long-press-drag can see them, so for a widget the frame is the only way to
 * move one at all; an app is dragged by the grid, and installing this as well
 * would put two drag handlers on the same pixels.
 */
private fun Modifier.bodyDrag(
    cellWidthPx: Float,
    cellHeightPx: Float,
    onMoveBy: (cellsX: Int, cellsY: Int) -> Unit,
): Modifier = composed {
    val drag = remember { HandleGesture() }
    // The caller's lambda closes over the item's *current* cell, and the item
    // moves during the drag. Without this the gesture kept calling the lambda
    // captured when pointerInput was installed, which always computed the
    // target from the cell the widget started in — so it moved one step and
    // then refused to move again. Same fault as the grid's drag, one file over.
    val move by rememberUpdatedState(onMoveBy)
    this
        // A tap in edit mode belongs to the frame, not to the widget.
        .pointerInput(Unit) { detectTapGestures { } }
        .pointerInput(cellWidthPx, cellHeightPx) {
            detectDragGestures(
                onDragStart = {
                    drag.travel = 0f
                    drag.travelY = 0f
                },
                onDrag = { change, amount ->
                    change.consume()
                    if (cellWidthPx <= 0f || cellHeightPx <= 0f) return@detectDragGestures
                    drag.travel += amount.x
                    drag.travelY += amount.y
                    val stepX = (drag.travel / cellWidthPx).roundToInt()
                    val stepY = (drag.travelY / cellHeightPx).roundToInt()
                    if (stepX == 0 && stepY == 0) return@detectDragGestures
                    // Consume whole cells as they are crossed, so the widget
                    // tracks the finger instead of jumping the whole distance
                    // when the drag ends.
                    drag.travel -= stepX * cellWidthPx
                    drag.travelY -= stepY * cellHeightPx
                    move(stepX, stepY)
                },
            )
        }
}

/**
 * One edge handle.
 *
 * The span is recomputed from the total pointer travel since the drag started,
 * not accumulated per event: the handle moves with the widget as it grows, and
 * a per-event count would compound that movement into the number of cells.
 */
@Composable
private fun BoxScope.ResizeHandle(
    alignment: Alignment,
    cellPx: Float,
    horizontal: Boolean,
    currentSpan: () -> Int,
    onPropose: (Int) -> Unit,
    onCommit: () -> Unit,
) {
    val tokens = FoldSpaceTheme.tokens
    // Plain fields, not Compose state: they change on every pointer event and
    // nothing draws from them, so making them observable would recompose the
    // frame dozens of times a drag for no visible difference.
    val gesture = remember { HandleGesture() }
    // Same reason as the body drag: these close over the span being resized.
    val propose by rememberUpdatedState(onPropose)
    val commit by rememberUpdatedState(onCommit)
    val span by rememberUpdatedState(currentSpan)

    Box(
        Modifier
            .align(alignment)
            .size(26.dp)
            .background(tokens.accent, CircleShape)
            .pointerInput(cellPx, horizontal) {
                detectDragGestures(
                    onDragStart = {
                        gesture.startSpan = span()
                        gesture.travel = 0f
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        if (cellPx <= 0f) return@detectDragGestures
                        gesture.travel += if (horizontal) amount.x else amount.y
                        val cells = (gesture.travel / cellPx).roundToInt()
                        propose((gesture.startSpan + cells).coerceAtLeast(1))
                    },
                    onDragEnd = { commit() },
                    onDragCancel = { commit() },
                )
            },
    )
}

/** Where one handle drag started from, and how far it has come. */
private class HandleGesture {
    var startSpan: Int = 1
    var travel: Float = 0f
    var travelY: Float = 0f
}
