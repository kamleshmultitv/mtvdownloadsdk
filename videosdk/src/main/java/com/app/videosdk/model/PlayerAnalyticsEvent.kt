package com.app.videosdk.model

data class PlayerAnalyticsEvent(
    val type: PlayerAnalyticsEventType,
    val contentId: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val attributes: Map<String, String> = emptyMap()
)

enum class PlayerAnalyticsEventType {
    PLAYER_READY,
    BUFFERING_STARTED,
    BUFFERING_ENDED,
    PLAY,
    PAUSE,
    PLAYBACK_COMPLETED,
    SEEK_STARTED,
    SEEK_COMPLETED,
    QUALITY_CHANGED,
    SUBTITLE_CHANGED,
    PLAYBACK_SPEED_CHANGED,
    FULLSCREEN_CHANGED,
    PIP_CHANGED,
    CAST_STATE_CHANGED,
    CAST_ERROR,
    AD_LOADED,
    AD_STARTED,
    AD_COMPLETED,
    AD_ERROR,
    MUTE_CHANGED,
    ERROR
}

data class PlayerDiagnosticEvent(
    val severity: PlayerDiagnosticSeverity,
    val code: String,
    val message: String,
    val contentId: String? = null,
    val attributes: Map<String, String> = emptyMap()
)

enum class PlayerDiagnosticSeverity {
    INFO,
    WARNING,
    ERROR
}
