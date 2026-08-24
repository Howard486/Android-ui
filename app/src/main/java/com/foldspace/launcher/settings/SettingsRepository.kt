package com.foldspace.launcher.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.foldspace.launcher.context.AutomationRule
import com.foldspace.launcher.context.AutomationRuleCodec
import com.foldspace.launcher.context.SwitchMode
import com.foldspace.launcher.pairs.AppPair
import com.foldspace.launcher.pairs.AppPairCodec
import com.foldspace.launcher.ui.icons.IconOverride
import com.foldspace.launcher.ui.icons.IconOverrideCodec
import com.foldspace.launcher.spaces.SpaceId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/** §12.3 Power Modes. */
enum class PowerMode { Smart, Performance, BatterySaver }

/** §15 — the theme presets shipped in V0.1 (§15.1). */
enum class ThemeId(val key: String, val displayName: String) {
    Minimal("minimal", "Minimal"),
    Cyber("cyber", "Cyber"),
    Executive("executive", "Executive"),
    AiDesk("aidesk", "AI Desk"),
    TrueBlack("trueblack", "True Black"),
    ;

    companion object {
        fun fromKey(key: String?): ThemeId = entries.firstOrNull { it.key == key } ?: Minimal
    }
}

/**
 * How dense the home grid is.
 *
 * Both postures are named here rather than deriving one from the other: the
 * inner screen is roughly 2.4x the cover screen's width, so any single
 * multiplier is wrong for at least one of the choices.
 */
enum class GridChoice(
    val key: String,
    val displayName: String,
    val foldedColumns: Int,
    val foldedRows: Int,
    val unfoldedColumns: Int,
    val unfoldedRows: Int,
) {
    /** iPhone's own grid. ~96dp a cell on a Fold cover screen. */
    Ios("ios", "4 × 6（iOS）", 4, 6, 8, 6),
    Balanced("balanced", "5 × 6", 5, 6, 9, 6),
    Dense("dense", "5 × 7", 5, 7, 10, 7),
    ;

    companion object {
        fun fromKey(key: String?): GridChoice = entries.firstOrNull { it.key == key } ?: Ios
    }
}

/**
 * How a notification count is drawn on an icon.
 *
 * Three because the two obvious options answer different questions. A count
 * tells you how much is waiting; a dot tells you only that something is, which
 * is all some people want and is far quieter on a full page. Off is the third
 * real answer, and a launcher that does not offer it is deciding for you.
 */
enum class BadgeStyle(val key: String, val label: String) {
    Count("count", "數字"),
    Dot("dot", "圓點"),
    Off("off", "不顯示"),
    ;

    companion object {
        fun fromKey(key: String?): BadgeStyle = entries.firstOrNull { it.key == key } ?: Count
    }
}

/**
 * Dock shape.
 *
 * One row of five was sized for a cover screen. The inner display of a Fold is
 * more than twice as wide and the same tray looks marooned on it, which is the
 * reason Microsoft Launcher offers up to three rows. Two is the ceiling here:
 * a third row starts eating the page it is supposed to sit under.
 */
data class DockShape(val rows: Int, val columns: Int) {
    companion object {
        const val MIN_ROWS = 1
        const val MAX_ROWS = 2
        const val MIN_COLUMNS = 3
        const val MAX_COLUMNS = 6
        val Default = DockShape(rows = 1, columns = 5)
    }
}

