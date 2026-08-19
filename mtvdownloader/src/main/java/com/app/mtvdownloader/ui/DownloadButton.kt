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
import com.app.mtvdownloader.helper.ReelDownloadHelper.handleDownloadClick
import com.app.mtvdownloader.helper.ReelDownloadHelper.cancelDownload
import com.app.mtvdownloader.helper.ReelDownloadHelper.pauseDownload
import com.app.mtvdownloader.helper.ReelDownloadHelper.resumeDownload
import com.app.mtvdownloader.helper.ReelDownloadHelper.startDownloadWithQuality
import com.app.mtvdownloader.local.entity.DownloadedContentEntity
import com.app.mtvdownloader.model.AllowDownloadMonetizationGate
import com.app.mtvdownloader.model.DownloadModel
import com.app.mtvdownloader.model.DownloadMonetizationGate
import com.app.mtvdownloader.model.DownloadQuality
import com.app.mtvdownloader.provider.DefaultDownloadIconProvider
import com.app.mtvdownloader.provider.DownloadIconProvider
import com.app.mtvdownloader.utils.Constants.DOWNLOAD_STATUS_COMPLETED
import com.app.mtvdownloader.utils.Constants.DOWNLOAD_STATUS_DOWNLOADING
import com.app.mtvdownloader.utils.Constants.DOWNLOAD_STATUS_FAILED
import com.app.mtvdownloader.utils.Constants.DOWNLOAD_STATUS_PAUSED
import com.app.mtvdownloader.utils.Constants.DOWNLOAD_STATUS_QUEUED
import com.app.mtvdownloader.utils.CustomQualitySelector
import com.app.mtvdownloader.utils.DownloadAnalytics
import com.app.mtvdownloader.utils.DownloadSourceResolver
import com.app.mtvdownloader.viewmodel.DownloadViewModel
import kotlinx.coroutines.launch


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
    downloadProgressColor: Color = Color(0xFF00C853),
    downloadProgressTrackColor: Color = Color.White.copy(alpha = 0.24f),
    monetizationGate: DownloadMonetizationGate = AllowDownloadMonetizationGate,
    onDownloadedListUpdate: (List<DownloadedContentEntity>) -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    /* ---------- State ---------- */
    var qualities by remember(contentItem) {
        mutableStateOf<List<DownloadQuality>>(emptyList())
    }
    var showSelector by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var isCheckingMonetization by remember { mutableStateOf(false) }

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
    val failureMessage = downloadState?.failureReason
        ?: downloadState?.failureCode

    val downloadedList by viewModel
        .getAllDownloadedContent()
        .collectAsState(initial = emptyList())

    LaunchedEffect(downloadedList) {
        onDownloadedListUpdate(downloadedList)
    }

    var lastFailureToastKey by remember(contentItem.id) {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(status, failureMessage) {
        if (status == DOWNLOAD_STATUS_FAILED) {
            val message = failureMessage
                ?.takeIf { it.isNotBlank() }
                ?: "Download failed"
            val toastKey = "${contentItem.id}:$message"

            if (lastFailureToastKey != toastKey) {
                lastFailureToastKey = toastKey
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    /* ---------- Download Executor ---------- */
    val startDownload: (DownloadQuality) -> Unit = remember(contentItem) {
        { quality ->
            startDownloadWithQuality(context, contentItem, quality)
        }
    }

    val startDownloadWithoutQuality: () -> Unit = remember(contentItem) {
        {
            handleDownloadClick(context, contentItem)
        }
    }

    /* ---------- Load qualities ---------- */
    suspend fun loadQualities() {
        val source = DownloadSourceResolver.resolve(contentItem)

        if (source == null) {
            showSelector = false
            Toast.makeText(context, "Unsupported download source", Toast.LENGTH_SHORT).show()
            return
        }

        if (!source.streamType.supportsQualitySelection) {
            showSelector = false
            startDownloadWithoutQuality()
            return
        }

        if (qualities.isEmpty()) {
            qualities = runCatching {
                HlsQualityHelper.getDownloadQualities(context, source.url)
            }.getOrDefault(emptyList())
        }

        when (qualities.size) {
            0 -> {
                showSelector = false
                startDownloadWithoutQuality()
            }

            1 -> {
                showSelector = false
                startDownload(qualities.first())
            }
        }
    }

    /* ---------- ICON (only change here) ---------- */
    val iconRes = remember(status, iconProvider) {
        iconProvider.iconFor(status)
    }

    /* ---------- UI ---------- */
    Box(modifier = modifier, contentAlignment = Alignment.Center) {

        when {
            isCheckingMonetization -> {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = Color.White,
                    modifier = Modifier.matchParentSize()
                )
            }

            status == DOWNLOAD_STATUS_DOWNLOADING -> {
                CircularProgressIndicator(
                    progress = { progress },
                    strokeWidth = 2.dp,
                    color = downloadProgressColor,
                    trackColor = downloadProgressTrackColor,
                    modifier = Modifier.matchParentSize()
                )
            }

            status == DOWNLOAD_STATUS_FAILED -> {
                CircularProgressIndicator(
                    progress = { 1f },
                    strokeWidth = 2.dp,
                    color = Color(0xFFFF5252),
                    trackColor = downloadProgressTrackColor,
                    modifier = Modifier.matchParentSize()
                )
            }
        }

        IconButton(
            enabled = !isCheckingMonetization,
            onClick = {
                when (status) {
                    DOWNLOAD_STATUS_DOWNLOADING,
                    DOWNLOAD_STATUS_QUEUED,
                    DOWNLOAD_STATUS_PAUSED -> {
                        showMenu = true
                    }

                    DOWNLOAD_STATUS_COMPLETED -> {
                        Toast.makeText(
                            context,
                            "${contentItem.title} already downloaded",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    else -> {
                        coroutineScope.launch {
                            DownloadAnalytics.notify {
                                onDownloadRequested(contentItem)
                            }

                            isCheckingMonetization = true
                            val canStart = runCatching {
                                monetizationGate.canStartDownload(context, contentItem)
                            }.getOrDefault(false)
                            isCheckingMonetization = false

                            if (canStart) {
                                DownloadAnalytics.notify {
                                    onMonetizationAllowed(contentItem)
                                }
                                showSelector = true
                            } else {
                                DownloadAnalytics.notify {
                                    onMonetizationBlocked(contentItem)
                                }
                                Toast.makeText(
                                    context,
                                    "Download not available",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
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

            if (status == DOWNLOAD_STATUS_DOWNLOADING || status == DOWNLOAD_STATUS_QUEUED) {
                DropdownMenuItem(
                    text = { Text("Pause Download") },
                    onClick = {
                        showMenu = false
                        pauseDownload(context, contentItem.id.toString())
                    }
                )
            }

            if (status == DOWNLOAD_STATUS_PAUSED) {
                DropdownMenuItem(
                    text = { Text("Resume Download") },
                    onClick = {
                        showMenu = false
                        resumeDownload(context, contentItem.id.toString())
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
        } else if (qualities.size > 1) {
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
                qualities = qualities,
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
