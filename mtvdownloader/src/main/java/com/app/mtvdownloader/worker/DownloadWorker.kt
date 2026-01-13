package com.app.mtvdownloader.worker

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download.*
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.app.mtvdownloader.DownloadUtil
import com.app.mtvdownloader.local.database.DownloadDatabase
import com.app.mtvdownloader.local.entity.DownloadedContentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.util.Date

@OptIn(UnstableApi::class)
class DownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val TAG = "DownloadWorker"
    private val dao = DownloadDatabase
        .getInstance(context)
        .downloadedContentDao()

    companion object {
        const val KEY_HLS_URI = "hls_uri"
        const val KEY_CONTENT_ID = "content_id"
        const val KEY_SEASON_ID = "season_id"
        const val KEY_CONTENT_TITLE = "content_title"
        const val KEY_SEASON_NAME = "season_name"
        const val KEY_THUMBNAIL_URL = "thumbnail_url"
        const val KEY_SEASON_THUMBNAIL_URL = "season_thumbnail_url"

        const val DOWNLOAD_STATUS_QUEUED = "queued"
        const val DOWNLOAD_STATUS_DOWNLOADING = "downloading"
        const val DOWNLOAD_STATUS_COMPLETED = "completed"
        const val DOWNLOAD_STATUS_FAILED = "failed"
        const val DOWNLOAD_STATUS_REMOVED = "removed"
    }

    override suspend fun doWork(): Result {

        val hlsUri = inputData.getString(KEY_HLS_URI) ?: return Result.failure()
        val contentId = inputData.getString(KEY_CONTENT_ID) ?: return Result.failure()
        val seasonId = inputData.getString(KEY_SEASON_ID).orEmpty()
        val title = inputData.getString(KEY_CONTENT_TITLE).orEmpty()
        val seasonName = inputData.getString(KEY_SEASON_NAME).orEmpty()
        val thumb = inputData.getString(KEY_THUMBNAIL_URL)
        val seasonImage = inputData.getString(KEY_SEASON_THUMBNAIL_URL)

        // 🔥 ALWAYS UPSERT AS QUEUED
        withContext(Dispatchers.IO) {
            dao.insert(
                DownloadedContentEntity(
                    contentId = contentId,
                    seasonId = seasonId,
                    title = title,
                    seasonName = seasonName,
                    hlsUrl = hlsUri,
                    thumbnailUrl = thumb,
                    seasonImage = seasonImage,
                    downloadStatus = DOWNLOAD_STATUS_QUEUED,
                    downloadProgress = 0
                )
            )
        }

        val downloadManager = DownloadUtil.getDownloadManager(applicationContext)
        val request = DownloadUtil.buildDownloadRequest(contentId, hlsUri)

        try {
            downloadManager.addDownload(request)
            downloadManager.resumeDownloads()
        } catch (t: Throwable) {
            Log.w(TAG, "add/resume failed: ${t.message}")
        }

        var lastProgress = -1
        var lastStatus: String? = null

        while (coroutineContext.isActive) {

            val download = try {
                downloadManager.downloadIndex.getDownload(contentId)
            } catch (e: Exception) {
                null
            }

            if (download == null) {
                delay(500)
                continue
            }

            val (status, progress) = when (download.state) {

                STATE_QUEUED ->
                    DOWNLOAD_STATUS_QUEUED to 0

                STATE_DOWNLOADING -> {
                    val pct =
                        if (download.contentLength > 0)
                            ((download.bytesDownloaded * 100) / download.contentLength).toInt()
                        else download.percentDownloaded.toInt().coerceIn(0, 100)

                    DOWNLOAD_STATUS_DOWNLOADING to pct
                }

                STATE_COMPLETED -> {
                    withContext(Dispatchers.IO) {
                        dao.updateProgressAndStatus(
                            contentId,
                            100,
                            DOWNLOAD_STATUS_COMPLETED,
                            Date().time,
                            DownloadUtil.getDownloadPath(contentId)
                        )
                    }
                    return Result.success()
                }

                STATE_FAILED -> {
                    withContext(Dispatchers.IO) {
                        dao.updateStatus(contentId, DOWNLOAD_STATUS_FAILED)
                    }
                    return Result.failure()
                }

                else -> {
                    delay(500)
                    continue
                }
            }

            // 🔥 FORCE QUEUED STATUS TO DB
            if (status != lastStatus || progress != lastProgress || status == DOWNLOAD_STATUS_QUEUED) {

                setProgress(
                    Data.Builder()
                        .putInt("download_progress", progress)
                        .putString("download_status", status)
                        .build()
                )

                withContext(Dispatchers.IO) {
                    dao.updateProgressAndStatus(
                        contentId,
                        progress,
                        status,
                        null,
                        null
                    )
                }

                lastStatus = status
                lastProgress = progress
            }

            delay(1000)
        }

        return Result.failure()
    }
}
