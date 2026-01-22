package com.app.videosdk.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
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

    @OptIn(UnstableApi::class)
    fun createPlayer(
        context: Context,
        contentList: List<PlayerModel>,
        videoUrl: String,
        drmToken: String? = null,
        srt: String? = null,
        playerView: PlayerView? = null,
        adsConfig: AdsConfig? = null,
        adsListener: AdsListener? = null,
        existingAdsLoader: ImaAdsLoader? = null
    ): Pair<ExoPlayer, ImaAdsLoader?> {

        // ---------------------------------------------------------
        // Resolve playable URI (online or offline)
        // ---------------------------------------------------------
        val resolvedUri = resolveToPlayableUri(context, contentList)
        require(resolvedUri != Uri.EMPTY) { "No playable content available" }

        val cleanUrl =
            if (resolvedUri.scheme == "http" || resolvedUri.scheme == "https")
                resolvedUri.toString().substringBefore("?")
            else
                resolvedUri.toString()

        val isDash = cleanUrl.endsWith(".mpd", ignoreCase = true)

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
           MEDIA SOURCE FACTORY
           ========================================================= */

        val dataSourceFactory: DataSource.Factory =
            contentList.first().cacheFactory
                ?: DefaultHttpDataSource.Factory()

        val mediaSourceFactory =
            DefaultMediaSourceFactory(context)
                .setDataSourceFactory(dataSourceFactory)
                .apply {

                    if (isDash && !drmToken.isNullOrBlank()) {
                        val drmProvider = DefaultDrmSessionManagerProvider().apply {
                            setDrmHttpDataSourceFactory(DefaultHttpDataSource.Factory())
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
                        cleanUrl.endsWith(".mpd", true) -> MimeTypes.APPLICATION_MPD
                        cleanUrl.endsWith(".m3u8", true) -> MimeTypes.APPLICATION_M3U8
                        cleanUrl.endsWith(".mp4", true) -> MimeTypes.VIDEO_MP4
                        else -> null
                    }
                )

        if (isDash && !drmToken.isNullOrBlank()) {
            mediaItemBuilder.setDrmConfiguration(
                MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                    .setLicenseUri(drmToken.toUri())
                    .build()
            )
        }

        if (!srt.isNullOrBlank()) {
            mediaItemBuilder.setSubtitleConfigurations(
                ImmutableList.of(initializeSubTitleTracker(srt))
            )
        }

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

    fun resolveToPlayableUri(
        context: Context,
        contentList: List<PlayerModel>
    ): Uri {

        if (contentList.isEmpty()) return Uri.EMPTY

        val content = contentList.first()
        val hasInternet = isInternetAvailable(context)

        val mpd = content.mpdUrl
        val hls = content.hlsUrl
        val live = content.liveUrl

        /* =========================================================
           OFFLINE MODE → ONLY LOCAL FILE / CONTENT URI
           ========================================================= */
        if (!hasInternet) {

            val localCandidate = mpd ?: hls ?: live ?: return Uri.EMPTY

            return when {
                localCandidate.startsWith("content://") ->
                    Uri.parse(localCandidate)

                localCandidate.startsWith("http://") ->
                    Uri.parse(localCandidate)

                localCandidate.startsWith("https://") ->
                    Uri.parse(localCandidate)

                localCandidate.startsWith("file://") ->
                    Uri.parse(localCandidate)

                localCandidate.startsWith("/") -> {
                    val file = File(localCandidate)
                    if (file.exists()) Uri.fromFile(file) else Uri.EMPTY
                }

                localCandidate.startsWith("content://") ->
                    Uri.parse(localCandidate)

                else -> Uri.EMPTY // ❌ http/https NOT allowed offline
            }
        }

        /* =========================================================
           ONLINE MODE → REMOTE STREAM
           ========================================================= */
        val remoteUrl = mpd ?: hls ?: live
        return if (!remoteUrl.isNullOrBlank()) {
            Uri.parse(remoteUrl)
        } else {
            Uri.EMPTY
        }
    }


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