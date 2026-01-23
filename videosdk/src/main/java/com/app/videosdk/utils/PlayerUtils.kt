package com.app.videosdk.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.annotation.RequiresPermission
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DefaultDrmSessionManagerProvider
import androidx.media3.exoplayer.ima.ImaAdsLoader
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.MappingTrackSelector
import androidx.media3.ui.PlayerView
import com.app.videosdk.listener.AdsListener
import com.app.videosdk.model.AdsConfig
import com.app.videosdk.model.PlayerModel
import com.app.videosdk.model.SubTitleModel
import com.app.videosdk.model.VideoQualityModel
import com.google.ads.interactivemedia.v3.api.AdEvent
import com.google.common.collect.ImmutableList
import com.google.gson.Gson
import java.io.File
import kotlin.math.pow

object PlayerUtils {

    /* =========================================================
       PLAYER + IMA
       ========================================================= */

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    @OptIn(UnstableApi::class)
    fun createPlayer(
        context: Context,
        contentList: List<PlayerModel>,
        selectedIndex: Int = 0,
        videoUrl: String,
        drmToken: String? = null,
        srt: String? = null,
        playerView: PlayerView? = null,
        adsConfig: AdsConfig? = null,
        adsListener: AdsListener? = null,
        existingAdsLoader: ImaAdsLoader? = null
    ): Pair<ExoPlayer, ImaAdsLoader?> {

        // ---------------------------------------------------------
        // Resolve playable URI
        // Prefer the explicit videoUrl passed from the caller.
        // Fall back to internal resolver only if it's blank.
        // ---------------------------------------------------------
        val resolvedUri =
          /*  if (videoUrl.isNotBlank() && videoUrl != "null") {
                videoUrl.toUri()
            } else {*/
                resolveToPlayableUri(contentList, selectedIndex)
        //    }

        // ✅ DEBUG: Log URI resolution
        Log.d("PlayerUtils", "=== PLAYER SETUP DEBUG ===")
        Log.d("PlayerUtils", "videoUrl param: $videoUrl")
        Log.d("PlayerUtils", "resolvedUri: $resolvedUri")
        Log.d("PlayerUtils", "isDash: ${resolvedUri.toString().endsWith(".mpd", ignoreCase = true)}")
        Log.d("PlayerUtils", "drmToken: ${if (drmToken.isNullOrBlank()) "null" else "present"}")
        Log.d("PlayerUtils", "hasCacheFactory: ${contentList[selectedIndex].cacheFactory != null}")
        Log.d("PlayerUtils", "content.drm: ${contentList[selectedIndex].drm}")
        Log.d("PlayerUtils", "==========================")

        require(resolvedUri != Uri.EMPTY) { "No playable content available" }
        val isDash = resolvedUri.toString().endsWith(".mpd", ignoreCase = true)

        /* =========================================================
           ADS LOADER
           ========================================================= */

        val adsLoader =
            existingAdsLoader
                ?: if (
                    adsConfig?.enableAds == true &&
                    !adsConfig.adTagUrl.isNullOrBlank()
                ) {
                    ImaAdsLoader.Builder(context)
                        .setAdEventListener { event ->
                            when (event.type) {
                                AdEvent.AdEventType.LOADED -> adsListener?.onAdsLoaded()
                                AdEvent.AdEventType.STARTED -> adsListener?.onAdStarted()
                                AdEvent.AdEventType.COMPLETED -> adsListener?.onAdCompleted()
                                AdEvent.AdEventType.ALL_ADS_COMPLETED -> adsListener?.onAllAdsCompleted()
                                else -> Unit
                            }
                        }
                        .setAdErrorListener { error ->
                            adsListener?.onAdError(error.error.message)
                            Log.e("IMA", "Ad error", error.error)
                        }
                        .build()
                } else null

        /* =========================================================
           DATA SOURCE FACTORY
           ========================================================= */

        val dataSourceFactory: DataSource.Factory =
            contentList[selectedIndex].cacheFactory
                ?: DefaultHttpDataSource.Factory()

        /* =========================================================
           MEDIA SOURCE FACTORY (FIXED DRM FOR OFFLINE)
           ========================================================= */

        val mediaSourceFactory =
            DefaultMediaSourceFactory(context)
                .setDataSourceFactory(dataSourceFactory)
                .apply {

                    if (isDash && !drmToken.isNullOrBlank()) {
                        // ✅ CRITICAL: Use CacheDataSource for DRM license requests too.
                        //    This allows offline playback using cached licenses.
                        //    If cacheFactory is available (downloaded content), use it.
                        //    Otherwise fall back to direct HTTP (online streaming).
                        val drmDataSourceFactory: DataSource.Factory =
                            contentList[selectedIndex].cacheFactory
                                ?: DefaultHttpDataSource.Factory()
                                    .setAllowCrossProtocolRedirects(true)

                        Log.d("PlayerUtils", "DRM Setup: Using ${if (contentList[selectedIndex].cacheFactory != null) "CacheDataSource" else "DefaultHttpDataSource"} for license requests")

                        val drmProvider =
                            DefaultDrmSessionManagerProvider().apply {
                                setDrmHttpDataSourceFactory(
                                    (drmDataSourceFactory as? DefaultHttpDataSource.Factory)
                                        ?.apply {
                                            setDefaultRequestProperties(
                                                mapOf(
                                                    "Authorization" to "Bearer $drmToken",
                                                    "Content-Type" to "application/octet-stream"
                                                )
                                            )
                                        }
                                        ?: drmDataSourceFactory
                                )
                            }

                        setDrmSessionManagerProvider(drmProvider)


                    }


                    adsLoader?.let { loader ->
                        setAdsLoaderProvider { loader }
                        playerView?.let { view ->
                            setAdViewProvider { view }
                        }
                    }
                }

        /* =========================================================
           PLAYER
           ========================================================= */

        val exoPlayer =
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
                .apply {
                    videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
                    repeatMode = Player.REPEAT_MODE_OFF
                }

        /* =========================================================
           MEDIA ITEM
           ========================================================= */

        val mediaItemBuilder =
            MediaItem.Builder()
                .setUri(resolvedUri)
                .setMimeType(
                    when {
                        resolvedUri.toString().endsWith(".mpd", true) -> MimeTypes.APPLICATION_MPD
                        resolvedUri.toString().endsWith(".m3u8", true) -> MimeTypes.APPLICATION_M3U8
                        resolvedUri.toString().endsWith(".mp4", true) -> MimeTypes.VIDEO_MP4
                        else -> null
                    }
                )

        // ✅ DASH DRM (ONLINE & OFFLINE)
        if (isDash && !drmToken.isNullOrBlank()) {
            // ✅ IMPORTANT: For offline playback, Media3 will use cached license from download.
            //    The licenseUri must match EXACTLY what was used during download.
            //    Headers are optional - only add if your license server requires them.
            val licenseUrl = contentList[selectedIndex].drmToken
            // ⬆️ this MUST be the same URL used during download

            val drmConfigBuilder = MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID).setLicenseUri(licenseUrl)
            // Only add headers if they're actually needed (some servers require Content-Type)
            // For offline playback, headers don't matter since cached license is used.
            /*drmConfigBuilder.setLicenseRequestHeaders(
                mapOf(
                    "Content-Type" to "application/octet-stream"
                )
            )*/
            val drmConfig = drmConfigBuilder.build()
            mediaItemBuilder.setDrmConfiguration(drmConfig)

            Log.d("PlayerUtils", "DRM Configuration set: licenseUri=${drmToken.take(50)}..., hasCacheFactory=${contentList[selectedIndex].cacheFactory != null}")
        }

