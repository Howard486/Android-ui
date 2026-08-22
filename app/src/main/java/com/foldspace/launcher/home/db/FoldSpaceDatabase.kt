package com.foldspace.launcher.home.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [HomeItemEntity::class, HomePageEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class FoldSpaceDatabase : RoomDatabase() {

    abstract fun homeItemDao(): HomeItemDao

    abstract fun homePageDao(): HomePageDao

    companion object {
        @Volatile
        private var instance: FoldSpaceDatabase? = null

        fun get(context: Context): FoldSpaceDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FoldSpaceDatabase::class.java,
                    "foldspace.db",
                )
                    // The layout is rebuilt from installed apps if it is ever
                    // lost, so a destructive migration costs the user their
                    // arrangement but never leaves the launcher unusable.
                    // Replace with real migrations once V1.0 ships.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
