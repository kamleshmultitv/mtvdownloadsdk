package com.app.mtvdownloader.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.app.mtvdownloader.local.entity.DownloadedContentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedContentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(content: DownloadedContentEntity)

    @Query("DELETE FROM downloaded_content WHERE contentId = :contentId")
    suspend fun deleteByContentId(contentId: String)

    @Query("SELECT * FROM downloaded_content WHERE contentId = :contentId")
    fun getDownloadedContent(contentId: String): Flow<DownloadedContentEntity?>

    @Query("SELECT * FROM downloaded_content WHERE contentId = :contentId LIMIT 1")
    suspend fun getDownloadedContentOnce(contentId: String): DownloadedContentEntity?

    @Query("SELECT * FROM downloaded_content WHERE contentUrl = :contentUrl LIMIT 1")
    suspend fun getDownloadedContentByContentUrl(contentUrl: String): DownloadedContentEntity?

    @Query("SELECT * FROM downloaded_content ORDER BY downloadedAt DESC")
    fun getAllDownloadedContent(): Flow<List<DownloadedContentEntity>>

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

    @Query("DELETE FROM downloaded_content WHERE contentId = :contentId")
    suspend fun delete(contentId: String)

    @Query(
        """
        SELECT COUNT(*) FROM downloaded_content
        WHERE downloadStatus = :downloading
        """
    )
    suspend fun hasActiveDownload(downloading: String): Int

    @Query(
        """
        SELECT * FROM downloaded_content
        WHERE downloadStatus = :status
        ORDER BY downloadedAt ASC
        LIMIT 1
        """
    )
    suspend fun getNextQueuedContent(status: String): DownloadedContentEntity?

    // Get DRM keySetId (as ByteArray? if stored as BLOB)
    @Query(
        """
        SELECT drmKeySetId FROM downloaded_content
        WHERE contentId = :contentId
        LIMIT 1
        """
    )
    suspend fun getDrmKeySetId(contentId: String): ByteArray?

    // Update DRM keySetId
    @Query(
        """
        UPDATE downloaded_content
        SET drmKeySetId = :keySetId
        WHERE contentId = :contentId
        """
    )
    suspend fun updateDrmKeySetId(
        contentId: String,
        keySetId: ByteArray?
    )
}
