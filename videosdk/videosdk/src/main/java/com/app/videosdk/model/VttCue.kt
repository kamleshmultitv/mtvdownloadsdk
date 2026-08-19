package com.app.videosdk.model

data class VttCue(
    val startMs: Long,
    val endMs: Long,
    val imageUrl: String,
    val x: Int? = null,
    val y: Int? = null,
    val w: Int? = null,
    val h: Int? = null
)