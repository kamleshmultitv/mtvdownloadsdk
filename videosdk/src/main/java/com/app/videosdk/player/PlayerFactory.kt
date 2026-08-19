package com.app.videosdk.player

import android.Manifest
import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.annotation.OptIn
import androidx.annotation.RequiresPermission
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DefaultDrmSessionManagerProvider
import androidx.media3.exoplayer.hls.SampleQueueMappingException
import androidx.media3.exoplayer.ima.ImaAdsLoader
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.app.videosdk.listener.AdsListener
import com.app.videosdk.model.AdsConfig
import com.app.videosdk.model.PlayerModel
import com.app.videosdk.utils.SdkLogger
import com.google.ads.interactivemedia.v3.api.AdEvent
import com.google.common.collect.ImmutableList

internal object PlayerFactory {

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    @OptIn(UnstableApi::class)
    fun createPlayer(
        context: Context,
        contentList: List<PlayerModel>? = null,
        selectedIndex: Int = 0,
        videoUrl: String,
        drmToken: String? = null,
        srt: String? = null,
        playerView: PlayerView? = null,
        adsConfig: AdsConfig? = null,
        adsListener: AdsListener? = null,
        existingAdsLoader: ImaAdsLoader? = null,
        playWhenReady: Boolean = true,
        onRecoveryStateChanged: (PlaybackRecoveryState) -> Unit = {}
    ): Pair<ExoPlayer, ImaAdsLoader?> {
        val content = contentList?.getOrNull(selectedIndex)
        val contentId = content?.id

        val download =
            content?.downloadManager
                ?.downloadIndex
                ?.getDownload(contentId.toString())

        val completedDownload = download?.takeIf { it.state == Download.STATE_COMPLETED }
        val isOffline = completedDownload != null

        SdkLogger.debug(
            "isOffline=$isOffline, contentId=$contentId, downloadExists=${download != null}"
        )

        val resolverUri = PlaybackSourceResolver.resolveToPlayableUri(contentList, selectedIndex)
        val onlineResolvedUri =
            when {
                resolverUri.isUsablePlaybackUri() -> resolverUri
                videoUrl.isNotBlank() -> videoUrl.trim().toUri()
                else -> Uri.EMPTY
            }
        val resolvedUri = if (isOffline) Uri.EMPTY else onlineResolvedUri

        val adsLoader =
            existingAdsLoader
                ?: if (adsConfig?.enableAds == true && !adsConfig.adTagUrl.isNullOrBlank()) {
                    ImaAdsLoader.Builder(context)
                        .setAdEventListener { event ->
                            when (event.type) {
                                AdEvent.AdEventType.LOADED -> {
                                    adsListener?.onAdsLoaded()
                                }

                                AdEvent.AdEventType.STARTED -> {
                                    adsListener?.onAdStarted()
                                }

                                AdEvent.AdEventType.COMPLETED -> {
                                    adsListener?.onAdCompleted()
                                }

                                AdEvent.AdEventType.ALL_ADS_COMPLETED -> {
                                    adsListener?.onAllAdsCompleted()
                                }

                                else -> Unit
                            }
                        }
                        .setAdErrorListener { error ->
                            adsListener?.onAdError(error.error.message)
                            SdkLogger.error("IMA ad error: ${error.error.message}", error.error)
                        }
                        .build()
                } else {
                    null
                }

        val cache = content?.downloadCache

        val dataSourceFactory: DataSource.Factory =
            content?.cacheFactory ?: if (cache != null) {
                CacheDataSource.Factory()
                    .setCache(cache)
                    .setUpstreamDataSourceFactory(null)
                    .setCacheWriteDataSinkFactory(null)
                    .setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE)
            } else {
                DefaultHttpDataSource.Factory()
            }

        val mediaSourceFactory =
            DefaultMediaSourceFactory(context)
                .setDataSourceFactory(dataSourceFactory)
                .apply {
                    if (!drmToken.isNullOrBlank()) {
                        setDrmSessionManagerProvider(
                            DefaultDrmSessionManagerProvider().apply {
                                setDrmHttpDataSourceFactory(DefaultHttpDataSource.Factory())
                            }
                        )
                    }

                    adsLoader?.let { loader ->
                        setAdsLoaderProvider { loader }
                        playerView?.let { view ->
                            setAdViewProvider { view }
                        }
                    }
                }

