package com.app.mtvdownloader.service

import android.app.Notification
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.app.mtvdownloader.DownloadUtil
import com.app.mtvdownloader.R
import com.app.mtvdownloader.local.database.DownloadDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
class MediaDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.app_name,
    0
) {

    companion object {
        const val CHANNEL_ID = "download_channel"
        private const val FOREGROUND_NOTIFICATION_ID = 1

        /**
         * ✅ CORRECT alternate solution
         * Media3 safely starts foreground when ready
         */
        fun start(context: Context) {
            start(
                context.applicationContext,
                MediaDownloadService::class.java
            )
        }
    }

    private val titleCache = mutableMapOf<String, String>()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun getDownloadManager(): DownloadManager {
        return DownloadUtil.getDownloadManager(this)
    }

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {

        val notificationHelper =
            DownloadUtil.getDownloadNotificationHelper(this, CHANNEL_ID)

        val message = resolveNotificationMessage(downloads)

        return notificationHelper.buildProgressNotification(
            this,
            R.drawable.ic_download,
            null,
            message,
            downloads,
            notMetRequirements
        )
    }

    private fun resolveNotificationMessage(downloads: List<Download>): String {

        if (downloads.isEmpty()) return getString(R.string.app_name)

        val title = resolveNotificationTitle(downloads)
        val progressText = resolveProgressText(downloads)

        return if (progressText.isBlank()) {
            title
        } else {
            "$title - $progressText"
        }
    }

    private fun resolveProgressText(downloads: List<Download>): String {

        val activeDownloads = downloads.filterNot {
            it.state == Download.STATE_COMPLETED
        }.ifEmpty {
            downloads
        }

        val progressValues = activeDownloads.mapNotNull {
            it.downloadPercentOrNull()
        }

        if (progressValues.isNotEmpty()) {
            val average = progressValues.average().roundToInt().coerceIn(0, 100)
            return "$average% downloaded"
        }

        return when {
            downloads.any { it.state == Download.STATE_QUEUED } -> "Waiting to download"
            downloads.any { it.state == Download.STATE_STOPPED } -> "Paused"
            downloads.any { it.state == Download.STATE_COMPLETED } -> "100% downloaded"
            else -> ""
        }
    }

    private fun Download.downloadPercentOrNull(): Int? {

        if (state == Download.STATE_COMPLETED) return 100

        if (!percentDownloaded.isNaN() && percentDownloaded >= 0f) {
            return percentDownloaded.roundToInt().coerceIn(0, 100)
        }

        return if (contentLength > 0L) {
            ((bytesDownloaded * 100L) / contentLength).toInt().coerceIn(0, 100)
        } else {
            null
        }
    }

    private fun resolveNotificationTitle(downloads: List<Download>): String {

        if (downloads.isEmpty()) return getString(R.string.app_name)

        val activeCount = downloads.count {
            it.state == Download.STATE_DOWNLOADING
        }

        if (activeCount > 1) {
            return "Downloading $activeCount items"
        }

        val contentId = downloads.first().request.id
        titleCache[contentId]?.let { return it }

        serviceScope.launch {
            val dao = DownloadDatabase
                .getInstance(applicationContext)
                .downloadedContentDao()

            dao.getDownloadedContentOnce(contentId)?.title?.let {
                titleCache[contentId] = it
            }
        }

        return getString(R.string.app_name)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
