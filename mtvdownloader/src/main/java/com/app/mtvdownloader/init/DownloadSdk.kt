package com.app.mtvdownloader.init

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.app.mtvdownloader.service.MediaDownloadService

object DownloadSdk {

    private const val TAG = "DownloadSdk"
    private var isInitialized = false

    @RequiresApi(Build.VERSION_CODES.O)
    fun init(application: Application) {
        if (isInitialized) return

        try {
            createDownloadNotificationChannel(application)
            isInitialized = true
            Log.d(TAG, "Download SDK initialized successfully")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize Download SDK", t)
        }
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