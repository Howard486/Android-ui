package com.foldspace.launcher.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.util.Calendar

/**
 * Today's events, from the device's own calendar provider.
 *
 * The zero-setup route. Whatever Outlook, Samsung Calendar or Google Calendar
 * syncs into the provider is here already: no app registration, no token, no
 * network, and it works with the aeroplane mode on. Microsoft Graph remains
 * the upgrade path, and the only path for To Do.
 *
 * Read when the page is looked at, never on a timer (§12.1 rule 4), and
 * nothing is stored — the provider is the store.
 */
class DeviceCalendarSource(
    private val context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    fun today(): AgendaSummary {
        if (!hasPermission()) return AgendaSummary()
        val now = clock()
        val endOfToday = startOfDay(now) + DAY_MS
        return Agenda.summarise(
            events = runCatching { read(now) }.getOrDefault(emptyList()),
            now = now,
            endOfToday = endOfToday,
        )
    }

    /**
     * Every calendar the provider holds, whether or not a calendar app draws
     * it.
     *
     * Note what is *not* here: a `VISIBLE = 1` filter. The events query has
     * never had one either, so a calendar hidden inside Samsung Calendar still
     * reaches this page — hiding it there is a decision about that app's grid,
     * not about whether the meeting is happening.
     */
    fun calendars(): List<CalendarAccount> {
        if (!hasPermission()) return emptyList()
        return runCatching { readCalendars() }.getOrDefault(emptyList())
    }

    private fun readCalendars(): List<CalendarAccount> {
        val cursor = context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            CALENDAR_PROJECTION,
            null,
            null,
            null,
        ) ?: return emptyList()

        val accounts = mutableListOf<CalendarAccount>()
        cursor.use {
            while (it.moveToNext()) {
                accounts += CalendarAccount(
                    displayName = it.getString(CAL_DISPLAY_NAME).orEmpty(),
                    accountName = it.getString(CAL_ACCOUNT_NAME).orEmpty(),
                    accountType = it.getString(CAL_ACCOUNT_TYPE).orEmpty(),
                    visible = it.getInt(CAL_VISIBLE) == 1,
                    syncing = it.getInt(CAL_SYNC_EVENTS) == 1,
                )
            }
        }
        return accounts
    }

    /** `HH:mm` for an instant, in the device's own zone. */
    fun timeLabelFor(millis: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = millis }
        return Agenda.timeLabel(
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
        )
    }

    private fun read(now: Long): List<AgendaEvent> {
        val start = startOfDay(now)
        // Two days, not one: "what is left today" answers the next hour, and
        // "what is tomorrow" is the thing you actually plan an evening around.
        val end = start + DAY_MS * DAYS

        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(start.toString())
            .appendPath(end.toString())
            .build()

        val cursor = context.contentResolver.query(
            uri,
            PROJECTION,
            null,
            null,
            CalendarContract.Instances.BEGIN + " ASC",
        ) ?: return emptyList()

        val events = mutableListOf<AgendaEvent>()
        cursor.use {
            while (it.moveToNext() && events.size < MAX_EVENTS) {
                // A declined invitation is not on your day.
                if (it.getInt(INDEX_STATUS) == CalendarContract.Instances.STATUS_CANCELED) continue
                val title = it.getString(INDEX_TITLE)?.takeIf { text -> text.isNotBlank() }
                    ?: UNTITLED
                events += AgendaEvent(
                    id = it.getLong(INDEX_ID),
                    title = title,
                    startMillis = it.getLong(INDEX_BEGIN),
                    endMillis = it.getLong(INDEX_END),
                    allDay = it.getInt(INDEX_ALL_DAY) == 1,
                    location = it.getString(INDEX_LOCATION)?.takeIf { text -> text.isNotBlank() },
                    calendarName = it.getString(INDEX_CALENDAR)?.takeIf { text -> text.isNotBlank() },
                    // Location first: that is where Outlook puts a Teams
                    // link. Only the URL is kept — the description it may
                    // have come from holds dial-in numbers, passcodes and an
                    // attendee list, and none of that reaches the screen.
                    joinUrl = MeetingLink.find(
                        it.getString(INDEX_LOCATION),
                        it.getString(INDEX_DESCRIPTION),
                        title,
                    )?.first,
                )
            }
        }
        return events
    }

    private fun startOfDay(now: Long): Long = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val DAYS = 2
        const val MAX_EVENTS = 40
        const val UNTITLED = "(未命名)"

        val PROJECTION = arrayOf(
            CalendarContract.Instances._ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.STATUS,
            CalendarContract.Instances.DESCRIPTION,
        )
        const val INDEX_ID = 0
        const val INDEX_TITLE = 1
        const val INDEX_BEGIN = 2
        const val INDEX_END = 3
        const val INDEX_ALL_DAY = 4
        const val INDEX_LOCATION = 5
        const val INDEX_CALENDAR = 6
        const val INDEX_STATUS = 7
        const val INDEX_DESCRIPTION = 8

        val CALENDAR_PROJECTION = arrayOf(
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.SYNC_EVENTS,
        )
        const val CAL_DISPLAY_NAME = 0
        const val CAL_ACCOUNT_NAME = 1
        const val CAL_ACCOUNT_TYPE = 2
        const val CAL_VISIBLE = 3
        const val CAL_SYNC_EVENTS = 4
    }
}
