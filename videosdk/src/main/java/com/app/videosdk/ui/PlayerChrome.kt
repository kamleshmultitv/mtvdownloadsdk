package com.app.videosdk.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import com.app.videosdk.listener.PipListener
import com.app.videosdk.listener.PlayerStateListener
import com.app.videosdk.model.CuePoint
import com.app.videosdk.model.EpisodeNowPlayingStyle
import com.app.videosdk.model.PlayerConfig
import com.app.videosdk.model.PlayerModel
import com.app.videosdk.model.ResolvedPlayerFeatureSet
import com.app.videosdk.ui.cut.CutBottomSheet
import com.app.videosdk.utils.CastUtils
import com.app.videosdk.utils.PlayerMode

@Composable
internal fun BoxScope.PlayerChrome(
    showControls: Boolean,
    isControllerVisible: Boolean,
    pipEnabled: Boolean,
    isLockScreen: Boolean,
    isLockOverlayVisible: Boolean,
    isInPipMode: Boolean,
    isLoading: Boolean,
    exoPlayer: ExoPlayer?,
    activeContentList: List<PlayerModel>?,
    activeIndex: Int,
    contentDuration: Long,
    playbackProgress: PlaybackProgressState,
    contentId: String?,
    analyticsEnabled: Boolean,
    diagnosticsEnabled: Boolean,
    playerStateListener: PlayerStateListener?,
    pipListener: PipListener?,
    isFullScreen: Boolean,
    playerModel: PlayerModel?,
    castUtils: CastUtils?,
    castEnabled: Boolean,
    episodeNowPlayingStyle: EpisodeNowPlayingStyle,
    cuePoints: List<CuePoint>,
    isSkipIntroClicked: Boolean,
    ageRating: String?,
    ageRatingPresentationKey: Long,
    isAgeRatingPresentationActive: Boolean,
    showUnlockConfirm: Boolean,
    isSettingsClick: Boolean,
    settingsSelectedItems: MutableMap<Int, Int>,
    playbackUrl: String?,
    playerConfig: PlayerConfig,
    featureSet: ResolvedPlayerFeatureSet,
    hasExplicitControlsConfig: Boolean,
    currentMode: PlayerMode,
    onFullScreenChanged: (Boolean) -> Unit,
    onCurrentModeChanged: (PlayerMode) -> Unit,
    onLockScreenChanged: (Boolean) -> Unit,
    onShowUnlockConfirmChanged: (Boolean) -> Unit,
    onPipEnabledChanged: (Boolean) -> Unit,
    onSettingsClickChanged: (Boolean) -> Unit,
    onBackPressed: () -> Unit,
    onShowControlsChanged: (Boolean) -> Unit,
    onPlayContent: (Int) -> Unit,
    onSkipIntroClicked: (Boolean) -> Unit,
    onNextEpisodeClick: (Int) -> Unit,
    onAgeRatingPresentationActiveChanged: (Boolean) -> Unit,
    onChapterClick: () -> Unit
) {
    var showCutSheet by remember { mutableStateOf(false) }

    if (!isControllerVisible && isLoading) {
        CircularProgressIndicator(
            modifier = Modifier.align(Alignment.Center),
            color = Color.White
        )
    }

    AnimatedVisibility(
        visible = showControls && isControllerVisible && !pipEnabled && !isLockScreen,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        exoPlayer?.let { player ->
            CustomPlayerController(
                playerModelList = activeContentList,
                index = activeIndex,
                totalDuration = contentDuration,
                playbackPositionMs = playbackProgress.positionMs,
                playbackIsPlaying = playbackProgress.isPlaying,
                contentId = contentId,
                analyticsEnabled = analyticsEnabled,
                playerStateListener = playerStateListener,
                pipListener = pipListener,
                isFullScreen = { full ->
                    val newMode = if (full) PlayerMode.FULL_SCREEN else PlayerMode.MINI
                    onCurrentModeChanged(newMode)
                    onFullScreenChanged(full)
                },
                isLockScreen = onLockScreenChanged,
                isCurrentlyFullScreen = isFullScreen,
                isCurrentlyLockScreen = isLockScreen,
                exoPlayer = player,
                externalCastUtils = castUtils,
                castEnabled = castEnabled,
                episodeNowPlayingStyle = episodeNowPlayingStyle,
                modifier = Modifier.fillMaxSize(),
                isControlsVisible = isControllerVisible,
                onShowControls = onShowControlsChanged,
                isPipEnabled = { enabled ->
                    onPipEnabledChanged(enabled)
                    if (enabled) {
                        onShowControlsChanged(false)
                        onSettingsClickChanged(false)
                    }
                },
                onSettingsButtonClick = onSettingsClickChanged,
                isLoading = isLoading,
                onBackPressed = {
                    if (currentMode == PlayerMode.FULL_SCREEN) {
                        onCurrentModeChanged(PlayerMode.MINI)
                        onFullScreenChanged(false)
                    } else {
                        onBackPressed()
                    }
                },
                cuePoints = cuePoints,
                playContent = onPlayContent,
                isSkipIntroClicked = isSkipIntroClicked,
                onSkipIntroClicked = onSkipIntroClicked,
                onNextEpisodeClick = onNextEpisodeClick,
                showContentTitle = !isAgeRatingPresentationActive,
                onChapterClick = {
                    if (playerModel?.isChapterEnabled == true) {
                        onChapterClick()
                    }
                },
                onCutClick = {
                    showCutSheet = true
                },
                controlsConfig = playerConfig.controls,
                showPreviousControl = hasExplicitControlsConfig &&
                    playerConfig.controls.previous
            )
        }
    }

    ageRating?.let { rating ->
        PlayerAgeRatingOverlay(
            ageRating = rating,
            presentationKey = ageRatingPresentationKey,
            isInPictureInPicture = pipEnabled || isInPipMode,
            modifier = Modifier.align(Alignment.TopStart),
            titleSlotTopPadding = if (isFullScreen) 19.dp else 23.dp,
            applyStatusBarPadding = isFullScreen,
            onPresentationActiveChanged = onAgeRatingPresentationActiveChanged
        )
    }

    if (isLockScreen) {
        AnimatedVisibility(
            visible = isLockOverlayVisible && !pipEnabled,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            exoPlayer?.let {
                LockScreenOverlay(
                    playerModel = playerModel,
                    isLocked = isLockScreen,
                    showUnlockConfirm = showUnlockConfirm,
                    onUnlockRequest = { onShowUnlockConfirmChanged(true) },
                    onConfirmUnlock = {
                        onLockScreenChanged(false)
                        onShowUnlockConfirmChanged(false)
                    }
                )
            }
        }
    }

    if (isSettingsClick) {
        SelectorHeader(
            playerModel = playerModel,
            exoPlayer = exoPlayer,
            subtitlesEnabled = featureSet.subtitles,
            playbackSpeedEnabled = featureSet.playbackSpeed,
            qualitySelectionEnabled = featureSet.qualitySelection,
            contentId = contentId,
            analyticsEnabled = analyticsEnabled,
            diagnosticsEnabled = diagnosticsEnabled,
            playerStateListener = playerStateListener,
            selectedItems = settingsSelectedItems,
            closeOptionCard = { onSettingsClickChanged(it) }
        )
    }

    if (showCutSheet && exoPlayer != null) {
        val duration = exoPlayer.duration

        if (duration > 0) {
            CutBottomSheet(
                contentId = playerModel?.id,
                url = playbackUrl,
                duration = duration,
                onDismiss = { showCutSheet = false }
            )
        }
    }
}
