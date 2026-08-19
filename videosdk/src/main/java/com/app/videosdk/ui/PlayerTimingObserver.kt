package com.app.videosdk.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.media3.exoplayer.ExoPlayer
import com.app.videosdk.model.Chapter
import com.app.videosdk.model.CuePoint
import com.app.videosdk.model.CueType
import com.app.videosdk.model.PlayerAdsConfig
import com.app.videosdk.model.PlayerModel
import com.app.videosdk.player.PlayerTimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun PlayerTimingObserver(
    exoPlayer: ExoPlayer?,
    playerModel: PlayerModel?,
    contentDuration: Long,
    currentPositionMs: Long,
    resetKey: Any?,
    effectiveBannerAdsEnabled: Boolean,
    hasExplicitAdsConfig: Boolean,
    adsConfig: PlayerAdsConfig,
    isAdsShowing: Boolean,
    pipEnabled: Boolean,
    isFullScreen: Boolean,
    isSkipIntroClicked: Boolean,
    onShowControls: () -> Unit,
    onShowLShapeAd: () -> Unit,
    onHideLShapeAd: () -> Unit,
    onCurrentChapterChanged: (Chapter?) -> Unit
) {
    val currentPlayerModel by rememberUpdatedState(playerModel)
    val currentContentDuration by rememberUpdatedState(contentDuration)
    val currentEffectiveBannerAdsEnabled by rememberUpdatedState(effectiveBannerAdsEnabled)
    val currentIsAdsShowing by rememberUpdatedState(isAdsShowing)
    val currentPipEnabled by rememberUpdatedState(pipEnabled)
    val currentIsFullScreen by rememberUpdatedState(isFullScreen)
    val currentIsSkipIntroClicked by rememberUpdatedState(isSkipIntroClicked)
    val currentOnShowControls by rememberUpdatedState(onShowControls)
    val currentOnShowLShapeAd by rememberUpdatedState(onShowLShapeAd)
    val currentOnHideLShapeAd by rememberUpdatedState(onHideLShapeAd)
    val currentOnCurrentChapterChanged by rememberUpdatedState(onCurrentChapterChanged)
    val coroutineScope = rememberCoroutineScope()

    val lBandCuePoints = remember(
        contentDuration,
        effectiveBannerAdsEnabled,
        hasExplicitAdsConfig,
        adsConfig,
        playerModel?.gamAdsConfig?.timeIntervalInMilliseconds
    ) {
        buildLBandCuePoints(
            contentDuration = contentDuration,
            effectiveBannerAdsEnabled = effectiveBannerAdsEnabled,
            hasExplicitAdsConfig = hasExplicitAdsConfig,
            adsConfig = adsConfig,
            model = playerModel
        )
    }

    val chapters = remember(playerModel) {
        if (playerModel?.isChapterEnabled == true) {
            playerModel.chapters?.sortedBy { it.startMs }.orEmpty()
        } else {
            emptyList()
        }
    }

    val triggeredLBands = remember(resetKey) { mutableSetOf<String>() }
    var hasShownNextEpisodeControls by remember(resetKey) { mutableStateOf(false) }
    var hasShownSkipIntroControls by remember(resetKey) { mutableStateOf(false) }

    LaunchedEffect(resetKey, chapters) {
        if (chapters.isEmpty()) {
            currentOnCurrentChapterChanged(null)
        }
    }

    LaunchedEffect(
        exoPlayer,
        resetKey,
        currentPositionMs,
        lBandCuePoints,
        chapters
    ) {
        if (exoPlayer == null) return@LaunchedEffect

        val model = currentPlayerModel

        lBandCuePoints.forEach { cue ->
            if (
                !triggeredLBands.contains(cue.id) &&
                currentPositionMs >= cue.positionMs &&
                currentEffectiveBannerAdsEnabled &&
                !currentIsAdsShowing &&
                !currentPipEnabled &&
                currentIsFullScreen
            ) {
                triggeredLBands.add(cue.id)
                currentOnShowLShapeAd()

                coroutineScope.launch {
                    delay(15_000L)

                    if (!currentIsAdsShowing && !currentPipEnabled) {
                        currentOnHideLShapeAd()
                    }
                }
            }
        }

        if (model != null && !model.isLive) {
            val skipIntro = model.skipIntro
            if (
                skipIntro != null &&
                !currentIsSkipIntroClicked &&
                !hasShownSkipIntroControls &&
                skipIntro.enableSkipIntro
            ) {
                val startTime = skipIntro.startTime ?: 0L

                if (
                    currentPositionMs >= startTime &&
                    currentPositionMs < startTime + 2_000L &&
                    !currentIsAdsShowing
                ) {
                    hasShownSkipIntroControls = true
                    currentOnShowControls()
                }
            }

            val nextEpisode = model.nextEpisode
            if (
                nextEpisode != null &&
                !hasShownNextEpisodeControls &&
                nextEpisode.enableNextEpisode &&
                currentContentDuration > 0
            ) {
                val showBeforeEndMs =
                    PlayerTimeUtils.parseDurationToMillis(nextEpisode.showBeforeEndMs)
                val triggerTime =
                    (currentContentDuration - showBeforeEndMs).coerceAtLeast(0L)

                if (
                    currentPositionMs >= triggerTime &&
                    triggerTime != 0L &&
                    !currentIsAdsShowing
                ) {
                    hasShownNextEpisodeControls = true
                    currentOnShowControls()
                }
            }
        }

        if (chapters.isNotEmpty()) {
            currentOnCurrentChapterChanged(
                chapters.lastOrNull { currentPositionMs >= it.startMs }
            )
        }
    }
}

private fun buildLBandCuePoints(
    contentDuration: Long,
    effectiveBannerAdsEnabled: Boolean,
    hasExplicitAdsConfig: Boolean,
    adsConfig: PlayerAdsConfig,
    model: PlayerModel?
): List<CuePoint> {
    val interval =
        if (hasExplicitAdsConfig) {
            adsConfig.gapDurationMs
        } else {
            model?.gamAdsConfig?.timeIntervalInMilliseconds
        }

    if (!effectiveBannerAdsEnabled || contentDuration <= 0L || interval == null || interval <= 0L) {
        return emptyList()
    }

    val startMs =
        if (hasExplicitAdsConfig && adsConfig.startTimeSec > 0) {
            adsConfig.startTimeSec.toLong() * 1000L
        } else {
            interval
        }
    val configuredEndMs =
        if (hasExplicitAdsConfig && adsConfig.endTimeSec > 0) {
            adsConfig.endTimeSec.toLong() * 1000L
        } else {
            contentDuration
        }
    val endMs = configuredEndMs.coerceAtMost(contentDuration)
    val cueList = mutableListOf<CuePoint>()
    var position = startMs
    var count = 1

    while (position in 1L..endMs) {
        cueList.add(
            CuePoint(
                positionMs = position,
                id = "lband_$count",
                type = CueType.L_BAND
            )
        )
        position += interval
        count++
    }

    return cueList
}
