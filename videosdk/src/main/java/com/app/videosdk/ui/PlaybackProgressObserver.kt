package com.app.videosdk.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.app.videosdk.utils.CastUtils
import kotlinx.coroutines.delay

@Composable
internal fun PlaybackProgressObserver(
    exoPlayer: ExoPlayer?,
    castUtils: CastUtils?,
    isCasting: Boolean,
    resetKey: Any?,
    onProgressChanged: (PlaybackProgressState) -> Unit
) {
    val currentOnProgressChanged by rememberUpdatedState(onProgressChanged)

    DisposableEffect(exoPlayer, isCasting) {
        val player = exoPlayer ?: return@DisposableEffect onDispose {}

        fun publishPlayerProgress() {
            currentOnProgressChanged(player.toProgressState())
        }

        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                publishPlayerProgress()
            }
        }

        player.addListener(listener)
        publishPlayerProgress()

        onDispose {
            player.removeListener(listener)
        }
    }

    LaunchedEffect(exoPlayer, castUtils, isCasting, resetKey) {
        val player = exoPlayer ?: return@LaunchedEffect

        while (true) {
            currentOnProgressChanged(
                if (isCasting && castUtils != null) {
                    PlaybackProgressState(
                        positionMs = castUtils.getCastPosition(),
                        durationMs = castUtils.getCastDuration(),
                        isPlaying = true,
                        playbackState = player.playbackState
                    )
                } else {
                    player.toProgressState()
                }
            )

            delay(if (player.isPlaying || isCasting) 250L else 1_000L)
        }
    }
}

private fun Player.toProgressState(): PlaybackProgressState =
    PlaybackProgressState(
        positionMs = currentPosition.coerceAtLeast(0L),
        durationMs = duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L,
        isPlaying = isPlaying,
        playbackState = playbackState
    )
