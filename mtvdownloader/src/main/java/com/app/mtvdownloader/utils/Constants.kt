package com.app.mtvdownloader.utils

object Constants {
    const val KEY_CONTENT_URI = "content_uri"
    const val KEY_DRM_LICENSE_URI = "drm_license_uri"
    const val KEY_CONTENT_ID = "content_id"
    const val KEY_SEASON_ID = "season_id"
    const val KEY_CONTENT_TITLE = "content_title"
    const val KEY_SEASON_NAME = "season_name"
    const val KEY_THUMBNAIL_URL = "thumbnail_url"
    const val KEY_SEASON_THUMBNAIL_URL = "season_thumbnail_url"

    const val KEY_STREAM_KEYS = "stream_keys"

    const val DOWNLOAD_STATUS_QUEUED = "queued"
    const val DOWNLOAD_STATUS_DOWNLOADING = "downloading"
    const val DOWNLOAD_STATUS_COMPLETED = "completed"
    const val DOWNLOAD_STATUS_FAILED = "failed"
    const val DOWNLOAD_STATUS_REMOVED = "removed"
    const val DOWNLOAD_STATUS_PAUSED = "paused"

    const val DOWNLOAD_STOP_REASON_USER_PAUSED = 1

    const val DRM_LICENSE_STATUS_NOT_REQUIRED = "not_required"
    const val DRM_LICENSE_STATUS_VALID = "valid"
    const val DRM_LICENSE_STATUS_OFFLINE_VALID = "offline_valid"
    const val DRM_LICENSE_STATUS_OFFLINE_FAILED = "offline_failed"
    const val DRM_LICENSE_STATUS_REFRESH_REQUIRED = "refresh_required"
    const val DRM_LICENSE_STATUS_REFRESH_FAILED = "refresh_failed"

    const val DRM_SCHEME_WIDEVINE = "widevine"

    const val FAILURE_DRM_INIT_DATA_MISSING = "DRM_INIT_DATA_MISSING"
    const val FAILURE_DRM_MANIFEST_PREPARE_FAILED = "DRM_MANIFEST_PREPARE_FAILED"
    const val FAILURE_DRM_AUTH_TOKEN_EXPIRED = "DRM_AUTH_TOKEN_EXPIRED"
    const val FAILURE_DRM_OFFLINE_LICENSE_FAILED = "DRM_OFFLINE_LICENSE_FAILED"
    const val FAILURE_DRM_KEYSETID_EMPTY = "DRM_KEYSETID_EMPTY"
    const val FAILURE_DRM_DOWNLOAD_REQUEST_FAILED = "DRM_DOWNLOAD_REQUEST_FAILED"
    const val FAILURE_DRM_PACKAGE_OR_SIGNING_NOT_ALLOWED = "DRM_PACKAGE_OR_SIGNING_NOT_ALLOWED"
}
