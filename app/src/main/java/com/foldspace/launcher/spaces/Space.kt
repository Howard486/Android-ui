package com.foldspace.launcher.spaces

/**
 * §3 — a Space is the user's current *situation*, not a home-screen page.
 * Each one owns its own dock, cards, notification policy and theme.
 */
enum class SpaceId(val key: String, val displayName: String) {
    /** Everyday default: personal apps, weather, notifications. */
    General("general", "通用"),

    /** Work: calendar, tasks, work-profile apps first. */
    Work("work", "工作"),

    /**
     * Simplified: fewer, larger targets and a quiet notification policy.
     *
     * This is an accessibility and preference choice, not a context — see
     * [isUserSelectableOnly].
     */
    Simple("simple", "簡易"),
    ;

    /**
     * §7.1 — the Context Engine may never suggest or switch into this Space.
     *
     * Simplified mode is something a person chooses for themselves; a launcher
     * that decides on its own that you should be moved into an easier
     * interface is making a judgement it has no business making. Only 通用 and
     * 工作 are context-driven.
     */
    val isUserSelectableOnly: Boolean get() = this == Simple

    companion object {
        fun fromKey(key: String?): SpaceId = entries.firstOrNull { it.key == key } ?: General

        /** The Spaces the engine is allowed to propose. */
        val contextual: List<SpaceId> get() = entries.filterNot { it.isUserSelectableOnly }
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
 * Per-Space notification policy (§3, §10.1). A Space showing only
 * [minimumTier] and above is what makes it genuinely different, rather than
 * just differently decorated.
 */
data class NotificationPolicy(
    val minimumTier: NotificationTier,
    /** Packages the user marked sensitive: badge only, never content analysis (§10.4). */
    val contentAnalysisEnabled: Boolean = true,
) {
    fun admits(tier: NotificationTier): Boolean = tier.ordinal <= minimumTier.ordinal

    companion object {
        val Default = NotificationPolicy(NotificationTier.Info)
        val Quiet = NotificationPolicy(NotificationTier.Action)
        val UrgentOnly = NotificationPolicy(NotificationTier.Now)
    }
}

/**
 * How densely a Space lays itself out.
 *
 * The two values differ in target size and item count, not in structure — a
 * Space that rearranged the screen as well as resizing it would make switching
 * disorienting rather than easier.
 */
enum class SpaceDensity {
    Standard,
    Simplified,
    ;

    /** App icon edge length, in dp. */
    val iconSizeDp: Int get() = if (this == Simplified) 72 else 52

    /** Columns in the folded home grid. */
    val compactColumns: Int get() = if (this == Simplified) 3 else 4

    /** §4.1 caps the folded grid at 4–8 apps; Simplified takes the low end. */
    val compactMaxApps: Int get() = if (this == Simplified) 6 else 8

    val dockIconSizeDp: Int get() = if (this == Simplified) 60 else 46

    /** Simplified always labels its icons; an unlabelled large icon is no easier. */
    val alwaysShowLabels: Boolean get() = this == Simplified

    /** Minimum drawer cell width, in dp. */
    val drawerCellDp: Int get() = if (this == Simplified) 108 else 80
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
    val density: SpaceDensity,
) {
    companion object {
        /**
         * Shipped defaults. Pinned apps are intentionally empty — guessing a
         * user's dock on first run is worse than an obviously empty one they
         * fill themselves, and the smart slots cover the gap meanwhile.
         */
        fun default(id: SpaceId): SpaceConfig = when (id) {
            SpaceId.General -> SpaceConfig(
                id = id,
                pinnedApps = emptyList(),
                smartDockSlots = 4,
                cards = listOf(CardId.Clock, CardId.Weather, CardId.Notifications),
                notificationPolicy = NotificationPolicy.Default,
                themeId = "minimal",
                density = SpaceDensity.Standard,
            )

            SpaceId.Work -> SpaceConfig(
                id = id,
                pinnedApps = emptyList(),
                smartDockSlots = 4,
                cards = listOf(CardId.Calendar, CardId.Tasks, CardId.Notifications, CardId.Clock),
                notificationPolicy = NotificationPolicy.Default,
                themeId = "executive",
                density = SpaceDensity.Standard,
            )

            SpaceId.Simple -> SpaceConfig(
                id = id,
                pinnedApps = emptyList(),
                // Three slots, not four: at 60dp the dock would otherwise crowd
                // the edges on a folded screen.
                smartDockSlots = 3,
                cards = listOf(CardId.Clock, CardId.Battery),
                notificationPolicy = NotificationPolicy.Quiet,
                // True Black is the highest-contrast preset and its motion
                // level is None, which is what Simplified wants anyway.
                themeId = "trueblack",
                density = SpaceDensity.Simplified,
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
