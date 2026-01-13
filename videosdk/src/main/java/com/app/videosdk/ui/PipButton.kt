package com.app.videosdk.ui

import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.app.videosdk.listener.PipListener

@Composable
fun PipButton(pipListener: PipListener? = null, isPipEnabled: (Boolean) -> Unit = {}) {
    IconButton(
        modifier = Modifier
            .wrapContentSize(),
        onClick = {
            isPipEnabled(true)
            pipListener?.onPipRequested()
        }
    ) {
        Icon(
            imageVector = Icons.Default.PictureInPicture,
            contentDescription = "Toggle Picture In Picture",
            tint = Color.White
        )
    }
}