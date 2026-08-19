package com.app.mtvdownloader.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.app.mtvdownloader.helper.HlsQualityHelper
import com.app.mtvdownloader.model.DownloadModel
import com.app.mtvdownloader.model.DownloadQuality
import com.app.mtvdownloader.utils.DownloadSourceResolver

@Composable
fun ShowQualitySelectorDialog(
    context: Context,
    contentItem: DownloadModel,
    onDismiss: () -> Unit,
    onQualitySelected: (DownloadQuality) -> Unit,
    qualities: List<DownloadQuality>? = null
) {
    var loadedQualities by remember { mutableStateOf(qualities.orEmpty()) }

    LaunchedEffect(contentItem, qualities) {
        if (qualities != null) {
            loadedQualities = qualities
            return@LaunchedEffect
        }

        val url = DownloadSourceResolver.resolve(contentItem)?.url
            ?: return@LaunchedEffect

        loadedQualities = HlsQualityHelper.getDownloadQualities(context, url)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Select Download Quality")
        },
        text = {
            Column {
                loadedQualities.forEach { quality ->
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            onQualitySelected(quality)
                            onDismiss()
                        }
                    ) {
                        Text(
                            text = quality.label,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Start
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
