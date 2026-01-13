package com.app.mtvdownloader.service

import android.app.Notification
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
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

        fun start(context: Context) {
            val intent = Intent(context, MediaDownloadService::class.java)

            CoroutineScope(Dispatchers.Main).launch {
                Util.startForegroundService(context.applicationContext, intent)
            }

            CoroutineScope(Dispatchers.IO).launch {
                DownloadUtil.getDownloadManager(context.applicationContext)
            }
        }
    }

    /** In-memory cache to avoid DB hits on main thread */
    private val titleCache = mutableMapOf<String, String>()

    override fun onCreate() {
        super.onCreate()

        val initialNotification =
            getForegroundNotification(mutableListOf(), 0)

        startForeground(FOREGROUND_NOTIFICATION_ID, initialNotification)
    }

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
            null,          // ✅ PendingIntent
            title,         // ✅ Title text
            downloads,
            notMetRequirements
        )
    }


    /**
     * Resolves notification title safely without blocking UI.
     */
    private fun resolveNotificationTitle(
        downloads: List<Download>
    ): String {

        if (downloads.isEmpty()) {
            return getString(R.string.app_name)
        }

        if (downloads.size > 1) {
            return "Downloading ${downloads.size} items"
        }

        val contentId = downloads.first().request.id

        // Return cached title if available
        titleCache[contentId]?.let { return it }

        // Fetch title in background and cache it
        CoroutineScope(Dispatchers.IO).launch {
            val dao = DownloadDatabase
                .getInstance(applicationContext)
                .downloadedContentDao()

            val entity = dao.getDownloadedContentOnce(contentId)
            entity?.title?.let {
                titleCache[contentId] = it
            }
        }

        // Temporary fallback (will update automatically)
        return getString(R.string.app_name)
    }
}
