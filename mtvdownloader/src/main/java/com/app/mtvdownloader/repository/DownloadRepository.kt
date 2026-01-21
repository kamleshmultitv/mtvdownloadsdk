package com.app.mtvdownloader.repository

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadManager
import com.app.mtvdownloader.DownloadUtil
import com.app.mtvdownloader.local.dao.DownloadedContentDao
import com.app.mtvdownloader.local.database.DownloadDatabase
import com.app.mtvdownloader.local.entity.DownloadedContentEntity
import com.app.mtvdownloader.utils.Constants.DOWNLOAD_STATUS_DOWNLOADING
import com.app.mtvdownloader.utils.Constants.DOWNLOAD_STATUS_PAUSED
import com.app.mtvdownloader.utils.Constants.DOWNLOAD_STATUS_QUEUED
import com.app.mtvdownloader.utils.Constants.DOWNLOAD_STATUS_REMOVED
import kotlinx.coroutines.flow.Flow

/**
 * Repository wrapping Media3 DownloadManager + Room DB.
 * No DI framework required.
 */
@UnstableApi
class DownloadRepository private constructor(appContext: Context) {

    private val dao: DownloadedContentDao =
        DownloadDatabase.getInstance(appContext).downloadedContentDao()

    private val downloadManager: DownloadManager =
        DownloadUtil.getDownloadManager(appContext)

    /* -------------------- READ -------------------- */

    fun getDownloadedContent(contentId: String): Flow<DownloadedContentEntity?> =
        dao.getDownloadedContent(contentId)

    fun getAllDownloadedContent(): Flow<List<DownloadedContentEntity>> =
        dao.getAllDownloadedContent()

    suspend fun getDownloadedContentOnce(
        contentId: String
    ): DownloadedContentEntity? =
        dao.getDownloadedContentOnce(contentId)

    /* -------------------- DELETE -------------------- */

    suspend fun deleteDownload(contentId: String) {

        // 1️⃣ Update DB first (UI reacts immediately)
        dao.updateStatus(
            contentId,
            DOWNLOAD_STATUS_REMOVED
        )

        // 2️⃣ Remove from Media3
        try {
            downloadManager.removeDownload(contentId)
        } catch (t: Throwable) {
            Log.w(
                "DownloadRepository",
                "removeDownload failed for id=$contentId: ${t.message}",
                t
            )
        }

        // 3️⃣ Final DB cleanup
        dao.delete(contentId)
    }

    suspend fun hasActiveDownload(): Boolean {
        return dao.hasActiveDownload(
            DOWNLOAD_STATUS_DOWNLOADING
        ) > 0
    }

    suspend fun insertOrUpdate(entity: DownloadedContentEntity) {
        dao.insert(entity)
    }

    suspend fun getNextQueuedContent(): DownloadedContentEntity? {
        return dao.getNextQueuedContent(
            DOWNLOAD_STATUS_QUEUED
        )
    }


    suspend fun pauseDownload(contentId: String) {

        // 1️⃣ Update DB (UI + queue logic)
        dao.updateStatus(
            contentId,
            DOWNLOAD_STATUS_PAUSED
        )

        // 2️⃣ Remove ONLY this download from Media3
        try {
            downloadManager.removeDownload(contentId)
        } catch (t: Throwable) {
            Log.w(
                "DownloadRepository",
                "pauseDownload remove failed for id=$contentId: ${t.message}",
                t
            )
        }
    }

    companion object {

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var INSTANCE: DownloadRepository? = null

        fun instance(context: Context): DownloadRepository {
            val appContext = context.applicationContext
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DownloadRepository(appContext)
                    .also { INSTANCE = it }
            }
        }
    }
}