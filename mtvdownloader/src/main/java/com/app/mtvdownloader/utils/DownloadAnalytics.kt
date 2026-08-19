package com.app.mtvdownloader.utils

import android.util.Log
import com.app.mtvdownloader.model.DownloadAnalyticsListener

object DownloadAnalytics {
    private const val TAG = "DownloadAnalytics"

    @Volatile
    private var listener: DownloadAnalyticsListener? = null

    fun setListener(downloadAnalyticsListener: DownloadAnalyticsListener?) {
        listener = downloadAnalyticsListener
    }

    fun notify(block: DownloadAnalyticsListener.() -> Unit) {
        val currentListener = listener ?: return
        runCatching {
            currentListener.block()
        }.onFailure { error ->
            Log.w(TAG, "Analytics listener failed: ${error.message}", error)
        }
    }
}
