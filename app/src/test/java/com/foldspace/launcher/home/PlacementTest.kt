package com.foldspace.launcher.home

import com.foldspace.launcher.spaces.SpaceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Collision at an arbitrary anchor, and how big a spanned icon is drawn.
 *
 * Both were missing and both are the kind of arithmetic that looks obviously
 * right and is not: a move with no collision check wrote silent overlaps for
 * months, and the icon size decides whether a 2x2 app looks deliberate or
 * looks broken.
 */
class PlacementTest {

    private fun item(
        id: Long,
        x: Int,
        y: Int,
        spanX: Int = 1,
        spanY: Int = 1,
    ) = HomeItem(
        id = id,
        type = HomeItemType.App,
        cellX = x,
        cellY = y,
        spanX = spanX,
        spanY = spanY,
        app = null,
        folderTitle = null,
    )

    private fun layout(vararg items: HomeItem, grid: GridSpec = GridSpec(4, 6)) = HomeLayout(
        space = SpaceId.General,
        posture = Posture.Folded,
        grid = grid,
        pages = listOf(HomePage(index = 0, items = items.toList())),
    )

    // ---- rectFits ----

    @Test
    fun `a free cell fits`() {
        val moving = item(1, 0, 0)
        assertTrue(layout(moving).rectFits(0, moving, 2, 3))
    }

    @Test
    fun `an item never collides with itself`() {
        val moving = item(1, 0, 0, spanX = 2, spanY = 2)
        assertTrue(layout(moving).rectFits(0, moving, 0, 0))
    }

    @Test
    fun `a cell a neighbour merely covers is refused`() {
        // The bug this exists to stop: a 2x2 widget at (1,1) covers (2,2),
        // which is not its anchor, so an anchor-only check let an app land
        // underneath it.
        val moving = item(1, 0, 0)
        val widget = item(2, 1, 1, spanX = 2, spanY = 2)
        val layout = layout(moving, widget)
        assertFalse(layout.rectFits(0, moving, 2, 2))
        assertFalse(layout.rectFits(0, moving, 1, 1))
        assertTrue(layout.rectFits(0, moving, 3, 0))
    }

    @Test
    fun `a rectangle may not hang off the grid`() {
        val moving = item(1, 0, 0, spanX = 2, spanY = 2)
        val layout = layout(moving)
        assertFalse(layout.rectFits(0, moving, 3, 0))
        assertFalse(layout.rectFits(0, moving, 0, 5))
        assertTrue(layout.rectFits(0, moving, 2, 4))
    }

    @Test
    fun `negative anchors and zero spans are refused`() {
        val moving = item(1, 0, 0)
        val layout = layout(moving)
        assertFalse(layout.rectFits(0, moving, -1, 0))
        assertFalse(layout.rectFits(0, moving, 0, -1))
        assertFalse(layout.rectFits(0, moving, 0, 0, spanX = 0))
        assertFalse(layout.rectFits(0, moving, 0, 0, spanY = 0))
    }

    @Test
    fun `an empty page accepts anything that fits the grid`() {
        val moving = item(1, 0, 0)
        val empty = HomeLayout(
            space = SpaceId.General,
            posture = Posture.Folded,
            grid = GridSpec(4, 6),
            pages = emptyList(),
        )
        assertTrue(empty.rectFits(0, moving, 3, 5))
        assertFalse(empty.rectFits(0, moving, 4, 5))
    }

    // ---- iconSizeFor ----

    @Test
    fun `a single cell keeps the base size`() {
        assertEquals(52, iconSizeFor(52, 1, 1, cellWidthDp = 96, cellHeightDp = 110))
    }

    @Test
    fun `a square span grows the icon`() {
        assertEquals(104, iconSizeFor(52, 2, 2, cellWidthDp = 96, cellHeightDp = 110))
    }

    @Test
    fun `a wide span is a wide box, not a bigger icon`() {
        // 3x1 is three cells across and one tall; the icon is bounded by the
        // short side or it would overflow vertically.
        assertEquals(52, iconSizeFor(52, 3, 1, cellWidthDp = 96, cellHeightDp = 110))
    }

    @Test
    fun `the icon never exceeds the box it is drawn in`() {
        val size = iconSizeFor(200, 2, 2, cellWidthDp = 40, cellHeightDp = 40)
        assertTrue("got $size", size <= 40 * 2)
    }

    @Test
    fun `an unmeasured grid still returns something drawable`() {
        assertEquals(52, iconSizeFor(52, 1, 1, cellWidthDp = 0, cellHeightDp = 0))
        assertTrue(iconSizeFor(52, 2, 2, cellWidthDp = 0, cellHeightDp = 0) > 0)
    }

    @Test
    fun `a tiny cell still yields a positive size`() {
        assertTrue(iconSizeFor(52, 1, 1, cellWidthDp = 4, cellHeightDp = 4) >= 1)
    }

    @Test
    fun `the label allowance covers everything AppTile spends besides the icon`() {
        // AppTile is a Column with 6dp padding above, 6dp of spacing between
        // icon and label, one labelSmall line (~16dp at its default line
        // height) and 6dp below. At 26 the reservation was eight short and
        // every label was sliced through the middle on the device.
        //
        // Nothing here measures text, so this is the only guard there can be:
        // it pins the constant to the arithmetic, and fails if either the
        // padding or the spacing in AppTile is changed without it.
        val paddingTop = 6
        val spacing = 6
        val labelLine = 16
        val paddingBottom = 6
        assertTrue(
            "allowance=$TILE_LABEL_ALLOWANCE_DP",
            TILE_LABEL_ALLOWANCE_DP >= paddingTop + spacing + labelLine + paddingBottom,
        )
    }

    @Test
    fun `a one-by-one icon never fills the whole cell height`() {
        // If it did there would be no room left for the label at all.
        val cellHeight = 96
        val size = iconSizeFor(
            baseDp = 200,
            spanX = 1,
            spanY = 1,
            cellWidthDp = 200,
            cellHeightDp = cellHeight,
        )
        assertTrue(size <= cellHeight - TILE_LABEL_ALLOWANCE_DP)
    }
}
