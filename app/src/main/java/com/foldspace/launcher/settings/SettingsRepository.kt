package com.foldspace.launcher.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.foldspace.launcher.context.AutomationRule
import com.foldspace.launcher.context.AutomationRuleCodec
import com.foldspace.launcher.context.SwitchMode
import com.foldspace.launcher.pairs.AppPair
import com.foldspace.launcher.pairs.AppPairCodec
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

        fun pinnedFor(space: SpaceId) = stringPreferencesKey("pinned_${space.key}")
    }

    private companion object {
        /** A component key contains '/', '#' and '.', so none of those work as a separator. */
        const val RECORD_SEPARATOR = "\u001F"
    }
}
