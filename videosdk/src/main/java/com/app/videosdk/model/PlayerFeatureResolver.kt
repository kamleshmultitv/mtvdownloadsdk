package com.app.videosdk.model

internal data class ResolvedPlayerFeatureSet(
    val subtitles: Boolean,
    val fullscreen: Boolean,
    val playbackSpeed: Boolean,
    val qualitySelection: Boolean,
    val cast: Boolean,
    val pip: Boolean,
    val skipIntro: Boolean,
    val nextEpisode: Boolean,
    val episodeSelector: Boolean,
    val chapters: Boolean,
    val spriteThumbnails: Boolean,
    val customControls: Boolean,
    val drm: Boolean,
    val offline: Boolean,
    val deepLinkClips: Boolean,
    val watermark: Boolean,
    val ageRating: Boolean,
    val freePreview: Boolean,
    val imaAds: Boolean,
    val gamBannerAds: Boolean,
    val lShapeAds: Boolean
)

internal fun PlayerConfig.resolveFeatureSet(): ResolvedPlayerFeatureSet {
    val base = when (featureTier) {
        PlayerFeatureTier.LEGACY_COMPAT ->
            ResolvedPlayerFeatureSet(
                subtitles = true,
                fullscreen = true,
                playbackSpeed = true,
                qualitySelection = true,
                cast = true,
                pip = true,
                skipIntro = true,
                nextEpisode = true,
                episodeSelector = true,
                chapters = true,
                spriteThumbnails = true,
                customControls = true,
                drm = true,
                offline = true,
                deepLinkClips = true,
                watermark = true,
                ageRating = true,
                freePreview = true,
                imaAds = true,
                gamBannerAds = true,
                lShapeAds = true
            )

        PlayerFeatureTier.BASIC_PLAYER ->
            ResolvedPlayerFeatureSet(
                subtitles = true,
                fullscreen = true,
                playbackSpeed = true,
                qualitySelection = true,
                cast = false,
                pip = false,
                skipIntro = false,
                nextEpisode = false,
                episodeSelector = false,
                chapters = false,
                spriteThumbnails = false,
                customControls = false,
                drm = false,
                offline = false,
                deepLinkClips = false,
                watermark = false,
                ageRating = false,
                freePreview = false,
                imaAds = false,
                gamBannerAds = false,
                lShapeAds = false
            )

        PlayerFeatureTier.PREMIUM_UX ->
            ResolvedPlayerFeatureSet(
                subtitles = true,
                fullscreen = true,
                playbackSpeed = true,
                qualitySelection = true,
                cast = true,
                pip = true,
                skipIntro = true,
                nextEpisode = true,
                episodeSelector = true,
                chapters = true,
                spriteThumbnails = true,
                customControls = true,
                drm = false,
                offline = false,
                deepLinkClips = false,
                watermark = false,
                ageRating = false,
                freePreview = false,
                imaAds = false,
                gamBannerAds = false,
                lShapeAds = false
            )

        PlayerFeatureTier.ENTERPRISE_PLAYBACK ->
            ResolvedPlayerFeatureSet(
                subtitles = true,
                fullscreen = true,
                playbackSpeed = true,
                qualitySelection = true,
                cast = true,
                pip = true,
                skipIntro = true,
                nextEpisode = true,
                episodeSelector = true,
                chapters = true,
                spriteThumbnails = true,
                customControls = true,
                drm = true,
                offline = true,
                deepLinkClips = true,
                watermark = true,
                ageRating = true,
                freePreview = true,
                imaAds = false,
                gamBannerAds = false,
                lShapeAds = false
            )
    }

    val monetized =
        if (monetizationPackage == PlayerMonetizationPackage.AD_SUPPORTED) {
            base.copy(
                imaAds = true,
                gamBannerAds = true,
                lShapeAds = true
            )
        } else {
            base
        }

    return monetized.withOverrides(featureGates)
}

