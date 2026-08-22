package com.foldspace.launcher.ui.layout

/**
 * §4 — the device posture FoldSpace lays out against.
 *
 * Deliberately *not* derived from width alone: a Fold's outer screen and a
 * folded-shut inner screen are the same posture but different widths, and the
 * spec's rule is "Unfolded is a different information architecture, not a
 * scaled-up Folded".
 */
enum class LayoutMode {
    /** Folded / outer screen. One-handed, 4–8 apps, summary notifications. */
    Compact,

    /** Unfolded and flat. Two- to three-column workspace. */
    Expanded,

    /** Half-opened, hinge horizontal. Content on top, controls below. */
    Tabletop,

    /** Half-opened, hinge vertical (book posture). Two facing pages. */
    Book,
    ;

    val isUnfolded: Boolean get() = this != Compact
}

/**
 * The resolved window state the UI observes. [hingeBoundsPx] is null on a flat
 * or non-folding display.
 */
data class FoldWindowState(
    val layoutMode: LayoutMode = LayoutMode.Compact,
    val hingeBoundsPx: HingeBounds? = null,
    val widthDp: Int = 0,
    val heightDp: Int = 0,
) {
    /** True when content must avoid drawing across the hinge (§4.3). */
    val hasOccludingHinge: Boolean get() = hingeBoundsPx?.isOccluding == true
}

data class HingeBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val isOccluding: Boolean,
    val isVertical: Boolean,
) {
    val widthPx: Int get() = right - left
    val heightPx: Int get() = bottom - top
}
