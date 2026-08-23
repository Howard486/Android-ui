package com.foldspace.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldspace.launcher.core.launcher.AppEntry
import com.foldspace.launcher.spaces.SpaceId
import com.foldspace.launcher.ui.components.FoldCard
import com.foldspace.launcher.ui.components.Pill
import com.foldspace.launcher.home.HomeItem
import com.foldspace.launcher.home.HomeItemType
import com.foldspace.launcher.home.HomeLayout
import com.foldspace.launcher.ui.feed.FeedPage
import com.foldspace.launcher.ui.home.FolderSheet
import com.foldspace.launcher.ui.widgets.WidgetPicker
import com.foldspace.launcher.ui.work.WorkItemsPage
import com.foldspace.launcher.ui.drawer.AppSearchOverlay
import com.foldspace.launcher.ui.home.AppLibraryPage
import com.foldspace.launcher.ui.home.LocalHapticsEnabled
import com.foldspace.launcher.ui.home.SimpleAppPicker
import com.foldspace.launcher.ui.icons.IconPackPicker
import com.foldspace.launcher.ui.icons.LocalIconPack
import com.foldspace.launcher.ui.pairs.PairEditor
import com.foldspace.launcher.ui.rules.RuleEditor
import com.foldspace.launcher.ui.home.BookHome
import com.foldspace.launcher.ui.home.HomeDock
import com.foldspace.launcher.ui.home.PagedHome
import com.foldspace.launcher.ui.home.SimpleHome
import com.foldspace.launcher.ui.home.GestureZones
import com.foldspace.launcher.ui.home.TabletopHome
import com.foldspace.launcher.ui.home.launcherVerticalGestures
import com.foldspace.launcher.ui.home.spaceSwipeGestures
import com.foldspace.launcher.ui.layout.LayoutMode
import com.foldspace.launcher.ui.notifications.NotificationCenter
import com.foldspace.launcher.ui.powerdock.PowerDockScreen
import com.foldspace.launcher.ui.settings.SettingsScreen
import com.foldspace.launcher.ui.theme.FoldSpaceTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The launcher's single screen.
 *
 * Overlays (drawer, notification centre, settings, power dock) are stacked in
 * one Box rather than routed through a nav graph: a launcher must survive
 * being killed and relaunched with no saved state (`stateNotNeeded`), and a
 * back stack that has to be restored is the wrong shape for that.
 */