        val exoPlayer =
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()

        var audioFallbackAttempted = false
        var onlineFallbackAttempted = false

        fun fallbackToOnline(reason: PlaybackRecoveryReason, message: String?): Boolean {
            if (!isOffline || onlineFallbackAttempted || !onlineResolvedUri.isUsablePlaybackUri()) {
                return false
            }

            onlineFallbackAttempted = true
            onRecoveryStateChanged(
                PlaybackRecoveryState(
                    reason = reason,
                    action = PlaybackRecoveryAction.FALLBACK_TO_ONLINE,
                    message = message,
                    attempt = 1
                )
            )

            exoPlayer.setMediaItem(
                buildOnlineMediaItem(
                    content = content,
                    resolvedUri = onlineResolvedUri,
                    drmToken = drmToken,
                    srt = srt,
                    adsLoader = adsLoader,
                    adsConfig = adsConfig,
                    exoPlayer = exoPlayer
                )
            )
            exoPlayer.prepare()
            content.seekTo?.let { position -> exoPlayer.seekTo(position) }
            exoPlayer.playWhenReady = true

            return true
        }

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                SdkLogger.error(
                    "Playback error: errorCode=${error.errorCodeName}, message=${error.message}, cause=${error.cause?.javaClass?.simpleName}",
                    error
                )

                when (error.errorCode) {
                    PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR ->
                        SdkLogger.error("Offline DRM license not found")

                    PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED ->
                        SdkLogger.error("Offline DRM license expired")

                    PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED ->
                        SdkLogger.error("Widevine provisioning failed")

                    else -> Unit
                }

                val recoveryReason = error.toRecoveryReason()

                if (
                    recoveryReason == PlaybackRecoveryReason.OFFLINE_DRM_LICENSE_MISSING ||
                    recoveryReason == PlaybackRecoveryReason.OFFLINE_DRM_LICENSE_EXPIRED ||
                    recoveryReason == PlaybackRecoveryReason.DRM_PROVISIONING_FAILED
                ) {
                    if (fallbackToOnline(recoveryReason, error.message)) return
                }

                if (
                    error.cause is SampleQueueMappingException ||
                    error.message?.contains("audio/mp4a-latm") == true
                ) {
                    if (audioFallbackAttempted) {
                        onRecoveryStateChanged(
                            PlaybackRecoveryState(
                                reason = recoveryReason,
                                action = PlaybackRecoveryAction.NONE,
                                message = error.message,
                                attempt = 1
                            )
                        )
                        return
                    }

                    audioFallbackAttempted = true
                    SdkLogger.info("Audio track unsupported; falling back to video-only")
                    onRecoveryStateChanged(
                        PlaybackRecoveryState(
                            reason = PlaybackRecoveryReason.AUDIO_TRACK_UNSUPPORTED,
                            action = PlaybackRecoveryAction.DISABLE_AUDIO_TRACK,
                            message = error.message,
                            attempt = 1
                        )
                    )

                    exoPlayer.trackSelectionParameters =
                        exoPlayer.trackSelectionParameters
                            .buildUpon()
                            .setDisabledTrackTypes(setOf(C.TRACK_TYPE_AUDIO))
                            .build()

                    exoPlayer.prepare()
                    exoPlayer.playWhenReady = true
                    return
                }

