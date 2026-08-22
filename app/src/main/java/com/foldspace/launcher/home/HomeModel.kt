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
}

data class HomePage(
    val index: Int,
    val items: List<HomeItem>,
    val kind: PageKind = PageKind.Grid,
)

/** The whole arrangement for one Space in one posture. */
data class HomeLayout(
    val space: SpaceId,
    val posture: Posture,
    val grid: GridSpec,
    val pages: List<HomePage>,
) {
    val isEmpty: Boolean get() = pages.all { it.items.isEmpty() }

    fun pageAt(index: Int): HomePage? = pages.getOrNull(index)

    /** First free cell on the given page, or null when the page is full. */
    fun firstFreeCell(pageIndex: Int): Pair<Int, Int>? {
        val taken = pages.firstOrNull { it.index == pageIndex }
            ?.items
            ?.mapTo(mutableSetOf()) { it.cellX to it.cellY }
            .orEmpty()
        for (y in 0 until grid.rows) {
            for (x in 0 until grid.columns) {
                if ((x to y) !in taken) return x to y
            }
        }
        return null
    }

    /** First free cell anywhere, scanning pages in order. */
    fun firstFreeCellAnywhere(): Triple<Int, Int, Int>? {
        for (page in pages) {
            firstFreeCell(page.index)?.let { (x, y) -> return Triple(page.index, x, y) }
        }
        return null
    }

    companion object {
        fun empty(space: SpaceId, posture: Posture) = HomeLayout(
            space = space,
            posture = posture,
            grid = GridSpec.of(space, posture),
            pages = emptyList(),
        )
    }
}
