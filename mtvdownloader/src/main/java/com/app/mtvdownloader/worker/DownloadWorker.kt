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

        Log.d(TAG, "Worker started")

        val contentUri = inputData.getString(KEY_CONTENT_URI)
        val contentId = inputData.getString(KEY_CONTENT_ID)
        val drmLicenseUri = inputData.getString(KEY_DRM_LICENSE_URI)

        Log.d(
            TAG,
            "InputData contentId=$contentId uri=$contentUri drmLicense=$drmLicenseUri"
        )

        if (contentUri == null || contentId == null) {
            Log.e(TAG, "Missing input data")
            return Result.failure()
        }

        val streamKeyString = inputData.getString(KEY_STREAM_KEYS)
        val streamKeys: List<StreamKey> =
            streamKeyString?.let { StreamKeyUtil.fromString(it) } ?: emptyList()

        val downloadManager = DownloadUtil.getDownloadManager(applicationContext)
        val dataSourceFactory = DownloadUtil.getHttpFactory(applicationContext)

        val mediaItem = if (!drmLicenseUri.isNullOrEmpty()) {
            Log.d(TAG, "Creating DRM MediaItem")
            MediaItem.Builder()
                .setUri(contentUri)
                .setDrmConfiguration(
                    MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                        .setLicenseUri(drmLicenseUri)
                        .setMultiSession(true)
                        .build()
                )
                .build()
        } else {
            Log.d(TAG, "Creating non-DRM MediaItem")
            MediaItem.fromUri(contentUri)
        }

        try {
            val request = suspendCancellableCoroutine { continuation ->

                val downloadHelper = DownloadHelper.forMediaItem(
                    applicationContext,
                    mediaItem,
                    null,
                    dataSourceFactory
                )

                continuation.invokeOnCancellation {
                    Log.d(TAG, "DownloadHelper cancelled")
                    downloadHelper.release()
                }

                downloadHelper.prepare(object : DownloadHelper.Callback {

                    override fun onPrepared(
                        helper: DownloadHelper,
                        tracksInfoAvailable: Boolean
                    ) {
                        Log.d(
                            TAG,
                            "DownloadHelper prepared | tracksAvailable=$tracksInfoAvailable"
                        )

                        val baseRequest = helper.getDownloadRequest(null)

                        Log.d(
                            TAG,
                            "BaseRequest uri=${baseRequest.uri} " +
                                    "streamKeys=${baseRequest.streamKeys.size}"
                        )

                        val builder = DownloadRequest.Builder(
                            contentId,
                            baseRequest.uri
                        )
                            .setMimeType(baseRequest.mimeType)
                            .setCustomCacheKey(baseRequest.customCacheKey)

                        if (streamKeys.isNotEmpty()) {
                            builder.setStreamKeys(streamKeys)
                        } else {
                            builder.setStreamKeys(baseRequest.streamKeys)
                        }

                        val finalRequest = builder.build()

                        Log.d(
                            TAG,
                            "Final DownloadRequest built | id=${finalRequest.id}"
                        )

                        continuation.resume(finalRequest)
                    }

                    override fun onPrepareError(
                        helper: DownloadHelper,
                        e: IOException
                    ) {
                        Log.e(TAG, "DownloadHelper prepare failed", e)
                        if (continuation.isActive) {
                            continuation.resumeWithException(e)
                        }
                    }
                })
            }

            Log.d(TAG, "Adding download to DownloadManager | id=${request.id}")
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
            } catch (e: Exception) {
                Log.e(TAG, "DownloadIndex error", e)
                null
            }

            if (download == null) {
                Log.d(TAG, "Download not yet available in index")
                delay(500)
                continue
            }


            Log.d(
                TAG,
                "Download state=${download.state} " +
                        "bytes=${download.bytesDownloaded}/${download.contentLength} " +
                        "percent=${download.percentDownloaded}"
            )

            if (download.request.keySetId != null) {
                Log.d(
                    TAG,
                    "Worker sees keySetId size=${download.request.keySetId!!.size}"
                )
            }

            when (download.state) {

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
                    Log.d(TAG, "Download completed")

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
                    Log.e(TAG, "Download failed")

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

        Log.e(TAG, "Worker cancelled")
        return Result.failure()
    }
}
