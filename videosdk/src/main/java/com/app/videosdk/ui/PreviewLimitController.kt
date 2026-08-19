package com.app.videosdk.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import com.app.videosdk.model.FreePreviewConfig
import com.app.videosdk.model.FreePreviewEndConfig

@Composable
internal fun PreviewLimitController(
    player: Player?,
    currentPositionMs: Long,
    freePreview: FreePreviewConfig?,
    freePreviewEnd: FreePreviewEndConfig?,
    resetKey: Any?,
    onPreviewLimitReached: (showDialog: Boolean) -> Unit
) {
    var limitReached by remember(resetKey) { mutableStateOf(false) }

    LaunchedEffect(player, currentPositionMs, freePreview, freePreviewEnd, resetKey) {
        val activePlayer = player ?: return@LaunchedEffect
        val preview = freePreview ?: return@LaunchedEffect

        if (limitReached) return@LaunchedEffect
        if (!preview.enabled) return@LaunchedEffect

        val limitMs =
            when {
                preview.durationMs > 0L -> preview.durationMs
                freePreviewEnd?.durationMs != null && freePreviewEnd.durationMs > 0L ->
                    freePreviewEnd.durationMs

                else -> 0L
            }

        if (limitMs <= 0L) return@LaunchedEffect

        if (currentPositionMs >= limitMs) {
            limitReached = true
            activePlayer.pause()
            onPreviewLimitReached(freePreviewEnd?.enabled == true || preview.popupAllowed)
        }
    }
}
