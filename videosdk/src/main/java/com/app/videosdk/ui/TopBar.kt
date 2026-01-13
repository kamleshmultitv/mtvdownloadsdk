package com.app.videosdk.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.app.videosdk.listener.PipListener
import com.app.videosdk.utils.CastUtils

@Composable
fun TopBar(
    title: String,
    isFullScreen: Boolean,
    context: android.content.Context,
    castUtils: CastUtils,
    pipListener: PipListener?,
    isPipEnabled: (Boolean) -> Unit,
    onBackPressed: () -> Unit,
    onSettingsClick: () -> Unit,
    onFullScreenToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        IconButton(onClick = onBackPressed) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }

        Text(
            text = title,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (castUtils.isCastTVAvailable(context) && context is FragmentActivity) {
            CastButton()
        }

        // PIP and Settings remain visible only in FullScreen (VOD context)
        if (isFullScreen) {
            PipButton(
                pipListener = pipListener,
                isPipEnabled = isPipEnabled
            )

            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White
                )
            }
        }

        // FIXED: Moved Fullscreen button OUTSIDE the if(isFullScreen) block 
        // to make it visible in Portrait mode.
        IconButton(onClick = onFullScreenToggle) {
            Icon(
                imageVector = if (isFullScreen)
                    Icons.Default.FullscreenExit
                else
                    Icons.Default.Fullscreen,
                contentDescription = "Toggle Fullscreen",
                tint = Color.White
            )
        }
    }
}
