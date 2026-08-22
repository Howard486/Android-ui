package com.foldspace.launcher.home.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeItemDao {

    @Query(
        """
        SELECT * FROM home_items
        WHERE spaceKey = :spaceKey AND postureKey = :postureKey
        ORDER BY pageIndex, cellY, cellX, sortOrder
        """,
    )
    fun observeLayout(spaceKey: String, postureKey: String): Flow<List<HomeItemEntity>>

    @Query(
        """
        SELECT * FROM home_items
        WHERE spaceKey = :spaceKey AND postureKey = :postureKey
        ORDER BY pageIndex, cellY, cellX, sortOrder
        """,
    )
    suspend fun getLayout(spaceKey: String, postureKey: String): List<HomeItemEntity>

    @Query(
        "SELECT COUNT(*) FROM home_items WHERE spaceKey = :spaceKey AND postureKey = :postureKey",
    )
    suspend fun countIn(spaceKey: String, postureKey: String): Int

    /** Every app component currently placed anywhere, for install/remove sync. */
    @Query("SELECT DISTINCT packageName FROM home_items WHERE itemType = 'app' AND packageName IS NOT NULL")
    suspend fun placedPackages(): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: HomeItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(items: List<HomeItemEntity>): List<Long>

    @Update
    suspend fun update(item: HomeItemEntity)

    @Query("DELETE FROM home_items WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Removing an app removes it wherever it sits, in every Space and both
     * postures — an uninstalled app has no business keeping a cell.
     */
    @Query("DELETE FROM home_items WHERE itemType = 'app' AND packageName = :packageName")
    suspend fun deleteByPackage(packageName: String)

    @Query("DELETE FROM home_items WHERE spaceKey = :spaceKey AND postureKey = :postureKey")
    suspend fun clearLayout(spaceKey: String, postureKey: String)

    @Transaction
    suspend fun replaceLayout(spaceKey: String, postureKey: String, items: List<HomeItemEntity>) {
        clearLayout(spaceKey, postureKey)
        insertAll(items)
    }
}
