package com.foldspace.launcher.ui.home

/*
 * Half-open layouts only.
 *
 * The folded and unfolded home screens moved to PagedHome when apps became
 * user-placed: a fixed grid the user arranged is a different thing from a
 * dashboard the launcher composed. Tabletop and book keep the card layouts
 * because a 5x7 grid split across a physical crease is unusable, and because
 * these are poses you hold for a minute, not somewhere you arrange apps.
 */

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.foldspace.launcher.core.launcher.AppEntry
import com.foldspace.launcher.spaces.CardId
import com.foldspace.launcher.spaces.SpaceDensity
import com.foldspace.launcher.ui.LauncherUiState
import com.foldspace.launcher.ui.cards.ClockCard
import com.foldspace.launcher.ui.cards.NativeCard
import com.foldspace.launcher.ui.components.AppTile
import com.foldspace.launcher.ui.dock.Dock

/**
 * §4.3 Half-open / Tabletop — content above the hinge, controls below it.
 *
 * The split follows the *actual* hinge position rather than a 50/50 guess, so
 * nothing straddles the crease on hardware where the fold is off-centre.
 */
@Composable
fun TabletopHome(
    state: LauncherUiState,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val density = LocalDensity.current
    val hinge = state.window.hingeBoundsPx
    val topWeight = if (hinge != null && state.window.heightDp > 0) {
        val hingeCentreDp = with(density) { ((hinge.top + hinge.bottom) / 2).toDp().value }
        (hingeCentreDp / state.window.heightDp).coerceIn(0.3f, 0.7f)
    } else {
        0.5f
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp),
    ) {
        // Upper half: time, next event, now playing, AI responses (§4.3).
        Column(
            Modifier
                .fillMaxWidth()
                .weight(topWeight),
            verticalArrangement = Arrangement.Center,
        ) {
            ClockCard(Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            NativeCard(
                id = CardId.Notifications,
                notifications = state.notifications,
                batteryPercent = state.powerDock.batteryPercent,
                charging = state.powerDock.active,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Lower half: quick actions, dock, controls.
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f - topWeight),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Dock(
                apps = state.dockApps().take(TABLETOP_DOCK_SLOTS),
                notifications = state.notifications,
                onLaunch = onLaunch,
                onLongPress = onLongPress,
                density = state.spaceConfig.density,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

/**
 * §4.3 Book posture — two facing pages, cards on one side, apps on the other,
 * with the hinge left clear between them.
 */
@Composable
fun BookHome(
    state: LauncherUiState,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val density = LocalDensity.current
    val hingeWidthDp = state.window.hingeBoundsPx
        ?.takeIf { it.isVertical && it.isOccluding }
        ?.let { with(density) { it.widthPx.toDp() } }
        ?: 0.dp

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 20.dp),
    ) {
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.spaceConfig.cards.forEach { card ->
                NativeCard(
                    id = card,
                    notifications = state.notifications,
                    batteryPercent = state.powerDock.batteryPercent,
                    charging = state.powerDock.active,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // The crease: deliberately empty.
        Spacer(Modifier.width(if (hingeWidthDp > 0.dp) hingeWidthDp else 16.dp))

        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(80.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.dockApps(), key = { it.key }) { entry ->
                    AppTile(
                        entry = entry,
                        onClick = { onLaunch(entry) },
                        onLongClick = { onLongPress(entry) },
                        badgeCount = state.notifications.countFor(entry.packageName),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Dock(
                apps = state.dockApps().take(TABLETOP_DOCK_SLOTS),
                notifications = state.notifications,
                onLaunch = onLaunch,
                onLongPress = onLongPress,
                density = state.spaceConfig.density,
            )
        }
    }
}

private const val TABLETOP_DOCK_SLOTS = 5
