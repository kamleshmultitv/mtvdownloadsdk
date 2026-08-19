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
import com.app.mtvdownloader.utils.Constants.DOWNLOAD_STOP_REASON_USER_PAUSED
import com.app.mtvdownloader.utils.Constants.DOWNLOAD_STATUS_DOWNLOADING
import com.app.mtvdownloader.utils.Constants.DOWNLOAD_STATUS_FAILED
import com.app.mtvdownloader.utils.Constants.DOWNLOAD_STATUS_PAUSED
import com.app.mtvdownloader.utils.Constants.DOWNLOAD_STATUS_QUEUED
import com.app.mtvdownloader.utils.Constants.DOWNLOAD_STATUS_REMOVED
import com.app.mtvdownloader.utils.Constants.DRM_LICENSE_STATUS_OFFLINE_VALID
import com.app.mtvdownloader.utils.Constants.DRM_LICENSE_STATUS_REFRESH_FAILED
import com.app.mtvdownloader.utils.Constants.FAILURE_DRM_OFFLINE_LICENSE_FAILED
import com.app.mtvdownloader.utils.DrmDebugLogger
import com.app.mtvdownloader.utils.DrmKeySetUtil
import com.app.mtvdownloader.utils.DownloadStreamType
import com.app.mtvdownloader.utils.DownloadSourceResolver
import com.app.mtvdownloader.utils.OfflineDrmLicenseUtil
import com.app.mtvdownloader.utils.StreamKeyUtil
import kotlinx.coroutines.flow.Flow

/**
 * Repository wrapping Media3 DownloadManager + Room DB.
 * No DI framework required.
 */
@UnstableApi
class DownloadRepository private constructor(
    private val appContext: Context
) {

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
        val existingEntity = dao.getDownloadedContentOnce(contentId)
        val existingDownload = runCatching {
            downloadManager.downloadIndex.getDownload(contentId)
        }.getOrNull()

        val offlineKeySetId =
            existingDownload?.request?.keySetId
                ?: existingEntity?.drmOfflineKeySetId
                ?: DrmKeySetUtil.decode(existingEntity?.drmOfflineKeySetIdBase64)

        if (
            existingEntity?.licenseUri?.isNotBlank() == true &&
            offlineKeySetId != null
        ) {
            OfflineDrmLicenseUtil.releaseWidevineOfflineLicense(
                context = appContext,
                licenseUri = existingEntity.licenseUri,
                keySetId = offlineKeySetId
            )
        }

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

    suspend fun renewWidevineOfflineLicense(
        contentId: String,
        freshLicenseUri: String,
        expiresAt: Long? = null
    ): DownloadedContentEntity? {
        val existing = dao.getDownloadedContentOnce(contentId) ?: return null
        val streamType = DownloadSourceResolver.inferType(existing.contentUrl)
        val isDash = streamType == DownloadStreamType.DASH ||
            existing.contentMimeType == "application/dash+xml" ||
            (streamType == null && existing.licenseUri.isNotBlank())

        if (freshLicenseUri.isBlank() || !isDash) {
            dao.updateFailure(
                contentId = contentId,
                status = DOWNLOAD_STATUS_FAILED,
                failureCode = FAILURE_DRM_OFFLINE_LICENSE_FAILED,
                failureReason = "Widevine renewal requires DASH content and a non-empty license URL",
                retryCount = existing.retryCount,
                maxRetryCount = existing.maxRetryCount
            )
            return dao.getDownloadedContentOnce(contentId)
        }

        val result = try {
            OfflineDrmLicenseUtil.ensureWidevineOfflineLicense(
                context = appContext,
                contentId = contentId,
                contentUri = existing.contentUrl,
                licenseUri = freshLicenseUri,
                streamKeys = StreamKeyUtil.fromString(existing.streamKeys.orEmpty()),
                existingKeySetId = null,
                forceNewLicense = true
            )
        } catch (t: Throwable) {
            DrmDebugLogger.failure(
                stage = "DRM_OFFLINE_LICENSE_FAILED",
                contentId = contentId,
                throwable = t
            )
            dao.updateDrmLicenseState(
                contentId = contentId,
                licenseUri = freshLicenseUri,
                expiresAt = expiresAt ?: existing.drmLicenseExpiresAt,
                lastRefreshAt = System.currentTimeMillis(),
                refreshStatus = DRM_LICENSE_STATUS_REFRESH_FAILED
            )
            return dao.getDownloadedContentOnce(contentId)
        }

        dao.updateDrmLicenseState(
            contentId = contentId,
            licenseUri = freshLicenseUri,
            expiresAt = expiresAt ?: result.expiresAt,
            lastRefreshAt = System.currentTimeMillis(),
            refreshStatus = DRM_LICENSE_STATUS_OFFLINE_VALID
        )
        dao.updateDrmOfflineLicenseState(
            contentId = contentId,
            keySetId = result.keySetId,
            keySetIdBase64 = result.keySetIdBase64,
            expiresAt = expiresAt ?: result.expiresAt,
            lastRefreshAt = System.currentTimeMillis(),
            refreshStatus = DRM_LICENSE_STATUS_OFFLINE_VALID
        )

        return dao.getDownloadedContentOnce(contentId)
    }

    suspend fun hasActiveDownload(): Boolean {
        return dao.countByStatuses(
            listOf(
                DOWNLOAD_STATUS_QUEUED,
                DOWNLOAD_STATUS_DOWNLOADING
            )
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

    suspend fun getDownloadingContent(): List<DownloadedContentEntity> {
        return dao.getContentByStatus(DOWNLOAD_STATUS_DOWNLOADING)
    }

    suspend fun markFailed(
        contentId: String,
        failureCode: String?,
        failureReason: String?,
        retryCount: Int,
        maxRetryCount: Int
    ) {
        dao.updateFailure(
            contentId = contentId,
            status = DOWNLOAD_STATUS_FAILED,
            failureCode = failureCode,
            failureReason = failureReason,
            retryCount = retryCount,
            maxRetryCount = maxRetryCount
        )
    }

    suspend fun updateDrmLicenseState(
        contentId: String,
        licenseUri: String,
        expiresAt: Long?,
        lastRefreshAt: Long?,
        refreshStatus: String?
    ) {
        dao.updateDrmLicenseState(
            contentId = contentId,
            licenseUri = licenseUri,
            expiresAt = expiresAt,
            lastRefreshAt = lastRefreshAt,
            refreshStatus = refreshStatus
        )
    }


    suspend fun pauseDownload(contentId: String) {

        // 1️⃣ Update DB (UI + queue logic)
        dao.updateStatus(
            contentId,
            DOWNLOAD_STATUS_PAUSED
        )

        // 2️⃣ Stop ONLY this Media3 download without deleting cached bytes.
        try {
            downloadManager.setStopReason(
                contentId,
                DOWNLOAD_STOP_REASON_USER_PAUSED
            )
        } catch (t: Throwable) {
            Log.w(
                "DownloadRepository",
                "pauseDownload stop failed for id=$contentId: ${t.message}",
                t
            )
        }
    }

    suspend fun queuePausedDownload(contentId: String) {
        dao.updateStatusAndQueuedAt(
            contentId = contentId,
            status = DOWNLOAD_STATUS_QUEUED,
            queuedAt = System.currentTimeMillis()
        )
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
