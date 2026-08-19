package com.app.videosdk.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.ExoPlayer
import com.app.videosdk.model.Chapter
import com.app.videosdk.model.CuePoint
import com.app.videosdk.model.CueType
import com.app.videosdk.model.PlayerModel
import com.app.videosdk.utils.PlayerUtils.formatTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomSlider(
    playerModel: PlayerModel? = null,
    modifier: Modifier = Modifier,
    currentPosition: Long,
    duration: Long,
    cuePoints: List<CuePoint> = emptyList(),
    onSeek: (Long) -> Unit,
    showControls: (Boolean) -> Unit,
    isLive: Boolean = false,
    exoPlayer: ExoPlayer? = null,
    onDragStateChange: (Boolean) -> Unit = {},
    onPreviewChange: (Long) -> Unit = {},
    onSeekStarted: (Long) -> Unit = {},
    onSeekCompleted: (Long) -> Unit = {},
    chapters: List<Chapter> = emptyList()
) {
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }

    /* ---------- ANIMATIONS ---------- */

    val animatedTrackHeight by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isSeeking) 14.dp else 4.dp,
        animationSpec = androidx.compose.animation.core.spring(),
        label = "trackHeight"
    )

    val animatedContainerHeight by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isSeeking) 24.dp else 8.dp,
        animationSpec = androidx.compose.animation.core.spring(),
        label = "containerHeight"
    )

    val animatedScrubContainerHeight by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isSeeking && !isLive && duration > 0) 56.dp else animatedContainerHeight,
        animationSpec = androidx.compose.animation.core.spring(),
        label = "scrubContainerHeight"
    )
    val durationLabelVerticalOffset =
        (animatedScrubContainerHeight - animatedContainerHeight) / 2
    val progressColor = customControlTintColor(
        tintRes = playerModel?.customControls?.iconTintRes,
        fallback = Color.Red
    )

    /* ---------- LIVE EDGE ---------- */

    val isAtLiveEdge = remember(currentPosition, duration, isLive) {
        isLive && (duration - currentPosition) < 10_000L
    }

    /* ---------- SYNC POSITION ---------- */

    LaunchedEffect(currentPosition, duration) {
        if (!isSeeking && duration > 0) {
            sliderPosition = currentPosition.toFloat() / duration
        }
    }

    /* ---------- LIVE BLINK ---------- */

    val infiniteTransition = rememberInfiniteTransition(label = "liveIndicator")
    val blinkAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blinkAlpha"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        /* ---------- CURRENT TIME ---------- */

        if (!isLive) {
            Text(
                text = formatTime(currentPosition),
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .offset(y = durationLabelVerticalOffset)
            )
        }

        /* ---------- SEEK BAR ---------- */

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(animatedScrubContainerHeight),
            contentAlignment = Alignment.BottomCenter
        ) {
            if (isSeeking && !isLive && duration > 0) {
                val previewBubbleWidth = 64.dp
                val maxPreviewBubbleOffset = (maxWidth - previewBubbleWidth).coerceAtLeast(0.dp)
                val previewBubbleOffset =
                    ((maxWidth * sliderPosition) - (previewBubbleWidth / 2))
                        .coerceIn(0.dp, maxPreviewBubbleOffset)
                val previewTimeMs =
                    ((playerModel?.seekTo ?: 0L) + (sliderPosition * duration).toLong())
                        .coerceAtLeast(0L)

                Text(
                    text = formatTime(previewTimeMs),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = previewBubbleOffset)
                        .width(previewBubbleWidth)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }

            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Slider(
                    value = sliderPosition,
                    valueRange = 0f..1f,

                    onValueChange = { value ->
                        sliderPosition = value

                        if (!isSeeking) {
                            isSeeking = true
                            onDragStateChange(true)
                            onSeekStarted(currentPosition)
                        }

                        showControls(true) // ✅ ALWAYS keep controls visible

                        if (duration > 0) {
                            onPreviewChange((value * duration).toLong())
                        }
                    },

                    onValueChangeFinished = {

                        val baseOffset = playerModel?.seekTo ?: 0L
                        val seekPosition = baseOffset + (sliderPosition * duration).toLong()

                        val nearestChapter = chapters.minByOrNull {
                            kotlin.math.abs(it.startMs - seekPosition)
                        }

                        if (nearestChapter != null &&
                            kotlin.math.abs(nearestChapter.startMs - seekPosition) < 3000
                        ) {
                            onSeek(nearestChapter.startMs)
                            onSeekCompleted(nearestChapter.startMs)
                        } else {
                            onSeek(seekPosition)
                            onSeekCompleted(seekPosition)
                        }

                        isSeeking = false
                        onDragStateChange(false)
                        showControls(true)
                    },

                    colors = SliderDefaults.colors(
                        thumbColor = Color.Transparent,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent
                    ),

                    thumb = {
                        Box(modifier = Modifier.size(0.dp))
                    },

                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(animatedContainerHeight)
                        .drawBehind {

                        val trackHeightPx = animatedTrackHeight.toPx()
                        val trackY = size.height / 2 - trackHeightPx / 2

                        drawRoundRect(
                            color = Color.Gray.copy(alpha = 0.5f),
                            topLeft = Offset(0f, trackY),
                            size = Size(size.width, trackHeightPx),
                            cornerRadius = CornerRadius(trackHeightPx / 2)
                        )

                        drawRoundRect(
                            color = if (isLive && isAtLiveEdge)
                                progressColor
                            else
                                progressColor.copy(alpha = 0.7f),
                            topLeft = Offset(0f, trackY),
                            size = Size(size.width * sliderPosition, trackHeightPx),
                            cornerRadius = CornerRadius(trackHeightPx / 2)
                        )

                        if (duration > 0) {
                            cuePoints.forEach { cue ->
                                val x = (cue.positionMs.toFloat() / duration) * size.width
                                val markerSize =
                                    if (isSeeking) 6.dp.toPx() else 4.dp.toPx()

                                drawRoundRect(
                                    color = when (cue.type) {
                                        CueType.AD -> Color.Yellow
                                        CueType.L_BAND -> Color.Green
                                    },
                                    topLeft = Offset(
                                        x - markerSize / 2,
                                        size.height / 2 - markerSize / 2
                                    ),
                                    size = Size(markerSize, markerSize),
                                    cornerRadius = CornerRadius(markerSize / 2)
                                )
                            }
                        }

                        if (duration > 0 && chapters.isNotEmpty()) {

                            chapters.forEach { chapter ->

                                val x = (chapter.startMs.toFloat() / duration) * size.width
                                val markerSize = if (isSeeking) 8.dp.toPx() else 6.dp.toPx()

                                drawRoundRect(
                                    color = if (playerModel?.isChapterEnabled == true) Color.Cyan else Color.Transparent ,
                                    topLeft = Offset(
                                        x - markerSize / 2,
                                        size.height / 2 - markerSize / 2
                                    ),
                                    size = Size(markerSize, markerSize),
                                    cornerRadius = CornerRadius(markerSize / 2)
                                )
                            }
                        }

                        if (duration > 0) {
                            val pointerWidthPx =
                                if (isSeeking) 3.dp.toPx() else trackHeightPx
                            val maxPointerLeft = (size.width - pointerWidthPx).coerceAtLeast(0f)
                            val pointerLeft =
                                (size.width * sliderPosition - pointerWidthPx / 2)
                                    .coerceIn(0f, maxPointerLeft)

                            drawRoundRect(
                                color = progressColor,
                                topLeft = Offset(pointerLeft, trackY),
                                size = Size(pointerWidthPx, trackHeightPx),
                                cornerRadius = CornerRadius(trackHeightPx / 2)
                            )
                        }

                        }
                )
            }
        }

        /* ---------- DURATION / LIVE ---------- */

        if (!isLive) {
            Text(
                text = formatTime(duration),
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .offset(y = durationLabelVerticalOffset)
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (isAtLiveEdge) Color.Transparent
                        else Color.Gray.copy(alpha = 0.3f)
                    )
                    .clickable {
                        onSeek(duration)
                        exoPlayer?.play()
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .alpha(if (isAtLiveEdge) blinkAlpha else 1f)
                        .clip(CircleShape)
                        .background(if (isAtLiveEdge) progressColor else Color.Gray)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isAtLiveEdge) "LIVE" else "Go Live",
                    color = if (isAtLiveEdge) progressColor else Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
