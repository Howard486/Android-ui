package com.foldspace.launcher.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * §10 — the in-memory store the listener writes and the UI reads.
 *
 * Process-scoped and deliberately *only* in memory: §10.4 requires that
 * notification bodies are never persisted and are dropped when the
 * notification is removed. There is no DAO here on purpose.
 */
object NotificationRepository {

    private val _summary = MutableStateFlow(NotificationSummary())
    val summary: StateFlow<NotificationSummary> = _summary.asStateFlow()

    /** Keys whose Nano tier is still pending — §10.3's "unclassified batch". */
    private val pendingRefinement = linkedSetOf<String>()

    fun onListenerConnected(connected: Boolean) {
        _summary.update {
            if (connected) it.copy(listenerConnected = true)
            // Disconnected means we no longer have a truthful view, so the
            // list must go with it rather than showing stale badges.
            else NotificationSummary(listenerConnected = false)
        }
    }

    fun upsert(item: NotificationItem) {
        pendingRefinement += item.key
        _summary.update { current ->
            val without = current.items.filterNot { it.key == item.key }
            current.copy(items = without + item)
        }
    }

    fun remove(key: String) {
        pendingRefinement -= key
        _summary.update { current ->
            current.copy(items = current.items.filterNot { it.key == key })
        }
    }

    fun replaceAll(items: List<NotificationItem>) {
        pendingRefinement.clear()
        pendingRefinement += items.map { it.key }
        _summary.update { it.copy(items = items) }
    }

    fun clear() {
        pendingRefinement.clear()
        _summary.update { it.copy(items = emptyList()) }
    }

    /** §10.3 — what the launcher hands to a batch pass when it next resumes. */
    fun unclassifiedBatch(limit: Int = 24): List<NotificationItem> {
        val current = _summary.value.items.associateBy { it.key }
        return pendingRefinement.mapNotNull(current::get)
            .filterNot { it.refined }
            .take(limit)
    }

    fun applyRefinedTiers(tiers: Map<String, com.foldspace.launcher.spaces.NotificationTier>) {
        if (tiers.isEmpty()) return
        pendingRefinement -= tiers.keys
        _summary.update { current ->
            current.copy(
                items = current.items.map { item ->
                    tiers[item.key]?.let { item.copy(tier = it, refined = true) } ?: item
                },
            )
        }
    }
}
