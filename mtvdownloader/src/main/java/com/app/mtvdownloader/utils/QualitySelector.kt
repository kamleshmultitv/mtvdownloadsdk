package com.app.mtvdownloader.utils

import androidx.compose.runtime.Composable
import com.app.mtvdownloader.model.DownloadQuality

typealias CustomQualitySelector =
        @Composable (qualities: List<DownloadQuality>, onSelect: (DownloadQuality) -> Unit, onDismiss: () -> Unit) -> Unit