@Composable
fun FoldSpaceRoot(
    viewModel: LauncherViewModel,
    onOpenNotificationSettings: () -> Unit,
    onOpenUsageSettings: () -> Unit,
    onRequestDefaultHome: () -> Unit,
    onExpandStatusBar: () -> Unit,
    onOpenLink: (String) -> Unit,
    onOpenPackage: (String) -> Unit,
    onExportLayout: () -> Unit,
    onImportLayout: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val drawerOpen by viewModel.drawerOpen.collectAsStateWithLifecycle()
    val notificationCenterOpen by viewModel.notificationCenterOpen.collectAsStateWithLifecycle()
    val settingsOpen by viewModel.settingsOpen.collectAsStateWithLifecycle()
    val homeLayout by viewModel.homeLayout.collectAsStateWithLifecycle()
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    val openFolderId by viewModel.openFolderId.collectAsStateWithLifecycle()
    val feedState by viewModel.feedState.collectAsStateWithLifecycle()
    val workItems by viewModel.workItems.collectAsStateWithLifecycle()
    val organiseMessage by viewModel.organiseMessage.collectAsStateWithLifecycle()
    val widgetPickerOpen by viewModel.widgetPickerOpen.collectAsStateWithLifecycle()
    val appCategories by viewModel.appCategories.collectAsStateWithLifecycle()
    val sheet by viewModel.sheet.collectAsStateWithLifecycle()
    val iconPack by viewModel.iconPack.collectAsStateWithLifecycle()
    val transientMessage by viewModel.transientMessage.collectAsStateWithLifecycle()

    val openFolder = remember(openFolderId, homeLayout) {
        openFolderId?.let { id ->
            homeLayout.pages
                .flatMap { it.items }
                .firstOrNull { it.id == id && it.type == HomeItemType.Folder }
        }
    }

    val tokens = FoldSpaceTheme.tokens

    val systemPadding = PaddingValues(
        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
        bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
            // §6.1 — the Wallet strip is reserved as padding so no FoldSpace
            // content is ever laid out inside it.
            GestureZones.reservedBottomPadding(state.settings.samsungWalletCompatibility),
    )

    // Pages sit below the header, so they must not re-apply the status-bar
    // inset the header already consumed.
    val bodyPadding = PaddingValues(bottom = systemPadding.calculateBottomPadding())

    val anyOverlayOpen = drawerOpen || notificationCenterOpen || settingsOpen ||
        openFolder != null || widgetPickerOpen || sheet != null

    // Back on a launcher means "close whatever is open", never "leave".
    BackHandler(enabled = anyOverlayOpen || editing) {
        viewModel.setDrawerOpen(false)
        viewModel.setNotificationCenterOpen(false)
        viewModel.setSettingsOpen(false)
        viewModel.closeFolder()
        viewModel.closeWidgetPicker()
        viewModel.closeSheet()
        viewModel.setEditing(false)
    }

    CompositionLocalProvider(
        LocalIconPack provides iconPack,
        LocalHapticsEnabled provides state.settings.hapticsEnabled,
    ) {
    Box(
        Modifier
            .fillMaxSize()
            .background(tokens.scrim.copy(alpha = tokens.scrim.alpha * 0.85f)),
    ) {
        // §11 — the Power Dock replaces the home surface entirely while
        // charging, and unplugging restores whatever Space was showing.
        if (state.powerDock.active && !anyOverlayOpen) {
            PowerDockScreen(
                dock = state.powerDock,
                notifications = state.notifications,
                shortcuts = state.dockApps(),
                onLaunch = viewModel::launch,
                onOpenSettings = { viewModel.setSettingsOpen(true) },
                contentPadding = systemPadding,
            )
        } else {
            HomeScaffold(
                state = state,
                layout = homeLayout,
                onLaunchItem = viewModel::launch,
                onItemLongPress = viewModel::onHomeItemLongPress,
                editing = editing,
                widgetHost = viewModel.widgetHost(),
                onOpenFolder = viewModel::openFolder,
                onMoveItem = viewModel::moveItem,
                onDropOnto = viewModel::dropOnto,
                onToggleEditing = { viewModel.setEditing(!editing) },
                onLongPressEmpty = viewModel::onLongPressEmptyCell,
                onResizeWidget = viewModel::resizeWidget,
                onRemoveItem = viewModel::removeItem,
                onCellMeasured = viewModel::onCellMeasured,
                feedContent = {
                    FeedPage(
                        state = feedState,
                        onRefresh = { viewModel.refreshFeed() },
                        onOpen = { item -> item.link?.let(onOpenLink) },
                        contentPadding = bodyPadding,
                    )
                },
                workContent = {
                    WorkItemsPage(
                        state = workItems,
                        onOpenApp = onOpenPackage,
                        onRequestNotificationAccess = onOpenNotificationSettings,
                        contentPadding = bodyPadding,
                    )
                },
                libraryContent = {
                    AppLibraryPage(
                        apps = state.apps,
                        categories = appCategories,
                        notifications = state.notifications,
                        density = state.spaceConfig.density,
                        columns = homeLayout.grid.columns,
                        onLaunch = viewModel::launch,
                        onLongPress = viewModel::openAppInfo,
                        contentPadding = bodyPadding,
                    )
                },
                onLaunch = viewModel::launch,
                onLongPress = viewModel::togglePin,
                onSwipeUp = { viewModel.setDrawerOpen(true) },
                onSwipeDown = onExpandStatusBar,
                onSelectSpace = viewModel::selectSpace,
                onOpenNotifications = { viewModel.setNotificationCenterOpen(true) },
                onOpenSettings = { viewModel.setSettingsOpen(true) },
                onAcceptSuggestion = viewModel::acceptSuggestion,
                onDismissSuggestion = viewModel::dismissSuggestion,
                contentPadding = systemPadding,
            )
        }

        Overlay(visible = drawerOpen) {
            AppSearchOverlay(
                apps = state.apps,
                notifications = state.notifications,
                suggested = state.dockApps(),
                onLaunch = {
                    viewModel.setDrawerOpen(false)
                    viewModel.launch(it)
                },
                onLongPress = viewModel::openAppInfo,
                contentPadding = systemPadding,
                density = state.spaceConfig.density,
            )
        }

        Overlay(visible = notificationCenterOpen) {
            NotificationCenter(
                summary = state.notifications,
                policy = state.spaceConfig.notificationPolicy,
                onRequestAccess = onOpenNotificationSettings,
                contentPadding = systemPadding,
            )
        }

        openFolder?.let { folder ->
            FolderSheet(
                folder = folder,
                density = state.spaceConfig.density,
                onLaunch = {
                    viewModel.closeFolder()
                    viewModel.launch(it)
                },
                onRename = viewModel::renameOpenFolder,
                onRemoveFromFolder = viewModel::removeFromFolder,
                onDismiss = viewModel::closeFolder,
                contentPadding = systemPadding,
            )
        }

        organiseMessage?.let { message ->
            OrganiseToast(
                message = message,
                canUndo = viewModel.canUndoOrganise,
                onUndo = viewModel::undoOrganise,
                onDismiss = viewModel::dismissOrganiseMessage,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = systemPadding.calculateBottomPadding() + 24.dp,
                    ),
            )
        }

        Overlay(visible = widgetPickerOpen) {
            WidgetPicker(
                providers = viewModel.availableWidgets(),
                onPick = viewModel::chooseWidget,
                onDismiss = viewModel::closeWidgetPicker,
                contentPadding = systemPadding,
            )
        }

        Overlay(visible = sheet == LauncherViewModel.Sheet.Rules) {
            RuleEditor(
                rules = state.settings.automationRules,
                switchMode = state.settings.switchMode,
                onSave = viewModel::saveRule,
                onDelete = viewModel::deleteRule,
                onToggle = viewModel::setRuleEnabled,
                onDismiss = viewModel::closeSheet,
                contentPadding = systemPadding,
            )
        }

        Overlay(visible = sheet == LauncherViewModel.Sheet.SimpleApps) {
            SimpleAppPicker(
                apps = state.apps,
                current = viewModel.simpleApps(),
                slots = viewModel.simpleSlots,
                onConfirm = {
                    viewModel.setSimpleApps(it)
                    viewModel.closeSheet()
                },
                onDismiss = viewModel::closeSheet,
                contentPadding = systemPadding,
            )
        }

        Overlay(visible = sheet == LauncherViewModel.Sheet.Pairs) {
            PairEditor(
                pairs = state.settings.appPairs,
                apps = state.apps,
                support = viewModel.splitSupport(),
                onSave = viewModel::savePair,
                onDelete = viewModel::deletePair,
                onLaunch = viewModel::launchPair,
                onDismiss = viewModel::closeSheet,
                contentPadding = systemPadding,
            )
        }

        Overlay(visible = sheet == LauncherViewModel.Sheet.IconPack) {
            IconPackPicker(
                packs = remember(sheet) { viewModel.installedIconPacks() },
                selected = state.settings.iconPackPackage,
                onSelect = viewModel::setIconPack,
                onDismiss = viewModel::closeSheet,
                contentPadding = systemPadding,
            )
        }

        transientMessage?.let { message ->
            OrganiseToast(
                message = message,
                canUndo = false,
                onUndo = {},
                onDismiss = viewModel::dismissTransientMessage,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = systemPadding.calculateBottomPadding() + 24.dp,
                    ),
            )
        }

        Overlay(visible = settingsOpen) {
            SettingsScreen(
                state = state,
                nanoAvailability = viewModel.nanoAvailability(),
                onSetTheme = { viewModel.setTheme(it) },
                onSetGridChoice = { viewModel.setGridChoice(it) },
                onSetHaptics = { viewModel.setHapticsEnabled(it) },
                onEditRules = { viewModel.openSheet(LauncherViewModel.Sheet.Rules) },
                onPickSimpleApps = { viewModel.openSheet(LauncherViewModel.Sheet.SimpleApps) },
                onEditPairs = { viewModel.openSheet(LauncherViewModel.Sheet.Pairs) },
                onPickIconPack = { viewModel.openSheet(LauncherViewModel.Sheet.IconPack) },
                onExportLayout = {
                    viewModel.setSettingsOpen(false)
                    onExportLayout()
                },
                onImportLayout = {
                    viewModel.setSettingsOpen(false)
                    onImportLayout()
                },
                onSetPowerMode = { viewModel.setPowerMode(it) },
                onSetSwitchMode = { viewModel.setSwitchMode(it) },
                onSetWalletCompatibility = { viewModel.setSamsungWalletCompatibility(it) },
                onSetContentAnalysis = { viewModel.setNotificationContentAnalysis(it) },
                onRequestDefaultHome = onRequestDefaultHome,
                onRequestNotificationAccess = onOpenNotificationSettings,
                onRequestUsageAccess = onOpenUsageSettings,
                onOrganiseApps = {
                    viewModel.setSettingsOpen(false)
                    viewModel.organiseApps()
                },
                onAddWidgetPage = viewModel::addWidgetPage,
                onClose = { viewModel.setSettingsOpen(false) },
                contentPadding = systemPadding,
            )
        }
    }
    }
}

