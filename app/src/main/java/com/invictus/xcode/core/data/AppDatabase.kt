package com.invictus.xcode.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RecentProjectEntity::class, PinEntity::class, SearchQueryEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recentProjectDao(): RecentProjectDao
    abstract fun pinDao(): PinDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `search_queries` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`kind` TEXT NOT NULL, `query` TEXT NOT NULL, " +
                        "`optionsJson` TEXT NOT NULL, `usedAt` INTEGER NOT NULL)",
                )
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "xcode.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
