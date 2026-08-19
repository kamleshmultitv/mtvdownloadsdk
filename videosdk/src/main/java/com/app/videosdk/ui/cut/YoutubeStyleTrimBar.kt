package com.app.videosdk.ui.cut

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.app.videosdk.utils.PlayerUtils.formatTime

@Composable
fun YoutubeStyleTrimBar(
    duration: Long,
    modifier: Modifier = Modifier,
    onRangeChanged: (Long, Long, Long) -> Unit // ✅ added totalDuration
) {

    val minClip = 30_000L
    val maxClip = 120_000L

    var selectedDuration by remember { mutableLongStateOf(120_000L) }

    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    var timelineWidthPx by remember { mutableFloatStateOf(1f) }

    val totalSeconds = duration / 1000f
    val pxPerSecond = 10f
    val totalWidthPx = totalSeconds * pxPerSecond
    val totalWidthDp = with(density) { totalWidthPx.toDp() }

    Column(modifier = modifier) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
        ) {

            // 🔹 Scrollable Timeline
            Row(
                modifier = Modifier
                    .horizontalScroll(scrollState)
                    .width(totalWidthDp)
                    .height(60.dp)
                    .onSizeChanged {
                        timelineWidthPx = it.width.toFloat()
                    }
            ) {
                repeat(totalSeconds.toInt()) { sec ->
                    Box(
                        Modifier
                            .width(with(density) { pxPerSecond.toDp() })
                            .fillMaxHeight()
                            .background(
                                if (sec % 5 == 0)
                                    Color.White
                                else
                                    Color.Gray
                            )
                    )
                }
            }

            // 🔵 Blue Selection Box (centered)
            val boxWidthPx =
                (selectedDuration / 1000f) * pxPerSecond

            val boxWidthDp =
                with(density) { boxWidthPx.toDp() }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(boxWidthDp)
                    .height(70.dp)
                    .border(3.dp, Color(0xFF2196F3))
            ) {

                val startMs =
                    (scrollState.value.toFloat() /
                            scrollState.maxValue.toFloat() *
                            (duration - selectedDuration)).toLong()

                val endMs = startMs + selectedDuration

                // Start time
                Text(
                    text = formatTime(startMs),
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                )

                // End time
                Text(
                    text = formatTime(endMs),
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                )
            }

            // 🔄 Convert scroll → time
            LaunchedEffect(scrollState.value, selectedDuration) {

                if (timelineWidthPx <= 0) return@LaunchedEffect

                val maxScroll = scrollState.maxValue.toFloat()
                if (maxScroll == 0f) return@LaunchedEffect

                val scrollPercent =
                    scrollState.value / maxScroll

                val startMs =
                    (scrollPercent * (duration - selectedDuration)).toLong()

                val endMs = startMs + selectedDuration

                val totalCropDuration = endMs - startMs // ✅ total duration

                onRangeChanged(startMs, endMs, totalCropDuration)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 🔧 Duration Selector (30s–120s)
        Text("Clip Length: ${selectedDuration / 1000} sec")

        Slider(
            value = selectedDuration.toFloat(),
            onValueChange = {
                selectedDuration = it.toLong()
            },
            valueRange = minClip.toFloat()..maxClip.toFloat()
        )
    }
}