package com.foldspace.launcher.context.signals

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.foldspace.launcher.context.ContextEvent

/**
 * §7 / §12.1 rule 4 — UsageStats is read only when the launcher resumes, and
 * then only if [THROTTLE_MS] has elapsed. This is the single most expensive
 * signal in the table, so it is the one with an explicit throttle.
 *
 * §16.1: the aggregate score is kept, the raw event history is not.
 */
class UsageSignalSource(
    private val context: Context,
    private val onEvent: (ContextEvent) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private var lastReadAt = 0L

    /** §21.1 — with no Usage Access granted, the Smart Dock degrades rather than breaking. */
    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Call from the launcher's onResume. Cheap no-op when throttled. */
    fun refreshIfStale(force: Boolean = false) {
        val now = clock()
        if (!force && now - lastReadAt < THROTTLE_MS) return
        if (!hasUsageAccess()) {
            onEvent(ContextEvent.UsageRefreshed(emptyMap()))
            return
        }
        lastReadAt = now
        onEvent(ContextEvent.UsageRefreshed(readScores(now)))
    }

    private fun readScores(now: Long): Map<String, Float> {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()

        val stats = runCatching {
            manager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - WINDOW_MS,
                now,
            )
        }.getOrNull().orEmpty()

        if (stats.isEmpty()) return emptyMap()

        // Recency-weighted foreground time: an app used for an hour yesterday
        // should not outrank one used for ten minutes just now, because the
        // dock is answering "what next", not "what most".
        val raw = mutableMapOf<String, Double>()
        for (entry in stats) {
            val foreground = entry.totalTimeInForeground
            if (foreground <= 0L) continue
            val ageMs = (now - entry.lastTimeUsed).coerceAtLeast(0L)
            val recency = HALF_LIFE_MS.toDouble() / (HALF_LIFE_MS + ageMs)
            raw.merge(entry.packageName, foreground * recency, Double::plus)
        }

        val max = raw.values.maxOrNull() ?: return emptyMap()
        if (max <= 0.0) return emptyMap()

        return raw.entries
            .sortedByDescending { it.value }
            .take(MAX_TRACKED_APPS)
            .associate { it.key to (it.value / max).toFloat() }
    }

    private companion object {
        const val THROTTLE_MS = 15 * 60 * 1000L
        const val WINDOW_MS = 7 * 24 * 60 * 60 * 1000L
        const val HALF_LIFE_MS = 6 * 60 * 60 * 1000L

        /** Only ever enough to fill the smart dock plus drawer suggestions. */
        const val MAX_TRACKED_APPS = 12
    }
}
