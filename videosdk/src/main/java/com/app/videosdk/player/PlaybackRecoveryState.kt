package com.app.videosdk.player

internal data class PlaybackRecoveryState(
    val reason: PlaybackRecoveryReason = PlaybackRecoveryReason.NONE,
    val action: PlaybackRecoveryAction = PlaybackRecoveryAction.NONE,
    val message: String? = null,
    val attempt: Int = 0
)

internal enum class PlaybackRecoveryReason {
    NONE,
    AUDIO_TRACK_UNSUPPORTED,
    OFFLINE_DRM_LICENSE_MISSING,
    OFFLINE_DRM_LICENSE_EXPIRED,
    DRM_PROVISIONING_FAILED,
    SOURCE_UNAVAILABLE,
    UNKNOWN
}

internal enum class PlaybackRecoveryAction {
    NONE,
    DISABLE_AUDIO_TRACK,
    FALLBACK_TO_ONLINE
}
