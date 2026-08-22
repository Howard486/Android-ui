package com.foldspace.launcher.notifications

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.foldspace.launcher.settings.PrivacyPreferences

/**
 * §10 Notification Intelligence Layer.
 *
 * The service does the cheapest possible work per callback — one rule pass,
 * one map write — because it runs on every notification the device posts, for
 * every app, whether or not FoldSpace is even visible. Anything heavier is
 * deferred to the batch pass the launcher runs when it resumes (§10.3).
 */
class FoldSpaceNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationRepository.onListenerConnected(true)
        // Seed with whatever is already on screen; without this the badges are
        // empty until the next notification happens to arrive.
        runCatching { activeNotifications }
            .getOrNull()
            ?.mapNotNull { it.toItem() }
            ?.let(NotificationRepository::replaceAll)
    }

    override fun onListenerDisconnected() {
        NotificationRepository.onListenerConnected(false)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.toItem()?.let(NotificationRepository::upsert)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // §10.4 — the body cache goes when the notification does.
        sbn?.key?.let(NotificationRepository::remove)
    }

    private fun StatusBarNotification.toItem(): NotificationItem? {
        val notification = notification ?: return null

        // Group summaries duplicate their children; counting both double-counts
        // every badge on messaging apps.
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null

        val analyseContent = PrivacyPreferences.analysesContentOf(packageName)
        val (category, tier) = NotificationClassifier.classify(this, analyseContent)

        return NotificationItem(
            key = key,
            packageName = packageName,
            title = if (analyseContent) {
                notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            } else {
                null
            },
            body = if (analyseContent) {
                notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            } else {
                null
            },
            category = category,
            tier = tier,
            postedAt = postTime,
            hasActions = notification.actions?.isNotEmpty() == true,
            isOngoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0,
            isClearable = isClearable,
        )
    }

    companion object {
        /**
         * §21.1 — the launcher must not crash or nag when access is absent, so
         * every consumer checks this first.
         */
        fun isAccessGranted(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ).orEmpty()
            if (enabled.isBlank()) return false

            val ours = ComponentName(context, FoldSpaceNotificationListener::class.java)
            return enabled.split(':').any { entry ->
                ComponentName.unflattenFromString(entry) == ours
            }
        }

        /** §16 — the settings screen shows the purpose string before sending the user here. */
        fun settingsIntent() =
            android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
