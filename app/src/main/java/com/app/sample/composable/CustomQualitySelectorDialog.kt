package com.app.sample.composable

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.app.mtvdownloader.model.DownloadQuality

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomQualitySelectorBottomSheet(
    qualities: List<DownloadQuality>,
    onDismiss: () -> Unit,
    onQualitySelected: (DownloadQuality) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column {

            Text(
                text = "Select Download Quality",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            qualities.forEach { quality ->
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
    }
}
