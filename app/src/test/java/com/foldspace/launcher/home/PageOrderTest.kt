package com.foldspace.launcher.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The permutation behind page reordering.
 *
 * Worth testing away from the database because the write is a two-phase park
 * across two tables inside one transaction — a permutation bug in there would
 * surface as a corrupted home screen, not as an exception.
 */
class PageOrderTest {

    @Test
    fun `moving a page forward shifts the ones it passes`() {
        assertEquals(listOf(1, 2, 0, 3), PageOrder.move(4, from = 0, to = 2))
    }

    @Test
    fun `moving a page backward shifts the ones it passes`() {
        assertEquals(listOf(0, 3, 1, 2), PageOrder.move(4, from = 3, to = 1))
    }

    @Test
    fun `moving to the same place changes nothing`() {
        assertEquals(listOf(0, 1, 2, 3), PageOrder.move(4, from = 2, to = 2))
    }

    @Test
    fun `an impossible drag leaves the order alone`() {
        assertEquals(listOf(0, 1, 2), PageOrder.move(3, from = -1, to = 1))
        assertEquals(listOf(0, 1, 2), PageOrder.move(3, from = 0, to = 9))
        assertEquals(emptyList<Int>(), PageOrder.move(0, from = 0, to = 0))
    }

    @Test
    fun `every page appears exactly once, whatever the move`() {
        for (from in 0 until 6) {
            for (to in 0 until 6) {
                val order = PageOrder.move(6, from, to)
                assertEquals("from=$from to=$to", (0 until 6).toSet(), order.toSet())
                assertEquals("from=$from to=$to", 6, order.size)
            }
        }
    }

    @Test
    fun `removing a page drops it and closes the gap`() {
        assertEquals(listOf(0, 2, 3), PageOrder.removed(4, index = 1))
        assertEquals(listOf(0, 1, 2), PageOrder.removed(4, index = 3))
    }

    @Test
    fun `removing something that is not there leaves the order alone`() {
        assertEquals(listOf(0, 1, 2), PageOrder.removed(3, index = 7))
    }

    @Test
    fun `changes lists only the pages that actually move`() {
        // 0 -> 2, 1 -> 0, 2 -> 1; page 3 stays put and is absent.
        assertEquals(
            listOf(1 to 0, 2 to 1, 0 to 2),
            PageOrder.changes(PageOrder.move(4, from = 0, to = 2)),
        )
    }

    @Test
    fun `changes is empty for an untouched order`() {
        assertEquals(emptyList<Pair<Int, Int>>(), PageOrder.changes(listOf(0, 1, 2, 3)))
    }

    @Test
    fun `moveWithin works in stored indices, not list positions`() {
        // Pages 0, 2 and 5 exist — 1, 3 and 4 were deleted or are hidden by
        // the current context. Moving the page stored at 5 to where 2 sits
        // must renumber those two, and must not touch a page numbered 1.
        val moves = PageOrder.moveWithin(listOf(0, 2, 5), fromIndex = 5, toIndex = 2)
        assertEquals(setOf(5 to 2, 2 to 5), moves.toSet())
        assertTrue(moves.none { it.first == 1 || it.second == 1 })
    }

    @Test
    fun `moveWithin never invents an index outside the set it was given`() {
        val indices = listOf(0, 2, 5, 9)
        for (from in indices) {
            for (to in indices) {
                val moves = PageOrder.moveWithin(indices, from, to)
                moves.forEach { (old, new) ->
                    assertTrue("old=$old", old in indices)
                    assertTrue("new=$new", new in indices)
                }
            }
        }
    }

    @Test
    fun `moveWithin is a permutation, so no page index is lost or duplicated`() {
        val indices = listOf(0, 2, 5, 9)
        val moves = PageOrder.moveWithin(indices, fromIndex = 0, toIndex = 9)
        // Every index that is vacated is also filled.
        assertEquals(moves.map { it.first }.sorted(), moves.map { it.second }.sorted())
    }

    @Test
    fun `moving a page onto itself changes nothing`() {
        assertTrue(PageOrder.moveWithin(listOf(0, 2, 5), 2, 2).isEmpty())
    }

    @Test
    fun `an index that is not in the set is refused rather than guessed at`() {
        assertTrue(PageOrder.moveWithin(listOf(0, 2, 5), fromIndex = 3, toIndex = 0).isEmpty())
        assertTrue(PageOrder.moveWithin(listOf(0, 2, 5), fromIndex = 0, toIndex = 3).isEmpty())
        assertTrue(PageOrder.moveWithin(emptyList(), 0, 1).isEmpty())
    }

    @Test
    fun `a contiguous set behaves exactly like the positional move`() {
        // The case that always worked, pinned so the new path cannot regress it.
        val indices = listOf(0, 1, 2, 3)
        assertEquals(
            PageOrder.changes(PageOrder.move(4, 3, 1)),
            PageOrder.moveWithin(indices, fromIndex = 3, toIndex = 1),
        )
    }
}
