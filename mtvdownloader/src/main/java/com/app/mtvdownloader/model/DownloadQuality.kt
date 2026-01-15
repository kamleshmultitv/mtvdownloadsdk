package com.app.mtvdownloader.model

import androidx.annotation.OptIn
import androidx.media3.common.StreamKey
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
data class DownloadQuality(
    val height: Int,
    val bitrate: Int,
    val label: String,
    val streamKey: StreamKey
)
