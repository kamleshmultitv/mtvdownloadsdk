package com.app.videosdk.model

import androidx.media3.datasource.cache.CacheDataSource

data class PlayerModel(
    val hlsUrl: String? = null,
    val mpdUrl: String? = null,
    val liveUrl: String? = null,
    val drm: String? = null,
    val drmToken: String? = null,
    val imageUrl: String? = null,
    val title: String? = null,
    val description: String? = null,
    val seasonTitle: String? = null,
    val seasonDescription: String? = null,
    val srt: String? = null,
    val spriteUrl: String? = null,
    val playbackSpeed: Float = 1.0f,
    val selectedSubtitle: String? = null,
    val selectedVideoQuality: Int = 1080,
    val isLive: Boolean = false,
    val adsConfig: AdsConfig? = null,
    val skipIntro: SkipIntro? = null,
    val nextEpisode: NextEpisode? = null,
    val cacheFactory: CacheDataSource.Factory? = null
)