/** Everything the user can change. One object so the UI observes a single flow. */
data class FoldSpaceSettings(
    val currentSpace: SpaceId = SpaceId.General,
    val switchMode: SwitchMode = SwitchMode.SuggestFirst,
    val powerMode: PowerMode = PowerMode.Smart,
    val themeId: ThemeId = ThemeId.Minimal,
    val gridChoice: GridChoice = GridChoice.Ios,
    /** §7.1 level 2 — the only rules allowed to switch a Space outright. */
    val automationRules: List<AutomationRule> = emptyList(),
    /** Off is a real preference, not just a power setting; on by default. */
    val hapticsEnabled: Boolean = true,
    /** Package of the installed icon pack, or null for the system icons. */
    val iconPackPackage: String? = null,
    /** Saved two-app pairs, as encoded by [AppPairCodec]. */
    val appPairs: List<AppPair> = emptyList(),
    /** §6.1 — on by default on Samsung hardware, and never silently disabled. */
    val samsungWalletCompatibility: Boolean = true,
    val notificationContentAnalysis: Boolean = true,
    val excludedNotificationPackages: Set<String> = emptySet(),
    val nanoEnabled: Boolean = false,
    val pinnedDockApps: Map<String, List<String>> = emptyMap(),
    /**
     * Apps the user has taken off the home screen entirely, by [AppEntry.key].
     *
     * With no app drawer an unplaced app is an unreachable app, so the layout
     * follows the installed list — which means "I do not want to see this"
     * had no way to be said at all until now.
     */
    val hiddenApps: Set<String> = emptySet(),
    val badgeStyle: BadgeStyle = BadgeStyle.Count,
    val dockShape: DockShape = DockShape.Default,
    /**
     * Whether unfolding switches to the desktop shell.
     *
     * On by default because it is what was asked for, and switchable because
     * unfolding is not always a decision to start working — sometimes it is
     * just a bigger screen for a video.
     */
    val desktopModeOnUnfold: Boolean = true,
    /** Renamed apps and hand-picked artwork, by [AppEntry.key]. */
    val iconOverrides: Map<String, IconOverride> = emptyMap(),
    /**
     * Apps that ask for a fingerprint before FoldSpace will open them.
     *
     * A speed bump, not security — see [setAppLocked].
     */
    val lockedApps: Set<String> = emptySet(),
    /**
     * The Azure app registration's client id.
     *
     * Null by default and it has to be, because registering the app needs the
     * user's own Microsoft account and the Azure portal — no code here can do
     * it for them. Until one is pasted in, the Microsoft section says exactly
     * what is missing rather than failing in some other way.
     */
    val microsoftClientId: String? = null,
    /**
     * Whether the agenda shows event titles without being asked.
     *
     * On by default now. I chose the other way round first — a home screen is
     * visible to whoever is standing next to you — but a calendar you have to
     * tap twice to read is a calendar you stop looking at, and that trade is
     * the user's to make, not mine.
     */
    val agendaTitlesVisible: Boolean = true,
    /**
     * Which hub tab was last open — 摘要 or 新聞.
     *
     * Stored rather than held in memory: the hub is one swipe from every home
     * screen, and a tab that resets on every process death is a tab you keep
     * re-choosing.
     */
    val hubTab: String = "summary",
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "foldspace")

/**
 * §16.1 — settings and theme are the only things FoldSpace stores durably.
 * Context snapshots, notification bodies and usage detail deliberately are not
 * here; they live in memory and die with the process.
 */
