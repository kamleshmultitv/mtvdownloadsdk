package com.app.videosdk.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

@Composable
internal fun PlayerGestureLayer(
    isFullScreen: Boolean,
    pipEnabled: Boolean,
    isAdsShowing: Boolean,
    isLockScreen: Boolean,
    isSettingsClick: Boolean,
    showControls: Boolean,
    fillScale: Float,
    zoomAccumulator: Float,
    hasActivatedFill: Boolean,
    onActivatedFillChanged: (Boolean) -> Unit,
    onFilledChanged: (Boolean) -> Unit,
    onZoomAccumulatorChanged: (Float) -> Unit,
    onLockOverlayVisibleChanged: (Boolean) -> Unit,
    onToggleControllerVisibility: () -> Unit
) {
    val currentFillScale by rememberUpdatedState(fillScale)
    val currentZoomAccumulator by rememberUpdatedState(zoomAccumulator)
    val currentHasActivatedFill by rememberUpdatedState(hasActivatedFill)
    val currentOnActivatedFillChanged by rememberUpdatedState(onActivatedFillChanged)
    val currentOnFilledChanged by rememberUpdatedState(onFilledChanged)
    val currentOnZoomAccumulatorChanged by rememberUpdatedState(onZoomAccumulatorChanged)
    val currentOnLockOverlayVisibleChanged by rememberUpdatedState(onLockOverlayVisibleChanged)
    val currentOnToggleControllerVisibility by rememberUpdatedState(onToggleControllerVisibility)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(isFullScreen, pipEnabled, isAdsShowing, isLockScreen) {
                detectTransformGestures { _, _, zoom, _ ->
                    if (!pipEnabled && !isAdsShowing && !isLockScreen && isFullScreen) {
                        val baseZoom =
                            if (!currentHasActivatedFill) {
                                currentOnFilledChanged(true)
                                currentOnActivatedFillChanged(true)
                                maxOf(1f, currentFillScale, currentZoomAccumulator)
                            } else {
                                currentZoomAccumulator
                            }

                        currentOnZoomAccumulatorChanged((baseZoom * zoom).coerceIn(1f, 3f))
                    }
                }
            }
            .pointerInput(pipEnabled, isSettingsClick, isLockScreen, showControls) {
                detectTapGestures(
                    onDoubleTap = {
                        currentOnActivatedFillChanged(false)
                        currentOnFilledChanged(false)
                        currentOnZoomAccumulatorChanged(1f)
                    },
                    onTap = {
                        when {
                            isLockScreen -> {
                                currentOnLockOverlayVisibleChanged(true)
                            }

                            showControls && !pipEnabled && !isSettingsClick -> {
                                currentOnToggleControllerVisibility()
                            }
                        }
                    }
                )
            }
    )
}
