package com.app.mtvdownloader.model

interface DownloadAnalyticsListener {
    fun onDownloadRequested(contentItem: DownloadModel) = Unit
    fun onMonetizationAllowed(contentItem: DownloadModel) = Unit
    fun onMonetizationBlocked(contentItem: DownloadModel) = Unit
    fun onDownloadEnqueued(
        contentId: String,
        sourceUrl: String,
        sourceType: String,
        qualityHeight: Int?,
        qualityBitrate: Int?
    ) = Unit

    fun onDownloadStarted(contentId: String) = Unit
    fun onDownloadProgress(contentId: String, progress: Int) = Unit
    fun onDownloadPaused(contentId: String) = Unit
    fun onDownloadResumed(contentId: String) = Unit
    fun onDownloadCancelled(contentId: String) = Unit
    fun onDownloadCompleted(contentId: String) = Unit
    fun onDownloadFailed(
        contentId: String,
        errorCode: String?,
        errorMessage: String?
    ) = Unit
}
