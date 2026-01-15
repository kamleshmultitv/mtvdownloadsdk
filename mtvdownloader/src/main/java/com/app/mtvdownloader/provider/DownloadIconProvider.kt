package com.app.mtvdownloader.provider

import androidx.annotation.DrawableRes
import com.app.mtvdownloader.R
import com.app.mtvdownloader.worker.DownloadWorker

fun interface DownloadIconProvider {
    @DrawableRes
    fun iconFor(status: String?): Int
}

object DefaultDownloadIconProvider : DownloadIconProvider {
    override fun iconFor(status: String?): Int {
        return when (status) {
            DownloadWorker.DOWNLOAD_STATUS_PAUSED ->
                R.drawable.ic_download_pause
            DownloadWorker.DOWNLOAD_STATUS_QUEUED ->
                R.drawable.ic_downlaod_queue
            DownloadWorker.DOWNLOAD_STATUS_COMPLETED ->
                R.drawable.ic_download_done
            DownloadWorker.DOWNLOAD_STATUS_DOWNLOADING ->
                R.drawable.ic_downloading
            else ->
                R.drawable.ic_download
        }
    }
}
