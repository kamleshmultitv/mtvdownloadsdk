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
import com.app.mtvdownloader.entity.DownloadEntity
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

/**
 * SDK-level download helper.
 * Client app has ZERO download logic.
 */
object DownloadHelper {

    private const val TAG = "ReelDownloadHelper"
    private const val DOWNLOAD_QUEUE_NAME = "reel_download_queue"

    /* ---------------------------------------------------- */
    /* 🔹 DOWNLOAD START */
    /* ---------------------------------------------------- */

    @OptIn(UnstableApi::class)
    fun handleDownloadClick(
        context: Context,
        contentItem: DownloadEntity?
    ) {
        if (contentItem == null) return

        if (
            contentItem.drm == "1" &&
            (contentItem.mpdUrl.isNullOrEmpty() || contentItem.drmToken.isNullOrEmpty())
        ) {
            return
        }

        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = DownloadRepository.instance(appContext)
                val contentId = contentItem.contentId

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
                    buildDownloadedEntity(contentItem)
                )

                enqueueDownloadWork(
                    context = appContext,
                    contentId = contentId,
                    data = buildWorkerData(contentItem),
                    hasActiveDownload = hasActiveDownload
                )

            } catch (t: Throwable) {
                Log.e(TAG, "Download enqueue failed", t)
                showToast(context, "Failed to start download")
            }
        }
    }

    /* ---------------------------------------------------- */
    /* 🔹 DOWNLOAD WITH QUALITY */
    /* ---------------------------------------------------- */

    @OptIn(UnstableApi::class)
    fun startDownloadWithQuality(
        context: Context,
        contentItem: DownloadEntity,
        quality: DownloadQuality
    ) {
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {

            val repository = DownloadRepository.instance(appContext)
            val contentId = contentItem.contentId

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
                buildDownloadedEntity(
                    contentItem = contentItem,
                    streamKeys = StreamKeyUtil.toString(listOf(quality.streamKey)),
                    height = quality.height,
                    bitrate = quality.bitrate
                )
            )

            enqueueDownloadWork(
                context = appContext,
                contentId = contentId,
                data = buildWorkerData(contentItem, quality),
                hasActiveDownload = hasActiveDownload
            )
        }
    }

    /* ---------------------------------------------------- */
    /* 🔹 PAUSE */
    /* ---------------------------------------------------- */

    @OptIn(UnstableApi::class)
    fun pauseDownload(context: Context, contentId: String) {
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {

            val repository = DownloadRepository.instance(appContext)

            repository.pauseDownload(contentId)

            WorkManager.getInstance(appContext)
                .cancelAllWorkByTag(contentId)

            val nextQueued = repository.getNextQueuedContent() ?: return@launch

            enqueueDownloadWork(
                context = appContext,
                contentId = nextQueued.contentId,
                data = buildWorkerData(nextQueued),
                hasActiveDownload = false
            )
        }
    }

    /* ---------------------------------------------------- */
    /* 🔹 RESUME */
    /* ---------------------------------------------------- */

    @OptIn(UnstableApi::class)
    fun resumeDownload(context: Context, contentItem: DownloadEntity) {
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            DownloadUtil.getDownloadManager(appContext).resumeDownloads()
            handleDownloadClick(context, contentItem)
        }
    }

    /* ---------------------------------------------------- */
    /* 🔹 CANCEL */
    /* ---------------------------------------------------- */

    @OptIn(UnstableApi::class)
    fun cancelDownload(context: Context, contentId: String) {
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {

            val repository = DownloadRepository.instance(appContext)

            repository.deleteDownload(contentId)

            WorkManager.getInstance(appContext)
                .cancelAllWorkByTag(contentId)

            val nextQueued = repository.getNextQueuedContent() ?: return@launch

            enqueueDownloadWork(
                context = appContext,
                contentId = nextQueued.contentId,
                data = buildWorkerData(nextQueued),
                hasActiveDownload = false
            )

            DownloadUtil.getDownloadManager(appContext).resumeDownloads()
        }
    }

    /* ---------------------------------------------------- */
    /* 🔹 INTERNAL HELPERS */
    /* ---------------------------------------------------- */

    private fun buildDownloadedEntity(
        contentItem: DownloadEntity,
        streamKeys: String? = null,
        height: Int? = null,
        bitrate: Int? = null
    ) = DownloadEntity(
        contentId = contentItem.contentId,
        seasonId = contentItem.seasonId.orEmpty(),
        title = contentItem.title.orEmpty(),
        seasonTitle = contentItem.seasonTitle.orEmpty(),
        mpdUrl = if (contentItem.drm == "1")
            contentItem.mpdUrl.orEmpty()
        else
            contentItem.hlsUrl.orEmpty(),
        hlsUrl = if (contentItem.drm == "1")
            contentItem.mpdUrl.orEmpty()
        else
            contentItem.hlsUrl.orEmpty(),
        drmToken = contentItem.drmToken.orEmpty(),
        imageUrl = contentItem.imageUrl,
        seasonBanner = contentItem.imageUrl,
        downloadStatus = DOWNLOAD_STATUS_QUEUED,
        progress = 0,
        streamKeys = streamKeys,
        videoHeight = height,
        videoBitrate = bitrate
    )

    private fun buildWorkerData(
        contentItem: DownloadEntity,
        quality: DownloadQuality? = null
    ): Data {
        return Data.Builder()
            .putString(KEY_CONTENT_ID, contentItem.contentId)
            .putString(KEY_SEASON_ID, contentItem.seasonId.orEmpty())
            .putString(KEY_CONTENT_TITLE, contentItem.title.orEmpty())
            .putString(KEY_SEASON_NAME, contentItem.seasonTitle.orEmpty())
            .putString(KEY_THUMBNAIL_URL, contentItem.imageUrl)
            .putString(KEY_SEASON_THUMBNAIL_URL, contentItem.imageUrl)
            .apply {
                if (quality != null) {
                    putString(
                        KEY_STREAM_KEYS,
                        StreamKeyUtil.toString(listOf(quality.streamKey))
                    )
                }

                if (contentItem.drm == "1") {
                    putString(KEY_CONTENT_URI, contentItem.mpdUrl)
                    putString(KEY_DRM_LICENSE_URI, contentItem.drmToken)
                } else {
                    putString(KEY_CONTENT_URI, contentItem.hlsUrl)
                }
            }
            .build()
    }

    private fun buildWorkerData(
        entity: DownloadEntity
    ): Data =
        Data.Builder()
            .putString(KEY_CONTENT_ID, entity.contentId)
            .putString(KEY_SEASON_ID, entity.seasonId)
            .putString(KEY_CONTENT_TITLE, entity.title)
            .putString(KEY_SEASON_NAME, entity.seasonTitle)
            .putString(KEY_THUMBNAIL_URL, entity.imageUrl)
            .putString(KEY_SEASON_THUMBNAIL_URL, entity.seasonBanner)
            .putString(KEY_CONTENT_URI, entity.mpdUrl ?: entity.hlsUrl)
            .putString(KEY_DRM_LICENSE_URI, entity.drmToken)
            .build()

    private fun enqueueDownloadWork(
        context: Context,
        contentId: String,
        data: Data,
        hasActiveDownload: Boolean
    ) {
        val workRequest =
            OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(data)
                .addTag(contentId)
                .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                DOWNLOAD_QUEUE_NAME,
                if (hasActiveDownload)
                    ExistingWorkPolicy.APPEND
                else
                    ExistingWorkPolicy.REPLACE,
                workRequest
            )

        MediaDownloadService.start(context)
    }

    suspend fun showToast(context: Context, message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}

