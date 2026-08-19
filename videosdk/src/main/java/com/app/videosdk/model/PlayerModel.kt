package com.app.videosdk.model

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.DownloadManager
import okhttp3.Cache

@OptIn(UnstableApi::class)
data class PlayerModel(
    val id: String? = null,
    val hlsUrl: String? = null,
    val mpdUrl: String? = null,
    val seekTo: Long? = null,
    var deepLinkEndMs: Long? = null,
    var deepLinkClipDuration: Long? = null,
    val liveUrl: String? = null,
    val drm: String? = null,
    val drmToken: String? = null,
    val imageUrl: String? = null,
    val episodeTitle: String? = null,
    val episodeDescription: String? = null,
    val seasonTitle: String? = null,
    val seasonDescription: String? = null,
    val description: String? = null,
    val seasonNumber: String? = null,
    val episodeNumber: String? = null,
    val duration: String? = null,
    val srt: String? = null,
    val spriteUrl: String? = null,
    val playbackSpeed: Float = 1.0f,
    val selectedSubtitle: String? = null,
    val selectedVideoQuality: Int = 1080,
    val isLive: Boolean = false,
    val adsConfig: AdsConfig? = null,
    val gamAdsConfig: GAMAdsConfig? = null,
    val skipIntro: SkipIntro? = null,
    val nextEpisode: NextEpisode? = null,
    val customControls: PlayerCustomControls? = null,
    val isClipEnabled: Boolean = false,
    val isChapterEnabled: Boolean = false,
    val chapters: List<Chapter>? = null,
    val cacheFactory: CacheDataSource.Factory? = null,
    val downloadManager: DownloadManager? = null,
    val downloadCache: SimpleCache? = null,
    val drmOfflineKeySetId: ByteArray? = null,
    val drmOfflineKeySetIdBase64: String? = null,

    /** Human-readable title supplied by the content API. */
    val title: String? = null,

    /** Direct playback URL used when no format-specific URL is supplied. */
    val videoUrl: String? = null,

    /** Thumbnail supplied by the content API. */
    val thumbnail: String? = null,

    /**
     * Age classification supplied by the content API (for example `U/A 13+`).
     *
     * The SDK renders this value verbatim. `null`, empty, and whitespace-only values
     * suppress the age-rating overlay.
     */
    val ageRating: String? = null,

    /**
     * Alternate backend field supported for APIs that name the classification
     * `contentRating`. [ageRating] takes precedence when both contain a value.
     */
    val contentRating: String? = null
)
