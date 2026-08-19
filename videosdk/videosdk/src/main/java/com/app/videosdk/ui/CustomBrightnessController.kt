package com.app.videosdk.ui

import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CustomBrightnessController(
    modifier: Modifier = Modifier,
    onShowControls: (Boolean) -> Unit = {}
) {
    val activity = LocalActivity.current

    val defaultBrightness = remember { getWindowBrightness(activity) }
    val currentBrightness = remember { mutableFloatStateOf(defaultBrightness) }
    val brightnessPercentage = (currentBrightness.floatValue * 100).toInt()
    val coroutineScope = rememberCoroutineScope()
    val dragGesture = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragStart = {
                onShowControls(true) // Keep visible while dragging
            },
            onVerticalDrag = { _, dragAmount ->
                val newBrightness = (currentBrightness.floatValue - dragAmount / 500)
                    .coerceIn(0f, 1f)
                currentBrightness.floatValue = newBrightness
                setWindowBrightness(activity, newBrightness)

                // Refresh visibility during continuous drag
                onShowControls(true)
            },
            onDragEnd = {
                // Delay hiding slightly after drag ends
                coroutineScope.launch {
                    delay(800L)
                    onShowControls(false)
                }
            }
        )
    }

    // UI — unchanged
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .background(Color.Transparent)
            .padding(16.dp)
            .size(60.dp, 200.dp)
            .then(dragGesture)
    ) {
        IconButton(
            modifier = Modifier
                .wrapContentSize()
                .size(24.dp),
            onClick = {}
        ) {
            Icon(
                imageVector = Icons.Default.Brightness6,
                contentDescription = "Brightness",
                modifier = Modifier.size(24.dp),
                tint = Color.White
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .width(4.dp)
                .height(120.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Gray)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(currentBrightness.floatValue)
                    .align(Alignment.BottomCenter)
                    .background(Color.Yellow)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "$brightnessPercentage%",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

fun getWindowBrightness(activity: Activity?): Float {
    val attr = activity?.window?.attributes
    return attr?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f
}

fun setWindowBrightness(activity: Activity?, brightness: Float) {
    activity?.window?.attributes = activity.window?.attributes?.apply {
        screenBrightness = brightness.coerceIn(0f, 1f)
    }
}