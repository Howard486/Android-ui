package com.foldspace.launcher.notifications

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.foldspace.launcher.spaces.NotificationTier

/**
 * §10.3 — the "cheap rule classification" that runs on *every* posted
 * notification. It must stay allocation-light and string-comparison-only:
 * this is the hot path, and §12.4 forbids inference here.
 *
 * Nano's batch pass (§10.3, second line) can later upgrade a tier; that is
 * what `NotificationItem.refined` records.
 */
object NotificationClassifier {

    /**
     * Packages whose notifications are almost always a login approval or a
     * one-time code. Getting these to `Now` without waiting for a model is the
     * single highest-value rule in the layer.
     */
    private val securityPackages = setOf(
        "com.azure.authenticator",
        "com.google.android.apps.authenticator2",
        "com.duosecurity.duomobile",
        "com.okta.android.auth",
        "com.symantec.vip.access",
        "com.rsa.securid",
    )

    private val workPackages = setOf(
        "com.microsoft.teams",
        "com.microsoft.office.outlook",
        "com.slack",
        "com.atlassian.android.jira.core",
        "us.zoom.videomeetings",
        "com.google.android.calendar",
        "com.microsoft.todos",
    )

    private val personalPackages = setOf(
        "jp.naver.line.android",
        "com.whatsapp",
        "com.facebook.orca",
        "org.telegram.messenger",
        "com.instagram.android",
    )

    /** Substrings that mark something as needing action *right now*. */
    private val nowKeywords = listOf(
        "verification", "verify", "sign-in", "sign in", "login", "log in",
        "approve", "authenticate", "one-time", "otp", "security code",
        "驗證", "登入", "核准", "驗證碼", "安全性",
        "starting now", "starts in", "即將開始", "會議開始",
    )

    private val noiseKeywords = listOf(
        "sale", "discount", "% off", "promo", "coupon", "deal",
        "recommended for you", "new episode", "trending",
        "優惠", "折扣", "特價", "推薦", "限時",
    )

    fun classify(
        sbn: StatusBarNotification,
        analyseContent: Boolean,
    ): Pair<NotificationCategory, NotificationTier> {
        val category = categoryOf(sbn.packageName)
        val tier = tierOf(sbn, category, analyseContent)
        return category to tier
    }

    private fun categoryOf(packageName: String): NotificationCategory = when {
        packageName in securityPackages -> NotificationCategory.Security
        packageName in workPackages -> NotificationCategory.Work
        packageName in personalPackages -> NotificationCategory.Personal
        else -> NotificationCategory.Other
    }

    private fun tierOf(
        sbn: StatusBarNotification,
        category: NotificationCategory,
        analyseContent: Boolean,
    ): NotificationTier {
        val notification = sbn.notification

        // Ongoing notifications are media controls, navigation, downloads —
        // present, but never something the user must act on.
        if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) {
            return NotificationTier.Info
        }

        // A security prompt outranks everything else, and does so from the
        // package alone — no content reading required, which matters because
        // these are exactly the notifications a user may have marked private.
        if (category == NotificationCategory.Security) return NotificationTier.Now

        // §10.4 — the user asked us not to look at this app's content, so the
        // decision has to come from structure alone.
        if (!analyseContent) {
            return if (notification.actions?.isNotEmpty() == true) {
                NotificationTier.Action
            } else {
                NotificationTier.Info
            }
        }

        val text = buildString {
            append(notification.extras.getCharSequence(Notification.EXTRA_TITLE) ?: "")
            append(' ')
            append(notification.extras.getCharSequence(Notification.EXTRA_TEXT) ?: "")
        }.lowercase()

        if (nowKeywords.any { it in text }) return NotificationTier.Now
        if (noiseKeywords.any { it in text }) return NotificationTier.Noise

        // A reply action is the clearest structural marker of "someone is
        // waiting on you" that exists without understanding the text.
        val hasRemoteInput = notification.actions?.any { it.remoteInputs?.isNotEmpty() == true }
        if (hasRemoteInput == true) return NotificationTier.Action

        return when {
            notification.priority >= Notification.PRIORITY_HIGH -> NotificationTier.Action
            category == NotificationCategory.Other -> NotificationTier.Noise
            else -> NotificationTier.Info
        }
    }
}
