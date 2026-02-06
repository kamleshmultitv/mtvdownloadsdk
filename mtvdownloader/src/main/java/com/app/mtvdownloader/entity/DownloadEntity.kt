package com.app.mtvdownloader.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_content")
data class DownloadEntity(
    @PrimaryKey val contentId: String,
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
    val drm: String? = null,
    val progress: Int? = null,
    val downloadStatus: String? = null,
    val isDownloaded: Boolean? = null,
    val isGroup: String? = null,
    val timeStamp: Long? = null,
    val seasonBanner: String? = null,
    val likeCount: String? = null,
    val mediaDuration: String? = null,
    val mediaPath: String? = null,
    val isCheck: Boolean = false,
    val type: String? = null,
    val token: String? = null,
    val lang: String? = null,
    val langId: String? = null,
    val str: String? = null,
    val layoutType: String? = null,
    val downloadExpiry: String? = null,
    val userId: Int? = null,
    val accessType: String? = null,
    val kId: String? = null,
    val keySetId: String? = null,
    val streamKeys: String? = null,
    val videoHeight: Int? = null,
    val videoBitrate: Int? = null
)