        // Subtitles
        if (!srt.isNullOrBlank()) {
            mediaItemBuilder.setSubtitleConfigurations(
                ImmutableList.of(initializeSubTitleTracker(srt))
            )
        }

        // Ads
        if (
            adsLoader != null &&
            adsConfig?.enableAds == true &&
            !adsConfig.adTagUrl.isNullOrBlank()
        ) {
            mediaItemBuilder.setAdsConfiguration(
                MediaItem.AdsConfiguration.Builder(
                    adsConfig.adTagUrl.trim().toUri()
                ).build()
            )
            adsLoader.setPlayer(exoPlayer)
        }

        /* =========================================================
           PREPARE & PLAY
           ========================================================= */

        exoPlayer.setMediaItem(mediaItemBuilder.build())
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        return exoPlayer to adsLoader
    }


    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun resolveToPlayableUri(
        contentList: List<PlayerModel>,
        selectedIndex: Int = 0
    ): Uri {
        if (contentList.isEmpty()) return Uri.EMPTY
        val content = contentList[selectedIndex]

        val mpd = content.mpdUrl
        val hls = content.hlsUrl
        val live = content.liveUrl

        // Always resolve to the best available stream URL.
        // Actual offline/online behavior is handled by the DataSource (CacheDataSource).
        val primaryUrl = when {
            // Prefer DASH for DRM content when provided
            content.drm == "1" && !mpd.isNullOrBlank() -> mpd
            !hls.isNullOrBlank() -> hls
            !live.isNullOrBlank() -> live
            !mpd.isNullOrBlank() -> mpd
            else -> null
        }?.trim()

        if (primaryUrl.isNullOrBlank()) return Uri.EMPTY

        return when {
            primaryUrl.startsWith("content://") ||
                    primaryUrl.startsWith("http://") ||
                    primaryUrl.startsWith("https://") ||
                    primaryUrl.startsWith("file://") -> {
                primaryUrl.toUri()
            }

            primaryUrl.startsWith("/") -> {
                val file = File(primaryUrl)
                if (file.exists()) Uri.fromFile(file) else Uri.EMPTY
            }

            else -> primaryUrl.toUri()
        }
    }

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun isInternetAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }


    private fun initializeSubTitleTracker(srt: String): MediaItem.SubtitleConfiguration {
        return MediaItem.SubtitleConfiguration.Builder(srt.toUri())
            .setMimeType(MimeTypes.APPLICATION_SUBRIP)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
    }

    @OptIn(UnstableApi::class)
    fun showAudioTrack(context: Context, exoPlayer: ExoPlayer?): List<SubTitleModel> {
        val exoPlayerInstance = exoPlayer ?: return emptyList()
        val trackSelector =
            exoPlayerInstance.trackSelector as? DefaultTrackSelector ?: return emptyList()
        val trackGroups = trackSelector.currentMappedTrackInfo?.getTrackGroups(1) ?: run {
            return emptyList()
        }
        val audioTracks = (0 until trackGroups.length).mapNotNull { index ->
            val format = trackGroups.get(index).getFormat(0)
            val label = format.language ?: format.label
            label.takeIf { it != null && it != "und" }
        }.distinct()

        return if (audioTracks.isNotEmpty()) {
            getAudioTrack(context, audioTracks)
        } else {
            emptyList()
        }
    }

    fun getAudioTrack(
        context: Context,
        audioTracks: List<String>
    ): List<SubTitleModel> {

        val json = context.assets
            .open("hls.json")
            .bufferedReader()
            .use { it.readText() }

        val gson = Gson()

        // ✅ RELEASE-SAFE: no generics, no reflection
        val subtitleArray =
            gson.fromJson(json, Array<SubTitleModel>::class.java)
                ?: emptyArray()

        val subtitleDataList = subtitleArray.toList()

        val result = mutableListOf<SubTitleModel>()

        for (audioTrack in audioTracks) {
            for (subtitleData in subtitleDataList) {
                if (audioTrack == subtitleData.id) {
                    result.add(subtitleData)
                    break
                }
            }
        }

        return result
    }


    @OptIn(UnstableApi::class)
    fun selectAudioTrack(language: String, exoPlayer: ExoPlayer?) {
        val trackSelector = exoPlayer?.trackSelector as? DefaultTrackSelector ?: return
        val parameters = trackSelector.buildUponParameters()
            .setPreferredAudioLanguage(language)
            .build()
        trackSelector.setParameters(parameters)
    }

    @OptIn(UnstableApi::class)
    fun getSubTitleFormats(exoPlayer: ExoPlayer?): List<Format> {
        val subTitleFormatList = mutableListOf<Format>()
        val trackSelector = exoPlayer?.trackSelector as? DefaultTrackSelector
        val mappedTrackInfo = trackSelector?.currentMappedTrackInfo ?: return emptyList()

        val subtitleRendererIndices = (0 until mappedTrackInfo.rendererCount).filter { index ->
            videoRendererInfo(mappedTrackInfo, index, isSubtitle = true)
        }

        subtitleRendererIndices.forEach { subtitleRendererIndex ->
            val override = mappedTrackInfo.getTrackGroups(subtitleRendererIndex)
            subTitleFormatList.addAll(getVideoQualityList(override))
        }

        if (subTitleFormatList.isEmpty()) {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        }

        return subTitleFormatList
    }

    @OptIn(UnstableApi::class)
    private fun videoRendererInfo(
        mappedTrackInfo: MappingTrackSelector.MappedTrackInfo,
        rendererIndex: Int,
        isSubtitle: Boolean = false
    ): Boolean {
        return mappedTrackInfo.getTrackGroups(rendererIndex).length > 0 &&
                mappedTrackInfo.getRendererType(rendererIndex) == if (isSubtitle) C.TRACK_TYPE_TEXT else C.TRACK_TYPE_VIDEO
    }

    @OptIn(UnstableApi::class)
    private fun getVideoQualityList(trackGroups: TrackGroupArray): List<Format> {
        val videoQuality = mutableListOf<Format>()
        for (groupIndex in 0 until trackGroups.length) {
            val group = trackGroups[groupIndex]
            for (trackIndex in 0 until group.length) {
                videoQuality.add(group.getFormat(trackIndex))
            }
        }
        return videoQuality
    }

    fun calculatePitch(speed: Float): Float {
        val basePitch = 1.0f
        val pitchChangeRatio = 0.05f
        return basePitch / speed.pow(pitchChangeRatio)
    }

    @OptIn(UnstableApi::class)
    fun getVideoFormats(exoPlayer: ExoPlayer?): List<VideoQualityModel> {
        val trackSelector = exoPlayer?.trackSelector as? DefaultTrackSelector
        val mappedTrackInfo = trackSelector?.currentMappedTrackInfo ?: return emptyList()

        val videoQualityList = mutableListOf(VideoQualityModel("auto", 0, 0, 0, "Auto"))

        for (index in 0 until mappedTrackInfo.rendererCount) {
            if (videoRendererInfo(mappedTrackInfo, index, false)) {
                val formats = getVideoQualityList(mappedTrackInfo.getTrackGroups(index))
                formats.forEach { format ->
                    videoQualityList.add(
                        VideoQualityModel(
                            id = format.id.toString(),
                            height = format.height,
                            width = format.width,
                            bitrate = format.bitrate,
                            title = format.height.toString()
                        )
                    )
                }
            }
        }

        return videoQualityList.sortedByDescending { it.height }
    }

    @OptIn(UnstableApi::class)
    fun setAutoVideoResolution(exoPlayer: ExoPlayer?) {
        val trackSelector = exoPlayer?.trackSelector as? DefaultTrackSelector
        trackSelector?.buildUponParameters()?.clearVideoSizeConstraints()?.let {
            trackSelector.setParameters(it)
        }
    }

    @OptIn(UnstableApi::class)
    fun changeVideoResolution(exoPlayer: ExoPlayer?, width: Int, height: Int) {
        val trackSelector = exoPlayer?.trackSelector as? DefaultTrackSelector
        trackSelector?.buildUponParameters()?.setMaxVideoSize(width, height)?.let {
            trackSelector.setParameters(it)
            Log.d("Utils", "Changed video resolution to ${width}x${height}")
        }
    }

    @SuppressLint("DefaultLocale")
    fun formatTime(milliseconds: Long): String {
        val totalSeconds = (milliseconds / 1000).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    fun timeToMillis(
        time: String?,
        offset: String? = "0"
    ): Long {
        val durationMs = parseDurationToMillis(time)
        val offsetMs = parseDurationToMillis(offset)

        return (durationMs - offsetMs).coerceAtLeast(0L)
    }

    @JvmStatic
    fun parseDurationToMillis(input: String?): Long {
        if (input.isNullOrBlank()) return 0L

        val value = input.trim()

        // Digits only → milliseconds
        if (value.all { it.isDigit() }) {
            return value.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        }

        val parts = value.split(":")
        val numbers = parts.map { it.toLongOrNull() ?: return 0L }

        return when (numbers.size) {
            3 -> { // HH:mm:ss
                val (h, m, s) = numbers
                (h * 3600 + m * 60 + s) * 1000
            }

            2 -> { // mm:ss
                val (m, s) = numbers
                (m * 60 + s) * 1000
            }

            else -> 0L
        }
    }
}