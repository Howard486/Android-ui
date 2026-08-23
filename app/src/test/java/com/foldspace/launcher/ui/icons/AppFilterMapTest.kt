package com.foldspace.launcher.ui.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFilterMapTest {

    private val calendarKey = "com.google.android.calendar/com.android.calendar.LaunchActivity"
    private val chromeKey = "com.android.chrome/com.google.android.apps.chrome.Main"

    private val filter = AppFilterMap(
        items = mapOf(calendarKey to "calendar_static", chromeKey to "chrome"),
        calendars = mapOf(calendarKey to "calendar_"),
    )

    @Test
    fun `a plain item resolves to its one drawable`() {
        assertEquals(listOf("chrome"), filter.drawableNamesFor(chromeKey, 14))
    }

    @Test
    fun `a calendar entry leads with the day, keeping the static one behind it`() {
        assertEquals(
            listOf("calendar_14", "calendar_static"),
            filter.drawableNamesFor(calendarKey, 14),
        )
    }

    @Test
    fun `single-digit days are not zero padded`() {
        // The convention requires calendar_1, not calendar_01. Getting this
        // wrong means every day before the tenth silently falls back.
        assertEquals("calendar_1", filter.drawableNamesFor(calendarKey, 1).first())
        assertEquals("calendar_9", filter.drawableNamesFor(calendarKey, 9).first())
    }

    @Test
    fun `days outside the month are clamped rather than producing a bad name`() {
        assertEquals("calendar_1", filter.drawableNamesFor(calendarKey, 0).first())
        assertEquals("calendar_31", filter.drawableNamesFor(calendarKey, 44).first())
        assertEquals("calendar_1", filter.drawableNamesFor(calendarKey, -3).first())
    }

    @Test
    fun `an unknown component resolves to nothing`() {
        assertTrue(filter.drawableNamesFor("nope/nope", 5).isEmpty())
    }

    @Test
    fun `a calendar entry with no item still resolves`() {
        val onlyCalendar = AppFilterMap(calendars = mapOf(calendarKey to "cal_"))
        assertEquals(listOf("cal_7"), onlyCalendar.drawableNamesFor(calendarKey, 7))
    }

    @Test
    fun `isDynamic distinguishes the two kinds`() {
        assertTrue(filter.isDynamic(calendarKey))
        assertFalse(filter.isDynamic(chromeKey))
        assertFalse(filter.isDynamic("nope/nope"))
    }

    @Test
    fun `size counts both kinds and emptiness is honest`() {
        assertEquals(3, filter.size)
        assertFalse(filter.isEmpty)
        assertTrue(AppFilterMap().isEmpty)
    }

    @Test
    fun `component keys are unwrapped from ComponentInfo`() {
        assertEquals(
            "com.android.chrome/com.google.android.apps.chrome.Main",
            componentKeyOf("ComponentInfo{com.android.chrome/com.google.android.apps.chrome.Main}"),
        )
    }

    @Test
    fun `malformed component strings are rejected rather than half-parsed`() {
        assertNull(componentKeyOf("ComponentInfo{}"))
        assertNull(componentKeyOf("ComponentInfo{no-slash-here}"))
        assertNull(componentKeyOf("garbage"))
        assertNull(componentKeyOf(""))
    }
}
