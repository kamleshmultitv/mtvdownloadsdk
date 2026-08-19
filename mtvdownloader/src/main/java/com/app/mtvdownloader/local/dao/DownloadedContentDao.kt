package com.app.mtvdownloader.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.app.mtvdownloader.local.entity.DownloadedContentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedContentDao {
    @Query("DELETE FROM downloaded_content WHERE contentId = :contentId")
    suspend fun deleteByContentId(contentId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(content: DownloadedContentEntity)

    @Query("SELECT * FROM downloaded_content WHERE contentId = :contentId")
    fun getDownloadedContent(contentId: String): Flow<DownloadedContentEntity?>

    @Query("SELECT * FROM downloaded_content WHERE contentId = :contentId LIMIT 1")
    suspend fun getDownloadedContentOnce(contentId: String): DownloadedContentEntity?

    @Query("SELECT * FROM downloaded_content WHERE contentUrl = :contentUrl LIMIT 1")
    suspend fun getDownloadedContentByContentUrl(contentUrl: String): DownloadedContentEntity?

    @Query("SELECT * FROM downloaded_content ORDER BY downloadedAt DESC")
    fun getAllDownloadedContent(): Flow<List<DownloadedContentEntity>>

    @Query("SELECT * FROM downloaded_content WHERE downloadStatus = :status ORDER BY queuedAt ASC, contentId ASC")
    suspend fun getContentByStatus(status: String): List<DownloadedContentEntity>

    @Query(
        """
        UPDATE downloaded_content
        SET downloadProgress = :progress,
            downloadStatus = :status,
            downloadedAt = COALESCE(:downloadedAt, downloadedAt),
            localFilePath = COALESCE(:localFilePath, localFilePath)
        WHERE contentId = :contentId
    """
    )
    suspend fun updateProgressAndStatus(
        contentId: String,
        progress: Int,
        status: String,
        downloadedAt: Long?,
        localFilePath: String?
    )

    @Query(
        """
        UPDATE downloaded_content
        SET downloadStatus = :status
        WHERE contentId = :contentId
    """
    )
    suspend fun updateStatus(
        contentId: String,
        status: String
    )

    @Query(
        """
        UPDATE downloaded_content
        SET downloadStatus = :status,
            queuedAt = :queuedAt
        WHERE contentId = :contentId
    """
    )
    suspend fun updateStatusAndQueuedAt(
        contentId: String,
        status: String,
        queuedAt: Long
    )

    @Query(
        """
        UPDATE downloaded_content
        SET downloadStatus = :status,
            failureCode = :failureCode,
            failureReason = :failureReason,
            retryCount = :retryCount,
            maxRetryCount = :maxRetryCount
        WHERE contentId = :contentId
    """
    )
    suspend fun updateFailure(
        contentId: String,
        status: String,
        failureCode: String?,
        failureReason: String?,
        retryCount: Int,
        maxRetryCount: Int
    )

    @Query(
        """
        UPDATE downloaded_content
        SET licenseUri = :licenseUri,
            drmLicenseExpiresAt = :expiresAt,
            drmLicenseLastRefreshAt = :lastRefreshAt,
            drmLicenseRefreshStatus = :refreshStatus
        WHERE contentId = :contentId
    """
    )
    suspend fun updateDrmLicenseState(
        contentId: String,
        licenseUri: String,
        expiresAt: Long?,
        lastRefreshAt: Long?,
        refreshStatus: String?
    )

    @Query(
        """
        UPDATE downloaded_content
        SET drmOfflineKeySetId = :keySetId,
            drmOfflineKeySetIdBase64 = :keySetIdBase64,
            drmLicenseExpiresAt = :expiresAt,
            drmLicenseLastRefreshAt = :lastRefreshAt,
            drmLicenseRefreshStatus = :refreshStatus
        WHERE contentId = :contentId
    """
    )
    suspend fun updateDrmOfflineLicenseState(
        contentId: String,
        keySetId: ByteArray?,
        keySetIdBase64: String?,
        expiresAt: Long?,
        lastRefreshAt: Long?,
        refreshStatus: String?
    )

    @Query("DELETE FROM downloaded_content WHERE contentId = :contentId")
    suspend fun delete(contentId: String)

    @Query(
        """
SELECT COUNT(*) FROM downloaded_content
WHERE downloadStatus IN (:statuses)
"""
    )
    suspend fun countByStatuses(statuses: List<String>): Int

    @Query(
        """
    SELECT * FROM downloaded_content
    WHERE downloadStatus = :status
    ORDER BY queuedAt ASC, contentId ASC
    LIMIT 1
    """
    )
    suspend fun getNextQueuedContent(status: String): DownloadedContentEntity?

}
