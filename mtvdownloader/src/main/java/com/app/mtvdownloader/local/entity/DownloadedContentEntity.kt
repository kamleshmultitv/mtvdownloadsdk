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
    val videoBitrate: Int? = null,
    val queuedAt: Long = System.currentTimeMillis(),
    val failureCode: String? = null,
    val failureReason: String? = null,
    val retryCount: Int = 0,
    val maxRetryCount: Int = 3,
    val drmLicenseExpiresAt: Long? = null,
    val drmLicenseLastRefreshAt: Long? = null,
    val drmLicenseRefreshStatus: String? = null,
    val drmOfflineKeySetId: ByteArray? = null,
    val drmOfflineKeySetIdBase64: String? = null,
    val drmScheme: String? = null,
    val drmKeyId: String? = null,
    val contentMimeType: String? = null
)
