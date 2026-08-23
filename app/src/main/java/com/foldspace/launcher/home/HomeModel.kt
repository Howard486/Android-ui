package com.foldspace.launcher.home

import com.foldspace.launcher.core.launcher.AppEntry
import com.foldspace.launcher.spaces.SpaceId

/**
 * Which physical arrangement a layout belongs to.
 *
 * Folded and unfolded keep *separate* arrangements rather than one reflowed
 * set — spec §4.2 is explicit that unfolded is not a scaled-up folded UI, and
 * reflowing would move every icon out from under the user's thumb on each
 * fold. The cost is two layouts to maintain; the unfolded one is seeded from
 * the folded one the first time it is needed.
 */
enum class Posture(val key: String) {
    Folded("folded"),
    Unfolded("unfolded"),
    ;

    companion object {
        fun fromKey(key: String?): Posture =
            entries.firstOrNull { it.key == key } ?: Folded
    }
}

/** Columns × rows of one page. */
data class GridSpec(val columns: Int, val rows: Int) {
    val cellsPerPage: Int get() = columns * rows

    fun contains(x: Int, y: Int): Boolean = x in 0 until columns && y in 0 until rows

    companion object {
        /**
         * §4.1 / the 5×7 request. On a Fold cover screen (~387dp wide) five
         * columns is ~77dp per cell, which is what a normal launcher uses.
         */
        val Folded = GridSpec(columns = 5, rows = 7)

        /**
         * The inner screen is ~940dp wide; five columns there would be 188dp
         * per cell, which is absurd. Eight columns keeps the cell size close
         * to the folded one.
         */
        val Unfolded = GridSpec(columns = 8, rows = 6)

        /** 簡易: a big clock and four apps, nothing else. */
        val Simple = GridSpec(columns = 2, rows = 2)

        fun of(space: SpaceId, posture: Posture): GridSpec = when {
            space == SpaceId.Simple -> Simple
            posture == Posture.Unfolded -> Unfolded
            else -> Folded
        }
    }
}

/**
 * What a page is for.
 *
 * Only non-grid pages need to be recorded; a grid page is implied by the items
 * sitting on it. Without this an empty widget page would be indistinguishable
 * from a page that does not exist.
 */
enum class PageKind(val key: String) {
    /** Apps, folders and widgets on the fixed grid. */
    Grid("grid"),

    /** The leftmost feed page (§ redesign doc). */
    Feed("feed"),

    /** A page the user reserved for widgets. Widgets are allowed anywhere;
     *  this one just starts empty and is labelled. */
    Widgets("widgets"),

    /** 工作 mode's notification-derived work items. */
    Work("work"),
    ;

    val isGrid: Boolean get() = this == Grid

    companion object {
        fun fromKey(key: String?): PageKind = entries.firstOrNull { it.key == key } ?: Grid
    }
}

/** What a cell holds. */
enum class HomeItemType(val key: String) {
    App("app"),
    Folder("folder"),
    Widget("widget"),
    ;

    companion object {
        fun fromKey(key: String): HomeItemType =
            entries.firstOrNull { it.key == key } ?: App
    }
}

/**
 * One placed thing, resolved against the installed-app list.
 *
 * [app] is null when the item is a folder or a widget, and also when an app
 * item's package is currently unavailable — an app on an unmounted SD card,
 * or one belonging to a work profile that is paused. Those keep their cell
 * rather than being deleted, so turning the profile back on restores the
 * layout instead of leaving a hole.
 */
data class HomeItem(
    val id: Long,
    val type: HomeItemType,
    val cellX: Int,
    val cellY: Int,
    val spanX: Int,
    val spanY: Int,
    val app: AppEntry?,
    val folderTitle: String?,
    val folderContents: List<HomeItem> = emptyList(),
    val appWidgetId: Int? = null,
    /** True for an app item whose package could not be resolved right now. */
    val unavailable: Boolean = false,
) {
    val label: String
        get() = when (type) {
            HomeItemType.App -> app?.label.orEmpty()
            HomeItemType.Folder -> folderTitle.orEmpty()
            HomeItemType.Widget -> ""
        }

    /**
     * True when this item's span reaches the given cell.
     *
     * An item was previously treated as owning only its anchor cell, so a 4x2
     * widget left seven cells looking free — apps were placed underneath it and
     * the widget itself was drawn squashed into one square.
     */
    fun covers(x: Int, y: Int): Boolean =
        x in cellX until cellX + spanX.coerceAtLeast(1) &&
            y in cellY until cellY + spanY.coerceAtLeast(1)
}

