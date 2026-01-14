package com.app.mtvdownloader.ui

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.app.mtvdownloader.R
import com.app.mtvdownloader.helper.ReelDownloadHelper.cancelDownload
import com.app.mtvdownloader.helper.ReelDownloadHelper.pauseDownload
import com.app.mtvdownloader.helper.ReelDownloadHelper.resumeDownload
import com.app.mtvdownloader.helper.ReelDownloadHelper.startDownloadWithQuality
import com.app.mtvdownloader.model.DownloadModel
import com.app.mtvdownloader.viewmodel.DownloadViewModel
import com.app.mtvdownloader.worker.DownloadWorker

@Composable
fun DownloadButton(
    contentItem: DownloadModel?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    if (contentItem == null) return

    var showMenu by remember { mutableStateOf(false) }
    var showQualitySelector by remember { mutableStateOf(false) }

    val application = remember {
        context.applicationContext as Application
    }

    val viewModel = remember {
        DownloadViewModel(application)
    }

    val downloadState by viewModel
        .observeDownload(contentItem.id.toString())
        .collectAsState(initial = null)

    val status = downloadState?.downloadStatus
    val progress = (downloadState?.downloadProgress ?: 0) / 100f

    val iconRes = remember(status) {
        when (status) {
            DownloadWorker.DOWNLOAD_STATUS_PAUSED ->
                R.drawable.ic_pause_download

            DownloadWorker.DOWNLOAD_STATUS_QUEUED ->
                R.drawable.ic_downlaod_queue

            DownloadWorker.DOWNLOAD_STATUS_COMPLETED ->
                R.drawable.ic_download_done

            else ->
                R.drawable.ic_download
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {

        if (status == DownloadWorker.DOWNLOAD_STATUS_DOWNLOADING) {
            CircularProgressIndicator(
                progress = progress,
                strokeWidth = 2.dp,
                color = Color.White,
                modifier = Modifier.matchParentSize()
            )
        }

        IconButton(
            onClick = {
                when (status) {
                    DownloadWorker.DOWNLOAD_STATUS_DOWNLOADING,
                    DownloadWorker.DOWNLOAD_STATUS_QUEUED,
                    DownloadWorker.DOWNLOAD_STATUS_PAUSED -> {
                        showMenu = true
                    }
                    DownloadWorker.DOWNLOAD_STATUS_COMPLETED -> {
                        Toast.makeText(
                            context,
                            "${contentItem.title}  already downloaded",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    else -> {
                        showQualitySelector = true
                    }
                }
            }
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = "Download",
                tint = Color.White
            )
        }

        /* ---------- Pause / Resume / Cancel Menu ---------- */

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {

            if (status == DownloadWorker.DOWNLOAD_STATUS_DOWNLOADING) {
                DropdownMenuItem(
                    text = { Text("Pause Download") },
                    onClick = {
                        showMenu = false
                        pauseDownload(context, contentItem.id.toString())
                    }
                )
            }

            if (status == DownloadWorker.DOWNLOAD_STATUS_PAUSED) {
                DropdownMenuItem(
                    text = { Text("Resume Download") },
                    onClick = {
                        showMenu = false
                        resumeDownload(context, contentItem)
                    }
                )
            }

            DropdownMenuItem(
                text = { Text("Cancel Download") },
                onClick = {
                    showMenu = false
                    cancelDownload(context, contentItem.id.toString())
                }
            )
        }
    }

    /* ---------- Quality Selector ---------- */

    if (showQualitySelector) {
        ShowQualitySelector(
            context = context,
            contentItem = contentItem,
            onDismiss = { showQualitySelector = false },
            onQualitySelected = { quality ->
                showQualitySelector = false
                startDownloadWithQuality(context, contentItem, quality)
            }
        )
    }
}
