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
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}
