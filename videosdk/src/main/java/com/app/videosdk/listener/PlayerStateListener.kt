package com.app.videosdk.listener

import androidx.media3.common.PlaybackException

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
}
