package com.app.mtvdownloader.worker

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.StreamKey
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download.*
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.app.mtvdownloader.DownloadUtil
import com.app.mtvdownloader.local.database.DownloadDatabase
import com.app.mtvdownloader.utils.Constants.DOWNLOAD_STATUS_COMPLETED
import com.app.mtvdownloader.utils.Constants.DOWNLOAD_STATUS_DOWNLOADING
import com.app.mtvdownloader.utils.Constants.DOWNLOAD_STATUS_FAILED
import com.app.mtvdownloader.utils.Constants.KEY_CONTENT_ID
import com.app.mtvdownloader.utils.Constants.KEY_CONTENT_URI
import com.app.mtvdownloader.utils.Constants.KEY_DRM_LICENSE_URI
import com.app.mtvdownloader.utils.Constants.KEY_STREAM_KEYS
import com.app.mtvdownloader.utils.StreamKeyUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Date
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(UnstableApi::class)
class DownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val TAG = "DownloadWorker"

    private val dao = DownloadDatabase
        .getInstance(context)
        .downloadedContentDao()

    override suspend fun doWork(): Result {

        val contentUri = inputData.getString(KEY_CONTENT_URI) ?: return Result.failure()
        val contentId = inputData.getString(KEY_CONTENT_ID) ?: return Result.failure()
        val drmLicenseUri = inputData.getString(KEY_DRM_LICENSE_URI)

        val streamKeyString = inputData.getString(KEY_STREAM_KEYS)
        val streamKeys: List<StreamKey> =
            streamKeyString?.let { StreamKeyUtil.fromString(it) } ?: emptyList()

        val downloadManager = DownloadUtil.getDownloadManager(applicationContext)
        val dataSourceFactory = DownloadUtil.getHttpFactory(applicationContext)

        // ✅ DRM handled via MediaItem (Media3 correct approach)
        val mediaItem = if (!drmLicenseUri.isNullOrEmpty()) {
            MediaItem.Builder()
                .setUri(contentUri)
                .setDrmConfiguration(
                    MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                        .setLicenseUri(drmLicenseUri)
                        .build()
                )
                .build()
        } else {
            MediaItem.fromUri(contentUri)
        }

        try {
            val request = suspendCancellableCoroutine { continuation ->

                val downloadHelper = DownloadHelper.forMediaItem(
                    applicationContext,
                    mediaItem,
                    null, // default RenderersFactory
                    dataSourceFactory
                )

                continuation.invokeOnCancellation {
                    downloadHelper.release()
                }

                downloadHelper.prepare(object : DownloadHelper.Callback {

                    override fun onPrepared(
                        helper: DownloadHelper,
                        tracksInfoAvailable: Boolean
                    ) {
                        val downloadRequest =
                            DownloadRequest.Builder(
                                contentId,
                                mediaItem.localConfiguration!!.uri
                            )
                                .setStreamKeys(streamKeys)
                                .build()

                        continuation.resume(downloadRequest)
                    }

                    override fun onPrepareError(
                        helper: DownloadHelper,
                        e: IOException
                    ) {
                        Log.e(TAG, "Failed to prepare download", e)
                        if (continuation.isActive) {
                            continuation.resumeWithException(e)
                        }
                    }
                })
            }

            downloadManager.addDownload(request)
            downloadManager.resumeDownloads()

        } catch (t: Throwable) {
            Log.e(TAG, "Failed to prepare or add download", t)
            return Result.failure()
        }

        var lastProgress = -1
        var lastStatus: String? = null

        while (coroutineContext.isActive) {

            val download = try {
                downloadManager.downloadIndex.getDownload(contentId)
            } catch (_: Exception) {
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

                    if (lastStatus != DOWNLOAD_STATUS_DOWNLOADING ||
                        progress != lastProgress
                    ) {

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
                                .putString(
                                    "download_status",
                                    DOWNLOAD_STATUS_DOWNLOADING
                                )
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

                else -> {
                    // Stop worker for unhandled states
                    return Result.failure()
                }
            }

            delay(1000)
        }

        return Result.failure()
    }
}