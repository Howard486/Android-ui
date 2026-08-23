package com.foldspace.launcher.home

import com.foldspace.launcher.core.launcher.AppEntry
import com.foldspace.launcher.core.launcher.ProfileType
import com.foldspace.launcher.home.db.HomeItemDao
import com.foldspace.launcher.home.db.HomeItemEntity
import com.foldspace.launcher.home.db.HomePageDao
import com.foldspace.launcher.home.db.HomePageEntity
import com.foldspace.launcher.settings.GridChoice
import com.foldspace.launcher.spaces.SpaceId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * The home-screen arrangement.
 *
 * Keyed by ([HomeSurface], [Posture]) rather than by Space. 通用 and 工作 share
 * one arrangement and differ only in which pages they show; the previous
 * per-Space keying meant three separate desktops to keep in step, and an app
 * placed in one context simply did not exist in the others.
 */
class HomeLayoutRepository(
    private val dao: HomeItemDao,
    private val pageDao: HomePageDao,
    private val installedApps: Flow<List<AppEntry>>,
) {

    /**
     * §redesign — one-tap organise must be undoable. A single-level in-memory
     * snapshot is enough: the guarantee people need is "I can put it back",
     * not a full history, and anything durable would have to survive a
     * process death that also discards the intent to undo.
     */
    private var undoSnapshot: LayoutSnapshot? = null

    val canUndo: Boolean get() = undoSnapshot != null

    /**
     * The layout as the UI consumes it: rows joined against the live installed
     * list, so an app that vanished shows as unavailable rather than as a
     * dangling row, and pages filtered to the ones this context shows.
     */
    fun observe(space: SpaceId, posture: Posture, choice: GridChoice): Flow<HomeLayout> {
        val surface = HomeSurface.of(space)
        return combine(
            dao.observeLayout(surface.key, posture.key),
            pageDao.observePages(surface.key, posture.key),
            installedApps,
        ) { rows, pages, apps -> resolve(space, surface, posture, choice, rows, pages, apps) }
    }

    /**
     * §5.2 — with no app drawer, every installed app has to be reachable from
     * a page, so first run places all of them. Ordering is alphabetical
     * because any "smart" first-run order is a guess the user then has to
     * undo.
     */
    suspend fun seedIfEmpty(
        surface: HomeSurface,
        posture: Posture,
        apps: List<AppEntry>,
        choice: GridChoice,
    ) {
        if (dao.countIn(surface.key, posture.key) > 0) return
        if (apps.isEmpty()) return

        val grid = GridSpec.of(surface, posture, choice)
        val placeable = apps
            // Private Space apps are never auto-placed: §14.2 requires they
            // stay in their own container and not leak onto the home screen.
            .filter { it.profile != ProfileType.Private }
            .let { if (surface == HomeSurface.Simple) it.take(grid.cellsPerPage) else it }

        dao.insertAll(
            placeable.mapIndexed { index, app -> app.toEntity(surface, posture, grid, index) },
        )
    }

    /**
     * Seeds the unfolded arrangement from the folded one the first time the
     * device is opened, so unfolding does not present an empty desktop. After
     * this they diverge and are never re-synced.
     */
    suspend fun seedPostureFrom(
        surface: HomeSurface,
        source: Posture,
        target: Posture,
        choice: GridChoice,
    ) {
        if (dao.countIn(surface.key, target.key) > 0) return

        val sourceRows = dao.getLayout(surface.key, source.key)
            .filter { it.container == HomeItemEntity.CONTAINER_DESKTOP && it.itemType == "app" }
        if (sourceRows.isEmpty()) return

        val grid = GridSpec.of(surface, target, choice)
        val slots = GridPacker.pack(sourceRows.map { it.spanX to it.spanY }, grid)
        dao.insertAll(
            sourceRows.mapIndexed { index, row ->
                val slot = slots[index]
                row.copy(
                    id = 0,
                    postureKey = target.key,
                    pageIndex = slot.pageIndex,
                    cellX = slot.cellX,
                    cellY = slot.cellY,
                )
            },
        )
    }

    /**
     * Repacks a surface into the given grid.
     *
     * The grid is the user's setting now, so 4×6 becoming 5×7 leaves every
     * stored cell meaningless. [GridPacker] is idempotent, so this is safe to
     * run whenever the setting is read rather than only when it changes.
     */
    suspend fun reflow(surface: HomeSurface, posture: Posture, choice: GridChoice) {
        val grid = GridSpec.of(surface, posture, choice)
        val desktop = dao.getLayout(surface.key, posture.key)
            .filter { it.container == HomeItemEntity.CONTAINER_DESKTOP }
            .sortedWith(compareBy({ it.pageIndex }, { it.cellY }, { it.cellX }))
        if (desktop.isEmpty()) return

        val slots = GridPacker.pack(
            desktop.map {
                it.spanX.coerceAtMost(grid.columns) to it.spanY.coerceAtMost(grid.rows)
            },
            grid,
        )

        // Nothing to do is the common case — against the grid the items are
        // already packed for, the packer returns exactly where they are — and
        // writing anyway would churn the observing flow on every settings read.
        val unchanged = desktop.withIndex().all { (index, row) ->
            val slot = slots[index]
            row.pageIndex == slot.pageIndex && row.cellX == slot.cellX &&
                row.cellY == slot.cellY &&
                row.spanX <= grid.columns && row.spanY <= grid.rows
        }
        if (unchanged) return

        // Folder members are left alone: they hold no cell, and rewriting them
        // is how their container gets lost.
        dao.repackDesktop(
            desktop.mapIndexed { index, row ->
                val slot = slots[index]
                row.copy(
                    pageIndex = slot.pageIndex,
                    cellX = slot.cellX,
                    cellY = slot.cellY,
                    spanX = row.spanX.coerceAtMost(grid.columns),
                    spanY = row.spanY.coerceAtMost(grid.rows),
                )
            },
        )
    }

    /**
     * Keeps the layout in step with what is installed. Newly installed apps
     * land in the first free cell; uninstalled ones are removed everywhere.
     */
    suspend fun syncInstalled(apps: List<AppEntry>, choice: GridChoice) {
        val installedPackages = apps.mapTo(mutableSetOf()) { it.packageName }
        val placed = dao.placedPackages().toSet()

        (placed - installedPackages).forEach { dao.deleteByPackage(it) }

        val newPackages = apps.filter {
            it.profile != ProfileType.Private && it.packageName !in placed
        }
        if (newPackages.isEmpty()) return

        // 簡易 is a fixed four apps the user picks; a new install has no
        // business appearing there.
        for (posture in Posture.entries) {
            val existing = dao.getLayout(HomeSurface.Desktop.key, posture.key)
            if (existing.isEmpty()) continue
            val grid = GridSpec.of(HomeSurface.Desktop, posture, choice)
            var slot = nextFreeSlot(existing, grid)
            for (app in newPackages) {
                dao.insert(app.toEntity(HomeSurface.Desktop, posture, grid, slot))
                slot++
            }
        }
    }

    suspend fun setSimpleApps(apps: List<AppEntry>) {
        val grid = GridSpec.Simple
        val entities = apps.take(grid.cellsPerPage).mapIndexed { index, app ->
            app.toEntity(HomeSurface.Simple, Posture.Folded, grid, index)
        }
        // Both postures share the arrangement here: four apps and a clock look
        // the same either way, so keeping two copies would only be two things
        // to get out of step.
        dao.replaceLayout(HomeSurface.Simple.key, Posture.Folded.key, entities)
        dao.replaceLayout(
            HomeSurface.Simple.key,
            Posture.Unfolded.key,
            entities.map { it.copy(id = 0, postureKey = Posture.Unfolded.key) },
        )
    }

    // ---- Editing ----

    suspend fun moveItem(itemId: Long, pageIndex: Int, cellX: Int, cellY: Int) =
        dao.moveToCell(itemId, pageIndex, cellX, cellY)

    /**
     * Dropping one app onto another makes a folder; dropping onto a folder
     * adds to it. The new folder is named after the categoriser's guess when
     * there is one, because an untitled folder is a folder the user has to
     * name before it means anything.
     */
    suspend fun dropOnto(movingId: Long, targetId: Long, suggestedTitle: String) {
        val target = dao.getById(targetId) ?: return
        if (target.itemType == HomeItemType.Folder.key) {
            dao.addToFolder(movingId, targetId)
        } else {
            dao.mergeIntoFolder(movingId, targetId, suggestedTitle)
        }
    }

    suspend fun removeFromFolder(itemId: Long, pageIndex: Int, cellX: Int, cellY: Int) =
        dao.removeFromFolder(itemId, pageIndex, cellX, cellY)

    suspend fun renameFolder(folderId: Long, title: String) = dao.renameFolder(folderId, title)

    /** §13 — records a bound widget at a cell. */
    suspend fun addWidget(
        surface: HomeSurface,
        posture: Posture,
        choice: GridChoice,
        pageIndex: Int,
        cellX: Int,
        cellY: Int,
        appWidgetId: Int,
        provider: String?,
        spanX: Int,
        spanY: Int,
    ) {
        val grid = GridSpec.of(surface, posture, choice)
        dao.insert(
            HomeItemEntity(
                surfaceKey = surface.key,
                postureKey = posture.key,
                pageIndex = pageIndex,
                cellX = cellX,
                cellY = cellY,
                spanX = spanX.coerceIn(1, grid.columns),
                spanY = spanY.coerceIn(1, grid.rows),
                itemType = HomeItemType.Widget.key,
                appWidgetId = appWidgetId,
                widgetProvider = provider,
            ),
        )
    }

    /** §13 — the user pulling a widget's edge to a new number of cells. */
    suspend fun resizeItem(itemId: Long, spanX: Int, spanY: Int) =
        dao.setSpan(itemId, spanX.coerceAtLeast(1), spanY.coerceAtLeast(1))

    suspend fun removeItem(itemId: Long) = dao.deleteById(itemId)

    // ---- Pages ----

    suspend fun addPage(
        surface: HomeSurface,
        posture: Posture,
        kind: PageKind,
        contexts: Set<SpaceId> = emptySet(),
    ) {
        val existing = pageDao.getPages(surface.key, posture.key)
        val items = dao.getLayout(surface.key, posture.key)
        val highest = maxOf(
            existing.maxOfOrNull { it.pageIndex } ?: -1,
            items.filter { it.container == HomeItemEntity.CONTAINER_DESKTOP }
                .maxOfOrNull { it.pageIndex } ?: -1,
        )
        pageDao.upsert(
            HomePageEntity(
                surfaceKey = surface.key,
                postureKey = posture.key,
                pageIndex = highest + 1,
                kind = kind.key,
                contexts = encodeContexts(contexts),
            ),
        )
    }

    /** Which 情境 show a page. Empty means all of them. */
    suspend fun setPageContexts(
        surface: HomeSurface,
        posture: Posture,
        pageIndex: Int,
        kind: PageKind,
        contexts: Set<SpaceId>,
    ) {
        pageDao.upsert(
            HomePageEntity(
                surfaceKey = surface.key,
                postureKey = posture.key,
                pageIndex = pageIndex,
                kind = kind.key,
                contexts = encodeContexts(contexts),
            ),
        )
    }

    suspend fun removePage(surface: HomeSurface, posture: Posture, pageIndex: Int) =
        pageDao.delete(surface.key, posture.key, pageIndex)

    // ---- One-tap organise ----

    /**
     * Groups every placed app into category folders.
     *
     * Kept alongside the App Library page, which shows the same categories
     * without moving anything: some people do want their real desktop
     * foldered, and that is a different want from wanting somewhere to browse.
     * Takes a snapshot first — rearranging someone's entire home screen with
     * no way back is a hostile thing to do, however good the categories are.
     */
    suspend fun organiseIntoFolders(
        surface: HomeSurface,
        posture: Posture,
        choice: GridChoice,
        categories: Map<String, AppCategory>,
    ): OrganiseOutcome {
        val before = dao.getLayout(surface.key, posture.key)
        val apps = before.filter {
            it.itemType == HomeItemType.App.key && it.packageName != null
        }
        if (apps.isEmpty()) return OrganiseOutcome(0, 0)

        undoSnapshot = LayoutSnapshot(surface, posture, before)

        val grid = GridSpec.of(surface, posture, choice)
        val grouped = apps.groupBy { categories[it.packageName] ?: AppCategory.Other }
            .toList()
            .sortedBy { (category, _) -> category.ordinal }

        // Built as an explicit plan rather than a flat list with placeholder
        // ids: folder membership is a parent/child relationship, and encoding
        // it as "the nth folder owns the nth distinct placeholder" is the kind
        // of positional coupling that breaks the first time the order changes.
        val plan = mutableListOf<PlannedSlot>()
        var slot = 0
        var folderCount = 0

        fun slotOf(index: Int) = Triple(
            index / grid.cellsPerPage,
            (index % grid.cellsPerPage) % grid.columns,
            (index % grid.cellsPerPage) / grid.columns,
        )

        for ((category, members) in grouped) {
            // A folder holding one app is worse than the app itself: an extra
            // tap for no organisation. Those stay loose on the grid.
            if (members.size <= 1) {
                members.forEach { member ->
                    val (page, x, y) = slotOf(slot++)
                    plan += PlannedSlot.Loose(
                        member.copy(
                            id = 0,
                            container = HomeItemEntity.CONTAINER_DESKTOP,
                            pageIndex = page,
                            cellX = x,
                            cellY = y,
                            sortOrder = 0,
                        ),
                    )
                }
                continue
            }

            val (page, x, y) = slotOf(slot++)
            folderCount++
            plan += PlannedSlot.Folder(
                folder = HomeItemEntity(
                    id = 0,
                    surfaceKey = surface.key,
                    postureKey = posture.key,
                    container = HomeItemEntity.CONTAINER_DESKTOP,
                    pageIndex = page,
                    cellX = x,
                    cellY = y,
                    itemType = HomeItemType.Folder.key,
                    folderTitle = category.displayName,
                ),
                members = members.mapIndexed { index, member ->
                    member.copy(
                        id = 0,
                        pageIndex = HomeItemDao.PARK_PAGE,
                        cellX = index,
                        cellY = 0,
                        sortOrder = index,
                    )
                },
            )
        }

        writePlan(surface, posture, plan)
        return OrganiseOutcome(foldersCreated = folderCount, appsPlaced = apps.size)
    }

    /**
     * Writes the plan, resolving each folder's real id before its members are
     * inserted. Members carry no container until this point, so there is no
     * window in which a member points at an id that does not exist.
     */
    private suspend fun writePlan(
        surface: HomeSurface,
        posture: Posture,
        plan: List<PlannedSlot>,
    ) {
        dao.clearLayout(surface.key, posture.key)
        for (entry in plan) {
            when (entry) {
                is PlannedSlot.Loose -> dao.insert(entry.item)
                is PlannedSlot.Folder -> {
                    val folderId = dao.insert(entry.folder)
                    dao.insertAll(entry.members.map { it.copy(container = folderId) })
                }
            }
        }
    }

    /** Puts the layout back exactly as it was before the last organise. */
    suspend fun undoOrganise(): Boolean {
        val snapshot = undoSnapshot ?: return false
        dao.replaceLayout(
            snapshot.surface.key,
            snapshot.posture.key,
            snapshot.rows.map { it.copy(id = 0) },
        )
        undoSnapshot = null
        return true
    }

    /**
     * First index not already occupied, scanning pages in reading order.
     *
     * Spans count: a widget holding four cells makes all four unavailable, or
     * the next installed app is placed underneath it.
     */
    private fun nextFreeSlot(existing: List<HomeItemEntity>, grid: GridSpec): Int {
        val taken = mutableSetOf<Int>()
        existing
            .filter { it.container == HomeItemEntity.CONTAINER_DESKTOP }
            .forEach { row ->
                for (y in row.cellY until row.cellY + row.spanY.coerceAtLeast(1)) {
                    for (x in row.cellX until row.cellX + row.spanX.coerceAtLeast(1)) {
                        if (!grid.contains(x, y)) continue
                        taken += row.pageIndex * grid.cellsPerPage + y * grid.columns + x
                    }
                }
            }
        var slot = 0
        while (slot in taken) slot++
        return slot
    }

    private fun encodeContexts(contexts: Set<SpaceId>): String =
        contexts.joinToString(",") { it.key }

    private fun decodeContexts(raw: String?): Set<SpaceId> =
        raw.orEmpty()
            .split(",")
            .filter { it.isNotBlank() }
            .mapNotNullTo(mutableSetOf()) { key -> SpaceId.entries.firstOrNull { it.key == key } }

    private fun AppEntry.toEntity(
        surface: HomeSurface,
        posture: Posture,
        grid: GridSpec,
        slot: Int,
    ) = HomeItemEntity(
        surfaceKey = surface.key,
        postureKey = posture.key,
        pageIndex = slot / grid.cellsPerPage,
        cellX = (slot % grid.cellsPerPage) % grid.columns,
        cellY = (slot % grid.cellsPerPage) / grid.columns,
        itemType = HomeItemType.App.key,
        packageName = packageName,
        className = className,
        userSerial = key.substringAfterLast('#').toLongOrNull(),
    )

    private fun resolve(
        space: SpaceId,
        surface: HomeSurface,
        posture: Posture,
        choice: GridChoice,
        rows: List<HomeItemEntity>,
        pageRows: List<HomePageEntity>,
        apps: List<AppEntry>,
    ): HomeLayout {
        val grid = GridSpec.of(surface, posture, choice)
        val byComponent = apps.associateBy { it.packageName to it.className }

        val desktop = rows.filter { it.container == HomeItemEntity.CONTAINER_DESKTOP }
        val membersByFolder = rows
            .filter { it.container != HomeItemEntity.CONTAINER_DESKTOP }
            .groupBy { it.container }

        fun toItem(row: HomeItemEntity): HomeItem {
            val app = row.packageName?.let { pkg ->
                byComponent[pkg to row.className.orEmpty()]
                    // Fall back to any launchable component of the package: an
                    // app update can rename its launcher activity, and losing
                    // the icon over that would be a poor trade.
                    ?: apps.firstOrNull { it.packageName == pkg }
            }
            val type = HomeItemType.fromKey(row.itemType)
            return HomeItem(
                id = row.id,
                type = type,
                cellX = row.cellX,
                cellY = row.cellY,
                spanX = row.spanX,
                spanY = row.spanY,
                app = app,
                folderTitle = row.folderTitle,
                folderContents = membersByFolder[row.id]
                    .orEmpty()
                    .sortedBy { it.sortOrder }
                    .map(::toItem),
                appWidgetId = row.appWidgetId,
                unavailable = type == HomeItemType.App && app == null,
            )
        }

        val declared = pageRows.associateBy { it.pageIndex }
        val itemsByPage = desktop.groupBy { it.pageIndex }

        // A page exists if it holds something or if it was explicitly declared
        // — a declared-but-empty widget page must not disappear.
        val indices = (itemsByPage.keys + declared.keys).sorted()

        val pages = indices
            .map { index ->
                val row = declared[index]
                HomePage(
                    index = index,
                    items = itemsByPage[index].orEmpty().map(::toItem),
                    kind = PageKind.fromKey(row?.kind),
                    contexts = decodeContexts(row?.contexts),
                )
            }
            // The Focus model: switching 情境 hides and reveals pages, it never
            // moves what is on them. Page indices stay as stored, so a hidden
            // page's cells are still its own when it comes back.
            .filter { it.visibleIn(space) }

        return HomeLayout(
            space = space,
            posture = posture,
            grid = grid,
            pages = pages,
            leading = HomeLayout.leadingFor(space),
        )
    }
}


/** What one organise pass did, so the UI can say it rather than just redraw. */
data class OrganiseOutcome(val foldersCreated: Int, val appsPlaced: Int)

/** A single-level undo point for [HomeLayoutRepository.organiseIntoFolders]. */
private data class LayoutSnapshot(
    val surface: HomeSurface,
    val posture: Posture,
    val rows: List<HomeItemEntity>,
)

/** One entry in a planned layout, before any database ids exist. */
private sealed interface PlannedSlot {
    data class Loose(val item: HomeItemEntity) : PlannedSlot

    data class Folder(
        val folder: HomeItemEntity,
        val members: List<HomeItemEntity>,
    ) : PlannedSlot
}
