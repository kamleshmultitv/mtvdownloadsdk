package com.app.mtvdownloader.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.app.mtvdownloader.local.dao.DownloadedContentDao
import com.app.mtvdownloader.local.entity.DownloadedContentEntity

@Database(
    entities = [DownloadedContentEntity::class],
    version = 5,
    exportSchema = true
)
abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloadedContentDao(): DownloadedContentDao

    companion object {
        @Volatile
        private var INSTANCE: DownloadDatabase? = null

        // Version 2 keeps the same schema. This preserves installs that already saw
        // DB version 2 without adding columns Room does not expect.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) = Unit
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloaded_content ADD COLUMN queuedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE downloaded_content ADD COLUMN failureCode TEXT")
                db.execSQL("ALTER TABLE downloaded_content ADD COLUMN failureReason TEXT")
                db.execSQL("ALTER TABLE downloaded_content ADD COLUMN retryCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE downloaded_content ADD COLUMN maxRetryCount INTEGER NOT NULL DEFAULT 3")
                db.execSQL("ALTER TABLE downloaded_content ADD COLUMN drmLicenseExpiresAt INTEGER")
                db.execSQL("ALTER TABLE downloaded_content ADD COLUMN drmLicenseLastRefreshAt INTEGER")
                db.execSQL("ALTER TABLE downloaded_content ADD COLUMN drmLicenseRefreshStatus TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloaded_content ADD COLUMN drmOfflineKeySetId BLOB")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloaded_content ADD COLUMN drmOfflineKeySetIdBase64 TEXT")
                db.execSQL("ALTER TABLE downloaded_content ADD COLUMN drmScheme TEXT")
                db.execSQL("ALTER TABLE downloaded_content ADD COLUMN drmKeyId TEXT")
                db.execSQL("ALTER TABLE downloaded_content ADD COLUMN contentMimeType TEXT")
            }
        }

        fun getInstance(context: Context): DownloadDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    DownloadDatabase::class.java,
                    "download_db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5
                    )
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
