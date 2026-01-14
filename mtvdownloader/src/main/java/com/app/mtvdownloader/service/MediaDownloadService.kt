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
import kotlinx.coroutines.*

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

        val title = resolveNotificationTitle(downloads)

        return notificationHelper.buildProgressNotification(
            this,
            R.drawable.ic_download,
            null,
            title,
            downloads,
            notMetRequirements
        )
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

        CoroutineScope(Dispatchers.IO).launch {
            val dao = DownloadDatabase
                .getInstance(applicationContext)
                .downloadedContentDao()

            dao.getDownloadedContentOnce(contentId)?.title?.let {
                titleCache[contentId] = it
            }
        }

        return getString(R.string.app_name)
    }
}
