package com.app.mtvdownloader.worker

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.StreamKey
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download.*
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.app.mtvdownloader.DownloadUtil
import com.app.mtvdownloader.local.database.DownloadDatabase
import com.app.mtvdownloader.utils.MediaItemFactory
import com.app.mtvdownloader.utils.StreamKeyUtil
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

        const val KEY_STREAM_KEYS = "stream_keys"

        const val DOWNLOAD_STATUS_QUEUED = "queued"
        const val DOWNLOAD_STATUS_DOWNLOADING = "downloading"
        const val DOWNLOAD_STATUS_COMPLETED = "completed"
        const val DOWNLOAD_STATUS_FAILED = "failed"
        const val DOWNLOAD_STATUS_REMOVED = "removed"
        const val DOWNLOAD_STATUS_PAUSED = "paused"
    }

    override suspend fun doWork(): Result {

        val hlsUri = inputData.getString(KEY_HLS_URI) ?: return Result.failure()
        val contentId = inputData.getString(KEY_CONTENT_ID) ?: return Result.failure()

        val streamKeyString = inputData.getString(KEY_STREAM_KEYS)
        val streamKeys: List<StreamKey> =
            streamKeyString?.let { StreamKeyUtil.fromString(it) } ?: emptyList()

        val downloadManager = DownloadUtil.getDownloadManager(applicationContext)

        // ✅ BUILD MEDIA ITEM WITH STREAM KEYS (QUALITY SELECTED)
        val mediaItem: MediaItem = MediaItemFactory.buildHlsDownloadMediaItem(
            contentId = contentId,
            hlsUrl = hlsUri,
            streamKeys = streamKeys
        )

        val request = DownloadRequest.Builder(
            mediaItem.mediaId,
            mediaItem.localConfiguration!!.uri
        )
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .setStreamKeys(mediaItem.localConfiguration!!.streamKeys)
            .build()

        try {
            downloadManager.addDownload(request)
            downloadManager.resumeDownloads()
        } catch (t: Throwable) {
            Log.e(TAG, "add/resume failed", t)
            return Result.failure()
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

            when (download.state) {

                STATE_QUEUED -> {
                    // already handled
                }

                STATE_DOWNLOADING -> {

                    val progress =
                        if (download.contentLength > 0)
                            ((download.bytesDownloaded * 100) / download.contentLength).toInt()
                        else download.percentDownloaded.toInt().coerceIn(0, 100)

                    if (lastStatus != DOWNLOAD_STATUS_DOWNLOADING || progress != lastProgress) {

                        withContext(Dispatchers.IO) {
                            dao.updateProgressAndStatus(
                                contentId,
                                progress,
                                DOWNLOAD_STATUS_DOWNLOADING,
                                null,
                                null
                            )
                        }

                        setProgress(
                            Data.Builder()
                                .putInt("download_progress", progress)
                                .putString("download_status", DOWNLOAD_STATUS_DOWNLOADING)
                                .build()
                        )

                        lastStatus = DOWNLOAD_STATUS_DOWNLOADING
                        lastProgress = progress
                    }
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
                        dao.updateStatus(
                            contentId,
                            DOWNLOAD_STATUS_FAILED
                        )
                    }

                    return Result.failure()
                }
            }

            delay(1000)
        }

        return Result.failure()
    }
}
