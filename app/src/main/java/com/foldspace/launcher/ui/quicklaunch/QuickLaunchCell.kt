package com.foldspace.launcher.ui.quicklaunch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.foldspace.launcher.core.launcher.AppEntry
import com.foldspace.launcher.home.QuickLaunchGrid
import com.foldspace.launcher.home.QuickTile
import com.foldspace.launcher.home.QuickTileKind
import com.foldspace.launcher.ui.components.AppIcon
import com.foldspace.launcher.ui.components.LabelOnWallpaper
import com.foldspace.launcher.ui.theme.FoldSpaceTheme

/**
 * What a quick-launch block needs from outside the grid.
 *
 * Bundled rather than passed as three more lambdas because the grid threads
 * every callback through two composables to reach a cell, and the block is the
 * only item type that needs to resolve anything by itself.
 */
data class QuickLaunchHost(
    /** Resolves a package name to an installed app, for the tile's icon. */
    val appFor: (String) -> AppEntry?,
    val onLaunch: (QuickTile) -> Unit,
)

/**
 * A block of quick-launch tiles.
 *
 * Drawn as a translucent tray rather than as loose icons, so it reads as one
 * object you can move and resize instead of as a cluster that happens to sit
 * together — which matters because it *is* one item as far as the grid is
 * concerned.
 *
 * Tiles beyond what the block can hold are not drawn. The editor says how many
 * those are; hiding them silently would make a block that looks complete and
 * is not.
 */
@Composable
fun QuickLaunchCell(
    tiles: List<QuickTile>,
    spanX: Int,
    spanY: Int,
    appFor: (String) -> AppEntry?,
    onLaunch: (QuickTile) -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = FoldSpaceTheme.tokens
    val columns = QuickLaunchGrid.columns(spanX)
    val shown = QuickLaunchGrid.visible(tiles, spanX, spanY)

    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(tokens.surfaceAlpha())
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (shown.isEmpty()) {
            // An empty block has nothing to tap but itself, and a block you
            // cannot fill is a block you cannot use.
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(onClick = onEdit),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "點一下加入捷徑",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted,
                    textAlign = TextAlign.Center,
                )
            }
            return@Box
        }

        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            shown.chunked(columns).forEach { row ->
                Row(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    row.forEach { tile ->
                        QuickTileView(
                            tile = tile,
                            app = tile.packageName?.let(appFor),
                            onClick = { onLaunch(tile) },
                            modifier = Modifier.weight(1f).fillMaxSize(),
                        )
                    }
                    // A short last row must not stretch its tiles across the
                    // whole width, or the block stops reading as a grid.
                    repeat(columns - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun QuickTileView(
    tile: QuickTile,
    app: AppEntry?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = FoldSpaceTheme.tokens

    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // An app or one of its shortcuts borrows the app's own icon. A web
        // address has none — fetching a favicon would mean a network request
        // per tile on every draw — so it gets a glyph and its host.
        if (app != null && tile.kind != QuickTileKind.Url) {
            AppIcon(app, TILE_ICON)
        } else {
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(tokens.surfaceElevated)
                    .padding(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (tile.kind == QuickTileKind.Url) "🌐" else "?",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textSecondary,
                )
            }
        }

        Text(
            text = tile.displayLabel(app?.label),
            style = MaterialTheme.typography.labelSmall.merge(LabelOnWallpaper),
            color = tokens.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** Small: the whole point is that many of these fit where four icons would. */
private val TILE_ICON = 26.dp
