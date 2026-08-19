package com.app.videosdk.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.videosdk.listener.PipListener
import com.app.videosdk.model.PlayerControlsConfig
import com.app.videosdk.model.PlayerModel
import com.app.videosdk.utils.CastUtils

@Composable
fun TopBar(
    playerModel: PlayerModel? = null,
    isFullScreen: Boolean = false,
    context: Context,
    castUtils: CastUtils?,
    pipListener: PipListener?,
    controlsConfig: PlayerControlsConfig = PlayerControlsConfig(),
    showContentTitle: Boolean = true,
    isPipEnabled: (Boolean) -> Unit,
    onBackPressed: () -> Unit,
    onSettingsClick: () -> Unit,
    onFullScreenToggle: () -> Unit,
    onLockScreenToggle: () -> Unit,
    onChapterClick: () -> Unit,
    onCutClick: () -> Unit
) {
    val episodeTitle = playerModel?.episodeTitle?.takeIf { it.isNotBlank() }
    val seasonTitle = playerModel?.seasonTitle?.takeIf { it.isNotBlank() }
    val heading = episodeTitle
        ?: playerModel?.title?.takeIf { it.isNotBlank() }
        ?: seasonTitle
    val subheading = seasonTitle?.takeUnless { it == heading }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isFullScreen) {
                    Modifier.background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.70f),
                                Color.Black.copy(alpha = 0.35f),
                                Color.Transparent
                            )
                        )
                    )
                } else {
                    Modifier
                }
            )
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .then(if (isFullScreen) Modifier.statusBarsPadding() else Modifier)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        IconButton(onClick = onBackPressed) {
            CustomIcon(
                resId = playerModel?.customControls?.backIconRes,
                defaultIcon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = playerModel?.customControls?.iconTintRes
            )
        }

        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = 2.dp, end = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (showContentTitle) {
                Column {
                    heading?.let {
                        Text(
                            text = it,
                            color = Color.White,
                            fontSize = if (isFullScreen) 16.sp else 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (isFullScreen) {
                        subheading?.let {
                            Text(
                                text = it,
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }


        if (isFullScreen && playerModel?.isClipEnabled == true) {
            IconButton(onClick = onCutClick) {
                CustomIcon(
                    resId = null,
                    defaultIcon = Icons.Default.ContentCut,
                    contentDescription = "Cut",
                    modifier = Modifier.size(24.dp),
                    tint = playerModel.customControls?.iconTintRes
                )
            }
        }

        if (isFullScreen && playerModel?.isChapterEnabled == true) {
            IconButton(onClick = onChapterClick) {
                CustomIcon(
                    resId = null,
                    defaultIcon = Icons.Default.AutoStories,
                    contentDescription = "Chapter",
                    modifier = Modifier.size(24.dp),
                    tint = playerModel.customControls?.iconTintRes
                )
            }
        }


        if (isFullScreen && controlsConfig.cast && castUtils != null) {
            CastButton(
                resId = playerModel?.customControls?.castIconRes,
                connectedResId = playerModel?.customControls?.castConnectedIconRes,
                tint = playerModel?.customControls?.iconTintRes
            )
        }

        // PIP and Settings remain visible only in FullScreen (VOD context)
        if (isFullScreen) {
            if (controlsConfig.pip) {
                PipButton(
                    playerModel,
                    pipListener = pipListener,
                    isPipEnabled = isPipEnabled
                )
            }

            if (controlsConfig.settings) {
                IconButton(onClick = onSettingsClick) {
                    CustomIcon(
                        resId = playerModel?.customControls?.settingsIconRes,
                        defaultIcon = Icons.Default.Settings,
                        contentDescription = "Settings",
                        modifier = Modifier.size(24.dp),
                        tint = playerModel?.customControls?.iconTintRes
                    )
                }
            }
        }

        if (isFullScreen) {
            IconButton(onClick = onLockScreenToggle) {
                CustomIcon(
                    resId = playerModel?.customControls?.lockIconRes,
                    defaultIcon = Icons.Default.Lock,
                    contentDescription = "Toggle Lock Screen",
                    modifier = Modifier.size(24.dp),
                    tint = playerModel?.customControls?.iconTintRes
                )
            }
        }

        // FIXED: Moved Fullscreen button OUTSIDE the if(isFullScreen) block 
        // to make it visible in Portrait mode.
        val showFullScreenButton =
            if (isFullScreen) controlsConfig.exitFullscreen else controlsConfig.fullscreen

        if (showFullScreenButton) {
            IconButton(
                onClick = onFullScreenToggle,
                modifier = Modifier.size(56.dp)
            ) {
                CustomIcon(
                    resId = if (isFullScreen) playerModel?.customControls?.exitFullScreenIconRes else playerModel?.customControls?.fullScreenIconRes,
                    defaultIcon = if (isFullScreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = "Toggle Fullscreen",
                    modifier = Modifier.size(24.dp),
                    tint = playerModel?.customControls?.iconTintRes
                )
            }
        }
    }
}
