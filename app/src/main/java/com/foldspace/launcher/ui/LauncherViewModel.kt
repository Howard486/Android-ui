package com.foldspace.launcher.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.foldspace.launcher.AppContainer
import com.foldspace.launcher.appContainer
import com.foldspace.launcher.context.ContextEvent
import com.foldspace.launcher.context.SpaceSuggestion
import com.foldspace.launcher.context.signals.BluetoothSignalSource
import com.foldspace.launcher.context.signals.PowerSignalSource
import com.foldspace.launcher.context.signals.UsageSignalSource
import com.foldspace.launcher.core.launcher.AppEntry
import com.foldspace.launcher.core.launcher.ProfileType
import com.foldspace.launcher.notifications.FoldSpaceNotificationListener
import com.foldspace.launcher.notifications.NotificationRepository
import com.foldspace.launcher.notifications.NotificationSummary
import com.foldspace.launcher.powerdock.PowerDockResolver
import com.foldspace.launcher.powerdock.PowerDockState
import com.foldspace.launcher.settings.FoldSpaceSettings
import com.foldspace.launcher.settings.PowerMode
import com.foldspace.launcher.settings.ThemeId
import com.foldspace.launcher.spaces.SpaceConfig
import com.foldspace.launcher.spaces.SpaceId
import com.foldspace.launcher.ui.layout.FoldWindowState
import com.foldspace.launcher.ui.theme.MotionLevel
import com.foldspace.launcher.ui.theme.ThemeTokens
import com.foldspace.launcher.ui.theme.Themes
import com.foldspace.launcher.ui.theme.effectiveMotion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

/** Everything one composition of the launcher needs. */
data class LauncherUiState(
    val settings: FoldSpaceSettings = FoldSpaceSettings(),
    val window: FoldWindowState = FoldWindowState(),
    val apps: List<AppEntry> = emptyList(),
    val appsLoading: Boolean = true,
    val notifications: NotificationSummary = NotificationSummary(),
    val suggestion: SpaceSuggestion? = null,
    val powerDock: PowerDockState = PowerDockState(),
    val usageScores: Map<String, Float> = emptyMap(),
    val isDefaultHome: Boolean = false,
    val hasNotificationAccess: Boolean = false,
    val hasUsageAccess: Boolean = false,
    val powerSaveActive: Boolean = false,
) {
    val space: SpaceId get() = settings.currentSpace

    val spaceConfig: SpaceConfig get() = SpaceConfig.default(space)

    /**
     * §3/§15 — a Space may pin a theme; otherwise the user's own choice wins.
     * Today only 簡易 pins one.
     */
    val activeTheme: ThemeId
        get() = spaceConfig.themeId?.let(ThemeId::fromKey) ?: settings.themeId

    val tokens: ThemeTokens get() = Themes.of(activeTheme)

    val motion: MotionLevel
        get() = effectiveMotion(tokens, settings.powerMode, powerSaveActive)

    /**
     * §5.4 Dynamic Dock: fixed pins first, then the smart slots filled from
     * usage. Apps already pinned are excluded so a pin never appears twice.
     */
    fun dockApps(): List<AppEntry> {
        val byKey = apps.associateBy { it.key }
        val pinnedKeys = settings.pinnedDockApps[space.key].orEmpty()
        val pinned = pinnedKeys.mapNotNull(byKey::get)

        val slots = spaceConfig.smartDockSlots
        if (slots <= 0) return pinned

        val pinnedPackages = pinned.mapTo(mutableSetOf()) { it.packageName }
        val smart = apps
            .asSequence()
            .filter { it.profile != ProfileType.Private }
            .filter { it.packageName !in pinnedPackages }
            .sortedByDescending { usageScores[it.packageName] ?: 0f }
            .filter { (usageScores[it.packageName] ?: 0f) > 0f }
            .take(slots)
            .toList()

        return pinned + smart
    }
}

/**
 * Owns the signal sources for the launcher's lifetime and forwards every one
 * of them into the Context Engine. Nothing here polls; [onResumed] is the only
 * place that actively reads anything, and it is throttled (§12.1 rule 4).
 */
