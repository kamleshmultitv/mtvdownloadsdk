package com.app.mtvdownloader.worker

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.StreamKey
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download.*
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.app.mtvdownloader.DownloadUtil
import com.app.mtvdownloader.helper.ReelDownloadHelper
import com.app.mtvdownloader.local.database.DownloadDatabase
import com.app.mtvdownloader.utils.Constants.DOWNLOAD_STATUS_COMPLETED
import com.app.mtvdownloader.utils.Constants.DOWNLOAD_STATUS_DOWNLOADING
import com.app.mtvdownloader.utils.Constants.DOWNLOAD_STATUS_FAILED
import com.app.mtvdownloader.utils.Constants.DOWNLOAD_STATUS_PAUSED
import com.app.mtvdownloader.utils.Constants.DOWNLOAD_STATUS_QUEUED
import com.app.mtvdownloader.utils.Constants.DRM_LICENSE_STATUS_OFFLINE_FAILED
import com.app.mtvdownloader.utils.Constants.DRM_LICENSE_STATUS_OFFLINE_VALID
import com.app.mtvdownloader.utils.Constants.FAILURE_DRM_AUTH_TOKEN_EXPIRED
import com.app.mtvdownloader.utils.Constants.FAILURE_DRM_DOWNLOAD_REQUEST_FAILED
import com.app.mtvdownloader.utils.Constants.FAILURE_DRM_INIT_DATA_MISSING
import com.app.mtvdownloader.utils.Constants.FAILURE_DRM_KEYSETID_EMPTY
import com.app.mtvdownloader.utils.Constants.FAILURE_DRM_MANIFEST_PREPARE_FAILED
import com.app.mtvdownloader.utils.Constants.FAILURE_DRM_OFFLINE_LICENSE_FAILED
import com.app.mtvdownloader.utils.Constants.KEY_CONTENT_ID
import com.app.mtvdownloader.utils.Constants.KEY_CONTENT_URI
import com.app.mtvdownloader.utils.Constants.KEY_DRM_LICENSE_URI
import com.app.mtvdownloader.utils.Constants.KEY_STREAM_KEYS
import com.app.mtvdownloader.utils.DrmDebugLogger
import com.app.mtvdownloader.utils.DrmKeySetUtil
import com.app.mtvdownloader.utils.DownloadAnalytics
import com.app.mtvdownloader.utils.DownloadStreamType
import com.app.mtvdownloader.utils.DownloadSourceResolver
import com.app.mtvdownloader.utils.OfflineDrmLicenseUtil
import com.app.mtvdownloader.utils.StreamKeyUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
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

    override suspend fun doWork(): Result {

        val contentUri = inputData.getString(KEY_CONTENT_URI) ?: return Result.failure()
        val contentId = inputData.getString(KEY_CONTENT_ID) ?: return Result.failure()
        val drmLicenseUri = inputData.getString(KEY_DRM_LICENSE_URI)

        val streamKeyString = inputData.getString(KEY_STREAM_KEYS)
        val streamKeys: List<StreamKey> =
            streamKeyString?.let { StreamKeyUtil.fromString(it) } ?: emptyList()

        val downloadManager = DownloadUtil.getDownloadManager(applicationContext)
        val maxRetryCount = DownloadUtil.getConfig().minRetryCount
        val streamType = DownloadSourceResolver.inferType(contentUri)
            ?: if (!drmLicenseUri.isNullOrBlank()) {
                DownloadStreamType.DASH
            } else {
                markFailure(
                    contentId = contentId,
                    failureCode = "unsupported_source",
                    failureReason = "Unsupported download source",
                    retryCount = 0,
                    maxRetryCount = maxRetryCount
                )
                return Result.failure()
            }

        try {
            Log.i(
                TAG,
                "Preparing download id=$contentId type=${streamType.name} " +
                    "drm=${!drmLicenseUri.isNullOrBlank()} streamKeys=${streamKeys.size}"
            )
            if (!drmLicenseUri.isNullOrBlank()) {
                DrmDebugLogger.stage(
                    stage = "DRM_DOWNLOAD_START",
                    contentId = contentId,
                    message = "type=${streamType.name} streamKeys=${streamKeys.size} " +
                        DrmDebugLogger.licenseSummary(drmLicenseUri)
                )
            }

            val existingDownload = runCatching {
                downloadManager.downloadIndex.getDownload(contentId)
            }.getOrNull()

            Log.d(
                TAG,
                "Existing Media3 download id=$contentId state=${existingDownload?.state} " +
                    "hasKeySet=${existingDownload?.request?.keySetId != null}"
            )

            val offlineKeySetId = resolveOfflineKeySetId(
                contentId = contentId,
                contentUri = contentUri,
                drmLicenseUri = drmLicenseUri,
                streamKeys = streamKeys,
                streamType = streamType,
                existingKeySetId = existingDownload?.request?.keySetId
            )

            if (existingDownload == null || existingDownload.state == STATE_FAILED) {
                val request = DownloadRequest.Builder(
                    contentId,
                    Uri.parse(contentUri)
                )
                    .setMimeType(streamType.mimeType)
                    .setStreamKeys(streamKeys)
                    .setKeySetId(offlineKeySetId)
                    .build()

                downloadManager.addDownload(request)
                Log.i(TAG, "Media3 download added id=$contentId type=${streamType.name}")
                if (offlineKeySetId != null) {
                    DrmDebugLogger.stage(
                        stage = "DRM_DOWNLOAD_REQUEST_CREATED",
                        contentId = contentId,
                        message = "keySetBytes=${offlineKeySetId.size} streamKeys=${streamKeys.size}"
                    )
                }
                if (!drmLicenseUri.isNullOrBlank()) {
                    DrmDebugLogger.stage(
                        stage = "DRM_SEGMENT_DOWNLOAD_START",
                        contentId = contentId,
                        message = "type=${streamType.name}"
                    )
                }
            } else if (existingDownload.state == STATE_COMPLETED) {
                withContext(Dispatchers.IO) {
                    dao.updateProgressAndStatus(
                        contentId,
                        100,
                        DOWNLOAD_STATUS_COMPLETED,
                        Date().time,
                        DownloadUtil.getDownloadPath(contentId)
                    )
                }
                ReelDownloadHelper.startNextQueuedDownload(applicationContext)
                return Result.success()
            } else {
                if (offlineKeySetId != null && existingDownload.request.keySetId == null) {
                    downloadManager.addDownload(
                        existingDownload.request.copyWithKeySetId(offlineKeySetId),
                        existingDownload.stopReason
                    )
                    Log.i(TAG, "Media3 download upgraded with offline DRM keySet id=$contentId")
                }

                downloadManager.setStopReason(
                    contentId,
                    STOP_REASON_NONE
                )
                Log.i(TAG, "Media3 download resumed id=$contentId")
                if (!drmLicenseUri.isNullOrBlank()) {
                    DrmDebugLogger.stage(
                        stage = "DRM_SEGMENT_DOWNLOAD_START",
                        contentId = contentId,
                        message = "resumed=true type=${streamType.name}"
                    )
                }
            }

            downloadManager.resumeDownloads()
            DownloadAnalytics.notify {
                onDownloadStarted(contentId)
            }

        } catch (t: Throwable) {
            if (t is CancellationException) {
                Log.i(TAG, "Download work cancelled before Media3 request id=$contentId")
                throw t
            }

            Log.e(TAG, "Failed to prepare or add download", t)
            val failureCode = if (t is OfflineDrmLicenseException) {
                t.failureCode
            } else if (!drmLicenseUri.isNullOrBlank()) {
                FAILURE_DRM_DOWNLOAD_REQUEST_FAILED
            } else {
                "request_failed"
            }

            markFailure(
                contentId = contentId,
                failureCode = failureCode,
                failureReason = t.cause?.message ?: t.message,
                retryCount = maxRetryCount,
                maxRetryCount = maxRetryCount
            )
            return Result.failure()
        }

        var lastProgress = -1
        var lastStatus: String? = null

        while (currentCoroutineContext().isActive) {

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
                    if (lastStatus != DOWNLOAD_STATUS_QUEUED) {
                        Log.d(TAG, "Media3 download queued id=$contentId")
                        lastStatus = DOWNLOAD_STATUS_QUEUED
                    }
                }

                STATE_STOPPED -> {
                    withContext(Dispatchers.IO) {
                        dao.updateStatus(
                            contentId,
                            DOWNLOAD_STATUS_PAUSED
                        )
                    }

                    DownloadAnalytics.notify {
                        onDownloadPaused(contentId)
                    }

                    return Result.success()
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

                        DownloadAnalytics.notify {
                            onDownloadProgress(contentId, progress)
                        }

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

                    DownloadAnalytics.notify {
                        onDownloadCompleted(contentId)
                    }

                    if (!drmLicenseUri.isNullOrBlank()) {
                        DrmDebugLogger.stage(
                            stage = "DRM_SEGMENT_DOWNLOAD_COMPLETE",
                            contentId = contentId,
                            message = "progress=100"
                        )
                    }

                    ReelDownloadHelper.startNextQueuedDownload(applicationContext)

                    return Result.success()
                }

                STATE_FAILED -> {
                    val failureCode = download.failureReason.toString()
                    val failureReason = "Media3 download failed with reason ${download.failureReason}"
                    Log.e(TAG, "Media3 download failed id=$contentId reason=${download.failureReason}")
                    if (!drmLicenseUri.isNullOrBlank()) {
                        DrmDebugLogger.stage(
                            stage = "DRM_SEGMENT_DOWNLOAD_FAILED",
                            contentId = contentId,
                            message = failureReason
                        )
                    }

                    markFailure(
                        contentId = contentId,
                        failureCode = failureCode,
                        failureReason = failureReason,
                        retryCount = maxRetryCount,
                        maxRetryCount = maxRetryCount
                    )

                    return Result.failure()
                }

                else -> {
                    markFailure(
                        contentId = contentId,
                        failureCode = "unexpected_media3_state_${download.state}",
                        failureReason = "Media3 download entered unexpected state ${download.state}",
                        retryCount = maxRetryCount,
                        maxRetryCount = maxRetryCount
                    )
                    return Result.failure()
                }
            }

            delay(1000)
        }

        return Result.failure()
    }

    private suspend fun resolveOfflineKeySetId(
        contentId: String,
        contentUri: String,
        drmLicenseUri: String?,
        streamKeys: List<StreamKey>,
        streamType: DownloadStreamType,
        existingKeySetId: ByteArray?
    ): ByteArray? {
        if (drmLicenseUri.isNullOrBlank() || streamType != DownloadStreamType.DASH) {
            return null
        }

        val storedDownload = withContext(Dispatchers.IO) {
            dao.getDownloadedContentOnce(contentId)
        }
        val storedKeySetId =
            storedDownload?.drmOfflineKeySetId
                ?: DrmKeySetUtil.decode(storedDownload?.drmOfflineKeySetIdBase64)

        val result = try {
            Log.i(TAG, "Acquiring offline DRM license id=$contentId streamKeys=${streamKeys.size}")
            OfflineDrmLicenseUtil.ensureWidevineOfflineLicense(
                context = applicationContext,
                contentId = contentId,
                contentUri = contentUri,
                licenseUri = drmLicenseUri,
                streamKeys = streamKeys,
                existingKeySetId = existingKeySetId ?: storedKeySetId
            )
        } catch (t: Throwable) {
            if (t is CancellationException) {
                Log.i(TAG, "Offline DRM acquisition cancelled id=$contentId")
                throw t
            }

            withContext(Dispatchers.IO) {
                dao.updateDrmOfflineLicenseState(
                    contentId = contentId,
                    keySetId = null,
                    keySetIdBase64 = null,
                    expiresAt = null,
                    lastRefreshAt = System.currentTimeMillis(),
                    refreshStatus = DRM_LICENSE_STATUS_OFFLINE_FAILED
                )
            }
            val stageMessage = t.message ?: t.cause?.message
            val failureCode = when (stageMessage) {
                FAILURE_DRM_AUTH_TOKEN_EXPIRED -> FAILURE_DRM_AUTH_TOKEN_EXPIRED
                FAILURE_DRM_INIT_DATA_MISSING -> FAILURE_DRM_INIT_DATA_MISSING
                FAILURE_DRM_MANIFEST_PREPARE_FAILED -> FAILURE_DRM_MANIFEST_PREPARE_FAILED
                FAILURE_DRM_KEYSETID_EMPTY -> FAILURE_DRM_KEYSETID_EMPTY
                else -> FAILURE_DRM_OFFLINE_LICENSE_FAILED
            }
            DrmDebugLogger.failure(
                stage = failureCode,
                contentId = contentId,
                throwable = t
            )
            throw OfflineDrmLicenseException(failureCode, t)
        }

        withContext(Dispatchers.IO) {
            dao.updateDrmOfflineLicenseState(
                contentId = contentId,
                keySetId = result.keySetId,
                keySetIdBase64 = result.keySetIdBase64,
                expiresAt = result.expiresAt,
                lastRefreshAt = System.currentTimeMillis(),
                refreshStatus = DRM_LICENSE_STATUS_OFFLINE_VALID
            )
        }

        Log.i(TAG, "Offline DRM license ready id=$contentId expiresAt=${result.expiresAt}")
        return result.keySetId
    }

    private class OfflineDrmLicenseException(
        val failureCode: String,
        cause: Throwable
    ) : Exception("Failed to acquire offline DRM license", cause)

    private suspend fun markFailure(
        contentId: String,
        failureCode: String?,
        failureReason: String?,
        retryCount: Int,
        maxRetryCount: Int
    ) {
        withContext(Dispatchers.IO) {
            if (failureCode?.startsWith("DRM_") == true) {
                dao.updateDrmOfflineLicenseState(
                    contentId = contentId,
                    keySetId = null,
                    keySetIdBase64 = null,
                    expiresAt = null,
                    lastRefreshAt = System.currentTimeMillis(),
                    refreshStatus = DRM_LICENSE_STATUS_OFFLINE_FAILED
                )
            }

            dao.updateFailure(
                contentId = contentId,
                status = DOWNLOAD_STATUS_FAILED,
                failureCode = failureCode,
                failureReason = failureReason,
                retryCount = retryCount,
                maxRetryCount = maxRetryCount
            )
        }

        DownloadAnalytics.notify {
            onDownloadFailed(
                contentId = contentId,
                errorCode = failureCode,
                errorMessage = failureReason
            )
        }

        ReelDownloadHelper.startNextQueuedDownload(applicationContext)
    }
}
