package com.app.mtvdownloader.helper

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.app.mtvdownloader.DownloadUtil
import com.app.mtvdownloader.local.entity.DownloadedContentEntity
import com.app.mtvdownloader.model.DownloadModel
import com.app.mtvdownloader.model.DownloadQuality
import com.app.mtvdownloader.repository.DownloadRepository
import com.app.mtvdownloader.service.MediaDownloadService
import com.app.mtvdownloader.utils.Constants.DOWNLOAD_STATUS_COMPLETED
import com.app.mtvdownloader.utils.Constants.DOWNLOAD_STATUS_DOWNLOADING
import com.app.mtvdownloader.utils.Constants.DOWNLOAD_STATUS_QUEUED
import com.app.mtvdownloader.utils.Constants.KEY_CONTENT_ID
import com.app.mtvdownloader.utils.Constants.KEY_CONTENT_TITLE
import com.app.mtvdownloader.utils.Constants.KEY_CONTENT_URI
import com.app.mtvdownloader.utils.Constants.KEY_DRM_LICENSE_URI
import com.app.mtvdownloader.utils.Constants.KEY_SEASON_ID
import com.app.mtvdownloader.utils.Constants.KEY_SEASON_NAME
import com.app.mtvdownloader.utils.Constants.KEY_SEASON_THUMBNAIL_URL
import com.app.mtvdownloader.utils.Constants.KEY_STREAM_KEYS
import com.app.mtvdownloader.utils.Constants.KEY_THUMBNAIL_URL
import com.app.mtvdownloader.utils.StreamKeyUtil
import com.app.mtvdownloader.worker.DownloadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.toString

/**
 * SDK-level download helper.
 * Client app has ZERO download logic.
 */
object ReelDownloadHelper {

    private const val TAG = "ReelDownloadHelper"
    private const val DOWNLOAD_QUEUE_NAME = "reel_download_queue"

    /* ---------------------------------------------------- */
    /* 🔹 DOWNLOAD START (UNCHANGED) */
    /* ---------------------------------------------------- */

