package com.app.mtvdownloader.init

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.app.mtvdownloader.DownloadUtil
import com.app.mtvdownloader.model.DownloadAnalyticsListener
import com.app.mtvdownloader.model.DownloadSdkConfig
import com.app.mtvdownloader.service.MediaDownloadService
import com.app.mtvdownloader.utils.DownloadAnalytics

object DownloadSdk {

    private const val TAG = "DownloadSdk"
    private var isInitialized = false

    fun init(
        application: Application,
        config: DownloadSdkConfig = DownloadSdkConfig(),
        analyticsListener: DownloadAnalyticsListener? = null
    ) {
        DownloadUtil.configure(config)
        DownloadAnalytics.setListener(analyticsListener)

        if (isInitialized) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                createDownloadNotificationChannel(application)
            }
            isInitialized = true
            Log.d(TAG, "Download SDK initialized successfully")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize Download SDK", t)
        }
    }

    fun setAnalyticsListener(listener: DownloadAnalyticsListener?) {
        DownloadAnalytics.setListener(listener)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createDownloadNotificationChannel(application: Application) {
        val channel = NotificationChannel(
            MediaDownloadService.CHANNEL_ID,
            "Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Background download notifications"
        }

        val manager =
            application.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)

        Log.d(TAG, "Download notification channel created")
    }
}