@Composable
private fun Overlay(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 6 },
        exit = fadeOut() + slideOutVertically { it / 6 },
    ) {
        content()
    }
}

@Composable
private fun HomeScaffold(
    state: LauncherUiState,
    layout: HomeLayout,
    onLaunchItem: (HomeItem) -> Unit,
    onItemLongPress: (HomeItem) -> Unit,
    editing: Boolean,
    widgetHost: com.foldspace.launcher.widgets.WidgetHostController,
    onOpenFolder: (HomeItem) -> Unit,
    onMoveItem: (HomeItem, Int, Int, Int) -> Unit,
    onDropOnto: (HomeItem, HomeItem) -> Unit,
    onToggleEditing: () -> Unit,
    onLongPressEmpty: (Int, Int, Int) -> Unit,
    onResizeWidget: (HomeItem, Int, Int) -> Unit,
    onRemoveItem: (HomeItem) -> Unit,
    onCellMeasured: (Int, Int) -> Unit,
    feedContent: @Composable () -> Unit,
    workContent: @Composable () -> Unit,
    libraryContent: @Composable () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onSelectSpace: (SpaceId) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
    onAcceptSuggestion: () -> Unit,
    onDismissSuggestion: () -> Unit,
    contentPadding: PaddingValues,
) {
    // The header owns the status-bar inset; the body keeps only the bottom one
    // (nav bar plus the reserved Wallet strip, §6.1).
    val bodyPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())

    Box(
        Modifier
            .fillMaxSize()
            .launcherVerticalGestures(onSwipeUp = onSwipeUp, onSwipeDown = onSwipeDown)
            .spaceSwipeGestures(
                onNext = { onSelectSpace(state.space.next()) },
                onPrevious = { onSelectSpace(state.space.previous()) },
            ),
    ) {
        // Header and content are stacked in a Column, not overlaid. They used
        // to be a Box with the Space bar floating over the top of the page,
        // which drew the pills straight through the clock.
        Column(Modifier.fillMaxSize()) {
            LauncherHeader(
                state = state,
                editing = editing,
                onToggleEditing = onToggleEditing,
                onSelectSpace = onSelectSpace,
                onOpenNotifications = onOpenNotifications,
                onOpenSettings = onOpenSettings,
                topPadding = contentPadding.calculateTopPadding(),
            )

            Box(Modifier.fillMaxWidth().weight(1f)) {
        when {
            // 簡易 is one fixed screen in every posture — pages and swiping
            // are exactly what it exists to remove.
            state.space == SpaceId.Simple -> SimpleHome(
                layout = layout,
                notifications = state.notifications,
                onLaunch = onLaunchItem,
                onLongPress = onItemLongPress,
                contentPadding = bodyPadding,
            )

            // Half-open poses keep the card layouts: a 5x7 grid split across
            // a horizontal crease is unusable, and these are transient poses
            // rather than somewhere apps get arranged.
            state.window.layoutMode == LayoutMode.Tabletop ->
                TabletopHome(state, onLaunch, onLongPress, contentPadding = bodyPadding)

            state.window.layoutMode == LayoutMode.Book ->
                BookHome(state, onLaunch, onLongPress, contentPadding = bodyPadding)

            else -> PagedHome(
                layout = layout,
                notifications = state.notifications,
                density = state.spaceConfig.density,
                widgetHost = widgetHost,
                editing = editing,
                onLaunch = onLaunchItem,
                onLongPress = onItemLongPress,
                onOpenFolder = onOpenFolder,
                onMove = onMoveItem,
                onDropOnto = onDropOnto,
                onLongPressEmpty = onLongPressEmpty,
                onResizeWidget = onResizeWidget,
                onRemoveItem = onRemoveItem,
                onCellMeasured = onCellMeasured,
                contentPadding = bodyPadding,
                feedContent = feedContent,
                workContent = workContent,
                libraryContent = libraryContent,
                dockContent = {
                    HomeDock(
                        apps = state.dockApps(),
                        notifications = state.notifications,
                        density = state.spaceConfig.density,
                        onLaunch = onLaunch,
                        onLongPress = onLongPress,
                    )
                },
            )
        }

            }
        }

        // §7.2 Suggest-first: an offer with a visible reason, never a silent switch.
        state.suggestion?.let { suggestion ->
            SuggestionBanner(
                spaceName = suggestion.space.displayName,
                reasonCode = suggestion.reasonCode,
                onAccept = onAcceptSuggestion,
                onDismiss = onDismissSuggestion,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = contentPadding.calculateBottomPadding() + 88.dp,
                    ),
            )
        }
    }
}

