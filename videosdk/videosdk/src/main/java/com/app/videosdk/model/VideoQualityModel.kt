package com.app.videosdk.model

data class VideoQualityModel(
    val id: String? = null,
    val height: Int = 0,
    val width: Int = 0,
    val bitrate: Int? = null,
    val title: String? = null
) : Comparable<VideoQualityModel> {

    override fun compareTo(other: VideoQualityModel): Int {
        return other.height - this.height
    }

    companion object {
        val DESCENDING_COMPARATOR = compareByDescending<VideoQualityModel> { it.height }
    }
}

