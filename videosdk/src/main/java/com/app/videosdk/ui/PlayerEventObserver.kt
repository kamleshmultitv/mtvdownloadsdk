package com.app.videosdk.ui

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.geometry.Size
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.ima.ImaAdsLoader
import com.app.videosdk.listener.PlayerStateListener
import com.app.videosdk.model.CuePoint
import com.app.videosdk.model.CueType
import com.app.videosdk.model.PlayerAnalyticsEventType
import com.app.videosdk.model.PlayerDiagnosticSeverity
import com.app.videosdk.utils.SdkLogger
import com.app.videosdk.utils.emitAnalytics
import com.app.videosdk.utils.emitDiagnostic
import kotlin.math.max

@OptIn(UnstableApi::class)
@Composable
internal fun PlayerEventObserver(
    exoPlayer: ExoPlayer?,
    adsLoader: ImaAdsLoader?,
    ageRating: String?,
    containerSize: Size,
    contentListSize: Int,
    selectedIndex: Int,
    contentId: String?,
    analyticsEnabled: Boolean,
    diagnosticsEnabled: Boolean,
    playerStateListener: PlayerStateListener?,
    imaCuePoints: SnapshotStateList<CuePoint>,
    onLoadingChanged: (Boolean) -> Unit,
    onContentDurationChanged: (Long) -> Unit,
    onFirstFrameRendered: () -> Unit,
    onVideoSizeChanged: (VideoSize) -> Unit,
    onFillScaleChanged: (Float) -> Unit,
    onFilledChanged: (Boolean) -> Unit,
    onZoomAccumulatorChanged: (Float) -> Unit,
    onActivatedFillChanged: (Boolean) -> Unit,
    onAgeRatingPresentationRequested: () -> Unit,
    onPlayNextContent: () -> Unit
) {
    val currentAgeRating by rememberUpdatedState(ageRating)
    val currentContainerSize by rememberUpdatedState(containerSize)
    val currentContentListSize by rememberUpdatedState(contentListSize)
    val currentSelectedIndex by rememberUpdatedState(selectedIndex)
    val currentContentId by rememberUpdatedState(contentId)
    val currentAnalyticsEnabled by rememberUpdatedState(analyticsEnabled)
    val currentDiagnosticsEnabled by rememberUpdatedState(diagnosticsEnabled)
    val currentPlayerStateListener by rememberUpdatedState(playerStateListener)
    val currentOnLoadingChanged by rememberUpdatedState(onLoadingChanged)
    val currentOnContentDurationChanged by rememberUpdatedState(onContentDurationChanged)
    val currentOnFirstFrameRendered by rememberUpdatedState(onFirstFrameRendered)
    val currentOnVideoSizeChanged by rememberUpdatedState(onVideoSizeChanged)
    val currentOnFillScaleChanged by rememberUpdatedState(onFillScaleChanged)
    val currentOnFilledChanged by rememberUpdatedState(onFilledChanged)
    val currentOnZoomAccumulatorChanged by rememberUpdatedState(onZoomAccumulatorChanged)
    val currentOnActivatedFillChanged by rememberUpdatedState(onActivatedFillChanged)
    val currentOnAgeRatingPresentationRequested by rememberUpdatedState(
        onAgeRatingPresentationRequested
    )
    val currentOnPlayNextContent by rememberUpdatedState(onPlayNextContent)

    DisposableEffect(exoPlayer) {
        val player = exoPlayer ?: return@DisposableEffect onDispose {}
        var hasPresentedForThisPlayback = false
        var restartArmed = false

        fun presentAgeRatingIfNeeded() {
            if (currentAgeRating == null || !player.isPlaying) return
            if (!hasPresentedForThisPlayback || restartArmed) {
                currentOnAgeRatingPresentationRequested()
                hasPresentedForThisPlayback = true
                restartArmed = false
            }
        }

        val listener = object : Player.Listener {
            override fun onVolumeChanged(volume: Float) {
                val isMuted = volume == 0f
                currentPlayerStateListener?.onMuteStateChanged(isMuted)
                currentPlayerStateListener.emitAnalytics(
                    enabled = currentAnalyticsEnabled,
                    type = PlayerAnalyticsEventType.MUTE_CHANGED,
                    contentId = currentContentId,
                    positionMs = player.currentPosition,
                    durationMs = player.duration,
                    attributes = mapOf("muted" to isMuted.toString())
                )
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                currentOnActivatedFillChanged(false)
                currentOnVideoSizeChanged(videoSize)
                if (videoSize.width == 0 || currentContainerSize == Size.Zero) return

                currentOnFillScaleChanged(
                    max(
                        currentContainerSize.width / videoSize.width,
                        currentContainerSize.height / videoSize.height
                    )
                )
                currentOnFilledChanged(false)
                currentOnZoomAccumulatorChanged(1f)
            }

            override fun onRenderedFirstFrame() {
                currentOnFirstFrameRendered()
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> {
                        currentOnLoadingChanged(true)
                        currentPlayerStateListener?.onBuffering(true)
                        currentPlayerStateListener.emitAnalytics(
                            enabled = currentAnalyticsEnabled,
                            type = PlayerAnalyticsEventType.BUFFERING_STARTED,
                            contentId = currentContentId,
                            positionMs = player.currentPosition,
                            durationMs = player.duration
                        )
                    }

                    Player.STATE_READY -> {
                        currentOnLoadingChanged(false)

                        val contentDuration = player.duration
                        if (contentDuration != C.TIME_UNSET && contentDuration > 0) {
                            currentOnContentDurationChanged(contentDuration)
                        }

                        val duration =
                            if (player.duration != C.TIME_UNSET && player.duration > 0) {
                                player.duration
                            } else {
                                0L
                            }

                        currentPlayerStateListener?.onBuffering(false)
                        currentPlayerStateListener.emitAnalytics(
                            enabled = currentAnalyticsEnabled,
                            type = PlayerAnalyticsEventType.BUFFERING_ENDED,
                            contentId = currentContentId,
                            positionMs = player.currentPosition,
                            durationMs = duration
                        )

                        currentPlayerStateListener?.onPlayerReady(duration)
                        currentPlayerStateListener.emitAnalytics(
                            enabled = currentAnalyticsEnabled,
                            type = PlayerAnalyticsEventType.PLAYER_READY,
                            contentId = currentContentId,
                            positionMs = player.currentPosition,
                            durationMs = duration
                        )

                        player.trackSelectionParameters =
                            player.trackSelectionParameters
                                .buildUpon()
                                .setForceHighestSupportedBitrate(false)
                                .build()
                    }

                    Player.STATE_ENDED -> {
                        currentOnLoadingChanged(false)
                        restartArmed = true
                        if (currentSelectedIndex + 1 < currentContentListSize) {
                            currentOnPlayNextContent()
                        }
                        currentPlayerStateListener?.onPlaybackCompleted()
                        currentPlayerStateListener.emitAnalytics(
                            enabled = currentAnalyticsEnabled,
                            type = PlayerAnalyticsEventType.PLAYBACK_COMPLETED,
                            contentId = currentContentId,
                            positionMs = player.currentPosition,
                            durationMs = player.duration
                        )
                    }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                if (currentDiagnosticsEnabled) {
                    tracks.groups.forEach { group ->
                        for (i in 0 until group.mediaTrackGroup.length) {
                            val format = group.mediaTrackGroup.getFormat(i)
                            currentPlayerStateListener.emitDiagnostic(
                                enabled = currentDiagnosticsEnabled,
                                severity = PlayerDiagnosticSeverity.INFO,
                                code = "track_available",
                                message = "Track available",
                                contentId = currentContentId,
                                attributes = mapOf(
                                    "type" to group.type.toString(),
                                    "mime" to format.sampleMimeType.orEmpty(),
                                    "codecs" to format.codecs.orEmpty()
                                )
                            )
                        }
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                currentPlayerStateListener?.onPlayStateChanged(isPlaying)
                currentPlayerStateListener.emitAnalytics(
                    enabled = currentAnalyticsEnabled,
                    type = if (isPlaying) {
                        PlayerAnalyticsEventType.PLAY
                    } else {
                        PlayerAnalyticsEventType.PAUSE
                    },
                    contentId = currentContentId,
                    positionMs = player.currentPosition,
                    durationMs = player.duration
                )
                if (isPlaying) presentAgeRatingIfNeeded()
            }

            override fun onMediaItemTransition(
                mediaItem: androidx.media3.common.MediaItem?,
                reason: Int
            ) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                    restartArmed = true
                    presentAgeRatingIfNeeded()
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (
                    reason == Player.DISCONTINUITY_REASON_SEEK &&
                    oldPosition.positionMs > 1_000L &&
                    newPosition.positionMs <= 1_000L
                ) {
                    restartArmed = true
                    presentAgeRatingIfNeeded()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                SdkLogger.error(
                    "Player error: code=${error.errorCodeName}, message=${error.message}, cause=${error.cause?.message}",
                    error
                )
                currentPlayerStateListener?.onPlayerError(error)
                currentPlayerStateListener.emitAnalytics(
                    enabled = currentAnalyticsEnabled,
                    type = PlayerAnalyticsEventType.ERROR,
                    contentId = currentContentId,
                    positionMs = player.currentPosition,
                    durationMs = player.duration,
                    attributes = mapOf(
                        "errorCode" to error.errorCodeName,
                        "message" to error.message.orEmpty()
                    )
                )
                currentPlayerStateListener.emitDiagnostic(
                    enabled = currentDiagnosticsEnabled,
                    severity = PlayerDiagnosticSeverity.ERROR,
                    code = error.errorCodeName,
                    message = error.message.orEmpty(),
                    contentId = currentContentId,
                    attributes = mapOf(
                        "cause" to error.cause?.javaClass?.simpleName.orEmpty()
                    )
                )
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (!timeline.isEmpty) {
                    val duration = player.duration
                    if (duration > 0) {
                        currentOnContentDurationChanged(duration)
                    }
                }

                if (timeline.isEmpty) return
                val period = Timeline.Period()
                timeline.getPeriod(0, period)
                imaCuePoints.clear()
                for (adGroupIndex in 0 until period.adGroupCount) {
                    val timeUs = period.getAdGroupTimeUs(adGroupIndex)
                    val positionMs =
                        if (timeUs == C.TIME_END_OF_SOURCE) player.duration else timeUs / 1000
                    imaCuePoints.add(
                        CuePoint(
                            id = "ima_$adGroupIndex",
                            positionMs = positionMs,
                            type = CueType.AD
                        )
                    )
                }
            }
        }

        player.addListener(listener)
        presentAgeRatingIfNeeded()

        onDispose {
            player.removeListener(listener)
            adsLoader?.setPlayer(null)
            player.release()
        }
    }
}
