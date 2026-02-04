package com.app.sample.model

import com.app.sample.utils.PlaybackMode
import com.app.sample.utils.StreamType

data class StreamInfo(
    val streamType: StreamType,
    val playbackMode: PlaybackMode
)