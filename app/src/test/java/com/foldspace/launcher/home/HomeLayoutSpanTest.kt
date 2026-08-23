package com.foldspace.launcher.home

import com.foldspace.launcher.spaces.SpaceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The span arithmetic behind widget placement and resizing.
 *
 * Worth testing off-device because every bug in it looks like a rendering
 * bug: an ignored span drew a four-cell widget in one cell, and an anchor-only
 * occupancy check placed apps underneath it.
 */
class HomeLayoutSpanTest {

    private fun item(
        id: Long,
        x: Int,
        y: Int,
        spanX: Int = 1,
        spanY: Int = 1,
        type: HomeItemType = HomeItemType.App,
    ) = HomeItem(
        id = id,
        type = type,
        cellX = x,
        cellY = y,
        spanX = spanX,
        spanY = spanY,
        app = null,
        folderTitle = null,
    )

    private fun layout(vararg items: HomeItem, grid: GridSpec = GridSpec(5, 7)) = HomeLayout(
        space = SpaceId.General,
        posture = Posture.Folded,
        grid = grid,
        pages = listOf(HomePage(index = 0, items = items.toList())),
    )

    @Test
    fun `a span covers every cell it reaches`() {
        val widget = item(1, x = 1, y = 2, spanX = 3, spanY = 2, type = HomeItemType.Widget)
        assertTrue(widget.covers(1, 2))
        assertTrue(widget.covers(3, 3))
        assertFalse(widget.covers(4, 3))
        assertFalse(widget.covers(3, 4))
        assertFalse(widget.covers(0, 2))
    }

    @Test
    fun `occupantAt finds a widget from any of its cells, not just the anchor`() {
        val page = HomePage(0, listOf(item(7, 1, 1, spanX = 2, spanY = 2)))
        assertEquals(7L, page.occupantAt(2, 2)?.id)
        assertNull(page.occupantAt(3, 1))
    }

    @Test
    fun `firstFreeCell skips cells a span is sitting on`() {
        // A 2x1 widget at the origin: the first two cells are gone, not one.
        val layout = layout(item(1, 0, 0, spanX = 2, spanY = 1))
        assertEquals(2 to 0, layout.firstFreeCell(0))
    }

    @Test
    fun `a span stops at the edge of the grid`() {
        val widget = item(1, x = 3, y = 0, spanX = 1, spanY = 1)
        val layout = layout(widget)
        assertTrue(layout.spanFits(0, widget, 2, 1))
        assertFalse(layout.spanFits(0, widget, 3, 1))
    }

    @Test
    fun `a span stops at a neighbour`() {
        val widget = item(1, x = 0, y = 0)
        val neighbour = item(2, x = 2, y = 0)
        val layout = layout(widget, neighbour)
        assertTrue(layout.spanFits(0, widget, 2, 1))
        assertFalse(layout.spanFits(0, widget, 3, 1))
    }

    @Test
    fun `an item never blocks itself, so shrinking always fits`() {
        val widget = item(1, x = 0, y = 0, spanX = 4, spanY = 3)
        val layout = layout(widget)
        assertTrue(layout.spanFits(0, widget, 1, 1))
        assertTrue(layout.spanFits(0, widget, 4, 3))
    }

    @Test
    fun `clampSpan gives back the largest span that fits`() {
        val widget = item(1, x = 0, y = 0)
        val layout = layout(widget, item(2, x = 3, y = 0))
        assertEquals(3 to 1, layout.clampSpan(0, widget, 5, 1))
    }

    @Test
    fun `clampSpan never returns something smaller than a cell`() {
        val widget = item(1, x = 4, y = 6)
        val layout = layout(widget)
        assertEquals(1 to 1, layout.clampSpan(0, widget, 0, 0))
    }

    @Test
    fun `clampSpan clamps both axes independently`() {
        val widget = item(1, x = 0, y = 0)
        // Blocks the third column and the third row separately.
        val layout = layout(widget, item(2, x = 2, y = 0), item(3, x = 0, y = 2))
        assertEquals(2 to 2, layout.clampSpan(0, widget, 5, 7))
    }
}
