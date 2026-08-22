package com.foldspace.launcher.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.foldspace.launcher.context.SwitchMode
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

/** Everything the user can change. One object so the UI observes a single flow. */
data class FoldSpaceSettings(
    val currentSpace: SpaceId = SpaceId.General,
    val switchMode: SwitchMode = SwitchMode.SuggestFirst,
    val powerMode: PowerMode = PowerMode.Smart,
    val themeId: ThemeId = ThemeId.Minimal,
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
