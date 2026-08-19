package com.app.videosdk.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import com.app.videosdk.model.PlayerControlsConfig
import com.app.videosdk.model.PlayerModel
import com.app.videosdk.utils.CastUtils

@Composable
fun CenterControls(
    playerModel: PlayerModel? = null,
    isLoading: Boolean,
    exoPlayer: ExoPlayer,
    castUtils: CastUtils?,
    isCasting: Boolean,
    isFullScreen: Boolean,
    verticalOffset: Dp = 0.dp,
    onShowControls: (Boolean) -> Unit,
    onForward: () -> Unit,
    onRewind: () -> Unit,
    onForwardHide: () -> Unit,
    onRewindHide: () -> Unit,
    onSeekStarted: (Long) -> Unit = {},
    onSeekCompleted: (Long) -> Unit = {},
    isZoomed: Boolean,
    onZoomChange: (Boolean) -> Unit,
    controlsConfig: PlayerControlsConfig = PlayerControlsConfig()
) {
    val gestureModifier = Modifier
        .pointerInput(Unit) {
            detectTransformGestures { _, _, zoom, _ ->
                if (zoom > 1f && !isZoomed) onZoomChange(true)
                else if (zoom < 1f && isZoomed) onZoomChange(false)
            }
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = { onShowControls(false) },
                onDoubleTap = { offset ->
                    val isLeft = offset.x < size.width / 2
                    val isSeekAllowed =
                        if (isLeft) controlsConfig.seekBack.enabled
                        else controlsConfig.seekForward.enabled

                    if (isSeekAllowed) {
                        val current =
                            if (isCasting && castUtils != null) castUtils.getCastPosition()
                            else exoPlayer.currentPosition

                        val seekSeconds =
                            if (isLeft) controlsConfig.seekBack.safeSeconds
                            else controlsConfig.seekForward.safeSeconds

                        val newPosition =
                            maxOf(current + if (isLeft) -(seekSeconds * 1000L) else seekSeconds * 1000L, 0)

                        onSeekStarted(current)
                        if (isCasting && castUtils != null) castUtils.seekOnCast(newPosition)
                        else exoPlayer.seekTo(newPosition)
                        onSeekCompleted(newPosition)

                        if (isLeft) onRewind() else onForward()
                    }
                }
            )
        }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .offset(y = verticalOffset),
        contentAlignment = Alignment.Center
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .then(gestureModifier)
        ) {
            ForwardBackwardButtonsOverlay(
                playerModel = playerModel,
                exoPlayer = exoPlayer,
                castUtils = castUtils,
                isCasting = isCasting,
                onRewindIconHide = onRewindHide,
                onForwardIconHide = onForwardHide,
                onSeekStarted = onSeekStarted,
                onSeekCompleted = onSeekCompleted,
                isControllerVisible = true,
                isFullScreen = isFullScreen,
                controlsConfig = controlsConfig
            )

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(55.dp),
                    color = Color.White
                )
            }
        }
    }
}
