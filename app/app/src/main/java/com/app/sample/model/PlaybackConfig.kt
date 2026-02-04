package com.app.sample.model

import com.app.videosdk.model.AdsConfig
import com.app.videosdk.model.NextEpisode
import com.app.videosdk.model.SkipIntro

data class PlaybackConfig(
    val url: String = "",
    val drmToken: String = "",
    val isLive: Boolean = false,
    val adsConfig: AdsConfig = AdsConfig(enableAds = false),
    val skipIntro: SkipIntro = SkipIntro(enableSkipIntro = false),
    val nextEpisode: NextEpisode = NextEpisode(enableNextEpisode = false)
)