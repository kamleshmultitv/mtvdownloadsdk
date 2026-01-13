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

    @Query("SELECT * FROM downloaded_content WHERE contentId = :contentId")
    fun getDownloadedContent(contentId: String): Flow<DownloadedContentEntity?>

    @Query("SELECT * FROM downloaded_content WHERE contentId = :contentId LIMIT 1")
    suspend fun getDownloadedContentOnce(contentId: String): DownloadedContentEntity?

    @Query("SELECT * FROM downloaded_content WHERE hlsUrl = :hlsUrl LIMIT 1")
    suspend fun getDownloadedContentByHlsUrl(hlsUrl: String): DownloadedContentEntity?

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
}

