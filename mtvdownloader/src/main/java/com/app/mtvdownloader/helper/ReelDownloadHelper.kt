package com.app.mtvdownloader.helper

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.app.mtvdownloader.local.entity.DownloadedContentEntity
import com.app.mtvdownloader.model.DownloadModel
import com.app.mtvdownloader.repository.DownloadRepository
import com.app.mtvdownloader.service.MediaDownloadService
import com.app.mtvdownloader.worker.DownloadWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ReelDownloadHelper {

    fun handleDownloadClick(
        context: Context,
        contentItem: DownloadModel?
    ) {
        if (contentItem == null || contentItem.hlsUrl.isNullOrEmpty()) return

        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {

            try {
                val repository = DownloadRepository.instance(appContext)
                val contentId = contentItem.id.toString()

                // 1️⃣ CHECK EXISTING STATE (AFTER FIX THIS WORKS)
                val existing = repository.getDownloadedContentOnce(contentId)

                when (existing?.downloadStatus) {
                    DownloadWorker.DOWNLOAD_STATUS_COMPLETED -> {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "${contentItem.title} already downloaded",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return@launch
                    }

                    DownloadWorker.DOWNLOAD_STATUS_QUEUED -> {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "${contentItem.title} is already in queue",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return@launch
                    }

                    DownloadWorker.DOWNLOAD_STATUS_DOWNLOADING -> {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "${contentItem.title} is downloading",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return@launch
                    }
                }

                // 2️⃣ CHECK IF ANOTHER DOWNLOAD IS RUNNING
                val hasActiveDownload = repository.hasActiveDownload()

                // 🔥 3️⃣ INSERT QUEUED ROW IMMEDIATELY (KEY FIX)
                repository.insertOrUpdate(
                    DownloadedContentEntity(
                        contentId = contentId,
                        seasonId = contentItem.seasonId.orEmpty(),
                        title = contentItem.title.orEmpty(),
                        seasonName = contentItem.seasonTitle.orEmpty(),
                        hlsUrl = contentItem.hlsUrl,
                        thumbnailUrl = contentItem.imageUrl,
                        seasonImage = contentItem.imageUrl,
                        downloadStatus = DownloadWorker.DOWNLOAD_STATUS_QUEUED,
                        downloadProgress = 0
                    )
                )

                // 4️⃣ BUILD WORK REQUEST
                val inputData = androidx.work.Data.Builder()
                    .putString(DownloadWorker.KEY_CONTENT_ID, contentId)
                    .putString(DownloadWorker.KEY_SEASON_ID, contentItem.seasonId.orEmpty())
                    .putString(DownloadWorker.KEY_CONTENT_TITLE, contentItem.title.orEmpty())
                    .putString(DownloadWorker.KEY_SEASON_NAME, contentItem.seasonTitle.orEmpty())
                    .putString(DownloadWorker.KEY_THUMBNAIL_URL, contentItem.imageUrl)
                    .putString(DownloadWorker.KEY_SEASON_THUMBNAIL_URL, contentItem.imageUrl)
                    .putString(DownloadWorker.KEY_HLS_URI, contentItem.hlsUrl)
                    .build()

                val workRequest =
                    OneTimeWorkRequestBuilder<DownloadWorker>()
                        .setInputData(inputData)
                        .addTag(contentId)
                        .build()

                WorkManager.getInstance(appContext)
                    .enqueueUniqueWork(
                        "reel_download_queue",
                        ExistingWorkPolicy.APPEND,
                        workRequest
                    )

                MediaDownloadService.start(appContext)

                // 5️⃣ TOAST MESSAGE (NOW ALWAYS CORRECT)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        if (hasActiveDownload)
                            "${contentItem.title} added to queue"
                        else
                            "${contentItem.title} downloading",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (t: Throwable) {
                Log.e("ReelDownloadHelper", "Download enqueue failed", t)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Failed to start download",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}
