package com.app.mtvdownloader.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_content")
data class DownloadedContentEntity(
    @PrimaryKey val contentId: String,
    val seasonId: String? = null,
    val title: String,
    val seasonName: String,
    val contentUrl: String,
    val licenseUri: String,
    val localFilePath: String? = null,
    val thumbnailUrl: String? = null,
    val seasonImage: String? = null,
    val downloadProgress: Int = 0,
    val downloadStatus: String,
    val downloadedAt: Long? = null,
    val streamKeys: String? = null,
    val videoHeight: Int? = null,
    val videoBitrate: Int? = null
)
