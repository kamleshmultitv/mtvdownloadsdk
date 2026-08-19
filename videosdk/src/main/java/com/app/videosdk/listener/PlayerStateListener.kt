package com.app.videosdk.listener

import androidx.media3.common.PlaybackException
import com.app.videosdk.model.PlayerAnalyticsEvent
import com.app.videosdk.model.PlayerDiagnosticEvent

interface PlayerStateListener {

    fun onPlayerReady(durationMs: Long) {}

    fun onBuffering(isBuffering: Boolean) {}

    fun onPlayStateChanged(isPlaying: Boolean) {}

    fun onPlaybackCompleted() {}

    fun onPlayerError(error: PlaybackException) {}

    fun onVideoChanged(index: Int) {}

    fun onFullScreenChanged(isFullScreen: Boolean) {}

    fun onPipModeChanged(isInPip: Boolean) {}

    fun onAdStateChanged(isAdPlaying: Boolean) {}
    fun onMuteStateChanged(isMuted: Boolean) {}

    fun onSeekStarted(positionMs: Long) {}

    fun onSeekCompleted(positionMs: Long) {}

    fun onQualityChanged(width: Int, height: Int, label: String?) {}

    fun onSubtitleChanged(language: String?, label: String?, enabled: Boolean) {}

    fun onPlaybackSpeedChanged(speed: Float) {}

    fun onAnalyticsEvent(event: PlayerAnalyticsEvent) {}

    fun onDiagnosticEvent(event: PlayerDiagnosticEvent) {}

    fun onCastPlaybackStateChanged(
        playerState: Int,
        idleReason: Int,
        contentType: String?,
        contentId: String?,
        contentUrl: String?
    ) {}

    fun onCastPlaybackFailed(
        message: String,
        playerState: Int,
        idleReason: Int,
        contentType: String?,
        contentId: String?,
        contentUrl: String?
    ) {}
}
