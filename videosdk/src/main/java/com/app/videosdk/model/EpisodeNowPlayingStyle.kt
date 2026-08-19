package com.app.videosdk.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class EpisodeNowPlayingStyle(
    val pillText: String = "Playing",
    val pillBackgroundColor: Color = Color(0xFF00C853),
    val pillTextColor: Color = Color.White,
    val pillDotColor: Color = Color.White,
    val cardBorderColor: Color = Color.Transparent,
    val cardBorderWidth: Dp = 0.dp
)
