package com.foldspace.launcher.ui.home

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether the launcher may buzz.
 *
 * A composition local for the same reason as the icon pack: the places that
 * want a haptic — picking up an icon, snapping a widget to a new span,
 * entering edit mode — are scattered, and threading a boolean through every
 * one of them is plumbing that earns nothing.
 *
 * VIBRATE has been declared in the manifest since V0.1 and was never once
 * used, so every one of those moments was silent.
 */
val LocalHapticsEnabled: ProvidableCompositionLocal<Boolean> =
    staticCompositionLocalOf { true }