data class HomePage(
    val index: Int,
    val items: List<HomeItem>,
    val kind: PageKind = PageKind.Grid,
) {
    /** Whatever holds this cell, whether or not the cell is its anchor. */
    fun occupantAt(x: Int, y: Int): HomeItem? = items.firstOrNull { it.covers(x, y) }
}

/** The whole arrangement for one Space in one posture. */
data class HomeLayout(
    val space: SpaceId,
    val posture: Posture,
    val grid: GridSpec,
    /** Grid pages only, numbered from 0. */
    val pages: List<HomePage>,
    /**
     * The page shown to the left of the grid, if any.
     *
     * Deliberately *not* a stored page at index 0. It was, and that put the
     * feed on the same index the app placer starts from, so every app on the
     * first page was drawn as the feed page and vanished. Keeping it out of
     * the index space makes that collision impossible rather than merely
     * avoided.
     */
    val leading: PageKind? = null,
) {
    val isEmpty: Boolean get() = pages.all { it.items.isEmpty() }

    fun pageAt(index: Int): HomePage? = pages.getOrNull(index)

    /** First free cell on the given page, or null when the page is full. */
    fun firstFreeCell(pageIndex: Int): Pair<Int, Int>? {
        val page = pages.firstOrNull { it.index == pageIndex }
        for (y in 0 until grid.rows) {
            for (x in 0 until grid.columns) {
                if (page?.occupantAt(x, y) == null) return x to y
            }
        }
        return null
    }

    /**
     * Whether [item] could occupy the given span from where it sits.
     *
     * The item itself is excluded, so a widget shrinking always fits; anything
     * else in the way, or the edge of the grid, stops it.
     */
    fun spanFits(pageIndex: Int, item: HomeItem, spanX: Int, spanY: Int): Boolean {
        if (spanX < 1 || spanY < 1) return false
        if (item.cellX + spanX > grid.columns) return false
        if (item.cellY + spanY > grid.rows) return false

        val others = pages.firstOrNull { it.index == pageIndex }
            ?.items
            ?.filter { it.id != item.id }
            .orEmpty()

        for (y in item.cellY until item.cellY + spanY) {
            for (x in item.cellX until item.cellX + spanX) {
                if (others.any { it.covers(x, y) }) return false
            }
        }
        return true
    }

    /**
     * The largest span at or below the one asked for that actually fits.
     *
     * The UI clamps as the handle is dragged, but a commit goes through here
     * too: a span that only the UI checked would be a span the database is
     * free to hold in a state the grid cannot draw.
     *
     * Searched rather than shrunk one axis at a time: giving way on one axis
     * can free the other, so any single pass reports a box smaller than the
     * grid actually allows. A page is at most 8x7, which makes the exhaustive
     * answer cheaper to justify than a clever one.
     */
    fun clampSpan(pageIndex: Int, item: HomeItem, spanX: Int, spanY: Int): Pair<Int, Int> {
        val wantX = spanX.coerceIn(1, grid.columns)
        val wantY = spanY.coerceIn(1, grid.rows)

        var bestX = 0
        var bestY = 0
        for (y in 1..wantY) {
            for (x in 1..wantX) {
                if (!spanFits(pageIndex, item, x, y)) continue
                if (x * y > bestX * bestY) {
                    bestX = x
                    bestY = y
                }
            }
        }
        // Nothing fit at all, which means something already overlaps this
        // item's own anchor. One cell is the only honest answer.
        return if (bestX == 0) 1 to 1 else bestX to bestY
    }

    /**
     * First free cell anywhere, scanning pages in order. Falls back to the
     * first cell of a new page rather than null: the callers use this to put
     * something down, and "nowhere to put it" would mean losing the item.
     */
    fun firstFreeCellAnywhere(): Triple<Int, Int, Int> {
        for (page in pages) {
            firstFreeCell(page.index)?.let { (x, y) -> return Triple(page.index, x, y) }
        }
        val nextPage = (pages.maxOfOrNull { it.index } ?: -1) + 1
        return Triple(nextPage, 0, 0)
    }

    companion object {
        fun empty(space: SpaceId, posture: Posture) = HomeLayout(
            space = space,
            posture = posture,
            grid = GridSpec.of(space, posture),
            pages = emptyList(),
            leading = leadingFor(space),
        )

        /** 通用 gets the news page, 工作 gets work items, 簡易 gets neither. */
        fun leadingFor(space: SpaceId): PageKind? = when (space) {
            SpaceId.General -> PageKind.Feed
            SpaceId.Work -> PageKind.Work
            SpaceId.Simple -> null
        }
    }
}
