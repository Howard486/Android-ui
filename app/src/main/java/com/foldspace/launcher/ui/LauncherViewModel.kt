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
import com.foldspace.launcher.feed.FeedState
import com.foldspace.launcher.home.HomeItem
import com.foldspace.launcher.home.HomeLayout
import com.foldspace.launcher.home.PageKind
import com.foldspace.launcher.home.Posture
import com.foldspace.launcher.work.WorkItemsDeriver
import com.foldspace.launcher.work.WorkItemsState
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
import com.foldspace.launcher.ui.layout.LayoutMode
import com.foldspace.launcher.ui.theme.MotionLevel
import com.foldspace.launcher.ui.theme.ThemeTokens
import com.foldspace.launcher.ui.theme.Themes
import com.foldspace.launcher.ui.theme.effectiveMotion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    /**
     * §4 — the posture the layout is keyed on. Tabletop and book both fall
     * back to the unfolded arrangement: they are transient poses, and giving
     * each its own saved layout would be four arrangements to keep in step.
     */
    private val posture: StateFlow<Posture> = _window
        .map { if (it.layoutMode == LayoutMode.Compact) Posture.Folded else Posture.Unfolded }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, Posture.Folded)

    @OptIn(ExperimentalCoroutinesApi::class)
    val homeLayout: StateFlow<HomeLayout> =
        combine(container.settings.currentSpace, posture) { space, p -> space to p }
            .distinctUntilChanged()
            .flatMapLatest { (space, p) -> container.homeLayout.observe(space, p) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                HomeLayout.empty(SpaceId.General, Posture.Folded),
            )

    // ---- Editing (phase 3) ----

    private val _editing = MutableStateFlow(false)
    val editing: StateFlow<Boolean> = _editing.asStateFlow()

    private val _openFolderId = MutableStateFlow<Long?>(null)
    val openFolderId: StateFlow<Long?> = _openFolderId.asStateFlow()

    private val _organiseMessage = MutableStateFlow<String?>(null)
    val organiseMessage: StateFlow<String?> = _organiseMessage.asStateFlow()

    val feedState: StateFlow<FeedState> = container.feed.state

    /** §6 — work items derived from notifications, never from a mailbox. */
    val workItems: StateFlow<WorkItemsState> = NotificationRepository.summary
        .map(WorkItemsDeriver::derive)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkItemsState())

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
        viewModelScope.launch {
            // With no app drawer, an unplaced app is an unreachable app, so
            // the layout has to follow the installed list rather than being
            // built once at install time.
            container.launcherApps.apps
                .filter { it.isNotEmpty() }
                .collect { apps ->
                    for (space in SpaceId.entries) {
                        container.homeLayout.seedIfEmpty(space, Posture.Folded, apps)
                    }
                    container.homeLayout.syncInstalled(apps)
                }
        }
        viewModelScope.launch {
            // The leftmost feed page and 工作's work page are declared once so
            // they exist even while empty — an undeclared empty page is
            // indistinguishable from no page at all.
            container.settings.currentSpace.collect { space ->
                for (posture in Posture.entries) {
                    when (space) {
                        SpaceId.General ->
                            container.homeLayout.ensurePage(space, posture, FEED_PAGE_INDEX, PageKind.Feed)

                        SpaceId.Work ->
                            container.homeLayout.ensurePage(space, posture, FEED_PAGE_INDEX, PageKind.Work)

                        SpaceId.Simple -> Unit
                    }
                }
            }
        }
        viewModelScope.launch {
            // The unfolded arrangement is created from the folded one the
            // first time the device is opened, then never re-synced (§4.2).
            posture.collect { current ->
                if (current != Posture.Unfolded) return@collect
                for (space in SpaceId.entries) {
                    container.homeLayout.seedPostureFrom(space, Posture.Folded, Posture.Unfolded)
                }
            }
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

    fun launch(item: HomeItem) {
        item.app?.let(::launch)
    }

    fun onHomeItemLongPress(item: HomeItem) {
        item.app?.let(::openAppInfo)
    }

    /** 簡易 — the user picks exactly which four apps appear. */
    fun setSimpleApps(apps: List<AppEntry>) = viewModelScope.launch {
        container.homeLayout.setSimpleApps(apps)
    }

    // ---- Editing ----

    fun setEditing(value: Boolean) {
        _editing.value = value
        if (!value) _openFolderId.value = null
    }

    fun openFolder(item: HomeItem) {
        _openFolderId.value = item.id
    }

    fun closeFolder() {
        _openFolderId.value = null
    }

    fun moveItem(item: HomeItem, page: Int, cellX: Int, cellY: Int) = viewModelScope.launch {
        container.homeLayout.moveItem(item.id, page, cellX, cellY)
    }

    fun dropOnto(moving: HomeItem, target: HomeItem) = viewModelScope.launch {
        // A folder made from two apps is named after whatever the categoriser
        // would have called them, so it means something before the user
        // renames it.
        val suggested = moving.app?.let { app ->
            container.categorizer.categorise(listOf(app)).categorised[app.packageName]
        }?.displayName ?: "資料夾"
        container.homeLayout.dropOnto(moving.id, target.id, suggested)
    }

    fun removeFromFolder(item: HomeItem) = viewModelScope.launch {
        val layout = homeLayout.value
        val free = layout.firstFreeCellAnywhere() ?: return@launch
        container.homeLayout.removeFromFolder(item.id, free.first, free.second, free.third)
    }

    fun renameOpenFolder(title: String) = viewModelScope.launch {
        val id = _openFolderId.value ?: return@launch
        container.homeLayout.renameFolder(id, title)
    }

    // ---- One-tap organise ----

    /**
     * §redesign — categorise, then rebuild the layout into folders. The
     * outcome is reported rather than left for the user to infer from a
     * screen that suddenly looks different.
     */
    fun organiseApps() = viewModelScope.launch {
        val apps = container.launcherApps.apps.value
        if (apps.isEmpty()) return@launch

        val result = container.categorizer.categorise(apps)
        // §8.1 — Nano would resolve the leftovers here. With no model on this
        // build they land in 其他, which is honest rather than a guess.
        val categories = result.categorised + container.categorizer.fallbackForUnknown(result.unknown)

        val outcome = container.homeLayout.organiseIntoFolders(
            space = state.value.space,
            posture = if (state.value.window.layoutMode == LayoutMode.Compact) {
                Posture.Folded
            } else {
                Posture.Unfolded
            },
            categories = categories,
        )

        _organiseMessage.value = if (result.unknown.isEmpty()) {
            "已整理 ${outcome.appsPlaced} 個 App 成 ${outcome.foldersCreated} 個資料夾"
        } else {
            "已整理 ${outcome.appsPlaced} 個 App 成 ${outcome.foldersCreated} 個資料夾" +
                "（${result.unknown.size} 個無法判斷，放入「其他」）"
        }
    }

    val canUndoOrganise: Boolean get() = container.homeLayout.canUndo

    fun undoOrganise() = viewModelScope.launch {
        if (container.homeLayout.undoOrganise()) {
            _organiseMessage.value = "已還原先前的排列"
        }
    }

    fun dismissOrganiseMessage() {
        _organiseMessage.value = null
    }

    // ---- Pages ----

    fun addWidgetPage() = viewModelScope.launch {
        container.homeLayout.addPage(state.value.space, currentPosture(), PageKind.Widgets)
    }

    fun refreshFeed(force: Boolean = false) = viewModelScope.launch {
        container.feed.refreshIfStale(force)
    }

    /** §13 — the host only listens while the launcher is actually on screen. */
    fun widgetHost() = container.widgetHost

    private fun currentPosture(): Posture =
        if (state.value.window.layoutMode == LayoutMode.Compact) Posture.Folded else Posture.Unfolded

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

        /**
         * The feed and work pages live at index 0 so they are the page to the
         * *left* of the apps, which is where the request put them.
         */
        const val FEED_PAGE_INDEX = 0
    }
}
