package com.foldspace.launcher.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The strip has been drawn at the wrong height twice. Both times the grid it
 * was laid out on disagreed with the box it was measured into, and both times
 * the only symptom was a widget rendering itself smaller than its cell.
 */
class WorkStripTest {

    @Test
    fun `an empty strip is one row, not zero`() {
        assertEquals(1, stripRows(emptyList(), maxRows = 8))
    }

    @Test
    fun `the strip is as tall as its lowest item, not its tallest span`() {
        // A one-row widget on row 2 needs three rows of strip. Taking the
        // tallest span instead gave one, and the widget was drawn at a third
        // of its height inside a box a third of the size it needed.
        assertEquals(3, stripRows(listOf(3), maxRows = 8))
        assertEquals(3, stripRows(listOf(1, 3, 2), maxRows = 8))
    }

    @Test
    fun `a stored row from a taller posture cannot stretch the strip`() {
        assertEquals(8, stripRows(listOf(12), maxRows = 8))
    }

    @Test
    fun `a nonsense ceiling still yields a drawable strip`() {
        assertEquals(1, stripRows(listOf(4), maxRows = 0))
    }

    @Test
    fun `the strip can be taller than three rows`() {
        // Three was a box the strip was drawn into, and the reason a widget
        // could not be dragged any taller.
        val folded = GridSpec.of(HomeSurface.Work, Posture.Folded)
        val unfolded = GridSpec.of(HomeSurface.Work, Posture.Unfolded)
        assertTrue(folded.rows > 3)
        assertTrue(unfolded.rows > 3)
        // Width still differs by posture; the extra room is for the tablet.
        assertTrue(unfolded.columns > folded.columns)
    }
}
