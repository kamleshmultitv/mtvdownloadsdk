package com.app.mtvdownloader.helper

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.common.StreamKey
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
import com.app.mtvdownloader.utils.Constants.DRM_LICENSE_STATUS_NOT_REQUIRED
import com.app.mtvdownloader.utils.Constants.DRM_LICENSE_STATUS_VALID
import com.app.mtvdownloader.utils.Constants.DRM_SCHEME_WIDEVINE
import com.app.mtvdownloader.utils.Constants.DOWNLOAD_STATUS_COMPLETED
import com.app.mtvdownloader.utils.Constants.DOWNLOAD_STATUS_DOWNLOADING
import com.app.mtvdownloader.utils.Constants.DOWNLOAD_STATUS_PAUSED
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
import com.app.mtvdownloader.utils.DownloadSourceResolver
import com.app.mtvdownloader.utils.ResolvedDownloadSource
import com.app.mtvdownloader.utils.DownloadAnalytics
import com.app.mtvdownloader.utils.StreamKeyUtil
import com.app.mtvdownloader.worker.DownloadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SDK-level download helper.
 * Client app should not need to manage service, worker, or Media3 queue state.
 */
object ReelDownloadHelper {

    private const val TAG = "ReelDownloadHelper"
    private const val DOWNLOAD_QUEUE_NAME = "reel_download_queue"

    @OptIn(UnstableApi::class)
    fun handleDownloadClick(
        context: Context,
        contentItem: DownloadModel?
    ) {
        enqueueNewDownload(context, contentItem, quality = null)
    }

    @OptIn(UnstableApi::class)
    fun startDownloadWithQuality(
        context: Context,
        contentItem: DownloadModel,
        quality: DownloadQuality
    ) {
        enqueueNewDownload(context, contentItem, quality)
    }

