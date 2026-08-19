package com.app.videosdk.listener

import androidx.media3.common.Player
import com.app.videosdk.model.PlayerModel

class PlayerController {

    var exoPlayer: Player? = null

    private var playRequestListener: ((PlayerModel, Long) -> Unit)? = null
    private var pendingVideo: PlayerModel? = null
    private var playRequestId = 0L

    /**
     * Plays a complete SDK video model. The player reads metadata such as the age
     * classification directly from [video]; no separate metadata setter is needed.
     */
    fun play(video: PlayerModel) {
        pendingVideo = video
        playRequestId++
        playRequestListener?.invoke(video, playRequestId)
    }

    internal fun attachPlayRequestListener(listener: (PlayerModel, Long) -> Unit) {
        playRequestListener = listener
        pendingVideo?.let { listener(it, playRequestId) }
    }

    internal fun detachPlayRequestListener(listener: (PlayerModel, Long) -> Unit) {
        if (playRequestListener === listener) playRequestListener = null
    }

    fun togglePlayPause() {
        exoPlayer?.let {
            it.playWhenReady = !it.isPlaying
        }
    }

    fun toggleMute() {
        exoPlayer?.let {
            it.volume = if (it.volume > 0f) 0f else 1f
        }
    }

    fun isPlaying(): Boolean = exoPlayer?.isPlaying ?: false

    fun isMuted(): Boolean = (exoPlayer?.volume ?: 1f) == 0f
}