class SettingsRepository(
    private val context: Context,
    scope: CoroutineScope,
) {

    val settings: StateFlow<FoldSpaceSettings> = context.dataStore.data
        .map(::decode)
        // Keeping PrivacyPreferences in step here rather than at each call site
        // means the listener can never read a stale exclusion list.
        .onEach { PrivacyPreferences.update(it.excludedNotificationPackages, it.notificationContentAnalysis) }
        .stateIn(scope, SharingStarted.Eagerly, FoldSpaceSettings())

    val currentSpace: Flow<SpaceId> = settings.map { it.currentSpace }

    suspend fun setCurrentSpace(space: SpaceId) = edit { it[Keys.CurrentSpace] = space.key }

    suspend fun setSwitchMode(mode: SwitchMode) = edit { it[Keys.SwitchMode] = mode.name }

    suspend fun setPowerMode(mode: PowerMode) = edit { it[Keys.PowerMode] = mode.name }

    suspend fun setTheme(theme: ThemeId) = edit { it[Keys.Theme] = theme.key }

    suspend fun setGridChoice(choice: GridChoice) = edit { it[Keys.Grid] = choice.key }

    suspend fun setAutomationRules(rules: List<AutomationRule>) = edit {
        it[Keys.Rules] = AutomationRuleCodec.encodeAll(rules)
    }

    suspend fun setHapticsEnabled(enabled: Boolean) = edit { it[Keys.Haptics] = enabled }

    /** Null clears the pack and goes back to the system's own icons. */
    suspend fun setIconPack(packageName: String?) = edit { prefs ->
        if (packageName.isNullOrBlank()) {
            prefs.remove(Keys.IconPack)
        } else {
            prefs[Keys.IconPack] = packageName
        }
    }

    suspend fun setAppPairs(pairs: List<AppPair>) = edit {
        it[Keys.AppPairs] = AppPairCodec.encodeAll(pairs)
    }

    /**
     * The settings a backup carries.
     *
     * Deliberately a whitelist, not everything: the current Space and the
     * notification exclusion list are about this device and this moment, and
     * carrying them to a restore would surprise more than it helps.
     */
    fun exportable(current: FoldSpaceSettings): Map<String, Set<String>> = buildMap {
        put(BackupKeys.RULES, AutomationRuleCodec.encodeAll(current.automationRules))
        put(BackupKeys.PAIRS, AppPairCodec.encodeAll(current.appPairs))
        put(BackupKeys.THEME, setOf(current.themeId.key))
        put(BackupKeys.GRID, setOf(current.gridChoice.key))
        current.iconPackPackage?.let { put(BackupKeys.ICON_PACK, setOf(it)) }
        put(BackupKeys.BADGE_STYLE, setOf(current.badgeStyle.key))
        put(BackupKeys.DOCK_SHAPE, setOf("${current.dockShape.rows}x${current.dockShape.columns}"))
        if (current.hiddenApps.isNotEmpty()) put(BackupKeys.HIDDEN, current.hiddenApps)
        // Names travel; the picture URIs are permissions granted to *this*
        // install and mean nothing after a restore, so they are dropped and
        // the restore keeps the rename alone.
        val names = current.iconOverrides.values
            .filter { !it.label.isNullOrBlank() }
            .map { IconOverride(it.appKey, it.label, null) }
        if (names.isNotEmpty()) put(BackupKeys.OVERRIDES, IconOverrideCodec.encodeAll(names))
        current.pinnedDockApps.forEach { (spaceKey, keys) ->
            if (keys.isNotEmpty()) put(BackupKeys.PINNED_PREFIX + spaceKey, keys.toSet())
        }
    }

    /** Applies what a backup carried. Unknown keys are ignored. */
    suspend fun importSettings(values: Map<String, Set<String>>) = edit { prefs ->
        values[BackupKeys.RULES]?.let { prefs[Keys.Rules] = it }
        values[BackupKeys.PAIRS]?.let { prefs[Keys.AppPairs] = it }
        values[BackupKeys.THEME]?.firstOrNull()?.let { prefs[Keys.Theme] = it }
        values[BackupKeys.GRID]?.firstOrNull()?.let { prefs[Keys.Grid] = it }
        values[BackupKeys.ICON_PACK]?.firstOrNull()?.let { prefs[Keys.IconPack] = it }
        values[BackupKeys.BADGE_STYLE]?.firstOrNull()?.let { prefs[Keys.BadgeStyle] = it }
        values[BackupKeys.HIDDEN]?.let { prefs[Keys.HiddenApps] = it }
        values[BackupKeys.OVERRIDES]?.let { prefs[Keys.IconOverrides] = it }
        values[BackupKeys.DOCK_SHAPE]?.firstOrNull()?.let { raw ->
            val rows = raw.substringBefore('x').toIntOrNull()
            val columns = raw.substringAfter('x').toIntOrNull()
            if (rows != null && columns != null) {
                prefs[Keys.DockRows] = rows.coerceIn(DockShape.MIN_ROWS, DockShape.MAX_ROWS)
                prefs[Keys.DockColumns] =
                    columns.coerceIn(DockShape.MIN_COLUMNS, DockShape.MAX_COLUMNS)
            }
        }
        values.forEach { (key, entries) ->
            if (!key.startsWith(BackupKeys.PINNED_PREFIX)) return@forEach
            val spaceKey = key.removePrefix(BackupKeys.PINNED_PREFIX)
            val space = SpaceId.entries.firstOrNull { it.key == spaceKey } ?: return@forEach
            // Pins are ordered; the backup stores them as a set, so the order
            // is whatever the set yields. Saying so beats implying otherwise.
            prefs[Keys.pinnedFor(space)] = entries.joinToString(RECORD_SEPARATOR)
        }
    }

    private object BackupKeys {
        const val RULES = "rules"
        const val PAIRS = "pairs"
        const val THEME = "theme"
        const val GRID = "grid"
        const val ICON_PACK = "iconPack"
        const val PINNED_PREFIX = "pinned:"
        const val BADGE_STYLE = "badgeStyle"
        const val DOCK_SHAPE = "dockShape"
        const val HIDDEN = "hiddenApps"
        const val OVERRIDES = "iconOverrides"
    }

    suspend fun setSamsungWalletCompatibility(enabled: Boolean) =
        edit { it[Keys.SamsungWallet] = enabled }

    suspend fun setNotificationContentAnalysis(enabled: Boolean) =
        edit { it[Keys.ContentAnalysis] = enabled }

    suspend fun setNanoEnabled(enabled: Boolean) = edit { it[Keys.NanoEnabled] = enabled }

    suspend fun toggleNotificationExclusion(packageName: String) = edit { prefs ->
        val current = prefs[Keys.ExcludedPackages].orEmpty()
        prefs[Keys.ExcludedPackages] =
            if (packageName in current) current - packageName else current + packageName
    }

    /**
     * Hiding is not a lock and does not pretend to be: the app is still
     * installed and still launchable from anywhere else on the device. What it
     * removes is the icon.
     */
    suspend fun setAppHidden(appKey: String, hidden: Boolean) = edit { prefs ->
        val current = prefs[Keys.HiddenApps].orEmpty()
        prefs[Keys.HiddenApps] = if (hidden) current + appKey else current - appKey
    }

    /**
     * Locks or unlocks one app.
     *
     * What this can do is refuse to launch an app *from FoldSpace* without a
     * fingerprint. What it cannot do is stop the app being opened from
     * recents, from a notification, from the Play Store, from a share sheet or
     * from another launcher — a home screen has no authority over any of
     * those. So this is a speed bump against someone picking up an unlocked
     * phone, and the UI says exactly that rather than the word "security".
     */
    suspend fun setAppLocked(appKey: String, locked: Boolean) = edit { prefs ->
        val current = prefs[Keys.LockedApps].orEmpty()
        prefs[Keys.LockedApps] = if (locked) current + appKey else current - appKey
    }

    suspend fun setMicrosoftClientId(clientId: String?) = edit { prefs ->
        val trimmed = clientId?.trim()
        if (trimmed.isNullOrBlank()) prefs.remove(Keys.MicrosoftClientId)
        else prefs[Keys.MicrosoftClientId] = trimmed
    }

    suspend fun setAgendaTitlesVisible(visible: Boolean) =
        edit { it[Keys.AgendaTitles] = visible }

    suspend fun setHubTab(key: String) = edit { it[Keys.HubTab] = key }

    suspend fun setBadgeStyle(style: BadgeStyle) = edit { it[Keys.BadgeStyle] = style.key }

    suspend fun setDesktopModeOnUnfold(enabled: Boolean) =
        edit { it[Keys.DesktopOnUnfold] = enabled }

    /**
     * Sets or clears one app's override. An override with neither a name nor a
     * picture is removed rather than stored as an empty row.
     */
    suspend fun setIconOverride(override: IconOverride) = edit { prefs ->
        val current = IconOverrideCodec.decodeAll(prefs[Keys.IconOverrides].orEmpty())
        val next = if (override.isEmpty) current - override.appKey
        else current + (override.appKey to override)
        prefs[Keys.IconOverrides] = IconOverrideCodec.encodeAll(next.values)
    }

    suspend fun setDockShape(shape: DockShape) = edit { prefs ->
        prefs[Keys.DockRows] = shape.rows.coerceIn(DockShape.MIN_ROWS, DockShape.MAX_ROWS)
        prefs[Keys.DockColumns] =
            shape.columns.coerceIn(DockShape.MIN_COLUMNS, DockShape.MAX_COLUMNS)
    }

    suspend fun setPinnedApps(space: SpaceId, keys: List<String>) = edit { prefs ->
        prefs[Keys.pinnedFor(space)] = keys.joinToString(RECORD_SEPARATOR)
    }

    // Must be declared `suspend` to match DataStore's transform type: Kotlin's
    // suspend conversion applies to lambda literals, not to a value of a
    // plain function type being passed through.
    private suspend fun edit(
        block: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit,
    ) {
        context.dataStore.edit(block)
    }

    private fun decode(prefs: Preferences): FoldSpaceSettings = FoldSpaceSettings(
        currentSpace = SpaceId.fromKey(prefs[Keys.CurrentSpace]),
        switchMode = prefs[Keys.SwitchMode]
            ?.let { name -> SwitchMode.entries.firstOrNull { it.name == name } }
            ?: SwitchMode.SuggestFirst,
        powerMode = prefs[Keys.PowerMode]
            ?.let { name -> PowerMode.entries.firstOrNull { it.name == name } }
            ?: PowerMode.Smart,
        themeId = ThemeId.fromKey(prefs[Keys.Theme]),
        gridChoice = GridChoice.fromKey(prefs[Keys.Grid]),
        automationRules = AutomationRuleCodec.decodeAll(prefs[Keys.Rules].orEmpty()),
        hapticsEnabled = prefs[Keys.Haptics] ?: true,
        iconPackPackage = prefs[Keys.IconPack]?.takeIf { it.isNotBlank() },
        appPairs = AppPairCodec.decodeAll(prefs[Keys.AppPairs].orEmpty()),
        samsungWalletCompatibility = prefs[Keys.SamsungWallet] ?: true,
        notificationContentAnalysis = prefs[Keys.ContentAnalysis] ?: true,
        excludedNotificationPackages = prefs[Keys.ExcludedPackages].orEmpty(),
        nanoEnabled = prefs[Keys.NanoEnabled] ?: false,
        pinnedDockApps = SpaceId.entries.associate { space ->
            space.key to prefs[Keys.pinnedFor(space)]
                ?.split(RECORD_SEPARATOR)
                ?.filter(String::isNotBlank)
                .orEmpty()
        },
        hiddenApps = prefs[Keys.HiddenApps].orEmpty(),
        badgeStyle = BadgeStyle.fromKey(prefs[Keys.BadgeStyle]),
        desktopModeOnUnfold = prefs[Keys.DesktopOnUnfold] ?: true,
        iconOverrides = IconOverrideCodec.decodeAll(prefs[Keys.IconOverrides].orEmpty()),
        lockedApps = prefs[Keys.LockedApps].orEmpty(),
        microsoftClientId = prefs[Keys.MicrosoftClientId]?.takeIf { it.isNotBlank() },
        agendaTitlesVisible = prefs[Keys.AgendaTitles] ?: true,
        hubTab = prefs[Keys.HubTab] ?: "summary",
        // Clamped on the way out as well as in: a value written by an older
        // build, or by a restored backup, must not produce a dock with zero
        // rows and no way back to the settings screen.
        dockShape = DockShape(
            rows = (prefs[Keys.DockRows] ?: DockShape.Default.rows)
                .coerceIn(DockShape.MIN_ROWS, DockShape.MAX_ROWS),
            columns = (prefs[Keys.DockColumns] ?: DockShape.Default.columns)
                .coerceIn(DockShape.MIN_COLUMNS, DockShape.MAX_COLUMNS),
        ),
    )

    private object Keys {
        val CurrentSpace = stringPreferencesKey("current_space")
        val SwitchMode = stringPreferencesKey("switch_mode")
        val PowerMode = stringPreferencesKey("power_mode")
        val Theme = stringPreferencesKey("theme")
        val Grid = stringPreferencesKey("grid_choice")
        val Rules = stringSetPreferencesKey("automation_rules")
        val Haptics = booleanPreferencesKey("haptics_enabled")
        val IconPack = stringPreferencesKey("icon_pack")
        val AppPairs = stringSetPreferencesKey("app_pairs")
        val SamsungWallet = booleanPreferencesKey("samsung_wallet_compat")
        val ContentAnalysis = booleanPreferencesKey("notification_content_analysis")
        val ExcludedPackages = stringSetPreferencesKey("excluded_notification_packages")
        val NanoEnabled = booleanPreferencesKey("nano_enabled")
        val HiddenApps = stringSetPreferencesKey("hidden_apps")
        val BadgeStyle = stringPreferencesKey("badge_style")
        val DesktopOnUnfold = booleanPreferencesKey("desktop_mode_on_unfold")
        val IconOverrides = stringSetPreferencesKey("icon_overrides")
        val LockedApps = stringSetPreferencesKey("locked_apps")
        val MicrosoftClientId = stringPreferencesKey("microsoft_client_id")
        val AgendaTitles = booleanPreferencesKey("agenda_titles_visible")
        val HubTab = stringPreferencesKey("hub_tab")
        val DockRows = intPreferencesKey("dock_rows")
        val DockColumns = intPreferencesKey("dock_columns")

        fun pinnedFor(space: SpaceId) = stringPreferencesKey("pinned_${space.key}")
    }

    private companion object {
        /** A component key contains '/', '#' and '.', so none of those work as a separator. */
        const val RECORD_SEPARATOR = "\u001F"
    }
}
