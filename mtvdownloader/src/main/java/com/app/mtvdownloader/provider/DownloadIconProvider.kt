package com.app.mtvdownloader.provider

import androidx.annotation.DrawableRes
import com.app.mtvdownloader.R
import com.app.mtvdownloader.utils.Constants.DOWNLOAD_STATUS_COMPLETED
import com.app.mtvdownloader.utils.Constants.DOWNLOAD_STATUS_DOWNLOADING
import com.app.mtvdownloader.utils.Constants.DOWNLOAD_STATUS_PAUSED
import com.app.mtvdownloader.utils.Constants.DOWNLOAD_STATUS_QUEUED

fun interface DownloadIconProvider {
    @DrawableRes
    fun iconFor(status: String?): Int
}

object DefaultDownloadIconProvider : DownloadIconProvider {
    override fun iconFor(status: String?): Int {
        return when (status) {
            DOWNLOAD_STATUS_PAUSED ->
                R.drawable.ic_download_pause
            DOWNLOAD_STATUS_QUEUED ->
                R.drawable.ic_downlaod_queue
            DOWNLOAD_STATUS_COMPLETED ->
                R.drawable.ic_download_done
            DOWNLOAD_STATUS_DOWNLOADING ->
                R.drawable.ic_downloading
            else ->
                R.drawable.ic_download
        }
    }
}
