package com.app.mtvdownloader.ui

import android.content.Context
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.app.mtvdownloader.helper.HlsQualityHelper
import com.app.mtvdownloader.model.DownloadModel
import com.app.mtvdownloader.model.DownloadQuality

@Composable
fun ShowQualitySelector(
    context: Context,
    contentItem: DownloadModel,
    onDismiss: () -> Unit,
    onQualitySelected: (DownloadQuality) -> Unit
) {
    var qualities by remember { mutableStateOf<List<DownloadQuality>>(emptyList()) }
    var expanded by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        qualities = HlsQualityHelper.getHlsQualities(
            context,
            contentItem.hlsUrl!!
        )
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = {
            expanded = false
            onDismiss()
        }
    ) {
        qualities.forEach { quality ->
            DropdownMenuItem(
                text = { Text(quality.label) },
                onClick = {
                    expanded = false
                    onQualitySelected(quality)
                }
            )
        }
    }
}