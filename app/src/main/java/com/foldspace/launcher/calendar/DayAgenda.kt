package com.foldspace.launcher.calendar

/** What an event is, coarsely, without repeating what it says. */
enum class AgendaCategory(val key: String, val label: String) {
    Meeting("meeting", "會議"),
    Focus("focus", "專注"),
    Travel("travel", "交通"),
    Busy("busy", "行程"),
    ;

    companion object {
        fun fromKey(key: String?): AgendaCategory =
            entries.firstOrNull { it.key == key } ?: Busy
    }
}

/**
 * One entry on today's agenda.
 *
 * [title] is carried, because the page can show it — but only after the user
 * asks for it. See [AgendaEvent.collapsedLabel].
 */
data class AgendaEvent(
    val id: Long,
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val location: String?,
    val calendarName: String?,
) {
    val category: AgendaCategory get() = Agenda.categorise(title)
}

/** Today, ordered, with the next thing picked out. */
data class AgendaSummary(
    val events: List<AgendaEvent> = emptyList(),
    val nextIndex: Int = -1,
) {
    val isEmpty: Boolean get() = events.isEmpty()
    val next: AgendaEvent? get() = events.getOrNull(nextIndex)
    val remaining: Int get() = if (nextIndex < 0) 0 else events.size - nextIndex
}

/**
 * Today's calendar, read from the device rather than from an account.
 *
 * This is the whole point of the route: `READ_CALENDAR` is already granted,
 * the provider already holds whatever Outlook, Samsung Calendar or Google
 * Calendar syncs into it, and none of it needs an app registration, a network
 * round trip or a token. What it cannot offer is To Do — Microsoft's tasks
 * live only behind Graph.
 *
 * The titles are the sensitive part, so the shaping of them lives here, pure
 * and tested, rather than being decided in a composable.
 */
object Agenda {

    /**
     * A coarse label from a title, which is then not needed again.
     *
     * The same keyword list the context engine has used since the calendar
     * signal was added — kept in one place now, so the two cannot disagree
     * about what counts as a meeting.
     */
    fun categorise(title: String?): AgendaCategory {
        val text = title.orEmpty().lowercase()
        return when {
            MEETING_WORDS.any { it in text } -> AgendaCategory.Meeting
            FOCUS_WORDS.any { it in text } -> AgendaCategory.Focus
            TRAVEL_WORDS.any { it in text } -> AgendaCategory.Travel
            else -> AgendaCategory.Busy
        }
    }

    /**
     * Orders the day and marks what is next.
     *
     * All-day entries sort last rather than first: they are true of the whole
     * day and say nothing about what happens at three o'clock, which is the
     * same reason the context engine ignores them.
     */
    fun summarise(events: List<AgendaEvent>, now: Long): AgendaSummary {
        if (events.isEmpty()) return AgendaSummary()

        val ordered = events.sortedWith(
            compareBy<AgendaEvent> { it.allDay }
                .thenBy { it.startMillis }
                // Ties break on id so the list cannot reshuffle between reads.
                .thenBy { it.id },
        )
        // "Next" means not finished yet, so a meeting you are sitting in is
        // still the next thing — which is what someone glancing at a home
        // screen wants to see.
        val index = ordered.indexOfFirst { !it.allDay && it.endMillis > now }
        return AgendaSummary(ordered, index)
    }

    /** `09:05`, always four digits and a colon. */
    fun timeLabel(hour: Int, minute: Int): String =
        "%02d:%02d".format(hour.coerceIn(0, 23), minute.coerceIn(0, 59))

    private val MEETING_WORDS = listOf("meeting", "sync", "standup", "1:1", "會議", "會", "面談")
    private val FOCUS_WORDS = listOf("focus", "deep work", "專注", "工作時間")
    private val TRAVEL_WORDS = listOf("flight", "train", "travel", "航班", "出差", "交通")
}

/**
 * What the row says before the user asks for more.
 *
 * The default is deliberately not the title. A launcher's home screen is the
 * one surface that is visible to whoever is standing next to you, and
 * "Q3 budget review with Acme" is not something to put there by default. The
 * category and the time are enough to plan around; the title is one tap away.
 */
fun AgendaEvent.collapsedLabel(): String = category.label
