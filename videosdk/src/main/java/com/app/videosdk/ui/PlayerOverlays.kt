package com.app.videosdk.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.app.videosdk.model.FreePreviewConfig
import com.app.videosdk.model.FreePreviewEndConfig
import com.app.videosdk.model.WatermarkConfig
import com.app.videosdk.model.WatermarkPosition

@Composable
internal fun PreviewLimitDialog(
    freePreview: FreePreviewConfig?,
    freePreviewEnd: FreePreviewEndConfig?,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit,
    onDismiss: () -> Unit
) {
    val endConfig = freePreviewEnd?.takeIf { it.enabled }
    val message =
        if (endConfig != null) {
            endConfig.popupText
        } else {
            freePreview?.popupText
        }.orEmpty().ifBlank { "Preview ended" }

    val primaryLabel =
        if (endConfig != null) {
            endConfig.primaryButtonLabel
        } else {
            freePreview?.buttonLabel
        }.orEmpty().ifBlank { "Continue" }

    val secondaryLabel =
        if (endConfig != null) {
            endConfig.secondaryButtonLabel
        } else {
            null
        }?.takeIf { it.isNotBlank() }

    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Text(text = message)
        },
        confirmButton = {
            Button(onClick = onPrimary) {
                Text(text = primaryLabel)
            }
        },
        dismissButton = {
            secondaryLabel?.let { label ->
                Button(onClick = onSecondary) {
                    Text(text = label)
                }
            }
        }
    )
}

@Composable
internal fun BoxScope.LiveImagePlaceholder(liveImageUrl: String?) {
    val imageUrl = liveImageUrl?.takeIf { it.isNotBlank() } ?: return

    Image(
        painter = rememberAsyncImagePainter(imageUrl),
        contentDescription = "Live Placeholder",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
internal fun BoxScope.PlayerWatermarkOverlay(watermark: WatermarkConfig?) {
    val config = watermark?.takeIf { it.enabled } ?: return
    val imageUrl = config.imageUrl?.takeIf { it.isNotBlank() } ?: return

    Image(
        painter = rememberAsyncImagePainter(imageUrl),
        contentDescription = "Watermark",
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .align(config.position.toAlignment())
            .padding(16.dp)
            .sizeIn(
                minWidth = 72.dp,
                minHeight = 32.dp,
                maxWidth = 140.dp,
                maxHeight = 72.dp
            )
    )
}

private fun WatermarkPosition.toAlignment(): Alignment =
    when (this) {
        WatermarkPosition.TOP_LEFT -> Alignment.TopStart
        WatermarkPosition.TOP_RIGHT -> Alignment.TopEnd
        WatermarkPosition.BOTTOM_LEFT -> Alignment.BottomStart
        WatermarkPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
        WatermarkPosition.CENTER -> Alignment.Center
    }
