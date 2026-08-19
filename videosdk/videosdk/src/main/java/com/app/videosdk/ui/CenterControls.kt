package com.app.videosdk.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import com.app.videosdk.utils.CastUtils

@Composable
fun CenterControls(
    isLoading: Boolean,
    exoPlayer: ExoPlayer,
    castUtils: CastUtils,
    isCasting: Boolean,
    onShowControls: (Boolean) -> Unit,
    showForwardIcon: Boolean,
    showRewindIcon: Boolean,
    onForward: () -> Unit,
    onRewind: () -> Unit,
    onForwardHide: () -> Unit,
    onRewindHide: () -> Unit,
    isZoomed: Boolean,
    onZoomChange: (Boolean) -> Unit
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
                    val current =
                        if (isCasting) castUtils.getCastPosition()
                        else exoPlayer.currentPosition

                    val newPosition =
                        maxOf(current + if (isLeft) -10_000 else 10_000, 0)

                    if (isCasting) castUtils.seekOnCast(newPosition)
                    else exoPlayer.seekTo(newPosition)

                    if (isLeft) onRewind() else onForward()
                }
            )
        }

    Box(
        modifier = Modifier.fillMaxSize(),
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
                exoPlayer = exoPlayer,
                context = LocalContext.current,
                showRewindIcon = showRewindIcon,
                showForwardIcon = showForwardIcon,
                onRewindIconHide = onRewindHide,
                onForwardIconHide = onForwardHide,
                isControllerVisible = true
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