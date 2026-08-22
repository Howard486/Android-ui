package com.foldspace.launcher.home

import com.foldspace.launcher.core.launcher.AppEntry
import com.foldspace.launcher.core.launcher.ProfileType
import com.foldspace.launcher.home.db.HomeItemDao
import com.foldspace.launcher.home.db.HomeItemEntity
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
    private val installedApps: Flow<List<AppEntry>>,
) {

    /**
     * The layout as the UI consumes it: rows joined against the live installed
     * list, so an app that vanished shows as unavailable rather than as a
     * dangling row.
     */
    fun observe(space: SpaceId, posture: Posture): Flow<HomeLayout> =
        combine(
            dao.observeLayout(space.key, posture.key),
            installedApps,
        ) { rows, apps -> resolve(space, posture, rows, apps) }

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

        val pages = desktop
            .groupBy { it.pageIndex }
            .toSortedMap()
            .map { (index, items) -> HomePage(index, items.map(::toItem)) }

        return HomeLayout(space = space, posture = posture, grid = grid, pages = pages)
    }
}
