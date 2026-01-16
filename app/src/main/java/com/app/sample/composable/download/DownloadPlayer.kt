package com.app.sample.composable.download

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import com.app.mtvdownloader.local.entity.DownloadedContentEntity
import com.app.sample.R
import com.app.sample.utils.FileUtils.buildContentListFromDownloaded
import com.app.videosdk.listener.PlayerStateListener
import com.app.videosdk.ui.MtvVideoPlayerSdk

@Composable
fun DownloadPlayer(
    downloadedContentEntity: DownloadedContentEntity,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.ic_launcher_background))
    ) {
        MtvVideoPlayerSdk(
            contentList = buildContentListFromDownloaded(downloadedContentEntity),
            index = 0,
            startInFullScreen = true,
            onPlayerBack = {},
            setFullScreen = {  },
            playerStateListener = object : PlayerStateListener {

                override fun onPlayerReady(durationMs: Long) {
                    Log.d("CLIENT", "Player ready: $durationMs")
                }

                override fun onPlayStateChanged(isPlaying: Boolean) {
                    Log.d("CLIENT", "Playing: $isPlaying")
                }

                override fun onPlaybackCompleted() {
                    Log.d("CLIENT", "Playback completed")
                }

                override fun onFullScreenChanged(isFullScreen: Boolean) {
                    if (!isFullScreen) {
                        onBack()
                    }
                    Log.d("CLIENT", "Full screen: $isFullScreen")
                }

                override fun onAdStateChanged(isAdPlaying: Boolean) {
                    Log.d("CLIENT", "Ad playing = $isAdPlaying")
                }
            }
        )
    }
}
