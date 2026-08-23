package com.foldspace.launcher.ui.home

/**
 * How a dock's pins are laid out across its rows.
 *
 * Pure, and separated from the composable, because "which icon is on which
 * row" is arithmetic and arithmetic is the only part of a launcher that can be
 * checked without a device.
 */
object DockSlots {

    /**
     * Splits [items] into at most [rows] rows of at most [columns] each,
     * filling left to right and top to bottom.
     *
     * Anything beyond `rows * columns` is dropped rather than squeezed in: a
     * tray that silently shrinks its icons to fit one more is how a dock stops
     * being tappable, and the pin list is the user's to trim.
     *
     * Never returns an empty trailing row, so a caller measuring the result
     * gets the height it will actually draw.
     */
    fun <T> arrange(items: List<T>, rows: Int, columns: Int): List<List<T>> {
        if (items.isEmpty() || rows <= 0 || columns <= 0) return emptyList()
        return items.take(rows * columns).chunked(columns)
    }

    /** How many pins a dock of this shape can show. */
    fun capacity(rows: Int, columns: Int): Int =
        if (rows <= 0 || columns <= 0) 0 else rows * columns

    /** Rows actually drawn for [count] pins — what the caller reserves height for. */
    fun rowsUsed(count: Int, rows: Int, columns: Int): Int {
        if (count <= 0 || rows <= 0 || columns <= 0) return 0
        val shown = minOf(count, rows * columns)
        return (shown + columns - 1) / columns
    }
}
