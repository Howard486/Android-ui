package com.foldspace.launcher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.MaterialTheme
import com.foldspace.launcher.core.launcher.AppEntry
import android.content.ComponentName
import com.foldspace.launcher.ui.icons.IconShaper
import com.foldspace.launcher.ui.icons.LocalIconPack
import com.foldspace.launcher.ui.icons.Squircle
import com.foldspace.launcher.ui.theme.FoldSpaceTheme

/** The standard FoldSpace surface: translucent card over the wallpaper. */
@Composable
fun FoldCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = FoldSpaceTheme.tokens
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(tokens.cardRadius))
            .background(tokens.surfaceAlpha())
            .border(1.dp, tokens.outline, RoundedCornerShape(tokens.cardRadius))
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun CardTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = FoldSpaceTheme.tokens.textMuted,
        modifier = modifier,
    )
}

/**
 * An app icon plus label, with a long-press affordance for the §5.3 actions.
 * [badgeCount] renders the §10.1 unread badge.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppTile(
    entry: AppEntry,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    iconSize: Dp = 52.dp,
    showLabel: Boolean = true,
    badgeCount: Int = 0,
) {
    val tokens = FoldSpaceTheme.tokens
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            // A null onLongClick leaves the long press to whatever is above
            // this tile. That matters on the home grid: `combinedClickable`
            // consumes the gesture before the grid's drag detector sees it,
            // which is why dragging an app stopped working at all.
            .then(
                if (onLongClick == null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                },
            )
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            AppIcon(entry, iconSize)
            if (badgeCount > 0) {
                Box(
                    Modifier
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(if (badgeCount > 9) 20.dp else 16.dp)
                        .clip(CircleShape)
                        .background(tokens.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.onAccent,
                        maxLines = 1,
                    )
                }
            }
        }

        if (showLabel) {
            Text(
                text = entry.label,
                style = MaterialTheme.typography.labelSmall.merge(LabelOnWallpaper),
                color = tokens.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Renders a platform `Drawable` into Compose.
 *
 * `Drawable` has no Compose painter of its own, and the standard trick of
 * wrapping it in a `Painter` re-draws on every frame. Rasterising once into an
 * `ImageBitmap`, keyed by the app's stable key, keeps a 300-app drawer from
 * re-running the drawable pipeline while scrolling.
 */
@Composable
fun AppIcon(entry: AppEntry, size: Dp) {
    val density = LocalDensity.current
    val tokens = FoldSpaceTheme.tokens
    val pxSize = with(density) { size.roundToPx() }.coerceAtLeast(1)

    // Every icon comes back as the same squircle at the same size. Drawing
    // the platform drawable straight into a square, which is what this did
    // before, let adaptive icons show their full background bleed and let
    // legacy icons keep whatever silhouette their author chose — the reason
    // a full page of them never lined up.
    // A pack supplies artwork; it never decides shape. Whatever comes back
    // still goes through IconShaper, so a pack whose own icons are square,
    // round and teardrop-shaped in the same set still lands as one uniform
    // grid.
    val pack = LocalIconPack.current
    val bitmap = remember(entry.key, pxSize, tokens.surfaceElevated, pack) {
        val packed = pack?.iconFor(ComponentName(entry.packageName, entry.className))
        IconShaper.render(
            drawable = packed ?: entry.icon,
            sizePx = pxSize,
            // The pack is part of the key, or switching packs would keep
            // serving the previous one's art out of the cache.
            key = entry.key + "@" + (pack?.packageName ?: "system"),
            tile = tokens.surfaceElevated,
        )
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = entry.label,
            modifier = Modifier.size(size),
        )
    } else {
        // Missing icon: a labelled placeholder beats an empty gap.
        Box(
            Modifier
                .size(size)
                .clip(Squircle.Shape)
                .background(tokens.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = entry.label.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = tokens.textSecondary,
            )
        }
    }
}

/**
 * The shadow that makes a label readable on someone else's wallpaper.
 *
 * Labels had none, so legibility rested entirely on dimming the wallpaper by
 * half — which is why the wallpaper barely showed. A shadow costs nothing and
 * is what lets the scrim come down.
 */
val LabelOnWallpaper = TextStyle(
    shadow = Shadow(
        color = Color.Black.copy(alpha = 0.65f),
        offset = Offset(0f, 1f),
        blurRadius = 4f,
    ),
)

/** Small pill used for tiers, statuses and Space names. */
@Composable
fun Pill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.35f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/** Horizontal progress bar used for battery and charge level. */
@Composable
fun LevelBar(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
) {
    val tokens = FoldSpaceTheme.tokens
    Box(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(tokens.surfaceElevated),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .clip(CircleShape)
                .background(color),
        )
    }
}
