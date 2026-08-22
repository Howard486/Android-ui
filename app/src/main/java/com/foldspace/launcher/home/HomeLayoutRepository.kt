package com.foldspace.launcher.home

import com.foldspace.launcher.core.launcher.AppEntry
import com.foldspace.launcher.core.launcher.ProfileType
import com.foldspace.launcher.home.db.HomeItemDao
import com.foldspace.launcher.home.db.HomeItemEntity
import com.foldspace.launcher.home.db.HomePageDao
import com.foldspace.launcher.home.db.HomePageEntity
import com.foldspace.launcher.spaces.SpaceId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * The home-screen arrangement.
 *
 * Everything here is keyed by (Space, Posture): the two postures hold separate
 * arrangements by design (see [Posture]), and each Space has its own.
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
     * dangling row.
     */
    fun observe(space: SpaceId, posture: Posture): Flow<HomeLayout> =
        combine(
            dao.observeLayout(space.key, posture.key),
            pageDao.observePages(space.key, posture.key),
            installedApps,
        ) { rows, pages, apps -> resolve(space, posture, rows, pages, apps) }

    /**
     * §5.2 — with no app drawer, every installed app has to be reachable from
     * a page, so first run places all of them. Ordering is alphabetical
     * because any "smart" first-run order is a guess the user then has to
     * undo.
     */
    suspend fun seedIfEmpty(space: SpaceId, posture: Posture, apps: List<AppEntry>) {
        if (dao.countIn(space.key, posture.key) > 0) return
        if (apps.isEmpty()) return

        val grid = GridSpec.of(space, posture)
        val placeable = apps
            // Private Space apps are never auto-placed: §14.2 requires they
            // stay in their own container and not leak onto the home screen.
            .filter { it.profile != ProfileType.Private }
            .let { if (space == SpaceId.Simple) it.take(grid.cellsPerPage) else it }

        dao.insertAll(placeable.mapIndexed { index, app -> app.toEntity(space, posture, grid, index) })
    }

    /**
     * Seeds the unfolded arrangement from the folded one the first time the
     * device is opened, so unfolding does not present an empty desktop. After
     * this they diverge and are never re-synced.
     */
    suspend fun seedPostureFrom(space: SpaceId, source: Posture, target: Posture) {
        if (dao.countIn(space.key, target.key) > 0) return

        val sourceRows = dao.getLayout(space.key, source.key)
            .filter { it.container == HomeItemEntity.CONTAINER_DESKTOP && it.itemType == "app" }
        if (sourceRows.isEmpty()) return

        val grid = GridSpec.of(space, target)
        // Reflowed in the source's reading order rather than copied cell for
        // cell: the grids are different shapes, so a copy would drop anything
        // outside the narrower one.
        dao.insertAll(
            sourceRows.mapIndexed { index, row ->
                row.copy(
                    id = 0,
                    postureKey = target.key,
                    pageIndex = index / grid.cellsPerPage,
                    cellX = (index % grid.cellsPerPage) % grid.columns,
                    cellY = (index % grid.cellsPerPage) / grid.columns,
                )
            },
        )
    }

    /**
     * Keeps the layout in step with what is installed. Newly installed apps
     * land in the first free cell; uninstalled ones are removed everywhere.
     */
    suspend fun syncInstalled(apps: List<AppEntry>) {
        val installedPackages = apps.mapTo(mutableSetOf()) { it.packageName }
        val placed = dao.placedPackages().toSet()

        (placed - installedPackages).forEach { dao.deleteByPackage(it) }

        val newPackages = apps.filter {
            it.profile != ProfileType.Private && it.packageName !in placed
        }
        if (newPackages.isEmpty()) return

        // Only Spaces that already have a layout get new apps appended; a
        // Space the user has never opened is seeded wholesale instead.
        for (space in SpaceId.entries) {
            if (space == SpaceId.Simple) continue
            for (posture in Posture.entries) {
                val existing = dao.getLayout(space.key, posture.key)
                if (existing.isEmpty()) continue
                val grid = GridSpec.of(space, posture)
                var slot = nextFreeSlot(existing, grid)
                for (app in newPackages) {
                    dao.insert(app.toEntity(space, posture, grid, slot))
                    slot++
                }
            }
        }
    }

    suspend fun setSimpleApps(apps: List<AppEntry>) {
        val grid = GridSpec.of(SpaceId.Simple, Posture.Folded)
        val entities = apps.take(grid.cellsPerPage).mapIndexed { index, app ->
            app.toEntity(SpaceId.Simple, Posture.Folded, grid, index)
        }
        // Both postures share the arrangement here: four apps and a clock look
        // the same either way, so keeping two copies would only be two things
        // to get out of step.
        dao.replaceLayout(SpaceId.Simple.key, Posture.Folded.key, entities)
        dao.replaceLayout(
            SpaceId.Simple.key,
            Posture.Unfolded.key,
            entities.map { it.copy(id = 0, postureKey = Posture.Unfolded.key) },
        )
    }

    // ---- Editing (phase 3) ----

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
        space: SpaceId,
        posture: Posture,
        pageIndex: Int,
        cellX: Int,
        cellY: Int,
        appWidgetId: Int,
        provider: String?,
        spanX: Int,
        spanY: Int,
    ) {
        val grid = GridSpec.of(space, posture)
        dao.insert(
            HomeItemEntity(
                spaceKey = space.key,
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

    suspend fun removeItem(itemId: Long) = dao.deleteById(itemId)

    suspend fun addPage(space: SpaceId, posture: Posture, kind: PageKind) {
        val existing = pageDao.getPages(space.key, posture.key)
        val items = dao.getLayout(space.key, posture.key)
        val highest = maxOf(
            existing.maxOfOrNull { it.pageIndex } ?: -1,
            items.filter { it.container == HomeItemEntity.CONTAINER_DESKTOP }
                .maxOfOrNull { it.pageIndex } ?: -1,
        )
        pageDao.upsert(
            HomePageEntity(space.key, posture.key, highest + 1, kind.key),
        )
    }

    suspend fun ensurePage(space: SpaceId, posture: Posture, pageIndex: Int, kind: PageKind) {
        pageDao.upsert(HomePageEntity(space.key, posture.key, pageIndex, kind.key))
    }

    // ---- One-tap organise (phase 3) ----

    /**
     * Groups every placed app into category folders.
     *
     * Takes a snapshot first: rearranging someone's entire home screen with no
     * way back is a hostile thing to do, however good the categories are.
     */
    suspend fun organiseIntoFolders(
        space: SpaceId,
        posture: Posture,
        categories: Map<String, AppCategory>,
    ): OrganiseOutcome {
        val before = dao.getLayout(space.key, posture.key)
        val apps = before.filter {
            it.itemType == HomeItemType.App.key && it.packageName != null
        }
        if (apps.isEmpty()) return OrganiseOutcome(0, 0)

        undoSnapshot = LayoutSnapshot(space, posture, before)

        val grid = GridSpec.of(space, posture)
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
                    spaceKey = space.key,
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

        writePlan(space, posture, plan)
        return OrganiseOutcome(foldersCreated = folderCount, appsPlaced = apps.size)
    }

    /**
     * Writes the plan, resolving each folder's real id before its members are
     * inserted. Members carry no container until this point, so there is no
     * window in which a member points at an id that does not exist.
     */
    private suspend fun writePlan(
        space: SpaceId,
        posture: Posture,
        plan: List<PlannedSlot>,
    ) {
        dao.clearLayout(space.key, posture.key)
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
            snapshot.space.key,
            snapshot.posture.key,
            snapshot.rows.map { it.copy(id = 0) },
        )
        undoSnapshot = null
        return true
    }

    /** First index not already occupied, scanning pages in reading order. */
    private fun nextFreeSlot(existing: List<HomeItemEntity>, grid: GridSpec): Int {
        val taken = existing
            .filter { it.container == HomeItemEntity.CONTAINER_DESKTOP }
            .mapTo(mutableSetOf()) { it.pageIndex * grid.cellsPerPage + it.cellY * grid.columns + it.cellX }
        var slot = 0
        while (slot in taken) slot++
        return slot
    }

    private fun AppEntry.toEntity(
        space: SpaceId,
        posture: Posture,
        grid: GridSpec,
        slot: Int,
    ) = HomeItemEntity(
        spaceKey = space.key,
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
        posture: Posture,
        rows: List<HomeItemEntity>,
        pageRows: List<HomePageEntity>,
        apps: List<AppEntry>,
    ): HomeLayout {
        val grid = GridSpec.of(space, posture)
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

        val kinds = pageRows.associate { it.pageIndex to PageKind.fromKey(it.kind) }
        val itemsByPage = desktop.groupBy { it.pageIndex }

        // A page exists if it holds something or if it was explicitly declared
        // — a declared-but-empty widget or feed page must not disappear.
        val indices = (itemsByPage.keys + kinds.keys).sorted()

        val pages = indices.map { index ->
            HomePage(
                index = index,
                items = itemsByPage[index].orEmpty().map(::toItem),
                kind = kinds[index] ?: PageKind.Grid,
            )
        }

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
    val space: SpaceId,
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
