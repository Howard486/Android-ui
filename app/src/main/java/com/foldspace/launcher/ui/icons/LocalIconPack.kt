package com.foldspace.launcher.ui.icons

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The icon pack in force, if any.
 *
 * A composition local rather than a parameter because [com.foldspace.launcher
 * .ui.components.AppIcon] is called from a dozen places that have no business
 * knowing about icon packs — the drawer, the dock, folders, the library, the
 * power dock. Threading it through all of them would be pure plumbing.
 */
val LocalIconPack: ProvidableCompositionLocal<LoadedIconPack?> =
    staticCompositionLocalOf { null }