                onRecoveryStateChanged(
                    PlaybackRecoveryState(
                        reason = recoveryReason,
                        action = PlaybackRecoveryAction.NONE,
                        message = error.message
                    )
                )
            }
        })

        val mediaItem: MediaItem =
            if (completedDownload != null) {
                SdkLogger.debug("Using offline MediaItem")

                val isDashOffline =
                    completedDownload.request.mimeType == MimeTypes.APPLICATION_MPD ||
                        completedDownload.request.uri.toString()
                            .substringBefore("?")
                            .endsWith(".mpd", ignoreCase = true) ||
                        (content.drm == "1" && !content.mpdUrl.isNullOrBlank())

                if (isDashOffline) {
                    buildOfflineDashMediaItem(
                        download = completedDownload,
                        content = content
                    )
                } else {
                    completedDownload.request.toMediaItem()
                }
            } else {
                SdkLogger.debug("Using online MediaItem: $resolvedUri")
                buildOnlineMediaItem(
                    content = content,
                    resolvedUri = resolvedUri,
                    drmToken = drmToken,
                    srt = srt,
                    adsLoader = adsLoader,
                    adsConfig = adsConfig,
                    exoPlayer = exoPlayer
                )
            }

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        content?.seekTo?.let { position ->
            exoPlayer.seekTo(position)
        }
        exoPlayer.playWhenReady = playWhenReady

        return exoPlayer to adsLoader
    }

    private fun buildOnlineMediaItem(
        content: PlayerModel?,
        resolvedUri: Uri,
        drmToken: String?,
        srt: String?,
        adsLoader: ImaAdsLoader?,
        adsConfig: AdsConfig?,
        exoPlayer: ExoPlayer
    ): MediaItem {
        val deepStart = content?.seekTo ?: 0L
        val deepEnd = content?.deepLinkEndMs

        return MediaItem.Builder()
            .setUri(resolvedUri)
            .apply {
                if (deepEnd != null && deepEnd > deepStart) {
                    setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(deepStart)
                            .setEndPositionMs(deepEnd)
                            .build()
                    )
                }

                if (!drmToken.isNullOrBlank()) {
                    setDrmConfiguration(
                        MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                            .setLicenseUri(drmToken)
                            .setForceDefaultLicenseUri(true)
                            .setMultiSession(true)
                            .build()
                    )
                }

                if (!srt.isNullOrBlank()) {
                    setSubtitleConfigurations(
                        ImmutableList.of(initializeSubTitleTracker(srt))
                    )
                }

                if (
                    adsLoader != null &&
                    adsConfig?.enableAds == true &&
                    !adsConfig.adTagUrl.isNullOrBlank()
                ) {
                    setAdsConfiguration(
                        MediaItem.AdsConfiguration.Builder(
                            adsConfig.adTagUrl.trim().toUri()
                        ).build()
                    )
                    adsLoader.setPlayer(exoPlayer)
                }
            }
            .build()
    }

    @OptIn(UnstableApi::class)
    private fun buildOfflineDashMediaItem(
        download: Download,
        content: PlayerModel?
    ): MediaItem {
        val mediaItem = download.request.toMediaItem()
        val keySetId = download.request.keySetId
            ?: content?.drmOfflineKeySetId
            ?: decodeKeySetId(content?.drmOfflineKeySetIdBase64)

        if (keySetId != null) {
            SdkLogger.info("DRM_OFFLINE_PLAYBACK_PREPARE contentId=${download.request.id} keySetBytes=${keySetId.size}")
            return mediaItem
                .buildUpon()
                .setDrmConfiguration(
                    MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                        .setKeySetId(keySetId)
                        .build()
                )
                .build()
        }

        SdkLogger.error("DRM_OFFLINE_PLAYBACK_FAILED contentId=${download.request.id} reason=missing_keySetId")

        return mediaItem
    }

    private fun decodeKeySetId(keySetIdBase64: String?): ByteArray? {
        val value = keySetIdBase64
            ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            ?: return null

        return runCatching {
            Base64.decode(value, Base64.DEFAULT)
        }.getOrNull()
    }

    private fun initializeSubTitleTracker(srt: String): MediaItem.SubtitleConfiguration =
        MediaItem.SubtitleConfiguration.Builder(srt.toUri())
            .setMimeType(MimeTypes.APPLICATION_SUBRIP)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()

    private fun Uri.isUsablePlaybackUri(): Boolean = toString().isNotBlank()

    private fun PlaybackException.toRecoveryReason(): PlaybackRecoveryReason =
        when (errorCode) {
            PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR ->
                PlaybackRecoveryReason.OFFLINE_DRM_LICENSE_MISSING

            PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED ->
                PlaybackRecoveryReason.OFFLINE_DRM_LICENSE_EXPIRED

            PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED ->
                PlaybackRecoveryReason.DRM_PROVISIONING_FAILED

            else ->
                if (
                    cause is SampleQueueMappingException ||
                    message?.contains("audio/mp4a-latm") == true
                ) {
                    PlaybackRecoveryReason.AUDIO_TRACK_UNSUPPORTED
                } else {
                    PlaybackRecoveryReason.UNKNOWN
                }
        }
}
