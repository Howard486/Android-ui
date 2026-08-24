package com.foldspace.launcher.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `events past midnight become tomorrow's`() {
        val midnight = 100_000L
        val summary = Agenda.summarise(
            listOf(event(1, midnight - 10), event(2, midnight + 10), event(3, midnight + 20)),
            now = 0,
            endOfToday = midnight,
        )
        assertEquals(listOf(1L), summary.events.map { it.id })
        assertEquals(listOf(2L, 3L), summary.tomorrow.map { it.id })
    }

    @Test
    fun `an event starting exactly at midnight belongs to tomorrow`() {
        val midnight = 100_000L
        val summary = Agenda.summarise(listOf(event(1, midnight)), now = 0, endOfToday = midnight)
        assertTrue(summary.events.isEmpty())
        assertEquals(listOf(1L), summary.tomorrow.map { it.id })
    }

    @Test
    fun `tomorrow is ordered the same way today is`() {
        val midnight = 100L
        val summary = Agenda.summarise(
            listOf(
                event(1, midnight + 500, allDay = true),
                event(2, midnight + 900),
                event(3, midnight + 200),
            ),
            now = 0,
            endOfToday = midnight,
        )
        assertEquals(listOf(3L, 2L, 1L), summary.tomorrow.map { it.id })
    }

    @Test
    fun `a day with nothing left today but something tomorrow is not empty`() {
        val summary = Agenda.summarise(listOf(event(1, 500)), now = 0, endOfToday = 100)
        assertTrue(summary.events.isEmpty())
        assertFalse(summary.isEmpty)
        assertNull(summary.next)
    }

    @Test
    fun `with no boundary given everything is today, as before`() {
        val summary = Agenda.summarise(listOf(event(1, 0), event(2, 999_999_999)), now = 0)
        assertEquals(2, summary.events.size)
        assertTrue(summary.tomorrow.isEmpty())
    }
}

class MeetingLinkTest {

    @Test
    fun `a Teams link in the location is found`() {
        val (url, host) = MeetingLink.find(
            "https://teams.microsoft.com/l/meetup-join/19%3ameeting_abc",
            null,
            "週會",
        )!!
        assertEquals("Teams", host)
        assertTrue(url.startsWith("https://teams.microsoft.com/"))
    }

    @Test
    fun `location wins over the body, which is where Outlook puts it`() {
        val (url, _) = MeetingLink.find(
            "https://teams.microsoft.com/first",
            "https://zoom.us/j/second",
        )!!
        assertEquals("https://teams.microsoft.com/first", url)
    }

    @Test
    fun `the other services people actually use are recognised`() {
        assertEquals("Zoom", MeetingLink.find("https://zoom.us/j/123")?.second)
        assertEquals("Meet", MeetingLink.find("https://meet.google.com/abc-defg-hij")?.second)
        assertEquals("Webex", MeetingLink.find("https://acme.webex.com/meet/x")?.second)
    }

    @Test
    fun `an ordinary link is not offered as a meeting`() {
        assertNull(MeetingLink.find("https://example.com/notes"))
        assertNull(MeetingLink.find("會議室 3B"))
        assertNull(MeetingLink.find(null, null, null))
        assertNull(MeetingLink.find(""))
    }

    @Test
    fun `only https is matched, so a plain-text host is not turned into a link`() {
        assertNull(MeetingLink.find("http://teams.microsoft.com/insecure"))
        assertNull(MeetingLink.find("teams.microsoft.com/l/meetup-join/x"))
    }

    @Test
    fun `trailing punctuation from prose is not part of the url`() {
        val (url, _) = MeetingLink.find("加入: https://teams.microsoft.com/l/x.")!!
        assertTrue(!url.endsWith("."))
        assertEquals("https://teams.microsoft.com/l/x", url)
    }

    @Test
    fun `a url wrapped in a bracket stops at the bracket`() {
        val (url, _) = MeetingLink.find("""<https://zoom.us/j/9>""")!!
        assertEquals("https://zoom.us/j/9", url)
    }

    @Test
    fun `only the url is carried, never the body it came from`() {
        val body = "撥入號碼 +886 2 1234 5678，密碼 998877\nhttps://teams.microsoft.com/l/x"
        val (url, _) = MeetingLink.find(null, body)!!
        assertEquals("https://teams.microsoft.com/l/x", url)
        assertTrue("998877" !in url)
        assertTrue("+886" !in url)
    }
}
