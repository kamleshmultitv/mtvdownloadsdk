package com.app.mtvdownloader.utils

import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.StreamKey

object MediaItemFactory {

    fun buildHlsDownloadMediaItem(
        contentId: String,
        hlsUrl: String,
        streamKeys: List<StreamKey>
    ): MediaItem {
        return MediaItem.Builder()
            .setUri(hlsUrl)
            .setMediaId(contentId)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .setStreamKeys(streamKeys)
            .build()
    }
}