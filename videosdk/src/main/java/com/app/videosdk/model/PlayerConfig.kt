package com.app.videosdk.model

data class PlayerConfig(
    val controls: PlayerControlsConfig = PlayerControlsConfig(),
    val ads: PlayerAdsConfig = PlayerAdsConfig(),
    val freePreview: FreePreviewConfig? = null,
    val freePreviewEnd: FreePreviewEndConfig? = null,
    val watermark: WatermarkConfig? = null,
    val liveImageUrl: String? = null,
    val subtitleEnabled: Boolean = false,
    val autoPlayFeature: Boolean = true,
    val autoPlayDetail: Boolean = true,
    val autoPlayAssets: Boolean = true,
    val featureTier: PlayerFeatureTier = PlayerFeatureTier.LEGACY_COMPAT,
    val monetizationPackage: PlayerMonetizationPackage = PlayerMonetizationPackage.NONE,
    val featureGates: PlayerFeatureGates = PlayerFeatureGates(),
    val analyticsEnabled: Boolean = false,
    val diagnosticsEnabled: Boolean = false,
    val logging: SdkLoggingConfig = SdkLoggingConfig()
)

data class PlayerControlsConfig(
    val play: Boolean = true,
    val pause: Boolean = true,
    val seekBack: ControlSeekConfig = ControlSeekConfig(enabled = true, seconds = 10),
    val seekForward: ControlSeekConfig = ControlSeekConfig(enabled = true, seconds = 10),
    val previous: Boolean = true,
    val next: Boolean = true,
    val mute: Boolean = true,
    val unmute: Boolean = true,
    val seasonSelector: Boolean = true,
    val settings: Boolean = true,
    val cast: Boolean = true,
    val pip: Boolean = true,
    val fullscreen: Boolean = true,
    val exitFullscreen: Boolean = true
)

enum class PlayerFeatureTier {
    LEGACY_COMPAT,
    BASIC_PLAYER,
    PREMIUM_UX,
    ENTERPRISE_PLAYBACK
}

enum class PlayerMonetizationPackage {
    NONE,
    AD_SUPPORTED
}

data class PlayerFeatureGates(
    val subtitles: Boolean? = null,
    val fullscreen: Boolean? = null,
    val playbackSpeed: Boolean? = null,
    val qualitySelection: Boolean? = null,
    val cast: Boolean? = null,
    val pip: Boolean? = null,
    val skipIntro: Boolean? = null,
    val nextEpisode: Boolean? = null,
    val episodeSelector: Boolean? = null,
    val chapters: Boolean? = null,
    val spriteThumbnails: Boolean? = null,
    val customControls: Boolean? = null,
    val drm: Boolean? = null,
    val offline: Boolean? = null,
    val deepLinkClips: Boolean? = null,
    val watermark: Boolean? = null,
    val ageRating: Boolean? = null,
    val freePreview: Boolean? = null,
    val imaAds: Boolean? = null,
    val gamBannerAds: Boolean? = null,
    val lShapeAds: Boolean? = null
)

data class SdkLoggingConfig(
    val level: SdkLogLevel = SdkLogLevel.OFF,
    val tag: String = "MtvVideoPlayerSdk"
)

enum class SdkLogLevel {
    OFF,
    ERROR,
    INFO,
    DEBUG
}

data class ControlSeekConfig(
    val enabled: Boolean,
    val seconds: Int
) {
    val safeSeconds: Int
        get() = seconds.coerceAtLeast(0)
}

data class PlayerAdsConfig(
    val googleAdsEnabled: Boolean = false,
    val vmapAdsEnabled: Boolean = false,
    val bannerAdsEnabled: Boolean = false,
    val startTimeSec: Int = 0,
    val endTimeSec: Int = 0,
    val gapDurationMs: Long = 600_000L,
    val closeButtonEnabled: Boolean = true
)

data class FreePreviewConfig(
    val enabled: Boolean,
    val durationMs: Long,
    val popupAllowed: Boolean,
    val popupText: String?,
    val buttonLabel: String?
)

data class FreePreviewEndConfig(
    val enabled: Boolean,
    val durationMs: Long,
    val popupText: String?,
    val primaryButtonLabel: String?,
    val secondaryButtonLabel: String?
)

data class WatermarkConfig(
    val enabled: Boolean,
    val imageUrl: String?,
    val type: String?,
    val position: WatermarkPosition,
    val textColor: String?,
    val fontSize: String?
)

enum class WatermarkPosition {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    CENTER
}
