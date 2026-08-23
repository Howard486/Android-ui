package com.foldspace.launcher.desktop

/** A launch rectangle in screen pixels. Pure, so the arithmetic can be tested. */
data class WindowBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Where a desktop-mode window opens.
 *
 * This is the *entire* extent of what a third-party launcher controls about a
 * window. `ActivityOptions.setLaunchBounds` has been public since API 24 and
 * decides where an app appears and how large; after that the window belongs to
 * the system. FoldSpace cannot move it, resize it, draw its title bar, or
 * switch between windows — those are the system's window decorations, and no
 * public API hands them to an ordinary app.
 *
 * So the one thing worth getting right is that two windows opened in a row do
 * not land exactly on top of each other, which is what a cascade is for.
 */
object DesktopWindows {

    /** A window's share of the screen. Roughly what a desktop OS opens at. */
    const val WIDTH_FRACTION = 0.58f
    const val HEIGHT_FRACTION = 0.66f

    /** How many offsets before the cascade returns to the top-left. */
    const val CASCADE_POSITIONS = 6

    /** Below this a window is not worth opening; better to fill what there is. */
    const val MIN_EXTENT_PX = 240

    /**
     * The rectangle for the [index]th window opened this session.
     *
     * Always fully on screen and always above the taskbar: a window that opens
     * underneath the taskbar has its bottom edge unreachable, and the user
     * cannot move it because moving is the system's job, not ours.
     */
    fun cascade(
        index: Int,
        screenWidth: Int,
        screenHeight: Int,
        taskbarHeight: Int,
        stepPx: Int,
        marginPx: Int,
    ): WindowBounds {
        val usableHeight = (screenHeight - taskbarHeight).coerceAtLeast(MIN_EXTENT_PX)
        val safeWidth = screenWidth.coerceAtLeast(MIN_EXTENT_PX)

        val width = (safeWidth * WIDTH_FRACTION).toInt()
            .coerceIn(minOf(MIN_EXTENT_PX, safeWidth), safeWidth)
        val height = (usableHeight * HEIGHT_FRACTION).toInt()
            .coerceIn(minOf(MIN_EXTENT_PX, usableHeight), usableHeight)

        val step = (index.coerceAtLeast(0) % CASCADE_POSITIONS) * stepPx.coerceAtLeast(0)

        // Clamp rather than wrap: a window pushed off the edge by the cascade
        // is worse than two windows sharing a corner.
        val maxLeft = (safeWidth - width).coerceAtLeast(0)
        val maxTop = (usableHeight - height).coerceAtLeast(0)
        val left = (marginPx + step).coerceIn(0, maxLeft)
        val top = (marginPx + step).coerceIn(0, maxTop)

        return WindowBounds(left, top, left + width, top + height)
    }
}
