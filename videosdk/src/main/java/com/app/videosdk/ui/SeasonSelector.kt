package com.app.videosdk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.ExoPlayer
import com.app.videosdk.model.PlayerModel
import com.app.videosdk.utils.CastUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonSelector(
    playerModel: PlayerModel? = null,
    exoPlayer: ExoPlayer,
    castUtils: CastUtils? = null,
    isCasting: Boolean = false,
    onShowControls: (Boolean) -> Unit,
    pausePlayer: (Boolean) -> Unit,
    expandSheet: (Boolean) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState()
    var showSheet by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val castIsActive = isCasting || castUtils?.castState?.value?.isCasting == true

    Box(
        modifier = Modifier
            .wrapContentSize()
            .background(color = Color.Transparent)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        if (dragAmount < -50) { // Swipe up → Open Bottom Sheet
                            coroutineScope.launch {
                                showSheet = true
                                sheetState.show()
                                exoPlayer.pause()
                                onShowControls(true)
                                pausePlayer(true)
                            }
                        } else if (dragAmount > 50 && showSheet) { // Swipe down → Close Bottom Sheet
                            coroutineScope.launch {
                                sheetState.hide()
                                showSheet = false
                                if (!castIsActive) {
                                    exoPlayer.play()
                                }
                                onShowControls(false)
                                pausePlayer(false)
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.clickable {
                coroutineScope.launch {
                    showSheet = true
                    sheetState.show()
                    exoPlayer.pause()
                    onShowControls(true)
                    pausePlayer(true)
                    expandSheet(true)
                }
            }
        ) {
            CustomIcon(
                resId = playerModel?.customControls?.seasonSelectorIconRes,
                defaultIcon = Icons.Default.KeyboardDoubleArrowUp,
                contentDescription = "Episodes",
                modifier = Modifier.size(16.dp),
                tint = playerModel?.customControls?.iconTintRes
            )
            Text(
                text = "Episodes",
                color = Color.White,
                fontSize = 14.sp
            )
        }
    }
}
