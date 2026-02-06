package com.app.mtvdownloader.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.app.mtvdownloader.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadedContentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(content: DownloadEntity)

    @Query("DELETE FROM downloaded_content WHERE contentId = :contentId")
    suspend fun deleteByContentId(contentId: String)

    @Query("SELECT * FROM downloaded_content WHERE contentId = :contentId")
    fun getDownloadedContent(contentId: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM downloaded_content WHERE contentId = :contentId LIMIT 1")
    suspend fun getDownloadedContentOnce(contentId: String): DownloadEntity?

    @Query("SELECT * FROM downloaded_content WHERE mpdUrl = :contentUrl LIMIT 1")
    suspend fun getDownloadedContentByContentUrl(contentUrl: String): DownloadEntity?

    @Query("SELECT * FROM downloaded_content ORDER BY timeStamp DESC")
    fun getAllDownloadedContent(): Flow<List<DownloadEntity>>

    @Query(
        """
        UPDATE downloaded_content
        SET progress = :progress,
            downloadStatus = :status,
            timeStamp = COALESCE(:downloadedAt, timeStamp),
            mediaPath = COALESCE(:localFilePath, mediaPath)
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
        ORDER BY timeStamp ASC
        LIMIT 1
        """
    )
    suspend fun getNextQueuedContent(status: String): DownloadEntity?

    // Get DRM keySetId (as ByteArray? if stored as BLOB)
    @Query(
        """
        SELECT keySetId FROM downloaded_content
        WHERE contentId = :contentId
        LIMIT 1
        """
    )
    suspend fun getDrmKeySetId(contentId: String): ByteArray?

    // Update DRM keySetId
    @Query(
        """
        UPDATE downloaded_content
        SET keySetId = :keySetId
        WHERE contentId = :contentId
        """
    )
    suspend fun updateDrmKeySetId(
        contentId: String,
        keySetId: ByteArray?
    )
}
