package com.app.sample

import android.app.Application
import androidx.media3.datasource.cache.CacheDataSource
import com.app.mtvdownloader.DownloadUtil

class AppClass : Application() {

    lateinit var cacheDataSourceFactory: CacheDataSource.Factory
        private set

    override fun onCreate() {
        super.onCreate()

        cacheDataSourceFactory =
            CacheDataSource.Factory()
                .setCache(DownloadUtil.getDownloadCache(this))
                .setUpstreamDataSourceFactory(
                    DownloadUtil.getHttpFactory(this)
                )
                // ✅ CRITICAL: No flags = serve from cache first, fallback to network only on cache miss.
                //    FLAG_IGNORE_CACHE_ON_ERROR was causing network fallback even when cache had data,
                //    which broke offline DRM playback. Without flags, cached licenses will be used offline.
                .setFlags(0)
    }
}
