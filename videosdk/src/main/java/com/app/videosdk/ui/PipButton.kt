package com.app.videosdk.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.app.videosdk.listener.PipListener
import com.app.videosdk.model.PlayerModel

@Composable
fun PipButton(
    playerModel: PlayerModel? = null,
    pipListener: PipListener? = null, isPipEnabled: (Boolean) -> Unit = {}) {
    IconButton(
        modifier = Modifier
            .wrapContentSize(),
        onClick = {
            isPipEnabled(true)
            pipListener?.onPipRequested(true)
        }
    ) {
        CustomIcon(resId = playerModel?.customControls?.pipIconRes,
            defaultIcon = Icons.Default.PictureInPicture,
            contentDescription = "Toggle Picture In Picture",
            modifier = Modifier.size(24.dp),
            tint = playerModel?.customControls?.iconTintRes)
    }
}