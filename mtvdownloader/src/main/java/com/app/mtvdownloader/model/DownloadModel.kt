package com.app.mtvdownloader.model

data class DownloadModel(
    val id: String? = null,
    val seasonId: String? = null,
    val hlsUrl: String? = null,
    val mpdUrl: String? = null,
    val drmToken: String? = null,
    val imageUrl: String? = null,
    val title: String? = null,
    val description: String? = null,
    val seasonTitle: String? = null,
    val seasonDescription: String? = null,
    val srt: String? = null,
)