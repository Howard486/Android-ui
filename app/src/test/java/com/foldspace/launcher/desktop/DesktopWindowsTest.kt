package com.foldspace.launcher.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopWindowsTest {

    // Roughly a Z Fold inner display at 3x.
    private val width = 2176
    private val height = 1812
    private val taskbar = 156
    private val step = 108
    private val margin = 72

    private fun at(index: Int) =
        DesktopWindows.cascade(index, width, height, taskbar, step, margin)

    @Test
    fun `a window never overlaps the taskbar`() {
        // It could not be moved out from under it: moving is the system's job.
        for (index in 0..20) {
            assertTrue("index=$index", at(index).bottom <= height - taskbar)
        }
    }

    @Test
    fun `a window is always fully on screen`() {
        for (index in 0..20) {
            val bounds = at(index)
            assertTrue("index=$index", bounds.left >= 0)
            assertTrue("index=$index", bounds.top >= 0)
            assertTrue("index=$index", bounds.right <= width)
        }
    }

    @Test
    fun `consecutive windows do not land in the same place`() {
        assertNotEquals(at(0), at(1))
        assertNotEquals(at(1), at(2))
    }

    @Test
    fun `the cascade returns to the start rather than walking off the edge`() {
        assertEquals(at(0), at(DesktopWindows.CASCADE_POSITIONS))
        assertEquals(at(1), at(DesktopWindows.CASCADE_POSITIONS + 1))
    }

    @Test
    fun `every window keeps the same size`() {
        val first = at(0)
        for (index in 1..12) {
            assertEquals(first.width, at(index).width)
            assertEquals(first.height, at(index).height)
        }
    }

    @Test
    fun `a negative index is treated as the first window`() {
        assertEquals(at(0), at(-1))
        assertEquals(at(0), at(-99))
    }

    @Test
    fun `a tiny screen still produces a usable rectangle rather than an inverted one`() {
        val bounds = DesktopWindows.cascade(3, 200, 200, 400, 50, 40)
        assertTrue(bounds.width > 0)
        assertTrue(bounds.height > 0)
        assertTrue(bounds.left >= 0)
        assertTrue(bounds.top >= 0)
    }

    @Test
    fun `a zero step puts every window in the same place, and says so plainly`() {
        assertEquals(DesktopWindows.cascade(0, width, height, taskbar, 0, margin),
                     DesktopWindows.cascade(5, width, height, taskbar, 0, margin))
    }

    @Test
    fun `the window is a sensible share of the screen`() {
        val bounds = at(0)
        assertTrue(bounds.width in (width / 2)..width)
        assertTrue(bounds.height in ((height - taskbar) / 2)..(height - taskbar))
    }
}
