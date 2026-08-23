package com.foldspace.launcher.home.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Declared as an abstract class rather than an interface: Room's handling of
 * suspend `@Transaction` default methods is well-trodden on abstract classes
 * and has historically been fussy on interfaces, and the multi-step swaps
 * below are exactly the case that needs them.
 */
@Dao
abstract class HomeItemDao {

    @Query(
        """
        SELECT * FROM home_items
        WHERE surfaceKey = :surfaceKey AND postureKey = :postureKey
        ORDER BY pageIndex, cellY, cellX, sortOrder
        """,
    )
    abstract fun observeLayout(surfaceKey: String, postureKey: String): Flow<List<HomeItemEntity>>

    @Query(
        """
        SELECT * FROM home_items
        WHERE surfaceKey = :surfaceKey AND postureKey = :postureKey
        ORDER BY pageIndex, cellY, cellX, sortOrder
        """,
    )
    abstract suspend fun getLayout(surfaceKey: String, postureKey: String): List<HomeItemEntity>

    @Query(
        "SELECT COUNT(*) FROM home_items WHERE surfaceKey = :surfaceKey AND postureKey = :postureKey",
    )
    abstract suspend fun countIn(surfaceKey: String, postureKey: String): Int

    @Query("SELECT * FROM home_items WHERE id = :id")
    abstract suspend fun getById(id: Long): HomeItemEntity?

    @Query(
        """
        SELECT * FROM home_items
        WHERE surfaceKey = :surfaceKey AND postureKey = :postureKey
          AND container = -1 AND pageIndex = :pageIndex
          AND cellX = :cellX AND cellY = :cellY
        LIMIT 1
        """,
    )
    abstract suspend fun itemAt(
        surfaceKey: String,
        postureKey: String,
        pageIndex: Int,
        cellX: Int,
        cellY: Int,
    ): HomeItemEntity?

    @Query("SELECT * FROM home_items WHERE container = :folderId ORDER BY sortOrder")
    abstract suspend fun folderMembers(folderId: Long): List<HomeItemEntity>

