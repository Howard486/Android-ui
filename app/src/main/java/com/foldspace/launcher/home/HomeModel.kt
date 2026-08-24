package com.foldspace.launcher.home

import com.foldspace.launcher.core.launcher.AppEntry
import com.foldspace.launcher.settings.GridChoice
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

/**
 * Which stored arrangement a layout belongs to.
 *
 * Not the Space. 通用 and 工作 share one arrangement and differ only in which
 * pages they show — the iOS Focus model, adopted because the alternative (one
 * arrangement per Space, which this replaced) meant an app placed in 通用
 * simply did not exist in 工作, and three separate desktops to keep in step.
 *
 * 簡易 keeps its own surface because it is not a view of the desktop; it is a
 * different screen with four apps and a clock.
 */
enum class HomeSurface(val key: String) {
    Desktop("desktop"),
    Simple("simple"),

    /**
     * The widgets the user puts on the work page.
     *
     * A surface of its own rather than a page of the desktop, because the work
     * page is derived from the context rather than stored, and because these
     * widgets should not move when the desktop is reflowed to a new grid.
     *
     * Never seeded with apps — it starts empty and holds only what is added
     * to it deliberately.
     */
    Work("work"),
    ;

    /** Whether new installs are auto-placed here. */
    val autoPlacesApps: Boolean get() = this != Work

    companion object {
        fun of(space: SpaceId): HomeSurface =
            if (space == SpaceId.Simple) Simple else Desktop

        fun fromKey(key: String?): HomeSurface =
            entries.firstOrNull { it.key == key } ?: Desktop
    }
}

/** Columns × rows of one page. */
data class GridSpec(val columns: Int, val rows: Int) {
    val cellsPerPage: Int get() = columns * rows

    fun contains(x: Int, y: Int): Boolean = x in 0 until columns && y in 0 until rows