/**
 * Clock, actions and the Space strip.
 *
 * Two rows rather than one: the clock and three action labels already fill a
 * folded screen's width, and cramming the Space pills onto the same line is
 * what produced the overlap this replaced.
 */
@Composable
private fun LauncherHeader(
    state: LauncherUiState,
    editing: Boolean,
    onToggleEditing: () -> Unit,
    onSelectSpace: (SpaceId) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
    topPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val tokens = FoldSpaceTheme.tokens
    val now by produceState(initialValue = Date()) {
        while (true) {
            value = Date()
            val calendar = Calendar.getInstance()
            delay(
                60_000L - (calendar.get(Calendar.SECOND) * 1000L +
                    calendar.get(Calendar.MILLISECOND)),
            )
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .padding(top = topPadding)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (editing) "編輯中 · 長按拖曳" else headerClockFormat.format(now),
                style = MaterialTheme.typography.titleMedium,
                color = if (editing) tokens.accent else tokens.textPrimary,
                modifier = Modifier.weight(1f),
            )

            val unread = state.notifications.items.size
            Text(
                text = if (unread > 0) "通知 $unread" else "通知",
                style = MaterialTheme.typography.labelSmall,
                color = if (unread > 0) tokens.accent else tokens.textMuted,
                modifier = Modifier.clickable(onClick = onOpenNotifications).padding(6.dp),
            )
            Text(
                text = if (editing) "完成" else "編輯",
                style = MaterialTheme.typography.labelSmall,
                color = if (editing) tokens.accent else tokens.textMuted,
                modifier = Modifier.clickable(onClick = onToggleEditing).padding(6.dp),
            )
            Text(
                text = "設定",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted,
                modifier = Modifier.clickable(onClick = onOpenSettings).padding(6.dp),
            )
        }

        Spacer(Modifier.height(6.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SpaceId.entries.forEach { space ->
                val selected = space == state.space
                Box(Modifier.clickable { onSelectSpace(space) }) {
                    Pill(
                        text = space.displayName,
                        color = if (selected) tokens.accent else tokens.textMuted,
                    )
                }
            }
        }
    }
}