    /** Every app package currently placed anywhere, for install/remove sync. */
    @Query("SELECT DISTINCT packageName FROM home_items WHERE itemType = 'app' AND packageName IS NOT NULL")
    abstract suspend fun placedPackages(): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insert(item: HomeItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertAll(items: List<HomeItemEntity>): List<Long>

    @Update
    abstract suspend fun update(item: HomeItemEntity)

    @Query("DELETE FROM home_items WHERE id = :id")
    abstract suspend fun deleteById(id: Long)

    /**
     * Removing an app removes it wherever it sits, on every surface and in both
     * postures — an uninstalled app has no business keeping a cell.
     */
    @Query(
        """
        DELETE FROM home_items
        WHERE packageName = :packageName AND itemType IN ('app', 'shortcut')
        """,
    )
    abstract suspend fun deleteByPackage(packageName: String)

    @Query("DELETE FROM home_items WHERE surfaceKey = :surfaceKey AND postureKey = :postureKey")
    abstract suspend fun clearLayout(surfaceKey: String, postureKey: String)

    @Transaction
    open suspend fun replaceLayout(surfaceKey: String, postureKey: String, items: List<HomeItemEntity>) {
        clearLayout(surfaceKey, postureKey)
        insertAll(items)
    }

    /**
     * Rewrites the desktop rows' cells, keeping their ids.
     *
     * Ids matter here: folder members point at their folder's id, so the
     * delete-and-reinsert that [replaceLayout] does would orphan every folder.
     *
     * Every row is parked on a scratch page first. Writing final positions one
     * at a time would collide with rows that have not moved yet, and the
     * unique index turns that into a failure rather than a silent overwrite —
     * which is the point of the index, but it has to be worked with.
     */
    @Transaction
    open suspend fun repackDesktop(rows: List<HomeItemEntity>) {
        rows.forEachIndexed { index, row ->
            update(row.copy(pageIndex = REPACK_PAGE, cellX = index, cellY = 0))
        }
        rows.forEach { update(it) }
    }

    /**
     * Moves an item to a cell, swapping with whatever is already there.
     *
     * The unique index on (surface, posture, container, page, x, y) makes a naive
     * two-step swap fail halfway, so the moving row is parked on a sentinel
     * cell first. The whole thing is one transaction — a swap that got half way
     * would leave two icons stacked, which is precisely what that index exists
     * to prevent.
     */
    @Transaction
    open suspend fun moveToCell(id: Long, pageIndex: Int, cellX: Int, cellY: Int) {
        val moving = getById(id) ?: return
        if (moving.container == HomeItemEntity.CONTAINER_DESKTOP &&
            moving.pageIndex == pageIndex && moving.cellX == cellX && moving.cellY == cellY
        ) {
            return
        }

        val occupant = itemAt(moving.surfaceKey, moving.postureKey, pageIndex, cellX, cellY)

        update(moving.copy(pageIndex = PARK_PAGE, cellX = PARK_CELL, cellY = PARK_CELL))

        if (occupant != null && occupant.id != moving.id) {
            // A swap only makes sense from a desktop cell. An item dragged out
            // of a folder has no cell to give back, so the occupant stays put
            // and the caller is expected to have picked a free cell.
            if (moving.container == HomeItemEntity.CONTAINER_DESKTOP) {
                update(
                    occupant.copy(
                        pageIndex = moving.pageIndex,
                        cellX = moving.cellX,
                        cellY = moving.cellY,
                    ),
                )
            } else {
                return
            }
        }

        update(
            moving.copy(
                container = HomeItemEntity.CONTAINER_DESKTOP,
                pageIndex = pageIndex,
                cellX = cellX,
                cellY = cellY,
                sortOrder = 0,
            ),
        )
    }

    /**
     * Turns two apps that met on one cell into a folder holding both. The
     * folder takes the target's cell, which is where the user dropped.
     */
    @Transaction
    open suspend fun mergeIntoFolder(movingId: Long, targetId: Long, title: String): Long {
        val moving = getById(movingId) ?: return -1L
        val target = getById(targetId) ?: return -1L
        if (moving.id == target.id) return -1L

        val folderId = insert(
            HomeItemEntity(
                surfaceKey = target.surfaceKey,
                postureKey = target.postureKey,
                pageIndex = PARK_PAGE,
                cellX = PARK_CELL,
                cellY = PARK_CELL,
                itemType = "folder",
                folderTitle = title,
            ),
        )

        // The unique index covers `container` too, so members of one folder
        // must not all share a cell — their slot number doubles as cellX.
        update(target.copy(container = folderId).atFolderSlot(0))
        update(moving.copy(container = folderId).atFolderSlot(1))

        getById(folderId)?.let {
            update(it.copy(pageIndex = target.pageIndex, cellX = target.cellX, cellY = target.cellY))
        }
        return folderId
    }

    @Transaction
    open suspend fun addToFolder(itemId: Long, folderId: Long) {
        val item = getById(itemId) ?: return
        if (item.id == folderId) return
        val nextOrder = folderMembers(folderId).size
        update(item.copy(container = folderId).atFolderSlot(nextOrder))
    }

    /**
     * Pulls an item back out onto a cell. A folder left holding one app is
     * dissolved: a one-app folder is pure overhead, two taps where one would
     * do.
     */
    @Transaction
    open suspend fun removeFromFolder(itemId: Long, pageIndex: Int, cellX: Int, cellY: Int) {
        val item = getById(itemId) ?: return
        val folderId = item.container
        if (folderId == HomeItemEntity.CONTAINER_DESKTOP) return

        update(
            item.copy(
                container = HomeItemEntity.CONTAINER_DESKTOP,
                pageIndex = pageIndex,
                cellX = cellX,
                cellY = cellY,
                sortOrder = 0,
            ),
        )

        val remaining = folderMembers(folderId)
        if (remaining.size > 1) {
            // Close the gap the departing member left, or the next addToFolder
            // computes a slot that is already taken.
            remaining.forEachIndexed { index, member -> update(member.atFolderSlot(index)) }
            return
        }

        val folder = getById(folderId) ?: return
        val survivor = remaining.firstOrNull()
        update(folder.copy(pageIndex = PARK_PAGE, cellX = PARK_CELL, cellY = PARK_CELL))
        if (survivor != null) {
            update(
                survivor.copy(
                    container = HomeItemEntity.CONTAINER_DESKTOP,
                    pageIndex = folder.pageIndex,
                    cellX = folder.cellX,
                    cellY = folder.cellY,
                    sortOrder = 0,
                ),
            )
        }
        deleteById(folderId)
    }

    @Query("UPDATE home_items SET folderTitle = :title WHERE id = :folderId")
    abstract suspend fun renameFolder(folderId: Long, title: String)

    /**
     * Resizing touches only the span: the anchor cell stays put, so the unique
     * index is untouched and no parking dance is needed.
     */
    @Query("UPDATE home_items SET spanX = :spanX, spanY = :spanY WHERE id = :id")
    abstract suspend fun setSpan(id: Long, spanX: Int, spanY: Int)

    /**
     * Positions a folder member. Members live off-page, and their slot number
     * is stored in cellX so that the unique index — which includes `container`
     * — still distinguishes them from each other.
     */
    private fun HomeItemEntity.atFolderSlot(slot: Int) = copy(
        pageIndex = PARK_PAGE,
        cellX = slot,
        cellY = 0,
        sortOrder = slot,
    )

    companion object {
        /**
         * A page no real page can be, used both to park a row mid-swap and to
         * hold folder members, without tripping the unique index.
         */
        const val PARK_PAGE = -99
        const val PARK_CELL = -99

        /**
         * A second sentinel page, distinct from [PARK_PAGE] so that a repack
         * in progress cannot land on a cell a folder member is holding.
         */
        const val REPACK_PAGE = -98
    }
}