    companion object {
        /** 簡易: a big clock and four apps, nothing else. */
        val Simple = GridSpec(columns = 2, rows = 2)

        /**
         * The grid in force.
         *
         * [choice] is the user's setting — 4×6 by default, which on a Fold
         * cover screen (~387dp) is ~96dp a cell, the spacing an iPhone uses.
         * 簡易 ignores it: four apps and a clock is the mode, not a density.
         */
        fun of(
            surface: HomeSurface,
            posture: Posture,
            choice: GridChoice = GridChoice.Ios,
        ): GridSpec = when {
            surface == HomeSurface.Simple -> Simple
            // A strip, not a page: it sits inside the work page above the
            // items, and a full-height grid there would push them off screen.
            surface == HomeSurface.Work ->
                GridSpec(columns = if (posture == Posture.Unfolded) 6 else 4, rows = 3)
            posture == Posture.Unfolded ->
                GridSpec(choice.unfoldedColumns, choice.unfoldedRows)

            else -> GridSpec(choice.foldedColumns, choice.foldedRows)
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

    /**
     * The leftmost page: 摘要 and 新聞 behind two tabs.
     *
     * It replaces the old context-dependent leading page, where 通用 got the
     * news and 工作 got the work items and 簡易 got nothing — so the same swipe
     * landed somewhere different depending on which Space you were in, and one
     * of the two things was always unreachable. The tabs make both reachable
     * from anywhere, which is what Microsoft Launcher's glance page does.
     */
    Hub("hub"),

    /** The old news-only leading page. Kept so a stored key still parses. */
    Feed("feed"),

    /** A page the user reserved for widgets. Widgets are allowed anywhere;
     *  this one just starts empty and is labelled. */
    Widgets("widgets"),

    /** 工作 mode's notification-derived work items. */
    Work("work"),

    /**
     * The App Library: every installed app grouped by category.
     *
     * A *view*, never a rearrangement. One-tap organise used to move the
     * user's real icons into folders, which is why it needed an undo and why
     * it was alarming when it ran. Reading the same categorisation onto its
     * own page costs the arrangement nothing.
     */
    Library("library"),
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

    /**
     * A shortcut an app asked to pin — "New message", a saved conversation, a
     * web page. Distinct from [App] because launching it goes through
     * `LauncherApps.startShortcut`, not through a launch intent.
     */
    Shortcut("shortcut"),

    /**
     * A block of small action tiles — an app, one of an app's shortcuts, a web
     * address. Launcher X's idea, which exists on iOS because iOS will not let
     * anything but a widget onto the home screen; here the value is not the
     * container but the tiles, since a URL and a shortcut are things the
     * desktop grid cannot otherwise hold, and sixteen of them fit where four
     * icons would.
     */
    QuickLaunch("quick"),
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
    /** Set for [HomeItemType.Shortcut]; the id the publishing app gave it. */
    val shortcutId: String? = null,
    /** The package a shortcut belongs to, kept even when [app] resolves. */
    val shortcutPackage: String? = null,
    /** Encoded tiles, for [HomeItemType.QuickLaunch]. See [QuickTileCodec]. */
    val payload: String? = null,
    /** True for an app item whose package could not be resolved right now. */
    val unavailable: Boolean = false,
) {
    val label: String
        get() = when (type) {
            HomeItemType.App -> app?.label.orEmpty()
            HomeItemType.Folder -> folderTitle.orEmpty()
            HomeItemType.Shortcut -> folderTitle.orEmpty()
            HomeItemType.Widget -> ""
            // A block is many things at once; naming it after any one of them
            // would be worse than leaving it unnamed.
            HomeItemType.QuickLaunch -> ""
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

    /**
     * Unread notifications this cell stands for.
     *
     * A folder answers for everything inside it. Without that, putting an app
     * into a folder hid its badge — which is the one thing a badge exists to
     * prevent, and the reason folders need one at all.
     */
    fun unreadCount(countFor: (String) -> Int): Int = when (type) {
        HomeItemType.Folder -> folderContents.sumOf { it.unreadCount(countFor) }
        HomeItemType.Widget -> 0
        // A block stands for several apps at once; one number over it would
        // not say which of them it belonged to.
        HomeItemType.QuickLaunch -> 0
        else -> app?.let { countFor(it.packageName) } ?: 0
    }
}

data class HomePage(
    val index: Int,
    val items: List<HomeItem>,
    val kind: PageKind = PageKind.Grid,
    /**
     * Which contexts show this page. Empty means all of them, which is what
     * an ordinary page is: switching 情境 hides and reveals pages, it never
     * moves what is on them.
     */
    val contexts: Set<SpaceId> = emptySet(),
) {
    /** Whatever holds this cell, whether or not the cell is its anchor. */
    fun occupantAt(x: Int, y: Int): HomeItem? = items.firstOrNull { it.covers(x, y) }

    fun visibleIn(space: SpaceId): Boolean = contexts.isEmpty() || space in contexts
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
     * Whether [item] would fit with its top-left corner at an arbitrary cell.
     *
     * [spanFits] only ever asks about growing in place. Moving needs the same
     * question at a new anchor, and nothing asked it: `HomeItemDao.itemAt`
     * matches the anchor cell alone, so dropping an app on a cell that a 2x2
     * widget *covers but does not anchor* wrote a silent overlap — after which
     * whichever item sorted first won every hit test and the other became
     * impossible to pick up.
     */
    fun rectFits(
        pageIndex: Int,
        item: HomeItem,
        cellX: Int,
        cellY: Int,
        spanX: Int = item.spanX,
        spanY: Int = item.spanY,
    ): Boolean {
        if (cellX < 0 || cellY < 0 || spanX < 1 || spanY < 1) return false
        if (cellX + spanX > grid.columns) return false
        if (cellY + spanY > grid.rows) return false

        val others = pages.firstOrNull { it.index == pageIndex }
            ?.items
            ?.filter { it.id != item.id }
            .orEmpty()

        for (y in cellY until cellY + spanY) {
            for (x in cellX until cellX + spanX) {
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
            grid = GridSpec.of(HomeSurface.of(space), posture),
            pages = emptyList(),
            leading = leadingFor(space),
        )

        /**
         * The same leading page wherever there is one.
         *
         * It used to differ per Space: 通用 got the news, 工作 got the work
         * summary, so switching context silently took one of them away and
         * neither was reachable from the other. One hub with two tabs fixes
         * that.
         *
         * 簡易 still has none, and that is not an oversight — it is one fixed
         * screen with no pages and no swiping, which is the entire reason it
         * exists. Giving it a news page would be undoing the mode.
         */
        fun leadingFor(space: SpaceId): PageKind? =
            if (space == SpaceId.Simple) null else PageKind.Hub
    }
}

/**
 * How large an icon should be drawn inside a cell it may span.
 *
 * A spanned app used to render as one base-size icon floating in a large empty
 * box, because `AppTile` was handed a fixed size regardless of how many cells
 * the item held. The icon grows with the *smaller* of the two spans — a 3x1
 * item is a wide box, not a big icon — and is then capped to what the box can
 * actually hold once the label has its room.
 */
fun iconSizeFor(
    baseDp: Int,
    spanX: Int,
    spanY: Int,
    cellWidthDp: Int,
    cellHeightDp: Int,
): Int {
    val columns = spanX.coerceAtLeast(1)
    val rows = spanY.coerceAtLeast(1)
    val wanted = baseDp.coerceAtLeast(1) * minOf(columns, rows)

    // Before the grid has been measured there is nothing to cap against.
    if (cellWidthDp <= 0 || cellHeightDp <= 0) return wanted

    val boxWidth = cellWidthDp * columns
    val boxHeight = cellHeightDp * rows - LABEL_ALLOWANCE_DP
    val fits = minOf(boxWidth, boxHeight)
    return wanted.coerceIn(1, fits.coerceAtLeast(1))
}

/**
 * Room kept below the icon, in dp.
 *
 * Must cover everything `AppTile` spends besides the icon itself: 6dp of
 * padding above, 6dp of spacing between icon and label, one `labelSmall` line
 * (~16dp at its default line height) and 6dp of padding below. At 26 the sum
 * was eight short and every label was sliced through the middle — which is
 * what a device screenshot showed, and what nothing here could have caught,
 * because no test measures text.
 *
 * Kept as one constant next to the arithmetic that uses it rather than
 * duplicated in the composable, so the two cannot drift apart again.
 */
const val TILE_LABEL_ALLOWANCE_DP = 34

private const val LABEL_ALLOWANCE_DP = TILE_LABEL_ALLOWANCE_DP

/** A cell assignment: which page, and where on it. */
data class CellSlot(val pageIndex: Int, val cellX: Int, val cellY: Int)

/**
 * Assigns cells to a list of spans, in the order given.
 *
 * Needed because the grid is now the user's choice: changing 4×6 to 5×7 leaves
 * every stored cell meaningless, and items outside the new bounds would simply
 * stop being drawn. Packing in reading order is the only reflow that keeps the
 * arrangement recognisable — the first icon stays first.
 *
 * Idempotent: run against the grid the items are already packed for, every
 * item lands back where it was. That is what lets the caller run it whenever
 * the setting is read rather than having to track whether it changed.
 */
object GridPacker {

    fun pack(spans: List<Pair<Int, Int>>, grid: GridSpec): List<CellSlot> {
        val taken = mutableSetOf<Triple<Int, Int, Int>>()
        val slots = mutableListOf<CellSlot>()

        for ((rawSpanX, rawSpanY) in spans) {
            val spanX = rawSpanX.coerceIn(1, grid.columns.coerceAtLeast(1))
            val spanY = rawSpanY.coerceIn(1, grid.rows.coerceAtLeast(1))
            slots += firstFit(taken, grid, spanX, spanY)
        }
        return slots
    }

    private fun firstFit(
        taken: MutableSet<Triple<Int, Int, Int>>,
        grid: GridSpec,
        spanX: Int,
        spanY: Int,
    ): CellSlot {
        var page = 0
        while (true) {
            for (y in 0..grid.rows - spanY) {
                for (x in 0..grid.columns - spanX) {
                    if (!fits(taken, page, x, y, spanX, spanY)) continue
                    occupy(taken, page, x, y, spanX, spanY)
                    return CellSlot(page, x, y)
                }
            }
            page++
        }
    }

    private fun fits(
        taken: Set<Triple<Int, Int, Int>>,
        page: Int,
        cellX: Int,
        cellY: Int,
        spanX: Int,
        spanY: Int,
    ): Boolean {
        for (y in cellY until cellY + spanY) {
            for (x in cellX until cellX + spanX) {
                if (Triple(page, x, y) in taken) return false
            }
        }
        return true
    }

    private fun occupy(
        taken: MutableSet<Triple<Int, Int, Int>>,
        page: Int,
        cellX: Int,
        cellY: Int,
        spanX: Int,
        spanY: Int,
    ) {
        for (y in cellY until cellY + spanY) {
            for (x in cellX until cellX + spanX) {
                taken += Triple(page, x, y)
            }
        }
    }
}
