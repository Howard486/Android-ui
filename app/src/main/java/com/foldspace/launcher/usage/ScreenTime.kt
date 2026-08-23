package com.foldspace.launcher.usage

/** One app's foreground time today. */
data class AppUsage(val packageName: String, val millis: Long)

/** Today, reduced to the three numbers worth showing. */
data class ScreenTimeSummary(
    val totalMillis: Long = 0L,
    val unlocks: Int = 0,
    val top: List<AppUsage> = emptyList(),
) {
    val isEmpty: Boolean get() = totalMillis <= 0L && unlocks <= 0 && top.isEmpty()
}

/**
 * Reduces a day's raw usage into a card.
 *
 * Pure, and separated from the query, because this is the part that can be
 * wrong in ways nobody notices: a total that double-counts, a top-three that
 * reorders itself between refreshes, a duration that reads "0 小時 90 分".
 *
 * §16.1 still applies. Nothing here is stored — the summary is derived on
 * demand from the platform's own records and dropped again.
 */
object ScreenTime {

    const val DEFAULT_TOP_N = 3

    fun summarise(
        entries: List<AppUsage>,
        unlocks: Int,
        topN: Int = DEFAULT_TOP_N,
    ): ScreenTimeSummary {
        // One package can appear several times in a day's stats, once per
        // interval bucket. Summing rather than taking the first is the
        // difference between a real total and an arbitrary sample.
        val merged = LinkedHashMap<String, Long>()
        for (entry in entries) {
            if (entry.millis <= 0L) continue
            merged.merge(entry.packageName, entry.millis, Long::plus)
        }

        val total = merged.values.sum()
        val top = merged.entries
            // Package name breaks ties, so two apps with identical time do not
            // swap places between refreshes.
            .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .take(topN.coerceAtLeast(0))
            .map { AppUsage(it.key, it.value) }

        return ScreenTimeSummary(total, unlocks.coerceAtLeast(0), top)
    }

    /**
     * "2 小時 14 分", "48 分", "不到 1 分".
     *
     * Minutes never exceed 59, and a nonzero duration never renders as zero —
     * "0 分" for forty seconds of use reads as a bug.
     */
    fun formatDuration(millis: Long): String {
        if (millis <= 0L) return "0 分"
        val totalMinutes = millis / 60_000L
        if (totalMinutes <= 0L) return "不到 1 分"
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours <= 0L -> "$minutes 分"
            minutes <= 0L -> "$hours 小時"
            else -> "$hours 小時 $minutes 分"
        }
    }
}
