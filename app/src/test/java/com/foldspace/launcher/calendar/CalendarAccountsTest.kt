package com.foldspace.launcher.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Can it read my Outlook calendar?" has one honest answer — yes, once Outlook
 * syncs it into the device provider — and the page can only give it if it can
 * recognise such a calendar among the accounts. Which strings count is a list
 * of vendor names, and the only other way to check one is wrong is to install
 * the app and look.
 */
class CalendarAccountsTest {

    private fun account(
        type: String = "com.google",
        name: String = "someone@gmail.com",
        syncing: Boolean = true,
        visible: Boolean = true,
        display: String = "行事曆",
    ) = CalendarAccount(display, name, type, visible, syncing)

    @Test
    fun `Outlook for Android is recognised by its own account type`() {
        assertTrue(account(type = "com.microsoft.office.outlook").isOutlook)
    }

    @Test
    fun `a work mailbox added through the system is Exchange, and counts`() {
        // The same mailbox, added a different way. It is the same thing to the
        // person asking, so it has to be the same answer.
        assertTrue(account(type = "com.samsung.android.exchange").isOutlook)
        assertTrue(account(type = "com.android.exchange").isOutlook)
        assertTrue(account(type = "com.microsoft.exchange").isOutlook)
    }

    @Test
    fun `a personal address counts even when the account type does not`() {
        assertTrue(account(type = "com.android.email", name = "me@outlook.com").isOutlook)
        assertTrue(account(type = "unknown", name = "me@HOTMAIL.COM").isOutlook)
    }

    @Test
    fun `an ordinary Google calendar is not Outlook`() {
        assertFalse(account().isOutlook)
        assertFalse(account(type = "LOCAL", name = "").isOutlook)
    }

    @Test
    fun `no calendars at all is a different answer from no Outlook`() {
        // An empty day means "nothing on" or "your work calendar was never
        // here", and the page has to say which.
        assertEquals(OutlookStatus.NoCalendars, CalendarAccounts.outlookStatus(emptyList()))
        assertEquals(OutlookStatus.Missing, CalendarAccounts.outlookStatus(listOf(account())))
    }

    @Test
    fun `present but not syncing is its own answer`() {
        val stale = account(type = "com.microsoft.office.outlook", syncing = false)
        assertEquals(OutlookStatus.NotSyncing, CalendarAccounts.outlookStatus(listOf(stale)))

        val live = account(type = "com.microsoft.office.outlook", syncing = true)
        assertEquals(OutlookStatus.Present, CalendarAccounts.outlookStatus(listOf(stale, live)))
    }

    @Test
    fun `a hidden calendar still counts, because its events are still read`() {
        // No VISIBLE filter has ever been applied to the events query. Hiding
        // a calendar inside Samsung Calendar is a decision about that app's
        // grid, not about whether the meeting is happening.
        val hidden = account(type = "com.microsoft.office.outlook", visible = false)
        assertEquals(OutlookStatus.Present, CalendarAccounts.outlookStatus(listOf(hidden)))
    }

    @Test
    fun `sources are named by address, deduplicated and stable`() {
        val accounts = listOf(
            account(name = "me@outlook.com", display = "行事曆"),
            account(name = "me@outlook.com", display = "生日"),
            account(name = "a@gmail.com"),
        )
        assertEquals(listOf("a@gmail.com", "me@outlook.com"), CalendarAccounts.labels(accounts))
    }

    @Test
    fun `a calendar with no address falls back to its own name`() {
        val local = account(type = "LOCAL", name = "", display = "我的行事曆")
        assertEquals(listOf("我的行事曆"), CalendarAccounts.labels(listOf(local)))
    }
}