class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val container: AppContainer = application.appContainer

    private val powerSignals = PowerSignalSource(application, ::onContextEvent)
    private val bluetoothSignals = BluetoothSignalSource(application, ::onContextEvent)
    private val usageSignals = UsageSignalSource(application, ::onContextEvent)

    private val _window = MutableStateFlow(FoldWindowState())
    private val _permissions = MutableStateFlow(PermissionState())

    private data class PermissionState(
        val defaultHome: Boolean = false,
        val notificationAccess: Boolean = false,
        val usageAccess: Boolean = false,
    )

    val state: StateFlow<LauncherUiState> = combine(
        container.settings.settings,
        _window,
        container.launcherApps.apps,
        combine(
            NotificationRepository.summary,
            container.contextEngine.suggestion,
            container.contextEngine.snapshot,
        ) { notifications, suggestion, snapshot -> Triple(notifications, suggestion, snapshot) },
        _permissions,
    ) { settings, window, apps, (notifications, suggestion, snapshot), permissions ->
        LauncherUiState(
            settings = settings,
            window = window,
            apps = apps,
            appsLoading = false,
            notifications = notifications,
            suggestion = suggestion,
            powerDock = PowerDockResolver.resolve(snapshot, powerSignals.chargeTimeRemainingMillis()),
            usageScores = snapshot.usageScores,
            isDefaultHome = permissions.defaultHome,
            hasNotificationAccess = permissions.notificationAccess,
            hasUsageAccess = permissions.usageAccess,
            powerSaveActive = snapshot.powerSave,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LauncherUiState())

    private val _drawerOpen = MutableStateFlow(false)
    val drawerOpen: StateFlow<Boolean> = _drawerOpen.asStateFlow()

    private val _notificationCenterOpen = MutableStateFlow(false)
    val notificationCenterOpen: StateFlow<Boolean> = _notificationCenterOpen.asStateFlow()

    private val _settingsOpen = MutableStateFlow(false)
    val settingsOpen: StateFlow<Boolean> = _settingsOpen.asStateFlow()

    init {
        container.launcherApps.start()
        powerSignals.start()
        bluetoothSignals.start()

        viewModelScope.launch {
            container.settings.settings.collect { settings ->
                container.contextEngine.setSwitchMode(settings.switchMode)
            }
        }
        viewModelScope.launch {
            // §7.2 Automatic mode: only user-authored rules reach this.
            container.contextEngine.applySpace.collect(::selectSpace)
        }
    }

    fun onWindowStateChanged(window: FoldWindowState) {
        if (_window.value.layoutMode == window.layoutMode &&
            _window.value.widthDp == window.widthDp
        ) {
            _window.value = window
            return
        }
        _window.value = window
        onContextEvent(ContextEvent.FoldChanged(window.layoutMode))
    }

    /** §12.1 rule 4 — the one place that reads anything actively. */
    fun onResumed() {
        val calendar = Calendar.getInstance()
        onContextEvent(
            ContextEvent.LauncherForeground(
                foreground = true,
                hourOfDay = calendar.get(Calendar.HOUR_OF_DAY),
                isWeekday = calendar.get(Calendar.DAY_OF_WEEK) !in
                    setOf(Calendar.SATURDAY, Calendar.SUNDAY),
            ),
        )
        usageSignals.refreshIfStale()
        refreshPermissions()
    }

    fun onPaused() {
        val calendar = Calendar.getInstance()
        onContextEvent(
            ContextEvent.LauncherForeground(
                foreground = false,
                hourOfDay = calendar.get(Calendar.HOUR_OF_DAY),
                isWeekday = calendar.get(Calendar.DAY_OF_WEEK) !in
                    setOf(Calendar.SATURDAY, Calendar.SUNDAY),
            ),
        )
    }

    fun refreshPermissions() {
        val app = getApplication<Application>()
        _permissions.value = PermissionState(
            defaultHome = container.homeRole.isDefaultHome,
            notificationAccess = FoldSpaceNotificationListener.isAccessGranted(app),
            usageAccess = usageSignals.hasUsageAccess(),
        )
    }

    fun selectSpace(space: SpaceId) {
        container.contextEngine.setUserOverride(space)
        viewModelScope.launch { container.settings.setCurrentSpace(space) }
    }

    fun acceptSuggestion() {
        state.value.suggestion?.let { selectSpace(it.space) }
    }

    fun dismissSuggestion() = container.contextEngine.dismissSuggestion()

    fun launch(entry: AppEntry) {
        container.launcherApps.launch(entry)
    }

    fun openAppInfo(entry: AppEntry) {
        if (!container.launcherApps.openAppInfo(entry)) {
            container.launcherApps.openSystemAppSettings(entry.packageName)
        }
    }

    fun setDrawerOpen(open: Boolean) {
        _drawerOpen.value = open
    }

    fun setNotificationCenterOpen(open: Boolean) {
        _notificationCenterOpen.value = open
    }

    fun setSettingsOpen(open: Boolean) {
        _settingsOpen.value = open
    }

    fun setTheme(theme: ThemeId) = viewModelScope.launch { container.settings.setTheme(theme) }

    fun setPowerMode(mode: PowerMode) =
        viewModelScope.launch { container.settings.setPowerMode(mode) }

    fun setSwitchMode(mode: com.foldspace.launcher.context.SwitchMode) =
        viewModelScope.launch { container.settings.setSwitchMode(mode) }

    fun setSamsungWalletCompatibility(enabled: Boolean) =
        viewModelScope.launch { container.settings.setSamsungWalletCompatibility(enabled) }

    fun setNotificationContentAnalysis(enabled: Boolean) =
        viewModelScope.launch { container.settings.setNotificationContentAnalysis(enabled) }

    fun togglePin(entry: AppEntry) = viewModelScope.launch {
        val space = state.value.space
        val current = state.value.settings.pinnedDockApps[space.key].orEmpty()
        val next = if (entry.key in current) current - entry.key else current + entry.key
        container.settings.setPinnedApps(space, next.take(MAX_PINNED))
    }

    /** §8 / §22 — surfaced in settings so an unsupported device says why. */
    fun nanoAvailability() = container.nano.availability()

    fun homeRoleIntent() = container.homeRole.createRequestRoleIntent()

    fun homeSettingsIntent() = container.homeRole.homeSettingsIntent()

    private fun onContextEvent(event: ContextEvent) = container.contextEngine.onEvent(event)

    override fun onCleared() {
        powerSignals.stop()
        bluetoothSignals.stop()
        container.launcherApps.stop()
        super.onCleared()
    }

    private companion object {
        /** §5.4 — "使用者可固定 2–3 個 App"; four is the hard ceiling. */
        const val MAX_PINNED = 4
    }
}
