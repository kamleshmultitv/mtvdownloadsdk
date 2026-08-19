package com.app.videosdk.ui

import androidx.media3.common.Player

internal data class PlaybackProgressState(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val playbackState: Int = Player.STATE_IDLE
)
