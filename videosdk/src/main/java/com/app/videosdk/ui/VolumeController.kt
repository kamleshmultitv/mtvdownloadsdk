package com.app.videosdk.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun CustomVolumeController(
    exoPlayer: ExoPlayer,
    modifier: Modifier = Modifier,
    onShowControls: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var systemVolume by remember { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    var isMuted by rememberSaveable { mutableStateOf(exoPlayer.volume == 0f) }

    val volumePercentage = (systemVolume * 100 / maxVolume).toInt()
    val isDragging = remember { mutableStateOf(false) }

    // Ensure ExoPlayer respects the mute state
    LaunchedEffect(systemVolume, isMuted) {
        exoPlayer.volume = if (isMuted) 0f else systemVolume / maxVolume
    }

    // BroadcastReceiver to detect system volume changes
    DisposableEffect(context) {
        val volumeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                    val newVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                    systemVolume = newVolume
                    if (newVolume > 0) isMuted = false // Unmute when volume is increased
                }
            }
        }

        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        context.registerReceiver(volumeReceiver, filter)

        onDispose {
            context.unregisterReceiver(volumeReceiver)
        }
    }

    // Gesture detection for vertical drag (volume control)
    val dragGesture = Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragStart = {
                isDragging.value = true
                onShowControls(true)
            },
            onDragEnd = {
                isDragging.value = false
                onShowControls(false)
            },
            onVerticalDrag = { _, dragAmount ->
                val scaleFactor = 0.02f // Sensitivity of drag
                val adjustedDrag = dragAmount * scaleFactor
                val stepSize = if (abs(dragAmount) > 10) 1.5f else 1.0f

                val newVolume =
                    (systemVolume - (adjustedDrag * stepSize)).coerceIn(0f, maxVolume.toFloat())

                systemVolume = newVolume
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume.roundToInt(), 0)
                if (newVolume > 0) isMuted = false // Unmute when volume is increased
            }
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .background(Color.Transparent)
            .padding(16.dp)
            .size(60.dp, 200.dp)
            .then(dragGesture)
    ) {
        // Volume Icon (Mute/Unmute)
        IconButton(
            modifier = Modifier.wrapContentSize()
                .size(24.dp),
            onClick = {
                onShowControls(true)
                isMuted = !isMuted
                exoPlayer.volume = if (isMuted) 0f else systemVolume / maxVolume
            }
        ) {
            Icon(
                imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = if (isMuted) "Unmute" else "Mute",
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Volume bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(120.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Green)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(1f - (systemVolume / maxVolume))
                    .background(Color.Gray, shape = RoundedCornerShape(4.dp))
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        // Volume percentage
        Text(
            text = "$volumePercentage%",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}