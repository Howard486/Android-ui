package com.foldspace.launcher.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reflow behind a user-changeable grid.
 *
 * Idempotence is the property that matters: the launcher repacks whenever it
 * reads the setting rather than tracking whether it changed, so a packer that
 * shuffled on a no-op run would rearrange the home screen at every launch.
 */
class GridPackerTest {

    private val grid = GridSpec(4, 6)

    @Test
    fun `single cells fill reading order`() {
        val slots = GridPacker.pack(List(6) { 1 to 1 }, grid)
        assertEquals(CellSlot(0, 0, 0), slots[0])
        assertEquals(CellSlot(0, 3, 0), slots[3])
        assertEquals(CellSlot(0, 0, 1), slots[4])
    }

    @Test
    fun `a full page spills onto the next`() {
        val slots = GridPacker.pack(List(grid.cellsPerPage + 1) { 1 to 1 }, grid)
        assertEquals(CellSlot(1, 0, 0), slots.last())
    }

    @Test
    fun `a wide item takes a row that fits it`() {
        // 4 wide on a 4-column grid can only start at column 0.
        val slots = GridPacker.pack(listOf(1 to 1, 4 to 1), grid)
        assertEquals(CellSlot(0, 0, 0), slots[0])
        assertEquals(CellSlot(0, 0, 1), slots[1])
    }

    @Test
    fun `a following item slots into the gap a wide one left`() {
        val slots = GridPacker.pack(listOf(3 to 1, 1 to 1), grid)
        assertEquals(CellSlot(0, 0, 0), slots[0])
        assertEquals(CellSlot(0, 3, 0), slots[1])
    }

    @Test
    fun `a span wider than the grid is clamped rather than dropped`() {
        val slots = GridPacker.pack(listOf(99 to 99), grid)
        assertEquals(CellSlot(0, 0, 0), slots[0])
    }

    @Test
    fun `packing is idempotent`() {
        val spans = listOf(1 to 1, 2 to 2, 1 to 1, 4 to 1, 1 to 1, 2 to 1)
        val first = GridPacker.pack(spans, grid)
        // Re-derive the input in the order the packer produced, which is what
        // the repository does when it re-reads a stored layout.
        val reordered = first.indices.sortedWith(
            compareBy({ first[it].pageIndex }, { first[it].cellY }, { first[it].cellX }),
        )
        val second = GridPacker.pack(reordered.map { spans[it] }, grid)
        assertEquals(reordered.map { first[it] }, second)
    }

    @Test
    fun `nothing ever overlaps`() {
        val spans = List(20) { index -> (index % 3 + 1) to (index % 2 + 1) }
        val slots = GridPacker.pack(spans, grid)
        val taken = mutableSetOf<Triple<Int, Int, Int>>()
        slots.forEachIndexed { index, slot ->
            val (spanX, spanY) = spans[index]
            for (y in slot.cellY until slot.cellY + spanY) {
                for (x in slot.cellX until slot.cellX + spanX) {
                    assertTrue(
                        "cell ($x,$y) on page ${slot.pageIndex} claimed twice",
                        taken.add(Triple(slot.pageIndex, x, y)),
                    )
                }
            }
        }
    }

    @Test
    fun `every item stays inside the grid`() {
        val spans = List(30) { index -> (index % 4 + 1) to (index % 3 + 1) }
        GridPacker.pack(spans, grid).forEachIndexed { index, slot ->
            val (spanX, spanY) = spans[index]
            assertTrue(slot.cellX + minOf(spanX, grid.columns) <= grid.columns)
            assertTrue(slot.cellY + minOf(spanY, grid.rows) <= grid.rows)
        }
    }
}
