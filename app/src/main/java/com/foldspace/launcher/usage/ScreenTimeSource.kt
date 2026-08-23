package com.foldspace.launcher.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import java.util.Calendar

/**
 * Today's screen time, read on demand.
 *
 * `PACKAGE_USAGE_STATS` has been declared since V0.1 and only ever fed the
 * smart dock's recency scores; the numbers a person might actually want to see
 * about their own day were never surfaced. Microsoft Launcher puts them in its
 * feed, and it is the one part of that feed that needs no account.
 *
 * Read when the page is looked at, never on a timer (§12.1), and nothing is
 * kept: the platform already stores this, and a second copy would be a second
 * thing to leak.
 */
class ScreenTimeSource(
    private val context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    fun today(): ScreenTimeSummary {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return ScreenTimeSummary()

        val now = clock()
        val start = startOfDay(now)

        val stats = runCatching {
            manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now)
        }.getOrNull().orEmpty()

        val entries = stats.mapNotNull { entry ->
            entry.totalTimeInForeground
                .takeIf { it > 0L }
                ?.let { AppUsage(entry.packageName, it) }
        }

        return ScreenTime.summarise(entries, unlocksSince(manager, start, now))
    }

    /**
     * Unlocks, counted from the keyguard being dismissed.
     *
     * `KEYGUARD_HIDDEN` rather than `SCREEN_INTERACTIVE`: the screen comes on
     * for every notification, and counting those would report a number several
     * times the truth.
     */
    private fun unlocksSince(manager: UsageStatsManager, start: Long, now: Long): Int =
        runCatching {
            val events = manager.queryEvents(start, now)
            val event = UsageEvents.Event()
            var count = 0
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.KEYGUARD_HIDDEN) count++
            }
            count
        }.getOrDefault(0)

    private fun startOfDay(now: Long): Long = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
