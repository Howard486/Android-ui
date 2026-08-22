package com.foldspace.launcher.home.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
abstract class HomePageDao {

    @Query(
        """
        SELECT * FROM home_pages
        WHERE spaceKey = :spaceKey AND postureKey = :postureKey
        ORDER BY pageIndex
        """,
    )
    abstract fun observePages(spaceKey: String, postureKey: String): Flow<List<HomePageEntity>>

    @Query(
        """
        SELECT * FROM home_pages
        WHERE spaceKey = :spaceKey AND postureKey = :postureKey
        ORDER BY pageIndex
        """,
    )
    abstract suspend fun getPages(spaceKey: String, postureKey: String): List<HomePageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(page: HomePageEntity)

    @Query(
        """
        DELETE FROM home_pages
        WHERE spaceKey = :spaceKey AND postureKey = :postureKey AND pageIndex = :pageIndex
        """,
    )
    abstract suspend fun delete(spaceKey: String, postureKey: String, pageIndex: Int)

    @Query("DELETE FROM home_pages WHERE spaceKey = :spaceKey AND postureKey = :postureKey")
    abstract suspend fun clear(spaceKey: String, postureKey: String)
}
