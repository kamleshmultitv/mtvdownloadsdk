package com.app.mtvdownloader.model

import android.content.Context

fun interface DownloadMonetizationGate {
    suspend fun canStartDownload(
        context: Context,
        contentItem: DownloadModel
    ): Boolean
}

object AllowDownloadMonetizationGate : DownloadMonetizationGate {
    override suspend fun canStartDownload(
        context: Context,
        contentItem: DownloadModel
    ): Boolean = true
}
