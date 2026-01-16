package com.app.mtvdownloader.ui

import android.app.Application
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.app.mtvdownloader.helper.HlsQualityHelper
import com.app.mtvdownloader.helper.ReelDownloadHelper.cancelDownload
import com.app.mtvdownloader.helper.ReelDownloadHelper.pauseDownload
import com.app.mtvdownloader.helper.ReelDownloadHelper.resumeDownload
import com.app.mtvdownloader.helper.ReelDownloadHelper.startDownloadWithQuality
import com.app.mtvdownloader.local.entity.DownloadedContentEntity
import com.app.mtvdownloader.model.DownloadModel
import com.app.mtvdownloader.model.DownloadQuality
import com.app.mtvdownloader.provider.DefaultDownloadIconProvider
import com.app.mtvdownloader.provider.DownloadIconProvider
import com.app.mtvdownloader.utils.CustomQualitySelector
import com.app.mtvdownloader.viewmodel.DownloadViewModel
import com.app.mtvdownloader.worker.DownloadWorker
import kotlin.toString


/**
 * Download button with built-in download state handling.
 *
 * @param contentItem Required download model.
 *
 * @param customQualitySelector Optional.
 * If provided, SDK will use this composable to show quality selection UI.
 * If null, SDK default quality selector dialog will be shown.
 *
 * @param iconProvider Optional.
 * Allows client to override download icons based on download status.
 * If not provided, SDK default icons are used.
 *
 * @param modifier Optional Compose modifier.
 */
@OptIn(UnstableApi::class)
@Composable
fun DownloadButton(
    contentItem: DownloadModel,
    modifier: Modifier = Modifier,
    customQualitySelector: CustomQualitySelector? = null, // optional
    iconProvider: DownloadIconProvider = DefaultDownloadIconProvider, // optional
    onDownloadedListUpdate: (List<DownloadedContentEntity>) -> Unit = {}
) {
    val context = LocalContext.current

    /* ---------- State ---------- */
    var qualities by remember(if (contentItem.drm == "1") contentItem.mpdUrl.toString() else contentItem.hlsUrl.toString()) {
        mutableStateOf<List<DownloadQuality>>(emptyList())
    }
    var showSelector by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    /* ---------- Download VM ---------- */
    val application = context.applicationContext as Application
    val viewModel = remember(contentItem.id) {
        DownloadViewModel(application)
    }

    val downloadState by viewModel
        .observeDownload(contentItem.id.toString())
        .collectAsState(initial = null)

    val status = downloadState?.downloadStatus
    val progress = (downloadState?.downloadProgress ?: 0) / 100f

    val downloadedList by viewModel
        .getAllDownloadedContent()
        .collectAsState(initial = emptyList())

    LaunchedEffect(downloadedList) {
        onDownloadedListUpdate(downloadedList)
    }

    /* ---------- Download Executor ---------- */
    val startDownload: (DownloadQuality) -> Unit = remember(contentItem.id) {
        { quality ->
            startDownloadWithQuality(context, contentItem, quality)
        }
    }

    /* ---------- Load qualities ---------- */
    suspend fun loadQualities() {
        if (qualities.isEmpty() && contentItem.hlsUrl != null && contentItem.mpdUrl != null) {
            qualities = HlsQualityHelper.getHlsQualities(
                context,
                if (contentItem.drm == "1") contentItem.mpdUrl else contentItem.hlsUrl
            )
        }
    }

    /* ---------- ICON (only change here) ---------- */
    val iconRes = remember(status, iconProvider) {
        iconProvider.iconFor(status)
    }

    /* ---------- UI ---------- */
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
                            "${contentItem.title} already downloaded",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    else -> {
                        showSelector = true
                    }
                }
            }
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = "Download",
                tint = Color.Unspecified
            )
        }

        /* ---------- Menu ---------- */
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
    if (showSelector) {
        LaunchedEffect(showSelector) {
            loadQualities()
        }

        if (qualities.size == 1) {
            showSelector = false
            startDownload(qualities.first())
        } else {
            customQualitySelector?.invoke(
                qualities,
                { quality ->
                    showSelector = false
                    startDownload(quality)
                },
                {
                    showSelector = false
                }
            ) ?: ShowQualitySelectorDialog(
                context = context,
                contentItem = contentItem,
                onDismiss = {
                    showSelector = false
                },
                onQualitySelected = { quality ->
                    showSelector = false
                    startDownload(quality)
                }
            )
        }


    }
}
