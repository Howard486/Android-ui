package com.foldspace.launcher.work

import com.foldspace.launcher.notifications.NotificationItem
import com.foldspace.launcher.notifications.NotificationSummary
import com.foldspace.launcher.spaces.NotificationTier

/** One thing waiting on the user, derived from a work app's notifications. */
data class WorkItem(
    val key: String,
    val packageName: String,
    val title: String,
    val detail: String?,
    val tier: NotificationTier,
    val postedAt: Long,
    val actionable: Boolean,
)

/** Grouped by the app they came from, because that is where acting on them happens. */
data class WorkItemGroup(
    val packageName: String,
    val displayName: String,
    val items: List<WorkItem>,
)

data class WorkItemsState(
    val groups: List<WorkItemGroup> = emptyList(),
    val listenerConnected: Boolean = false,
) {
    val total: Int get() = groups.sumOf { it.items.size }

    val needsAction: Int
        get() = groups.sumOf { group ->
            group.items.count { it.tier == NotificationTier.Now || it.tier == NotificationTier.Action }
        }
}

/**
 * 工作 mode's work-item list.
 *
 * **This reads notifications, not mailboxes.** Outlook for Android exposes no
 * public interface for reading mail or tasks — no content provider, no intent
 * — so the only on-device source is what those apps chose to notify about.
 * That has a consequence the UI has to state rather than hide: an item that
 * never raised a notification, or whose notification was dismissed, is not
 * here. Real inbox and To Do data would mean Microsoft Graph and a sign-in,
 * which is deliberately out of scope for this build.
 */
object WorkItemsDeriver {

    /** Apps whose notifications count as work. */
    private val workPackages = mapOf(
        "com.microsoft.office.outlook" to "Outlook",
        "com.microsoft.teams" to "Teams",
        "com.microsoft.todos" to "To Do",
        "com.microsoft.planner" to "Planner",
        "com.google.android.calendar" to "行事曆",
        "com.microsoft.sharepoint" to "SharePoint",
        "com.slack" to "Slack",
        "com.atlassian.android.jira.core" to "Jira",
        "us.zoom.videomeetings" to "Zoom",
    )

    fun isWorkPackage(packageName: String): Boolean = packageName in workPackages

    fun derive(summary: NotificationSummary): WorkItemsState {
        if (!summary.listenerConnected) return WorkItemsState(listenerConnected = false)

        val groups = summary.items
            .filter { isWorkPackage(it.packageName) }
            // Ongoing notifications are a call in progress or a sync banner:
            // present, but not something waiting on a reply.
            .filterNot { it.isOngoing }
            .groupBy { it.packageName }
            .map { (packageName, items) ->
                WorkItemGroup(
                    packageName = packageName,
                    displayName = workPackages[packageName] ?: packageName.substringAfterLast('.'),
                    items = items
                        .map(::toWorkItem)
                        .sortedWith(
                            compareBy<WorkItem> { it.tier.ordinal }
                                .thenByDescending { it.postedAt },
                        ),
                )
            }
            .sortedBy { group -> group.items.firstOrNull()?.tier?.ordinal ?: Int.MAX_VALUE }

        return WorkItemsState(groups = groups, listenerConnected = true)
    }

    private fun toWorkItem(item: NotificationItem) = WorkItem(
        key = item.key,
        packageName = item.packageName,
        // §10.4 — an app the user excluded from content analysis arrives with
        // no text at all, so the row says that rather than showing a blank.
        title = item.title?.takeIf { it.isNotBlank() } ?: "內容未分析",
        detail = item.body?.takeIf { it.isNotBlank() },
        tier = item.tier,
        postedAt = item.postedAt,
        actionable = item.hasActions,
    )
}
