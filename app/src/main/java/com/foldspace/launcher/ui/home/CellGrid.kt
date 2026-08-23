package com.foldspace.launcher.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import com.foldspace.launcher.home.GridSpec

/**
 * Carries a child's cell position and span through to the grid's measure pass.
 */
private class CellPlacement(
    val cellX: Int,
    val cellY: Int,
    val spanX: Int,
    val spanY: Int,
) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = this@CellPlacement
}

/** Places a child at a cell, optionally spanning several. */
fun Modifier.gridCell(cellX: Int, cellY: Int, spanX: Int = 1, spanY: Int = 1): Modifier =
    this.then(CellPlacement(cellX, cellY, spanX, spanY))

/**
 * A fixed grid whose children sit at absolute cells and may span several.
 *
 * Written as a custom Layout rather than nested rows because rows cannot span.
 * The nested-row version silently drew every widget in a single cell however
 * many it had claimed, which is why a widget that asked for four cells arrived
 * squashed into one — the span was stored and then ignored all the way to the
 * screen.
 */
@Composable
fun CellGridLayout(
    grid: GridSpec,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val width = constraints.maxWidth
        // A pager page is bounded, but guard anyway: an unbounded height would
        // make every cell infinitely tall and the measure would throw.
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else 0

        val columns = grid.columns.coerceAtLeast(1)
        val rows = grid.rows.coerceAtLeast(1)
        val cellWidth = width / columns
        val cellHeight = height / rows

        val placed = measurables.map { measurable ->
            val placement = measurable.parentData as? CellPlacement
            val spanX = (placement?.spanX ?: 1).coerceIn(1, columns)
            val spanY = (placement?.spanY ?: 1).coerceIn(1, rows)
            measurable.measure(
                Constraints.fixed(
                    width = (cellWidth * spanX).coerceAtLeast(0),
                    height = (cellHeight * spanY).coerceAtLeast(0),
                ),
            ) to placement
        }

        layout(width, height) {
            placed.forEach { (placeable, placement) ->
                placeable.place(
                    x = (placement?.cellX ?: 0).coerceIn(0, columns - 1) * cellWidth,
                    y = (placement?.cellY ?: 0).coerceIn(0, rows - 1) * cellHeight,
                )
            }
        }
    }
}