    @OptIn(UnstableApi::class)
    fun handleDownloadClick(
        context: Context,
        contentItem: DownloadModel?
    ) {

        if (
            contentItem?.drm == "1" &&
            contentItem.drm.isNotEmpty() &&
            (contentItem.mpdUrl.isNullOrEmpty() || contentItem.hlsUrl.isNullOrEmpty())
        ) {
            return
        }

        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = DownloadRepository.instance(appContext)
                val contentId = contentItem?.id.toString()

                when (repository.getDownloadedContentOnce(contentId)?.downloadStatus) {
                    DOWNLOAD_STATUS_COMPLETED -> {
                        showToast(context, "${contentItem?.title} already downloaded")
                        return@launch
                    }

                    DOWNLOAD_STATUS_QUEUED -> {
                        showToast(context, "${contentItem?.title} is already in queue")
                        return@launch
                    }

                    DOWNLOAD_STATUS_DOWNLOADING -> {
                        showToast(context, "${contentItem?.title} is downloading")
                        return@launch
                    }
                }

                val hasActiveDownload = repository.hasActiveDownload()

                repository.insertOrUpdate(
                    DownloadedContentEntity(
                        contentId = contentId,
                        seasonId = contentItem?.seasonId.orEmpty(),
                        title = contentItem?.title.orEmpty(),
                        seasonName = contentItem?.seasonTitle.orEmpty(),
                        contentUrl = if (contentItem?.drm == "1") contentItem.mpdUrl.toString() else contentItem?.hlsUrl.toString(),
                        licenseUri = contentItem?.drmToken.toString(),
                        thumbnailUrl = contentItem?.imageUrl,
                        seasonImage = contentItem?.imageUrl,
                        downloadStatus = DOWNLOAD_STATUS_QUEUED,
                        downloadProgress = 0
                    )
                )

                val workRequestBuilder = OneTimeWorkRequestBuilder<DownloadWorker>()

                val dataBuilder = Data.Builder()
                    .putString(KEY_CONTENT_ID, contentId)
                    .putString(
                        KEY_SEASON_ID,
                        contentItem?.seasonId.orEmpty()
                    )
                    .putString(
                        KEY_CONTENT_TITLE,
                        contentItem?.title.orEmpty()
                    )
                    .putString(
                        KEY_SEASON_NAME,
                        contentItem?.seasonTitle.orEmpty()
                    )
                    .putString(KEY_THUMBNAIL_URL, contentItem?.imageUrl)
                    .putString(
                        KEY_SEASON_THUMBNAIL_URL,
                        contentItem?.imageUrl
                    )

                if (contentItem?.drm == "1") {
                    dataBuilder.putString(KEY_CONTENT_URI, contentItem.mpdUrl.toString())
                    dataBuilder.putString(
                        KEY_DRM_LICENSE_URI,
                        contentItem.drmToken.toString()
                    ) // Add license URL for DRM
                } else {
                    dataBuilder.putString(KEY_CONTENT_URI, contentItem?.hlsUrl.toString())
                }

                workRequestBuilder.setInputData(dataBuilder.build())
                val workRequest = workRequestBuilder.addTag(contentId).build()

                WorkManager.getInstance(appContext)
                    .enqueueUniqueWork(
                        DOWNLOAD_QUEUE_NAME,
                        if (hasActiveDownload)
                            ExistingWorkPolicy.APPEND
                        else
                            ExistingWorkPolicy.REPLACE,
                        workRequest
                    )

                MediaDownloadService.start(appContext)

            } catch (t: Throwable) {
                Log.e(TAG, "Download enqueue failed", t)
                showToast(context, "Failed to start download")
            }
        }
    }

    @OptIn(UnstableApi::class)
    fun startDownloadWithQuality(
        context: Context,
        contentItem: DownloadModel,
        quality: DownloadQuality
    ) {
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {

            val repository = DownloadRepository.instance(appContext)
            val contentId = contentItem.id.toString()

            when (repository.getDownloadedContentOnce(contentId)?.downloadStatus) {
                DOWNLOAD_STATUS_COMPLETED -> {
                    showToast(context, "${contentItem.title} already downloaded")
                    return@launch
                }

                DOWNLOAD_STATUS_QUEUED -> {
                    showToast(context, "${contentItem.title} is already in queue")
                    return@launch
                }

                DOWNLOAD_STATUS_DOWNLOADING -> {
                    showToast(context, "${contentItem.title} is downloading")
                    return@launch
                }
            }

            val hasActiveDownload = repository.hasActiveDownload()

            repository.insertOrUpdate(
                DownloadedContentEntity(
                    contentId = contentId,
                    seasonId = contentItem.seasonId.orEmpty(),
                    title = contentItem.title.orEmpty(),
                    seasonName = contentItem.seasonTitle.orEmpty(),
                    contentUrl = if (contentItem.drm == "1") contentItem.mpdUrl.toString() else contentItem.hlsUrl.toString(),
                    licenseUri = contentItem.drmToken.toString(),
                    thumbnailUrl = contentItem.imageUrl,
                    seasonImage = contentItem.imageUrl,
                    downloadStatus = DOWNLOAD_STATUS_QUEUED,
                    downloadProgress = 0,
                    streamKeys = StreamKeyUtil.toString(listOf(quality.streamKey)),
                    videoHeight = quality.height,
                    videoBitrate = quality.bitrate
                )
            )

            val workRequestBuilder = OneTimeWorkRequestBuilder<DownloadWorker>()

            val dataBuilder = Data.Builder()
                .putString(KEY_CONTENT_ID, contentId)
                .putString(
                    KEY_STREAM_KEYS,
                    StreamKeyUtil.toString(listOf(quality.streamKey))
                )

            if (contentItem.drm == "1") {
                dataBuilder.putString(KEY_CONTENT_URI, contentItem.mpdUrl.toString())
                dataBuilder.putString(
                    KEY_DRM_LICENSE_URI,
                    contentItem.drmToken.toString()
                ) // Add license URL for DRM
            } else {
                dataBuilder.putString(KEY_CONTENT_URI, contentItem.hlsUrl.toString())
            }

            workRequestBuilder.setInputData(dataBuilder.build())
            val workRequest = workRequestBuilder.addTag(contentId).build()

            WorkManager.getInstance(appContext)
                .enqueueUniqueWork(
                    DOWNLOAD_QUEUE_NAME,
                    if (hasActiveDownload)
                        ExistingWorkPolicy.APPEND
                    else
                        ExistingWorkPolicy.REPLACE,
                    workRequest
                )

            MediaDownloadService.start(appContext)
        }
    }

    /* ---------------------------------------------------- */
    /* 🔹 PAUSE */
    /* ---------------------------------------------------- */

    @OptIn(UnstableApi::class)
    fun pauseDownload(
        context: Context,
        contentId: String
    ) {
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {

            val repository = DownloadRepository.instance(appContext)

            // 1️⃣ Pause current item safely
            repository.pauseDownload(contentId)

            // 2️⃣ Cancel worker for this item
            WorkManager.getInstance(appContext)
                .cancelAllWorkByTag(contentId)

            // 3️⃣ Start next queued item
            val nextQueued = repository.getNextQueuedContent()
                ?: return@launch

            val workRequest =
                OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setInputData(
                        Data.Builder()
                            .putString(KEY_CONTENT_ID, nextQueued.contentId)
                            .putString(KEY_SEASON_ID, nextQueued.seasonId)
                            .putString(KEY_CONTENT_TITLE, nextQueued.title)
                            .putString(KEY_SEASON_NAME, nextQueued.seasonName)
                            .putString(KEY_THUMBNAIL_URL, nextQueued.thumbnailUrl)
                            .putString(
                                KEY_SEASON_THUMBNAIL_URL,
                                nextQueued.seasonImage
                            )
                            .putString(KEY_CONTENT_URI, nextQueued.contentUrl)
                            .putString(KEY_DRM_LICENSE_URI, nextQueued.licenseUri)
                            .build()
                    )
                    .addTag(nextQueued.contentId)
                    .build()

            WorkManager.getInstance(appContext)
                .enqueueUniqueWork(
                    DOWNLOAD_QUEUE_NAME,
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )

            MediaDownloadService.start(appContext)
        }
    }

    /* ---------------------------------------------------- */
    /* 🔹 RESUME */
    /* ---------------------------------------------------- */

    @OptIn(UnstableApi::class)
    fun resumeDownload(
        context: Context,
        contentItem: DownloadModel
    ) {
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {

            // Media3 resumes from SAME BYTE OFFSET automatically
            DownloadUtil.getDownloadManager(appContext)
                .resumeDownloads()

            handleDownloadClick(context, contentItem)
        }
    }

    /* ---------------------------------------------------- */
    /* 🔹 CANCEL */
    /* ---------------------------------------------------- */

    @OptIn(UnstableApi::class)
    fun cancelDownload(
        context: Context,
        contentId: String
    ) {
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {

            val repository = DownloadRepository.instance(appContext)

            repository.deleteDownload(contentId)

            WorkManager.getInstance(appContext)
                .cancelAllWorkByTag(contentId)

            val nextQueued = repository.getNextQueuedContent()

            if (nextQueued != null) {

                val workRequest =
                    OneTimeWorkRequestBuilder<DownloadWorker>()
                        .setInputData(
                            Data.Builder()
                                .putString(KEY_CONTENT_ID, nextQueued.contentId)
                                .putString(KEY_SEASON_ID, nextQueued.seasonId)
                                .putString(KEY_CONTENT_TITLE, nextQueued.title)
                                .putString(KEY_SEASON_NAME, nextQueued.seasonName)
                                .putString(
                                    KEY_THUMBNAIL_URL,
                                    nextQueued.thumbnailUrl
                                )
                                .putString(
                                    KEY_SEASON_THUMBNAIL_URL,
                                    nextQueued.seasonImage
                                )
                                .putString(KEY_CONTENT_URI, nextQueued.contentUrl)
                                .putString(KEY_DRM_LICENSE_URI, nextQueued.licenseUri)
                                .build()
                        )
                        .addTag(nextQueued.contentId)
                        .build()

                WorkManager.getInstance(appContext)
                    .enqueueUniqueWork(
                        DOWNLOAD_QUEUE_NAME,
                        ExistingWorkPolicy.REPLACE,
                        workRequest
                    )

                DownloadUtil.getDownloadManager(appContext)
                    .resumeDownloads()
            }
        }
    }

    /* ---------------------------------------------------- */

    suspend fun showToast(context: Context, message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

}
