package com.app.videosdk.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

private const val AGE_RATING_VISIBLE_MILLIS = 5_000L
private const val AGE_RATING_DELAY_MILLIS = 2_500L
private const val AGE_RATING_REVEAL_MILLIS = 450
private const val AGE_RATING_COLLAPSE_MILLIS = 350
private const val AGE_RATING_SURFACE_ALPHA = 0x80 / 255f
private const val AGE_RATING_RED_BAR_WIDTH_DP = 2
private val AgeRatingBadgeShape = RoundedCornerShape(6.dp)

/**
 * OTT-style, non-interactive age-classification badge for player surfaces.
 *
 * [presentationKey] must change when a video begins or restarts. The component owns
 * only presentation and timing; it has no dependency on Media3 or playback logic.
 * The supplied [ageRating] is displayed verbatim and blank values render nothing.
 * After [initialDelayMillis], the badge reveals left-to-right and later collapses
 * from its right edge back toward the left.
 */
@Composable
fun PlayerAgeRatingOverlay(
    ageRating: String?,
    presentationKey: Long,
    isInPictureInPicture: Boolean,
    modifier: Modifier = Modifier,
    initialDelayMillis: Long = AGE_RATING_DELAY_MILLIS,
    visibleDurationMillis: Long = AGE_RATING_VISIBLE_MILLIS,
    titleSlotTopPadding: Dp = 27.dp,
    applyStatusBarPadding: Boolean = false,
    onPresentationActiveChanged: (Boolean) -> Unit = {}
) {
    if (ageRating.isNullOrBlank()) return

    val lifecycleOwner = LocalLifecycleOwner.current
    val presentationCallback by rememberUpdatedState(onPresentationActiveChanged)
    var lifecycleStarted by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
    }
    var timerVisible by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                lifecycleStarted = true
            }

            override fun onStop(owner: LifecycleOwner) {
                lifecycleStarted = false
                timerVisible = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(ageRating, presentationKey, isInPictureInPicture, lifecycleStarted) {
        timerVisible = false
        presentationCallback(false)
        if (presentationKey <= 0L || ageRating.isNullOrBlank() ||
            isInPictureInPicture || !lifecycleStarted
        ) return@LaunchedEffect

        try {
            delay(initialDelayMillis.coerceAtLeast(0L))
            if (isInPictureInPicture || !lifecycleStarted) return@LaunchedEffect

            presentationCallback(true)
            timerVisible = true
            delay(visibleDurationMillis.coerceAtLeast(0L))
            timerVisible = false
            delay(AGE_RATING_COLLAPSE_MILLIS.toLong())
        } finally {
            timerVisible = false
            presentationCallback(false)
        }
    }

    Box(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(
                androidx.compose.foundation.layout.WindowInsetsSides.Horizontal
            ))
            .then(if (applyStatusBarPadding) Modifier.statusBarsPadding() else Modifier)
            .padding(
                start = 54.dp,
                top = titleSlotTopPadding
            ),
        contentAlignment = Alignment.TopStart
    ) {
        AnimatedVisibility(
            visible = timerVisible && !isInPictureInPicture && !ageRating.isNullOrBlank(),
            enter = expandHorizontally(
                expandFrom = Alignment.Start,
                animationSpec = tween(AGE_RATING_REVEAL_MILLIS),
                clip = true
            ) + fadeIn(tween(AGE_RATING_REVEAL_MILLIS)),
            exit = shrinkHorizontally(
                shrinkTowards = Alignment.Start,
                animationSpec = tween(AGE_RATING_COLLAPSE_MILLIS),
                clip = true
            ) + fadeOut(tween(AGE_RATING_COLLAPSE_MILLIS))
        ) {
            // No clickable/pointer modifier: hit testing falls through to the player.
            Text(
                text = ageRating.orEmpty(),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(AgeRatingBadgeShape)
                    .background(
                        color = Color.Black.copy(alpha = AGE_RATING_SURFACE_ALPHA)
                    )
                    .border(
                        width = 0.5.dp,
                        color = Color.White.copy(alpha = AGE_RATING_SURFACE_ALPHA),
                        shape = AgeRatingBadgeShape
                    )
                    .drawBehind {
                        drawRect(
                            color = Color.Red,
                            topLeft = Offset.Zero,
                            size = Size(AGE_RATING_RED_BAR_WIDTH_DP.dp.toPx(), size.height)
                        )
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}