    @OptIn(UnstableApi::class)
    fun pauseDownload(
        context: Context,
        contentId: String
    ) {
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            val repository = DownloadRepository.instance(appContext)

            repository.pauseDownload(contentId)
            DownloadAnalytics.notify {
                onDownloadPaused(contentId)
            }

            WorkManager.getInstance(appContext)
                .cancelAllWorkByTag(contentId)

            enqueueNextQueued(appContext, repository)
            MediaDownloadService.start(appContext)
        }
    }

    @OptIn(UnstableApi::class)
    fun resumeDownload(
        context: Context,
        contentItem: DownloadModel
    ) {
        resumeDownload(context, contentItem.id.orEmpty())
    }

    @OptIn(UnstableApi::class)
    fun resumeDownload(
        context: Context,
        contentId: String
    ) {
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            val repository = DownloadRepository.instance(appContext)
            val pausedItem = repository.getDownloadedContentOnce(contentId)

            if (pausedItem == null) {
                showToast(context, "Download not found")
                return@launch
            }

            resumeStoredDownload(
                context = appContext,
                repository = repository,
                item = pausedItem,
                preemptActiveDownload = true
            )

            DownloadAnalytics.notify {
                onDownloadResumed(contentId)
            }
        }
    }

    @OptIn(UnstableApi::class)
    fun cancelDownload(
        context: Context,
        contentId: String
    ) {
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            val repository = DownloadRepository.instance(appContext)

            repository.deleteDownload(contentId)
            DownloadAnalytics.notify {
                onDownloadCancelled(contentId)
            }

            WorkManager.getInstance(appContext)
                .cancelAllWorkByTag(contentId)

            enqueueNextQueued(appContext, repository)

            MediaDownloadService.start(appContext)

            DownloadUtil.getDownloadManager(appContext)
                .resumeDownloads()
        }
    }

    @OptIn(UnstableApi::class)
    private fun enqueueNewDownload(
        context: Context,
        contentItem: DownloadModel?,
        quality: DownloadQuality?
    ) {
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val source = DownloadSourceResolver.resolve(contentItem)
                if (source == null || contentItem == null) {
                    DownloadAnalytics.notify {
                        onDownloadFailed(
                            contentItem?.id.orEmpty(),
                            "unsupported_source",
                            "Unsupported download source"
                        )
                    }
                    showToast(context, "Unsupported download source")
                    return@launch
                }

                val repository = DownloadRepository.instance(appContext)
                val contentId = contentItem.id.orEmpty()

                val existingDownload = repository.getDownloadedContentOnce(contentId)

                if (existingDownload?.downloadStatus == DOWNLOAD_STATUS_PAUSED) {
                    resumeStoredDownload(
                        context = appContext,
                        repository = repository,
                        item = existingDownload,
                        preemptActiveDownload = true
                    )
                    return@launch
                }

                if (
                    shouldSkipExistingDownload(
                        context,
                        existingDownload,
                        contentItem.title
                    )
                ) {
                    return@launch
                }

                val hasActiveDownload = repository.hasActiveDownload()
                val streamKeys = quality?.streamKeys.orEmpty()

                repository.insertOrUpdate(
                    createEntity(
                        contentItem = contentItem,
                        source = source,
                        streamKeys = streamKeys,
                        quality = quality
                    )
                )

                enqueueWork(
                    context = appContext,
                    contentId = contentId,
                    inputData = buildInputData(
                        contentId = contentId,
                        contentItem = contentItem,
                        source = source,
                        streamKeys = streamKeys
                    ),
                    policy = if (hasActiveDownload) ExistingWorkPolicy.APPEND else ExistingWorkPolicy.REPLACE
                )

                DownloadAnalytics.notify {
                    onDownloadEnqueued(
                        contentId = contentId,
                        sourceUrl = source.url,
                        sourceType = source.streamType.name.lowercase(),
                        qualityHeight = quality?.height,
                        qualityBitrate = quality?.bitrate
                    )
                }

                MediaDownloadService.start(appContext)
            } catch (t: Throwable) {
                Log.e(TAG, "Download enqueue failed", t)
                DownloadAnalytics.notify {
                    onDownloadFailed(
                        contentItem?.id.orEmpty(),
                        "enqueue_failed",
                        t.message
                    )
                }
                showToast(context, "Failed to start download")
            }
        }
    }

    private suspend fun shouldSkipExistingDownload(
        context: Context,
        existingDownload: DownloadedContentEntity?,
        title: String?
    ): Boolean {
        return when (existingDownload?.downloadStatus) {
            DOWNLOAD_STATUS_COMPLETED -> {
                showToast(context, "${title.orEmpty()} already downloaded")
                true
            }

            DOWNLOAD_STATUS_QUEUED -> {
                showToast(context, "${title.orEmpty()} is already in queue")
                true
            }

            DOWNLOAD_STATUS_DOWNLOADING -> {
                showToast(context, "${title.orEmpty()} is downloading")
                true
            }

            DOWNLOAD_STATUS_PAUSED -> false

            else -> false
        }
    }

    @OptIn(UnstableApi::class)
    private fun createEntity(
        contentItem: DownloadModel,
        source: ResolvedDownloadSource,
        streamKeys: List<StreamKey>,
        quality: DownloadQuality?
    ): DownloadedContentEntity {
        return DownloadedContentEntity(
            contentId = contentItem.id.orEmpty(),
            seasonId = contentItem.seasonId.orEmpty(),
            title = contentItem.title.orEmpty(),
            seasonName = contentItem.seasonTitle.orEmpty(),
            contentUrl = source.url,
            licenseUri = source.licenseUri.orEmpty(),
            thumbnailUrl = contentItem.imageUrl,
            seasonImage = contentItem.imageUrl,
            downloadStatus = DOWNLOAD_STATUS_QUEUED,
            downloadProgress = 0,
            streamKeys = streamKeys.takeIf { it.isNotEmpty() }?.let(StreamKeyUtil::toString),
            videoHeight = quality?.height,
            videoBitrate = quality?.bitrate,
            maxRetryCount = DownloadUtil.getConfig().minRetryCount,
            drmLicenseExpiresAt = contentItem.drmLicenseExpiresAt,
            drmScheme = if (source.licenseUri.isNullOrBlank()) null else {
                contentItem.drmScheme ?: DRM_SCHEME_WIDEVINE
            },
            drmKeyId = contentItem.drmKeyId,
            contentMimeType = source.streamType.mimeType,
            drmLicenseRefreshStatus = if (source.licenseUri.isNullOrBlank()) {
                DRM_LICENSE_STATUS_NOT_REQUIRED
            } else {
                DRM_LICENSE_STATUS_VALID
            }
        )
    }

    @OptIn(UnstableApi::class)
    private fun buildInputData(
        contentId: String,
        contentItem: DownloadModel,
        source: ResolvedDownloadSource,
        streamKeys: List<StreamKey>
    ): Data {
        return Data.Builder()
            .putString(KEY_CONTENT_ID, contentId)
            .putString(KEY_SEASON_ID, contentItem.seasonId.orEmpty())
            .putString(KEY_CONTENT_TITLE, contentItem.title.orEmpty())
            .putString(KEY_SEASON_NAME, contentItem.seasonTitle.orEmpty())
            .putString(KEY_THUMBNAIL_URL, contentItem.imageUrl)
            .putString(KEY_SEASON_THUMBNAIL_URL, contentItem.imageUrl)
            .putString(KEY_CONTENT_URI, source.url)
            .putString(KEY_DRM_LICENSE_URI, source.licenseUri)
            .apply {
                if (streamKeys.isNotEmpty()) {
                    putString(KEY_STREAM_KEYS, StreamKeyUtil.toString(streamKeys))
                }
            }
            .build()
    }

    private fun buildInputData(item: DownloadedContentEntity): Data {
        return Data.Builder()
            .putString(KEY_CONTENT_ID, item.contentId)
            .putString(KEY_SEASON_ID, item.seasonId)
            .putString(KEY_CONTENT_TITLE, item.title)
            .putString(KEY_SEASON_NAME, item.seasonName)
            .putString(KEY_THUMBNAIL_URL, item.thumbnailUrl)
            .putString(KEY_SEASON_THUMBNAIL_URL, item.seasonImage)
            .putString(KEY_CONTENT_URI, item.contentUrl)
            .putString(KEY_DRM_LICENSE_URI, item.licenseUri)
            .putString(KEY_STREAM_KEYS, item.streamKeys)
            .build()
    }

    private fun enqueueWork(
        context: Context,
        contentId: String,
        inputData: Data,
        policy: ExistingWorkPolicy
    ) {
        val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(inputData)
            .addTag(contentId)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                DOWNLOAD_QUEUE_NAME,
                policy,
                workRequest
            )
    }

    private suspend fun enqueueNextQueued(
        appContext: Context,
        repository: DownloadRepository
    ) {
        val nextQueued = repository.getNextQueuedContent() ?: return

        enqueueWork(
            context = appContext,
            contentId = nextQueued.contentId,
            inputData = buildInputData(nextQueued),
            policy = ExistingWorkPolicy.REPLACE
        )
    }

    @OptIn(UnstableApi::class)
    internal suspend fun startNextQueuedDownload(context: Context) {
        val appContext = context.applicationContext
        val repository = DownloadRepository.instance(appContext)
        enqueueNextQueued(appContext, repository)
        MediaDownloadService.start(appContext)
    }

    private suspend fun resumeStoredDownload(
        context: Context,
        repository: DownloadRepository,
        item: DownloadedContentEntity,
        preemptActiveDownload: Boolean
    ) {
        if (preemptActiveDownload) {
            pauseActiveDownloadsExcept(
                context = context,
                repository = repository,
                contentId = item.contentId
            )
        }

        repository.queuePausedDownload(item.contentId)

        val queuedItem = repository.getDownloadedContentOnce(item.contentId)
            ?: item.copy(downloadStatus = DOWNLOAD_STATUS_QUEUED)

        enqueueWork(
            context = context,
            contentId = queuedItem.contentId,
            inputData = buildInputData(queuedItem),
            policy = ExistingWorkPolicy.REPLACE
        )

        MediaDownloadService.start(context)
        DownloadUtil.getDownloadManager(context).resumeDownloads()
    }

    private suspend fun pauseActiveDownloadsExcept(
        context: Context,
        repository: DownloadRepository,
        contentId: String
    ) {
        repository.getDownloadingContent()
            .filterNot { it.contentId == contentId }
            .forEach { activeItem ->
                repository.pauseDownload(activeItem.contentId)

                WorkManager.getInstance(context)
                    .cancelAllWorkByTag(activeItem.contentId)

                DownloadAnalytics.notify {
                    onDownloadPaused(activeItem.contentId)
                }
            }
    }

    suspend fun showToast(context: Context, message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
