package com.app.mtvdownloader

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.database.ExoDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * Media3 Download utility (Singleton)
 * Handles cache, download manager, and DRM keySetId persistence.
 */
@OptIn(UnstableApi::class)
object DownloadUtil {

    private const val TAG = "DownloadUtil"

    private const val DOWNLOAD_DIR = "downloads"
    private const val MAX_CACHE_BYTES = 500L * 1024L * 1024L // 500 MB

    @Volatile private var databaseProvider: ExoDatabaseProvider? = null
    @Volatile private var downloadCache: SimpleCache? = null
    @Volatile private var downloadManager: DownloadManager? = null
    @Volatile private var downloadDirectory: File? = null
    @Volatile private var downloadNotificationHelper: DownloadNotificationHelper? = null

    // Disk + network safe
    private val backgroundExecutor: Executor by lazy {
        Executors.newFixedThreadPool(1)
    }

    /* ---------------- DATABASE ---------------- */

    @Synchronized
    fun getDatabaseProvider(context: Context): ExoDatabaseProvider {
        return databaseProvider ?: ExoDatabaseProvider(
            context.applicationContext
        ).also { databaseProvider = it }
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
            val cacheDir = getDownloadDirectory(context)
            val evictor = LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES)
            val db = getDatabaseProvider(context)

            SimpleCache(cacheDir, evictor, db).also {
                downloadCache = it
                Log.d(TAG, "SimpleCache initialized at ${cacheDir.absolutePath}")
            }
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
        ).also { downloadNotificationHelper = it }
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

    @Synchronized
    fun getDataSourceFactory(context: Context): DefaultDataSource.Factory {
        return DefaultDataSource.Factory(
            context.applicationContext,
            getHttpFactory(context)
        )
    }

    @Synchronized
    fun getCacheDataSourceFactory(context: Context): CacheDataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(getDownloadCache(context))
            .setUpstreamDataSourceFactory(getDataSourceFactory(context))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
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
                maxParallelDownloads = 1
                minRetryCount = 3
            }

            // 🔑 DRM keySetId listener (CRITICAL for offline DASH)
            manager.addListener(object : DownloadManager.Listener {

                override fun onDownloadChanged(
                    downloadManager: DownloadManager,
                    download: Download,
                    finalException: Exception?
                ) {
                    val contentId = download.request.id
                    val keySetId = download.request.keySetId

                    if (keySetId != null) {
                        Log.d(TAG, "DRM keySetId captured for $contentId")

                        saveKeySetIdIfNeeded(
                            contentId = contentId,
                            keySetId = keySetId
                        )
                    }
                }
            })

            downloadManager = manager
            Log.d(TAG, "DownloadManager initialized")
            manager
        }
    }

    /* ---------------- DRM PERSISTENCE ---------------- */

    /**
     * Prevent duplicate writes (onDownloadChanged fires multiple times)
     */
    private fun saveKeySetIdIfNeeded(contentId: String, keySetId: ByteArray) {
        if (!isKeySetIdSaved(contentId)) {
            saveKeySetId(contentId, keySetId)
        }
    }

    /**
     * TODO: Implement using Room / DataStore / File
     */
    private fun isKeySetIdSaved(contentId: String): Boolean {
        // Example:
        // return drmDao.getKeySetId(contentId) != null
        return false
    }

    /**
     * TODO: Persist keySetId securely
     */
    private fun saveKeySetId(contentId: String, keySetId: ByteArray) {
        // Example Room:
        // drmDao.insert(DrmEntity(contentId, keySetId))
        Log.d(TAG, "keySetId saved for $contentId")
    }

    /* ---------------- PATH UTILITY ---------------- */

    /**
     * Logical identifier only.
     * Media3 manages actual cache paths internally.
     */
    fun getDownloadPath(contentId: String): String = contentId
}
