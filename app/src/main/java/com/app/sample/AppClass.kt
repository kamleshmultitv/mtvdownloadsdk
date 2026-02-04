package com.app.sample

import android.app.Application
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import com.app.mtvdownloader.DownloadUtil
import com.app.mtvdownloader.DownloadUtil.getDownloadManager
import androidx.media3.exoplayer.offline.DownloadManager
import com.app.mtvdownloader.DownloadUtil.getDownloadCache

@UnstableApi
class AppClass : Application() {

    lateinit var cacheDataSourceFactory: CacheDataSource.Factory
        private set

    lateinit var downloadManager: DownloadManager
        private set

    lateinit var downloadCache: SimpleCache
        private set

    override fun onCreate() {
        super.onCreate()

        // ✅ INIT DOWNLOAD MANAGER SAFELY
        downloadManager = getDownloadManager(this)
        downloadCache = getDownloadCache(this)

        // ✅ INIT CACHE FACTORY
        cacheDataSourceFactory =
            CacheDataSource.Factory()
                .setCache(getDownloadCache(this))
                .setUpstreamDataSourceFactory(
                    DownloadUtil.getDataSourceFactory(this)
                )
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}
