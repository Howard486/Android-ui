package com.foldspace.launcher.ui.components

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import com.foldspace.launcher.settings.BadgeStyle

/**
 * How notification badges are drawn.
 *
 * A composition local for the same reason the icon pack is one: [AppTile] is
 * called from the home grid, the dock, folders, the drawer, the library and
 * the power dock, and none of those has any business taking a badge
 * preference as a parameter just to pass it along.
 */
val LocalBadgeStyle: ProvidableCompositionLocal<BadgeStyle> =
    staticCompositionLocalOf { BadgeStyle.Count }
