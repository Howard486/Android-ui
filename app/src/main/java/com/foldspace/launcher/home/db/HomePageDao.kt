package com.foldspace.launcher.home.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class HomePageDao {

    @Query(
        """
        SELECT * FROM home_pages
        WHERE surfaceKey = :surfaceKey AND postureKey = :postureKey
        ORDER BY pageIndex
        """,
    )
    abstract fun observePages(surfaceKey: String, postureKey: String): Flow<List<HomePageEntity>>

    @Query(
        """
        SELECT * FROM home_pages
        WHERE surfaceKey = :surfaceKey AND postureKey = :postureKey
        ORDER BY pageIndex
        """,
    )
    abstract suspend fun getPages(surfaceKey: String, postureKey: String): List<HomePageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(page: HomePageEntity)

    @Query(
        """
        DELETE FROM home_pages
        WHERE surfaceKey = :surfaceKey AND postureKey = :postureKey AND pageIndex = :pageIndex
        """,
    )
    abstract suspend fun delete(surfaceKey: String, postureKey: String, pageIndex: Int)

    @Query("DELETE FROM home_pages WHERE surfaceKey = :surfaceKey AND postureKey = :postureKey")
    abstract suspend fun clear(surfaceKey: String, postureKey: String)

    /**
     * Renumbers a declared page.
     *
     * A raw UPDATE rather than `@Update`, which matches by primary key and so
     * cannot change one — and `pageIndex` is part of this table's key.
     */
    @Query(
        """
        UPDATE home_pages SET pageIndex = :toPage
        WHERE surfaceKey = :surfaceKey AND postureKey = :postureKey AND pageIndex = :fromPage
        """,
    )
    abstract suspend fun movePage(
        surfaceKey: String,
        postureKey: String,
        fromPage: Int,
        toPage: Int,
    )

    /** Two-phase for the same reason as the items table: the key would collide. */
    @Transaction
    open suspend fun applyPageMoves(
        surfaceKey: String,
        postureKey: String,
        moves: List<Pair<Int, Int>>,
    ) {
        if (moves.isEmpty()) return

        moves.forEach { (oldPage, _) ->
            movePage(surfaceKey, postureKey, oldPage, HomeItemDao.REORDER_PARK_BASE - oldPage)
        }
        moves.forEach { (oldPage, newPage) ->
            movePage(surfaceKey, postureKey, HomeItemDao.REORDER_PARK_BASE - oldPage, newPage)
        }
    }
}
