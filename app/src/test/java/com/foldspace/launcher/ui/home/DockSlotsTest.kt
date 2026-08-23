package com.foldspace.launcher.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DockSlotsTest {

    private val six = listOf("a", "b", "c", "d", "e", "f")

    @Test
    fun `one row is what the dock has always been`() {
        assertEquals(listOf(listOf("a", "b", "c", "d", "e")), DockSlots.arrange(six, 1, 5))
    }

    @Test
    fun `a second row takes the overflow, in order`() {
        assertEquals(
            listOf(listOf("a", "b", "c"), listOf("d", "e", "f")),
            DockSlots.arrange(six, 2, 3),
        )
    }

    @Test
    fun `anything past capacity is dropped rather than squeezed in`() {
        // Silently shrinking icons to fit one more is how a dock stops being
        // tappable; the pin list is the user's to trim.
        assertEquals(listOf(listOf("a", "b")), DockSlots.arrange(six, 1, 2))
        assertEquals(4, DockSlots.arrange(six, 2, 2).flatten().size)
    }

    @Test
    fun `a short last row is kept short, not padded`() {
        val rows = DockSlots.arrange(listOf("a", "b", "c", "d", "e"), 2, 3)
        assertEquals(listOf(listOf("a", "b", "c"), listOf("d", "e")), rows)
    }

    @Test
    fun `no empty trailing row is ever produced`() {
        for (count in 0..12) {
            val items = List(count) { "x$it" }
            val rows = DockSlots.arrange(items, 2, 4)
            assertTrue("count=$count", rows.none { it.isEmpty() })
        }
    }

    @Test
    fun `an empty pin list draws nothing`() {
        assertEquals(emptyList<List<String>>(), DockSlots.arrange(emptyList<String>(), 2, 5))
    }

    @Test
    fun `a nonsense shape draws nothing rather than crashing`() {
        assertEquals(emptyList<List<String>>(), DockSlots.arrange(six, 0, 5))
        assertEquals(emptyList<List<String>>(), DockSlots.arrange(six, 2, 0))
        assertEquals(emptyList<List<String>>(), DockSlots.arrange(six, -1, -1))
        assertEquals(0, DockSlots.capacity(0, 5))
        assertEquals(0, DockSlots.rowsUsed(3, 0, 5))
    }

    @Test
    fun `capacity is the product`() {
        assertEquals(5, DockSlots.capacity(1, 5))
        assertEquals(12, DockSlots.capacity(2, 6))
    }

    @Test
    fun `rowsUsed matches what arrange actually draws`() {
        for (count in 0..14) {
            for (rows in 1..2) {
                for (columns in 3..6) {
                    val items = List(count) { "x$it" }
                    assertEquals(
                        "count=$count rows=$rows cols=$columns",
                        DockSlots.arrange(items, rows, columns).size,
                        DockSlots.rowsUsed(count, rows, columns),
                    )
                }
            }
        }
    }
}
