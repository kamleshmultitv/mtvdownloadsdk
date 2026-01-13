package com.app.videosdk.model

data class SkipIntro(
    val startTime: Long? = 5000L,
    val endTime: Long? = 95000L,
    val enableSkipIntro: Boolean = false
)
