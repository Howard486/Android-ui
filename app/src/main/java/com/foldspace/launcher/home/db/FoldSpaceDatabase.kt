package com.foldspace.launcher.home.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [HomeItemEntity::class, HomePageEntity::class],
    version = 5,
    exportSchema = false,
)
abstract class FoldSpaceDatabase : RoomDatabase() {

    abstract fun homeItemDao(): HomeItemDao

    abstract fun homePageDao(): HomePageDao

    companion object {
        /**
         * Adds the quick-launch payload.
         *
         * A nullable column with no default, so every existing row is
         * untouched and reads back as null — which is what every item that is
         * not a quick-launch block should have.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE home_items ADD COLUMN payload TEXT")
            }
        }

        @Volatile
        private var instance: FoldSpaceDatabase? = null

        fun get(context: Context): FoldSpaceDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    FoldSpaceDatabase::class.java,
                    "foldspace.db",
                )
                    // A real migration for 4 -> 5, so this update keeps the
                    // arrangement. The destructive fallback stays for any
                    // path that has no migration declared — it is a floor, not
                    // the plan, and every version bump from here should bring
                    // its own migration rather than lean on it.
                    .addMigrations(MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
