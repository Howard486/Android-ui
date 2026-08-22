package com.foldspace.launcher.spaces

/**
 * §3 — a Space is the user's current *situation*, not a home-screen page.
 * Each one owns its own dock, cards, notification policy and theme.
 */
enum class SpaceId(val key: String, val displayName: String) {
    Home("home", "Home"),
    Work("work", "Work"),
    Focus("focus", "Focus"),
    Media("media", "Media"),
    Travel("travel", "Travel"),
    Night("night", "Night"),
    ;

    companion object {
        fun fromKey(key: String?): SpaceId = entries.firstOrNull { it.key == key } ?: Home
    }
}

/**
 * §10.2 four-level notification model. Ordinal order is the ranking order —
 * `Now` first — so lists can sort on it directly.
 */
enum class NotificationTier(val displayName: String) {
    Now("現在處理"),
    Action("待回覆"),
    Info("知道即可"),
    Noise("低優先"),
}

/**
 * Per-Space notification policy (§3, §10.1). `Focus` shows only [minimumTier]
 * and above, which is what makes Focus different from just hiding a dock.
 */
data class NotificationPolicy(
    val minimumTier: NotificationTier,
    /** Packages the user marked sensitive: badge only, never content analysis (§10.4). */
    val contentAnalysisEnabled: Boolean = true,
) {
    fun admits(tier: NotificationTier): Boolean = tier.ordinal <= minimumTier.ordinal

    companion object {
        val Default = NotificationPolicy(NotificationTier.Info)
        val FocusOnly = NotificationPolicy(NotificationTier.Now)
        val Quiet = NotificationPolicy(NotificationTier.Action)
    }
}

/**
 * §18 SpaceConfig. `pinnedApps` are the fixed dock slots the user controls;
 * `smartDockSlots` is how many trailing slots the engine may fill (§5.4).
 */
data class SpaceConfig(
    val id: SpaceId,
    val pinnedApps: List<String>,
    val smartDockSlots: Int,
    val cards: List<CardId>,
    val notificationPolicy: NotificationPolicy,
    val themeId: String,
) {
    companion object {
        /**
         * Shipped defaults. Pinned apps are intentionally empty — guessing a
         * user's dock on first run is worse than an obviously empty one they
         * fill themselves, and the smart slots cover the gap meanwhile.
         */
        fun default(id: SpaceId): SpaceConfig = when (id) {
            SpaceId.Home -> SpaceConfig(
                id = id,
                pinnedApps = emptyList(),
                smartDockSlots = 4,
                cards = listOf(CardId.Clock, CardId.Weather, CardId.Notifications),
                notificationPolicy = NotificationPolicy.Default,
                themeId = "minimal",
            )

            SpaceId.Work -> SpaceConfig(
                id = id,
                pinnedApps = emptyList(),
                smartDockSlots = 4,
                cards = listOf(CardId.Calendar, CardId.Tasks, CardId.Notifications, CardId.Clock),
                notificationPolicy = NotificationPolicy.Default,
                themeId = "executive",
            )

            SpaceId.Focus -> SpaceConfig(
                id = id,
                pinnedApps = emptyList(),
                smartDockSlots = 1,
                cards = listOf(CardId.Timer, CardId.Clock),
                notificationPolicy = NotificationPolicy.FocusOnly,
                themeId = "minimal",
            )

            SpaceId.Media -> SpaceConfig(
                id = id,
                pinnedApps = emptyList(),
                smartDockSlots = 3,
                cards = listOf(CardId.NowPlaying, CardId.Clock),
                notificationPolicy = NotificationPolicy.Quiet,
                themeId = "cyber",
            )

            SpaceId.Travel -> SpaceConfig(
                id = id,
                pinnedApps = emptyList(),
                smartDockSlots = 4,
                cards = listOf(CardId.Clock, CardId.Weather, CardId.Calendar),
                notificationPolicy = NotificationPolicy.Default,
                themeId = "minimal",
            )

            SpaceId.Night -> SpaceConfig(
                id = id,
                pinnedApps = emptyList(),
                smartDockSlots = 2,
                cards = listOf(CardId.Clock, CardId.Battery),
                notificationPolicy = NotificationPolicy.Quiet,
                themeId = "trueblack",
            )
        }
    }
}

/** §13.1 Native Cards — FoldSpace's own content layer, distinct from AppWidgets. */
enum class CardId(val key: String, val displayName: String) {
    Clock("clock", "時鐘"),
    Weather("weather", "天氣"),
    Battery("battery", "電量"),
    Calendar("calendar", "行事曆"),
    Tasks("tasks", "待辦"),
    Notifications("notifications", "通知摘要"),
    NowPlaying("now_playing", "正在播放"),
    Timer("timer", "計時器"),
    ;

    companion object {
        fun fromKey(key: String): CardId? = entries.firstOrNull { it.key == key }
    }
}
