package com.app.mtvdownloader

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.database.ExoDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Media3 Download utility (Singleton)
 */
@OptIn(UnstableApi::class)
object DownloadUtil {

    private const val TAG = "DownloadUtil"

    private const val DOWNLOAD_DIR = "downloads"
    private const val MAX_CACHE_BYTES = 500L * 1024L * 1024L // ✅ 500 MB (safe for HLS)

    @Volatile private var databaseProvider: ExoDatabaseProvider? = null
    @Volatile private var downloadCache: SimpleCache? = null
    @Volatile private var downloadManager: DownloadManager? = null
    @Volatile private var downloadDirectory: File? = null
    @Volatile private var downloadNotificationHelper: DownloadNotificationHelper? = null

    // ✅ Limit threads (disk + network safe)
    private val backgroundExecutor: Executor by lazy {
        Executors.newFixedThreadPool(2)
    }

    /* ---------------- DATABASE ---------------- */

    @Synchronized
    fun getDatabaseProvider(context: Context): ExoDatabaseProvider {
        return databaseProvider ?: ExoDatabaseProvider(
            context.applicationContext
        ).also {
            databaseProvider = it
        }
    }

    /* ---------------- DIRECTORY ---------------- */

    @Synchronized
    fun getDownloadDirectory(context: Context): File {
        return downloadDirectory ?: File(
            context.getExternalFilesDir(null),
            DOWNLOAD_DIR
        ).also {
            if (!it.exists()) it.mkdirs()
            downloadDirectory = it
        }
    }

    /* ---------------- CACHE ---------------- */

    @Synchronized
    fun getDownloadCache(context: Context): SimpleCache {
        return downloadCache ?: run {
            val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES)
            val cache = SimpleCache(
                getDownloadDirectory(context),
                evictor,
                getDatabaseProvider(context)
            )
            downloadCache = cache
            Log.d(TAG, "SimpleCache created")
            cache
        }
    }

    /* ---------------- NOTIFICATION ---------------- */

    @Synchronized
    fun getDownloadNotificationHelper(
        context: Context,
        channelId: String
    ): DownloadNotificationHelper {
        return downloadNotificationHelper ?: DownloadNotificationHelper(
            context.applicationContext,
            channelId
        ).also {
            downloadNotificationHelper = it
        }
    }

    /* ---------------- DATASOURCE ---------------- */

    fun getHttpFactory(context: Context): DefaultHttpDataSource.Factory {
        return DefaultHttpDataSource.Factory()
            .setUserAgent(
                Util.getUserAgent(
                    context.applicationContext,
                    context.packageName
                )
            )
            .setAllowCrossProtocolRedirects(true)
    }

    /* ---------------- DOWNLOAD MANAGER ---------------- */

    @Synchronized
    fun getDownloadManager(context: Context): DownloadManager {
        return downloadManager ?: run {

            val manager = DownloadManager(
                context.applicationContext,
                getDatabaseProvider(context),
                getDownloadCache(context),
                getHttpFactory(context),
                backgroundExecutor
            ).apply {
                // ✅ IMPORTANT SETTINGS
                maxParallelDownloads = 1       // sequential downloads
                minRetryCount = 3
            }

            downloadManager = manager
            Log.d(TAG, "DownloadManager initialized")
            manager
        }
    }

    /* ---------------- DOWNLOAD REQUEST ---------------- */

    fun buildDownloadRequest(
        contentId: String,
        url: String
    ): DownloadRequest {
        return DownloadRequest.Builder(
            contentId,
            Uri.parse(url)
        ).build()
    }

    /* ---------------- PATH UTILITY ---------------- */

    /**
     * Logical identifier for downloaded content.
     * Media3 handles actual cache paths internally.
     */
    fun getDownloadPath(contentId: String): String {
        return contentId
    }
}