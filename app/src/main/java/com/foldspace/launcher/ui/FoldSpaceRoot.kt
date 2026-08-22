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
import androidx.compose.runtime.getValue
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
import com.foldspace.launcher.ui.work.WorkItemsPage
import com.foldspace.launcher.ui.drawer.AppSearchOverlay
import com.foldspace.launcher.ui.home.BookHome
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

    val anyOverlayOpen = drawerOpen || notificationCenterOpen || settingsOpen || openFolder != null

    // Back on a launcher means "close whatever is open", never "leave".
    BackHandler(enabled = anyOverlayOpen || editing) {
        viewModel.setDrawerOpen(false)
        viewModel.setNotificationCenterOpen(false)
        viewModel.setSettingsOpen(false)
        viewModel.closeFolder()
        viewModel.setEditing(false)
    }

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
            HomeSurface(
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
                feedContent = {
                    FeedPage(
                        state = feedState,
                        onRefresh = { viewModel.refreshFeed() },
                        onOpen = { item -> item.link?.let(onOpenLink) },
                        contentPadding = systemPadding,
                    )
                },
                workContent = {
                    WorkItemsPage(
                        state = workItems,
                        onOpenApp = onOpenPackage,
                        onRequestNotificationAccess = onOpenNotificationSettings,
                        contentPadding = systemPadding,
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

        Overlay(visible = settingsOpen) {
            SettingsScreen(
                state = state,
                nanoAvailability = viewModel.nanoAvailability(),
                onSetTheme = { viewModel.setTheme(it) },
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
private fun HomeSurface(
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
    feedContent: @Composable () -> Unit,
    workContent: @Composable () -> Unit,
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
    Box(
        Modifier
            .fillMaxSize()
            .launcherVerticalGestures(onSwipeUp = onSwipeUp, onSwipeDown = onSwipeDown)
            .spaceSwipeGestures(
                onNext = { onSelectSpace(state.space.next()) },
                onPrevious = { onSelectSpace(state.space.previous()) },
            ),
    ) {
        when {
            // 簡易 is one fixed screen in every posture — pages and swiping
            // are exactly what it exists to remove.
            state.space == SpaceId.Simple -> SimpleHome(
                layout = layout,
                notifications = state.notifications,
                onLaunch = onLaunchItem,
                onLongPress = onItemLongPress,
                contentPadding = contentPadding,
            )

            // Half-open poses keep the card layouts: a 5x7 grid split across
            // a horizontal crease is unusable, and these are transient poses
            // rather than somewhere apps get arranged.
            state.window.layoutMode == LayoutMode.Tabletop ->
                TabletopHome(state, onLaunch, onLongPress, contentPadding = contentPadding)

            state.window.layoutMode == LayoutMode.Book ->
                BookHome(state, onLaunch, onLongPress, contentPadding = contentPadding)

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
                contentPadding = contentPadding,
                feedContent = feedContent,
                workContent = workContent,
            )
        }

        SpaceBar(
            state = state,
            editing = editing,
            onToggleEditing = onToggleEditing,
            onSelectSpace = onSelectSpace,
            onOpenNotifications = onOpenNotifications,
            onOpenSettings = onOpenSettings,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = contentPadding.calculateTopPadding()),
        )

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

/** §3 — the Space strip. Tapping switches; swiping left/right does the same. */
@Composable
private fun SpaceBar(
    state: LauncherUiState,
    editing: Boolean,
    onToggleEditing: () -> Unit,
    onSelectSpace: (SpaceId) -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = FoldSpaceTheme.tokens
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
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

        Spacer(Modifier.width(8.dp))

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
}

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
