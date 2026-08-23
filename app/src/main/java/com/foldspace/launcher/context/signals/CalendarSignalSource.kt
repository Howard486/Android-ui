package com.foldspace.launcher.context.signals

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import com.foldspace.launcher.context.ContextEvent
import java.util.concurrent.TimeUnit

/**
 * §7 — what the calendar says is happening now.
 *
 * `READ_CALENDAR` has been declared since V0.1 and nothing ever read the
 * provider, so `ContextSnapshot.calendarCategory` was permanently null and
 * every rule that could have used it was inert. A sensitive permission
 * declared and never used is worse than one not declared: the user is asked
 * to trust something that buys them nothing.
 *
 * Reads only the current instance's *availability and title keywords*, never
 * attendees, location or description. The category is a coarse label the rule
 * engine can match; the event's text never reaches the UI (§8.4's rule
 * applied to the calendar as well as to the model).
 */
class CalendarSignalSource(
    private val context: Context,
    private val onEvent: (ContextEvent) -> Unit,
) {

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Reads the calendar once, if allowed. Called from [onResumed]'s throttled
     * path only — §12.1 rule 4 forbids polling, and a launcher that queried
     * the provider on a timer would be exactly that.
     */
    fun refresh(nowMillis: Long) {
        if (!hasPermission()) {
            onEvent(ContextEvent.CalendarChanged(null))
            return
        }

        val category = runCatching { readCurrentCategory(nowMillis) }.getOrNull()
        onEvent(ContextEvent.CalendarChanged(category))
    }

    private fun readCurrentCategory(nowMillis: Long): String? {
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        // A window rather than an instant: an event starting in ten minutes is
        // as much "in a meeting" as one already running.
        val from = nowMillis - TimeUnit.MINUTES.toMillis(LOOKBACK_MINUTES)
        val to = nowMillis + TimeUnit.MINUTES.toMillis(LOOKAHEAD_MINUTES)
        builder.appendPath(from.toString())
        builder.appendPath(to.toString())

        val cursor: Cursor = context.contentResolver.query(
            builder.build(),
            PROJECTION,
            null,
            null,
            CalendarContract.Instances.BEGIN + " ASC",
        ) ?: return null

        cursor.use {
            while (it.moveToNext()) {
                val allDay = it.getInt(INDEX_ALL_DAY) == 1
                // An all-day event says nothing about what you are doing at
                // 3pm, and treating one as "busy" would pin the context for a
                // whole day on a birthday reminder.
                if (allDay) continue
                val availability = it.getInt(INDEX_AVAILABILITY)
                if (availability == CalendarContract.Instances.AVAILABILITY_FREE) continue
                return categorise(it.getString(INDEX_TITLE))
            }
        }
        return null
    }

    /**
     * A coarse label, never the title itself.
     *
     * The title is matched against a small keyword list and then discarded.
     * Passing "Q3 budget review with Acme" into a rule would put a private
     * calendar entry into persisted settings.
     */
    private fun categorise(title: String?): String {
        val text = title.orEmpty().lowercase()
        return when {
            MEETING_WORDS.any { it in text } -> CATEGORY_MEETING
            FOCUS_WORDS.any { it in text } -> CATEGORY_FOCUS
            TRAVEL_WORDS.any { it in text } -> CATEGORY_TRAVEL
            else -> CATEGORY_BUSY
        }
    }

    private companion object {
        const val LOOKBACK_MINUTES = 5L
        const val LOOKAHEAD_MINUTES = 15L

        const val CATEGORY_MEETING = "meeting"
        const val CATEGORY_FOCUS = "focus"
        const val CATEGORY_TRAVEL = "travel"
        const val CATEGORY_BUSY = "busy"

        val MEETING_WORDS = listOf("meeting", "sync", "standup", "1:1", "會議", "會", "面談")
        val FOCUS_WORDS = listOf("focus", "deep work", "專注", "工作時間")
        val TRAVEL_WORDS = listOf("flight", "train", "travel", "航班", "出差", "交通")

        val PROJECTION = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.AVAILABILITY,
        )
        const val INDEX_TITLE = 0
        const val INDEX_ALL_DAY = 1
        const val INDEX_AVAILABILITY = 2
    }
}
