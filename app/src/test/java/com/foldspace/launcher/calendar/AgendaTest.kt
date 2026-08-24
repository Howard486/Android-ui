package com.foldspace.launcher.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgendaTest {

    private fun event(
        id: Long,
        start: Long,
        end: Long = start + 3_600_000L,
        title: String = "事情",
        allDay: Boolean = false,
    ) = AgendaEvent(id, title, start, end, allDay, null, null)

    @Test
    fun `titles map to a coarse category and nothing else leaks`() {
        assertEquals(AgendaCategory.Meeting, Agenda.categorise("Weekly sync with Acme"))
        assertEquals(AgendaCategory.Meeting, Agenda.categorise("產品會議"))
        assertEquals(AgendaCategory.Focus, Agenda.categorise("Deep work block"))
        assertEquals(AgendaCategory.Travel, Agenda.categorise("Flight to Tokyo"))
        assertEquals(AgendaCategory.Busy, Agenda.categorise("Dentist"))
    }

    @Test
    fun `an unknown or empty title still gets a category rather than a blank row`() {
        assertEquals(AgendaCategory.Busy, Agenda.categorise(null))
        assertEquals(AgendaCategory.Busy, Agenda.categorise(""))
    }

    @Test
    fun `the collapsed row shows the category, never the title`() {
        val secret = event(1, 0, title = "Q3 budget review with Acme")
        assertEquals("行程", secret.collapsedLabel())
        assertTrue("Acme" !in secret.collapsedLabel())
    }

    @Test
    fun `the day is ordered by start time`() {
        val summary = Agenda.summarise(
            listOf(event(1, 3_000), event(2, 1_000), event(3, 2_000)),
            now = 0,
        )
        assertEquals(listOf(2L, 3L, 1L), summary.events.map { it.id })
    }

    @Test
    fun `all-day entries sort last because they say nothing about three o'clock`() {
        val summary = Agenda.summarise(
            listOf(event(1, 0, allDay = true), event(2, 9_000)),
            now = 0,
        )
        assertEquals(listOf(2L, 1L), summary.events.map { it.id })
    }

    @Test
    fun `next is the first thing not yet finished, including one in progress`() {
        val summary = Agenda.summarise(
            listOf(event(1, 0, end = 100), event(2, 200, end = 300)),
            now = 50,
        )
        // Sitting in a meeting: that meeting is still what is next.
        assertEquals(1L, summary.next?.id)
        assertEquals(2, summary.remaining)
    }

    @Test
    fun `a finished day has no next and nothing remaining`() {
        val summary = Agenda.summarise(listOf(event(1, 0, end = 100)), now = 5_000)
        assertNull(summary.next)
        assertEquals(0, summary.remaining)
        assertTrue(!summary.isEmpty)
    }

    @Test
    fun `an all-day-only day never nominates one as next`() {
        val summary = Agenda.summarise(listOf(event(1, 0, allDay = true)), now = 5)
        assertNull(summary.next)
    }

    @Test
    fun `an empty day is empty`() {
        assertTrue(Agenda.summarise(emptyList(), now = 0).isEmpty)
        assertEquals(0, Agenda.summarise(emptyList(), now = 0).remaining)
    }

    @Test
    fun `ordering is stable when two events start together`() {
        val first = Agenda.summarise(listOf(event(2, 100), event(1, 100)), now = 0)
        val second = Agenda.summarise(listOf(event(1, 100), event(2, 100)), now = 0)
        assertEquals(first.events.map { it.id }, second.events.map { it.id })
    }

    @Test
    fun `times are zero padded and clamped`() {
        assertEquals("09:05", Agenda.timeLabel(9, 5))
        assertEquals("23:59", Agenda.timeLabel(23, 59))
        assertEquals("00:00", Agenda.timeLabel(-1, -1))
        assertEquals("23:59", Agenda.timeLabel(99, 99))
    }
}
