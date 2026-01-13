package com.app.mtvdownloader.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.app.mtvdownloader.local.dao.DownloadedContentDao
import com.app.mtvdownloader.local.entity.DownloadedContentEntity

// bump version to 2 because we add a migration from 1 -> 2
@Database(
    entities = [DownloadedContentEntity::class],
    version = 1
)
abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloadedContentDao(): DownloadedContentDao

    companion object {
        @Volatile
        private var INSTANCE: DownloadDatabase? = null

        // Example migration: add a new column "notes" to the RemindMeEntity table.
        // Adjust table/column names to match your actual @Entity(tableName = "...") if you use custom names.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1) Add column 'notes' with default empty string so existing rows get value.
                // If you want NULL default, drop DEFAULT clause and set nullable type in entity.
                db.execSQL("ALTER TABLE DownloadedContentEntity ADD COLUMN notes TEXT NOT NULL DEFAULT ''")

                // 2) If you need to create a new table in v2, you can do it here:
                // db.execSQL("""
                //   CREATE TABLE IF NOT EXISTS `NewEntity` (
                //     `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                //     `title` TEXT
                //   )
                // """.trimIndent())

                // 3) If you need to populate data or migrate values, you can run INSERT/UPDATEs here.
                // Example: copy data from an old table to a new normalized table, etc.
            }
        }

        fun getInstance(context: Context): DownloadDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DownloadDatabase::class.java,
                    "download_db"
                )
                    // add migrations you implement
                    .addMigrations(MIGRATION_1_2)
                    // do NOT call fallbackToDestructiveMigration() if you want to preserve user data.
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
