package com.foldspace.launcher.ui

import android.app.Application
import android.appwidget.AppWidgetProviderInfo
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.foldspace.launcher.AppContainer
import com.foldspace.launcher.appContainer
import com.foldspace.launcher.context.ContextEvent
import com.foldspace.launcher.context.AutomationRule
import com.foldspace.launcher.context.SpaceSuggestion
import com.foldspace.launcher.context.signals.BluetoothSignalSource
import com.foldspace.launcher.context.signals.PowerSignalSource
import com.foldspace.launcher.context.signals.UsageSignalSource
import com.foldspace.launcher.core.launcher.AppEntry
import com.foldspace.launcher.core.launcher.ProfileType
import com.foldspace.launcher.feed.FeedState
import com.foldspace.launcher.home.AppCategory
import com.foldspace.launcher.home.HomeItem
import com.foldspace.launcher.home.GridSpec
import com.foldspace.launcher.home.HomeLayout
import com.foldspace.launcher.home.HomeSurface
import com.foldspace.launcher.home.LayoutBackup
import com.foldspace.launcher.home.LayoutBackupCodec
import com.foldspace.launcher.pairs.AppPair
import com.foldspace.launcher.ui.icons.IconPackInfo
import com.foldspace.launcher.ui.icons.LoadedIconPack
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
import com.foldspace.launcher.settings.GridChoice
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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
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

    /** The grid density the user picked; 4x6 by default (§ iOS home doc). */
    private val gridChoice: StateFlow<GridChoice> = container.settings.settings
        .map { it.gridChoice }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, GridChoice.Ios)

    @OptIn(ExperimentalCoroutinesApi::class)
    val homeLayout: StateFlow<HomeLayout> =
        combine(container.settings.currentSpace, posture, gridChoice) { space, p, grid ->
            Triple(space, p, grid)
        }
            .distinctUntilChanged()
            .flatMapLatest { (space, p, grid) -> container.homeLayout.observe(space, p, grid) }
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

    /**
     * Categories for the App Library page.
     *
     * The same ladder one-tap organise uses, read rather than applied: the
     * Library groups a *view* of the apps and never moves an icon, so it needs
     * no snapshot and no undo.
     */
    val appCategories: StateFlow<Map<String, AppCategory>> = container.launcherApps.apps
        .map { apps ->
            if (apps.isEmpty()) return@map emptyMap()
            val result = container.categorizer.categorise(apps)
            result.categorised + container.categorizer.fallbackForUnknown(result.unknown)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** §6 — work items derived from notifications, never from a mailbox. */
    val workItems: StateFlow<WorkItemsState> = NotificationRepository.summary
        .map(WorkItemsDeriver::derive)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkItemsState())

    /**
     * 簡易's arrangement, observed independently of the current context — the
     * picker is reached from settings, which the user may open from any 情境.
     */
    private val simpleLayout: StateFlow<HomeLayout> =
        container.homeLayout.observe(SpaceId.Simple, Posture.Folded, GridChoice.Ios)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                HomeLayout.empty(SpaceId.Simple, Posture.Folded),
            )

    /** How many slots 簡易 has. */
    val simpleSlots: Int get() = GridSpec.Simple.cellsPerPage

    // ---- Icon packs ----

    private val _iconPack = MutableStateFlow<LoadedIconPack?>(null)
    val iconPack: StateFlow<LoadedIconPack?> = _iconPack.asStateFlow()

    fun installedIconPacks(): List<IconPackInfo> = container.iconPacks.installed()

    fun setIconPack(packageName: String?) = viewModelScope.launch {
        container.settings.setIconPack(packageName)
    }

    // ---- Automation rules (§7.1 level 2) ----

    fun saveRule(rule: AutomationRule) = viewModelScope.launch {
        val current = state.value.settings.automationRules.filterNot { it.id == rule.id }
        container.settings.setAutomationRules(current + rule)
    }

    fun deleteRule(id: String) = viewModelScope.launch {
        container.settings.setAutomationRules(
            state.value.settings.automationRules.filterNot { it.id == id },
        )
    }

    fun setRuleEnabled(id: String, enabled: Boolean) = viewModelScope.launch {
        container.settings.setAutomationRules(
            state.value.settings.automationRules.map {
                if (it.id == id) it.copy(enabled = enabled) else it
            },
        )
    }

    fun setHapticsEnabled(enabled: Boolean) = viewModelScope.launch {
        container.settings.setHapticsEnabled(enabled)
    }

    // ---- App pairs ----

    fun savePair(pair: AppPair) = viewModelScope.launch {
        val current = state.value.settings.appPairs.filterNot { it.id == pair.id }
        container.settings.setAppPairs(current + pair)
    }

    fun deletePair(id: String) = viewModelScope.launch {
        container.settings.setAppPairs(state.value.settings.appPairs.filterNot { it.id == id })
    }

    /**
     * Opens a pair. Whether the two land side by side is the system's call —
     * see [com.foldspace.launcher.pairs.SplitLauncher] for why a third-party
     * launcher cannot force it.
     */
    fun launchPair(pair: AppPair) {
        if (!container.splitLauncher.launch(pair, state.value.apps)) {
            _transientMessage.value = "配對中有 App 已移除，請重新設定"
        }
    }

    fun splitSupport(): com.foldspace.launcher.pairs.SplitLauncher.Support =
        container.splitLauncher.support(state.value.window.layoutMode != LayoutMode.Compact)

    // ---- Backup and restore ----

    private val _transientMessage = MutableStateFlow<String?>(null)
    val transientMessage: StateFlow<String?> = _transientMessage.asStateFlow()

    fun dismissTransientMessage() {
        _transientMessage.value = null
    }

    /** The file's whole contents, for the Activity to write through the SAF. */
    suspend fun exportLayoutText(): String =
        LayoutBackupCodec.encode(container.homeLayout.exportLayout())

    /** The Activity owns the file I/O; only it knows whether the write landed. */
    fun reportBackupResult(succeeded: Boolean) {
        _transientMessage.value = if (succeeded) "已匯出桌面備份" else "無法讀寫這個檔案"
    }

    fun importLayoutText(raw: String) = viewModelScope.launch {
        val backup: LayoutBackup? = LayoutBackupCodec.decode(raw)
        if (backup == null) {
            _transientMessage.value = "這不是 FoldSpace 的備份檔"
            return@launch
        }
        val outcome = container.homeLayout.importLayout(backup, state.value.apps)
        _transientMessage.value = outcome.describe()
    }

    // ---- Widgets (phase 4) ----

    private val _widgetPickerOpen = MutableStateFlow(false)
    val widgetPickerOpen: StateFlow<Boolean> = _widgetPickerOpen.asStateFlow()

    /** Where the widget the user is adding will land. */
    private var pendingWidgetCell: Triple<Int, Int, Int>? = null

    /**
     * The measured size of one grid cell, in dp, reported by the grid itself.
     *
     * A widget's default span is worked out from this. It used to be assumed
     * to be 72x88dp, which is right on neither screen of a foldable and left
     * every added widget the wrong size before the user touched it.
     */
    private var measuredCellDp: Pair<Int, Int>? = null

    fun onCellMeasured(widthDp: Int, heightDp: Int) {
        if (widthDp > 0 && heightDp > 0) measuredCellDp = widthDp to heightDp
    }

    /**
     * Asks the Activity to run a system dialog. Only an Activity can launch
     * the bind-consent and configure flows, so the ViewModel hands the request
     * over rather than holding an Activity reference.
     */
    sealed interface WidgetSystemRequest {
        val appWidgetId: Int

        data class Bind(
            override val appWidgetId: Int,
            val info: AppWidgetProviderInfo,
        ) : WidgetSystemRequest

        data class Configure(
            override val appWidgetId: Int,
            val info: AppWidgetProviderInfo,
        ) : WidgetSystemRequest
    }

    private val _widgetRequests = MutableSharedFlow<WidgetSystemRequest>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val widgetRequests: MutableSharedFlow<WidgetSystemRequest> = _widgetRequests

    private val _drawerOpen = MutableStateFlow(false)
    val drawerOpen: StateFlow<Boolean> = _drawerOpen.asStateFlow()

    private val _notificationCenterOpen = MutableStateFlow(false)
    val notificationCenterOpen: StateFlow<Boolean> = _notificationCenterOpen.asStateFlow()

    private val _settingsOpen = MutableStateFlow(false)
    val settingsOpen: StateFlow<Boolean> = _settingsOpen.asStateFlow()

    /** One enum instead of four booleans: only one of these is ever open. */
    enum class Sheet { Rules, SimpleApps, Pairs, IconPack }

    private val _sheet = MutableStateFlow<Sheet?>(null)
    val sheet: StateFlow<Sheet?> = _sheet.asStateFlow()

    fun openSheet(value: Sheet) {
        _settingsOpen.value = false
        _sheet.value = value
    }

    fun closeSheet() {
        _sheet.value = null
    }

    /** The apps 簡易 currently shows, for the picker to start from. */
    fun simpleApps(): List<AppEntry> = simpleLayout.value.pages
        .flatMap { it.items }
        .sortedWith(compareBy({ it.cellY }, { it.cellX }))
        .mapNotNull { it.app }

    init {
        container.launcherApps.start()
        powerSignals.start()
        bluetoothSignals.start()

        viewModelScope.launch {
            container.settings.settings.collect { settings ->
                container.contextEngine.setSwitchMode(settings.switchMode)
                // §7.1 level 2 was dead code: the engine was constructed with
                // an empty rule list and nothing ever filled it, so the one
                // level allowed to switch a Space outright could never fire.
                container.ruleEngine.rules = settings.automationRules
            }
        }
        viewModelScope.launch {
            // Loading a pack parses its appfilter, so it happens once per
            // choice rather than per icon.
            container.settings.settings
                .map { it.iconPackPackage }
                .distinctUntilChanged()
                .collect { packageName ->
                    _iconPack.value = packageName?.let(container.iconPacks::load)
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
                    val grid = gridChoice.value
                    // 簡易 is seeded too, so its four slots are never empty on
                    // first run; the user replaces them from settings.
                    for (surface in HomeSurface.entries) {
                        container.homeLayout.seedIfEmpty(surface, Posture.Folded, apps, grid)
                    }
                    container.homeLayout.syncInstalled(apps, grid)
                }
        }
        viewModelScope.launch {
            // The unfolded arrangement is created from the folded one the
            // first time the device is opened, then never re-synced (§4.2).
            posture.collect { current ->
                if (current != Posture.Unfolded) return@collect
                for (surface in HomeSurface.entries) {
                    container.homeLayout.seedPostureFrom(
                        surface,
                        Posture.Folded,
                        Posture.Unfolded,
                        gridChoice.value,
                    )
                }
            }
        }
        viewModelScope.launch {
            // Changing the grid leaves every stored cell meaningless, so the
            // desktop is repacked. The packer is idempotent, so collecting the
            // setting rather than watching for a change is safe and means a
            // layout left over from an older grid is repaired on next launch.
            gridChoice.collect { grid ->
                for (posture in Posture.entries) {
                    container.homeLayout.reflow(HomeSurface.Desktop, posture, grid)
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
        val free = layout.firstFreeCellAnywhere()
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
            surface = HomeSurface.of(state.value.space),
            posture = currentPosture(),
            choice = gridChoice.value,
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

    /** Long-press on blank space. Today it goes straight to the picker. */
    fun onLongPressEmptyCell(page: Int, cellX: Int, cellY: Int) {
        pendingWidgetCell = Triple(page, cellX, cellY)
        _widgetPickerOpen.value = true
    }

    fun closeWidgetPicker() {
        _widgetPickerOpen.value = false
        pendingWidgetCell = null
    }

    fun availableWidgets(): List<AppWidgetProviderInfo> =
        container.widgetHost.installedProviders()

    /**
     * §13 — only a system app holds BIND_APPWIDGET, so an ordinary launcher
     * has to ask the user per widget. The id is allocated first because the
     * consent dialog is about that specific id.
     */
    fun chooseWidget(info: AppWidgetProviderInfo) {
        _widgetPickerOpen.value = false
        val id = container.widgetHost.allocateId()
        if (container.widgetHost.canBindWithoutPrompt(info, id)) {
            afterBind(id, info)
        } else {
            _widgetRequests.tryEmit(WidgetSystemRequest.Bind(id, info))
        }
    }

    fun onWidgetBindResult(appWidgetId: Int, info: AppWidgetProviderInfo, granted: Boolean) {
        if (!granted) {
            // An id that never got bound would otherwise leak for the life of
            // the host.
            container.widgetHost.releaseId(appWidgetId)
            pendingWidgetCell = null
            return
        }
        afterBind(appWidgetId, info)
    }

    private fun afterBind(appWidgetId: Int, info: AppWidgetProviderInfo) {
        if (container.widgetHost.needsConfiguration(info)) {
            _widgetRequests.tryEmit(WidgetSystemRequest.Configure(appWidgetId, info))
        } else {
            placeWidget(appWidgetId, info)
        }
    }

    fun onWidgetConfigureResult(
        appWidgetId: Int,
        info: AppWidgetProviderInfo,
        completed: Boolean,
    ) {
        if (!completed) {
            container.widgetHost.releaseId(appWidgetId)
            pendingWidgetCell = null
            return
        }
        placeWidget(appWidgetId, info)
    }

    private fun placeWidget(appWidgetId: Int, info: AppWidgetProviderInfo) {
        val cell = pendingWidgetCell ?: homeLayout.value.firstFreeCellAnywhere()
        pendingWidgetCell = null

        viewModelScope.launch {
            val layout = homeLayout.value
            val (cellWidthDp, cellHeightDp) = measuredCellDp ?: FALLBACK_CELL_DP
            val (spanX, spanY) = container.widgetHost.defaultSpan(
                info,
                cellWidthDp = cellWidthDp,
                cellHeightDp = cellHeightDp,
            )
            container.homeLayout.addWidget(
                surface = HomeSurface.of(state.value.space),
                posture = currentPosture(),
                choice = gridChoice.value,
                pageIndex = cell.first,
                cellX = cell.second,
                cellY = cell.third,
                appWidgetId = appWidgetId,
                provider = info.provider.flattenToString(),
                spanX = spanX.coerceAtMost(layout.grid.columns),
                spanY = spanY.coerceAtMost(layout.grid.rows),
            )
        }
    }

    /**
     * §13 — a widget pulled to a new number of cells.
     *
     * Clamped against the layout again here rather than trusting the handle:
     * the UI checks as it drags, but the database would otherwise be free to
     * hold a span the grid cannot draw.
     */
    fun resizeWidget(item: HomeItem, spanX: Int, spanY: Int) = viewModelScope.launch {
        val layout = homeLayout.value
        val page = layout.pages.firstOrNull { p -> p.items.any { it.id == item.id } } ?: return@launch
        val (x, y) = layout.clampSpan(page.index, item, spanX, spanY)
        if (x == item.spanX && y == item.spanY) return@launch
        container.homeLayout.resizeItem(item.id, x, y)
    }

    fun removeItem(item: HomeItem) = viewModelScope.launch {
        if (item.type == com.foldspace.launcher.home.HomeItemType.Widget) {
            item.appWidgetId?.let(container.widgetHost::releaseId)
        }
        container.homeLayout.removeItem(item.id)
    }

    /**
     * A page the user reserved for widgets.
     *
     * Scoped to the context it was created in, which is the point of the Focus
     * model: a work-only widget page should not follow you into 通用.
     */
    fun addWidgetPage() = viewModelScope.launch {
        container.homeLayout.addPage(
            surface = HomeSurface.of(state.value.space),
            posture = currentPosture(),
            kind = PageKind.Widgets,
            contexts = setOf(state.value.space),
        )
    }

    fun setGridChoice(choice: GridChoice) =
        viewModelScope.launch { container.settings.setGridChoice(choice) }

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

        /** Only used before the grid has been measured once. */
        val FALLBACK_CELL_DP = 72 to 88
    }
}
