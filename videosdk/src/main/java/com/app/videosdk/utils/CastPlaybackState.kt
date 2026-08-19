package com.app.videosdk.utils

import com.google.android.gms.cast.MediaStatus

data class CastPlaybackState(
    val isCasting: Boolean = false,
    val playerState: Int = MediaStatus.PLAYER_STATE_UNKNOWN,
    val idleReason: Int = MediaStatus.IDLE_REASON_NONE,
    val contentType: String? = null,
    val contentId: String? = null,
    val contentUrl: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)
