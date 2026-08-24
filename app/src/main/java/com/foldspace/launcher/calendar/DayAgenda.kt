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
    /**
     * The join link, if this is an online meeting.
     *
     * Extracted from the event and kept alone — the description it came out of
     * is not carried. A meeting invitation body routinely holds a dial-in
     * number, a passcode and an attendee list, and none of that has any
     * business on a home screen.
     */
    val joinUrl: String? = null,
) {
    val category: AgendaCategory get() = Agenda.categorise(title)
}

/** Today, ordered, with the next thing picked out. */
data class AgendaSummary(
    val events: List<AgendaEvent> = emptyList(),
    val nextIndex: Int = -1,
    /** The same events, split at midnight. Tomorrow may be empty. */
    val tomorrow: List<AgendaEvent> = emptyList(),
) {
    val isEmpty: Boolean get() = events.isEmpty() && tomorrow.isEmpty()
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
    fun summarise(
        events: List<AgendaEvent>,
        now: Long,
        /**
         * Midnight tonight. Anything at or after it is tomorrow's.
         *
         * Passed in rather than computed: the boundary depends on the device's
         * time zone, and a pure function has no business asking about one.
         */
        endOfToday: Long = Long.MAX_VALUE,
    ): AgendaSummary {
        if (events.isEmpty()) return AgendaSummary()

        val split = events.partition { it.startMillis < endOfToday }
        val tomorrow = split.second.sortedWith(
            compareBy<AgendaEvent> { it.allDay }.thenBy { it.startMillis }.thenBy { it.id },
        )

        val ordered = split.first.sortedWith(
            compareBy<AgendaEvent> { it.allDay }
                .thenBy { it.startMillis }
                // Ties break on id so the list cannot reshuffle between reads.
                .thenBy { it.id },
        )
        // "Next" means not finished yet, so a meeting you are sitting in is
        // still the next thing — which is what someone glancing at a home
        // screen wants to see.
        val index = ordered.indexOfFirst { !it.allDay && it.endMillis > now }
        return AgendaSummary(ordered, index, tomorrow)
    }

    /** `09:05`, always four digits and a colon. */
    fun timeLabel(hour: Int, minute: Int): String =
        "%02d:%02d".format(hour.coerceIn(0, 23), minute.coerceIn(0, 59))

    private val MEETING_WORDS = listOf("meeting", "sync", "standup", "1:1", "會議", "會", "面談")
    private val FOCUS_WORDS = listOf("focus", "deep work", "專注", "工作時間")
    private val TRAVEL_WORDS = listOf("flight", "train", "travel", "航班", "出差", "交通")
}

/**
 * Finds an online-meeting link in an event.
 *
 * This is what makes Teams meetings work without touching a Microsoft
 * account at all: a Teams meeting *is* a calendar event, and its join URL is
 * in the invitation. Nothing here authenticates, fetches or asks anyone's
 * permission — the link is already on the device.
 */
object MeetingLink {

    /** Hosts worth offering a button for, longest-lived first. */
    private val HOSTS = listOf(
        "teams.microsoft.com" to "Teams",
        "teams.live.com" to "Teams",
        "zoom.us" to "Zoom",
        "meet.google.com" to "Meet",
        "webex.com" to "Webex",
    )

    /**
     * The first join link in any of [texts], with the service it belongs to.
     *
     * Scans in the order given, so a link in the location field — which is
     * where Outlook puts it for a Teams meeting — beats one buried in the
     * body.
     */
    fun find(vararg texts: String?): Pair<String, String>? {
        for (text in texts) {
            if (text.isNullOrBlank()) continue
            val match = URL.find(text) ?: continue
            val url = match.value.trimEnd('.', ',', ')', '>', '"', ';')
            val host = HOSTS.firstOrNull { (needle, _) -> needle in url.lowercase() } ?: continue
            return url to host.second
        }
        return null
    }

    /**
     * Deliberately narrow: `https` only, and stopping at whitespace or a
     * closing bracket. A calendar body is arbitrary text from whoever sent
     * the invitation, and a greedy pattern over it is how a launcher ends up
     * offering to open something nobody meant it to.
     */
    private val URL = Regex("""https://[^\s<>"']+""")
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
