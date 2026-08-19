package com.app.sample

import android.app.Application
import android.util.Log
import com.app.mtvdownloader.init.DownloadSdk
import com.app.mtvdownloader.model.DownloadAnalyticsListener
import com.app.mtvdownloader.model.DownloadModel
import com.app.mtvdownloader.model.DownloadSdkConfig

class AppClass : Application() {

    override fun onCreate() {
        super.onCreate()

        DownloadSdk.init(
            application = this,
            config = DownloadSdkConfig(
                maxCacheBytes = 1024L * 1024L * 1024L,
                maxParallelDownloads = 1,
                minRetryCount = 3
            ),
            analyticsListener = object : DownloadAnalyticsListener {
                override fun onDownloadRequested(contentItem: DownloadModel) {
                    Log.d("DownloadAnalytics", "Requested: ${contentItem.id}")
                }

                override fun onMonetizationAllowed(contentItem: DownloadModel) {
                    Log.d("DownloadAnalytics", "Allowed: ${contentItem.id}")
                }

                override fun onMonetizationBlocked(contentItem: DownloadModel) {
                    Log.d("DownloadAnalytics", "Blocked: ${contentItem.id}")
                }

                override fun onDownloadCompleted(contentId: String) {
                    Log.d("DownloadAnalytics", "Completed: $contentId")
                }

                override fun onDownloadFailed(
                    contentId: String,
                    errorCode: String?,
                    errorMessage: String?
                ) {
                    Log.d(
                        "DownloadAnalytics",
                        "Failed: $contentId code=$errorCode message=$errorMessage"
                    )
                }
            }
        )
    }
}
