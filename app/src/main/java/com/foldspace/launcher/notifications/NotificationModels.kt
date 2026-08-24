package com.foldspace.launcher.notifications

import com.foldspace.launcher.spaces.NotificationTier

/** §10.1 — the broad bucket used for Space-based ranking. */
enum class NotificationCategory { Work, Personal, Security, Other }

/**
 * One live notification, as FoldSpace holds it.
 *
 * §10.4 is the reason [title] and [body] are nullable and never written to
 * disk: this object lives in memory only, and is dropped the moment the
 * system says the notification was removed. Anything that must survive is the
 * classification label, not the text (§16.1).
 */
data class NotificationItem(
    val key: String,
    val packageName: String,
    val title: String?,
    val body: String?,
    val category: NotificationCategory,
    val tier: NotificationTier,
    val postedAt: Long,
    val hasActions: Boolean,
    val isOngoing: Boolean,
    val isClearable: Boolean,
    /** True once a classifier other than the cheap rule pass has seen it. */
    val refined: Boolean = false,
)

/** §10.1 — per-app badge counts, the cheapest thing the home screen consumes. */
data class NotificationSummary(
    val items: List<NotificationItem> = emptyList(),
    val listenerConnected: Boolean = false,
) {
    val badgeCounts: Map<String, Int> =
        items.filter { it.isClearable && !it.isOngoing }
            .groupingBy { it.packageName }
            .eachCount()

    fun countFor(packageName: String): Int = badgeCounts[packageName] ?: 0

    fun tiered(tier: NotificationTier): List<NotificationItem> =
        items.filter { it.tier == tier }.sortedByDescending { it.postedAt }

    /**
     * What is actually waiting on you: Now and Action, nothing below.
     *
     * The header used to show `items.size`, which on a real phone is a number
     * like 83 — a figure nobody can act on and nobody can clear. Counting the
     * two tiers that mean "this needs you" is the only version of that badge
     * worth glancing at, and the tiers have existed since V0.1 while only
     * ever being used for ordering.
     */
    val actionableCount: Int
        get() = items.count {
            it.tier == NotificationTier.Now || it.tier == NotificationTier.Action
        }

    /** §10.1 "Important notification card" — what the Compact home surfaces. */
    fun mostUrgent(limit: Int = 3): List<NotificationItem> =
        items.sortedWith(
            compareBy<NotificationItem> { it.tier.ordinal }.thenByDescending { it.postedAt },
        ).take(limit)
}