private val headerClockFormat = SimpleDateFormat("HH:mm  EEE M/d", Locale.getDefault())

@Composable
private fun SuggestionBanner(
    spaceName: String,
    reasonCode: String,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = FoldSpaceTheme.tokens
    FoldCard(modifier.fillMaxWidth()) {
        Text(
            text = "要切換到 $spaceName 嗎？",
            style = MaterialTheme.typography.titleMedium,
            color = tokens.textPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = reasonCode.humanReason(),
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textSecondary,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = "切換",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.accent,
                modifier = Modifier.clickable(onClick = onAccept).padding(4.dp),
            )
            Text(
                text = "先不要",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted,
                modifier = Modifier.clickable(onClick = onDismiss).padding(4.dp),
            )
        }
    }
}

/**
 * §8.4 — the UI never renders a model's free text. Reason codes are mapped to
 * strings here, so an unknown code degrades to a generic line instead of
 * leaking whatever the classifier produced.
 */
private fun String.humanReason(): String = when {
    startsWith("USER_RULE_") -> "符合你設定的自動化規則"
    this == "CHARGING_AT_NIGHT" -> "夜間充電中"
    this == "BT_CAR_CONNECTED" -> "已連線到車用裝置"
    this == "CONTEXT_SCORE" -> "依目前時間與使用情境判斷"
    else -> "依目前情境判斷"
}

private fun SpaceId.next(): SpaceId =
    SpaceId.entries[(ordinal + 1) % SpaceId.entries.size]

private fun SpaceId.previous(): SpaceId =
    SpaceId.entries[(ordinal - 1 + SpaceId.entries.size) % SpaceId.entries.size]


/**
 * Reports what one-tap organise did, and offers the way back.
 *
 * The undo is the point. Rearranging someone's whole home screen is only
 * acceptable if putting it back is one tap, and an undo the user has to go
 * hunting for in settings is not one tap.
 */
@Composable
private fun OrganiseToast(
    message: String,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = FoldSpaceTheme.tokens
    FoldCard(modifier.fillMaxWidth()) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.textPrimary,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            if (canUndo) {
                Text(
                    text = "還原",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.accent,
                    modifier = Modifier.clickable(onClick = onUndo).padding(4.dp),
                )
            }
            Text(
                text = "知道了",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted,
                modifier = Modifier.clickable(onClick = onDismiss).padding(4.dp),
            )
        }
    }
}
