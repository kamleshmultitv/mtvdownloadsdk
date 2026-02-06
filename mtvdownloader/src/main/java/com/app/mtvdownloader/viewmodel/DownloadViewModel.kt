package com.app.mtvdownloader.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.util.UnstableApi
import com.app.mtvdownloader.entity.DownloadEntity
import com.app.mtvdownloader.repository.DownloadRepository
import kotlinx.coroutines.flow.Flow

@UnstableApi
class DownloadViewModel(application: Application) :
    AndroidViewModel(application) {

    private val repository = DownloadRepository.instance(application)

    fun observeDownload(contentId: String): Flow<DownloadEntity?> {
        return repository.getDownloadedContent(contentId)
    }

    // ✅ NEW: get all downloaded content list
    fun getAllDownloadedContent(): Flow<List<DownloadEntity>> {
        return repository.getAllDownloadedContent()
    }
}
