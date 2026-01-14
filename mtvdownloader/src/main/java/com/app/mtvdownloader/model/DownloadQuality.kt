package com.app.mtvdownloader.model

import androidx.media3.common.StreamKey

data class DownloadQuality(
    val height: Int,
    val bitrate: Int,
    val label: String,
    val streamKey: StreamKey
)