internal fun PlayerConfig.withResolvedFeatures(
    features: ResolvedPlayerFeatureSet
): PlayerConfig =
    copy(
        controls = controls.withResolvedFeatures(features),
        ads = ads.copy(
            googleAdsEnabled = ads.googleAdsEnabled &&
                (features.imaAds || features.gamBannerAds || features.lShapeAds),
            vmapAdsEnabled = ads.vmapAdsEnabled && features.imaAds,
            bannerAdsEnabled = ads.bannerAdsEnabled &&
                (features.gamBannerAds || features.lShapeAds)
        ),
        freePreview = freePreview.takeIf { features.freePreview },
        freePreviewEnd = freePreviewEnd.takeIf { features.freePreview },
        watermark = watermark.takeIf { features.watermark },
        subtitleEnabled = subtitleEnabled && features.subtitles
    )

internal fun PlayerControlsConfig.withResolvedFeatures(
    features: ResolvedPlayerFeatureSet
): PlayerControlsConfig =
    copy(
        previous = previous && features.nextEpisode,
        next = next && features.nextEpisode,
        seasonSelector = seasonSelector && features.episodeSelector,
        settings = settings &&
            (features.subtitles || features.playbackSpeed || features.qualitySelection),
        cast = cast && features.cast,
        pip = pip && features.pip,
        fullscreen = fullscreen && features.fullscreen,
        exitFullscreen = exitFullscreen && features.fullscreen
    )

internal fun PlayerModel.withResolvedFeatures(
    features: ResolvedPlayerFeatureSet
): PlayerModel =
    copy(
        mpdUrl = if (drm == "1" && !features.drm) null else mpdUrl,
        drm = drm.takeIf { features.drm },
        drmToken = drmToken.takeIf { features.drm },
        adsConfig = adsConfig.takeIf { features.imaAds },
        gamAdsConfig = gamAdsConfig.takeIf { features.gamBannerAds || features.lShapeAds },
        skipIntro = skipIntro.takeIf { features.skipIntro },
        nextEpisode = nextEpisode.takeIf { features.nextEpisode },
        customControls = customControls.takeIf { features.customControls },
        isClipEnabled = isClipEnabled && features.deepLinkClips,
        isChapterEnabled = isChapterEnabled && features.chapters,
        chapters = chapters.takeIf { features.chapters },
        spriteUrl = spriteUrl.takeIf { features.spriteThumbnails },
        cacheFactory = cacheFactory.takeIf { features.offline },
        downloadManager = downloadManager.takeIf { features.offline },
        downloadCache = downloadCache.takeIf { features.offline },
        deepLinkEndMs = deepLinkEndMs.takeIf { features.deepLinkClips },
        deepLinkClipDuration = deepLinkClipDuration.takeIf { features.deepLinkClips },
        ageRating = ageRating.takeIf { features.ageRating },
        contentRating = contentRating.takeIf { features.ageRating }
    )

private fun ResolvedPlayerFeatureSet.withOverrides(
    overrides: PlayerFeatureGates
): ResolvedPlayerFeatureSet =
    copy(
        subtitles = overrides.subtitles ?: subtitles,
        fullscreen = overrides.fullscreen ?: fullscreen,
        playbackSpeed = overrides.playbackSpeed ?: playbackSpeed,
        qualitySelection = overrides.qualitySelection ?: qualitySelection,
        cast = overrides.cast ?: cast,
        pip = overrides.pip ?: pip,
        skipIntro = overrides.skipIntro ?: skipIntro,
        nextEpisode = overrides.nextEpisode ?: nextEpisode,
        episodeSelector = overrides.episodeSelector ?: episodeSelector,
        chapters = overrides.chapters ?: chapters,
        spriteThumbnails = overrides.spriteThumbnails ?: spriteThumbnails,
        customControls = overrides.customControls ?: customControls,
        drm = overrides.drm ?: drm,
        offline = overrides.offline ?: offline,
        deepLinkClips = overrides.deepLinkClips ?: deepLinkClips,
        watermark = overrides.watermark ?: watermark,
        ageRating = overrides.ageRating ?: ageRating,
        freePreview = overrides.freePreview ?: freePreview,
        imaAds = overrides.imaAds ?: imaAds,
        gamBannerAds = overrides.gamBannerAds ?: gamBannerAds,
        lShapeAds = overrides.lShapeAds ?: lShapeAds
    )
