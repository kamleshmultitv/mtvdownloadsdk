package com.app.videosdk.ui.ads

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.app.videosdk.model.PlayerModel
import com.google.android.gms.ads.AdSize

@Composable
fun LShapeAdContainer(
    playerModel: PlayerModel? = null,
    isFullScreen: Boolean = false,
    isVisible: Boolean,
    bannerAdsEnabled: Boolean? = null,
    closeButtonEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    onCloseClick: () -> Unit = {},
    videoContent: @Composable () -> Unit
) {

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {

        val showAds =
            isVisible &&
                    isFullScreen &&
                    (bannerAdsEnabled ?: (playerModel?.gamAdsConfig?.isAdsEnabled == true))

        val bottomHeight = 75.dp

        // Vertical width = 30% of total width
        val verticalTargetWidth = maxWidth * 0.35f

        val animatedVerticalWidth by animateDpAsState(
            targetValue = if (showAds) verticalTargetWidth else 0.dp,
            animationSpec = spring(
                dampingRatio = 0.9f,
                stiffness = 250f
            ),
            label = "verticalWidth"
        )

        val animatedAlpha by animateFloatAsState(
            targetValue = if (showAds) 1f else 0f,
            animationSpec = tween(350),
            label = "alpha"
        )

        Row(Modifier.fillMaxSize()) {

            // ===============================
            // ✅ LEFT VERTICAL (FULL HEIGHT)
            // ===============================
            Box(
                modifier = Modifier
                    .width(animatedVerticalWidth)
                    .fillMaxHeight()
                    .background(Color.Black.copy(alpha = animatedAlpha)),
                contentAlignment = Alignment.Center
            ) {
                if (showAds) {
                    LBandBanner(
                        adUnitId = playerModel?.gamAdsConfig?.verticalBan.orEmpty(),
                        adSize = AdSize.MEDIUM_RECTANGLE
                    )
                }
            }

            // ===============================
            // RIGHT SIDE (PLAYER + BOTTOM)
            // ===============================
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {

                // ===============================
                // 🎬 PLAYER AREA
                // ===============================
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = if (showAds) Alignment.TopEnd else Alignment.Center
                ) {

                    if (showAds) {

                        BoxWithConstraints {

                            val maxW = maxWidth
                            val maxH = maxHeight

                            val videoHeightBasedOnWidth = maxW / (16f / 9f)

                            val finalHeight =
                                if (videoHeightBasedOnWidth <= maxH)
                                    videoHeightBasedOnWidth
                                else
                                    maxH

                            val finalWidth = finalHeight * (16f / 9f)

                            Box(
                                modifier = Modifier
                                    .width(finalWidth)
                                    .height(finalHeight)
                            ) {
                                videoContent()
                            }
                        }

                    } else {
                        Box(Modifier.fillMaxSize()) {
                            videoContent()
                        }
                    }
                }

                // ===============================
                // ✅ HORIZONTAL (RIGHT SIDE ONLY)
                // ===============================
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (showAds) bottomHeight else 0.dp)
                        .background(Color.Black.copy(alpha = animatedAlpha)),
                    contentAlignment = Alignment.Center
                ) {
                    if (showAds) {
                        LBandBanner(
                            adUnitId = playerModel?.gamAdsConfig?.horizontalBan.orEmpty(),
                            adSize = AdSize.LEADERBOARD
                        )
                    }
                }
            }
        }

        if (showAds && closeButtonEnabled) {
            IconButton(
                onClick = onCloseClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Ad",
                    tint = Color.White
                )
            }
        }
    }
}
