package com.app.sample.composable.download

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import com.app.mtvdownloader.local.entity.DownloadedContentEntity
import com.app.mtvdownloader.repository.DownloadRepository
import com.app.sample.AppClass
import com.app.sample.R
import com.app.sample.utils.FileUtils.buildContentListFromDownloaded
import com.app.videosdk.listener.PlayerStateListener
import com.app.videosdk.ui.MtvVideoPlayerSdk
import okhttp3.internal.platform.PlatformRegistry.applicationContext

@Composable
fun DownloadPlayer(
    downloadedContentEntity: DownloadedContentEntity,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val contentList = buildContentListFromDownloaded(downloadedContentEntity)
    
    // ✅ DEBUG: Log offline playback setup
    Log.d("DownloadPlayer", "=== OFFLINE PLAYBACK DEBUG ===")
    Log.d("DownloadPlayer", "ContentId: ${downloadedContentEntity.contentId}")
    Log.d("DownloadPlayer", "DownloadStatus: ${downloadedContentEntity.downloadStatus}")
    Log.d("DownloadPlayer", "ContentUrl: ${downloadedContentEntity.contentUrl}")
    Log.d("DownloadPlayer", "LicenseUri: ${downloadedContentEntity.licenseUri}")
    Log.d("DownloadPlayer", "DRM: ${contentList.firstOrNull()?.drm}")
    Log.d("DownloadPlayer", "HasCacheFactory: ${contentList.firstOrNull()?.cacheFactory != null}")
    Log.d("DownloadPlayer", "MPD URL: ${contentList.firstOrNull()?.mpdUrl}")
    Log.d("DownloadPlayer", "HLS URL: ${contentList.firstOrNull()?.hlsUrl}")
    
    // ✅ Verify download is completed
    if (downloadedContentEntity.downloadStatus != "completed") {
        Log.w("DownloadPlayer", "⚠️ WARNING: Download status is '${downloadedContentEntity.downloadStatus}', not 'completed'. Playback may fail.")
    }
    
    Log.d("DownloadPlayer", "=============================")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.ic_launcher_background))
    ) {
        MtvVideoPlayerSdk(
            contentList = contentList,
            index = 0,
            startInFullScreen = true,
            onPlayerBack = {},
            setFullScreen = {  },
            playerStateListener = object : PlayerStateListener {

                override fun onPlayerReady(durationMs: Long) {
                    Log.d("DownloadPlayer", "✅ Player ready: $durationMs ms")
                }

                override fun onPlayStateChanged(isPlaying: Boolean) {
                    Log.d("DownloadPlayer", "▶️ Playing: $isPlaying")
                }

                override fun onPlaybackCompleted() {
                    Log.d("DownloadPlayer", "✅ Playback completed")
                }

                override fun onFullScreenChanged(isFullScreen: Boolean) {
                    if (!isFullScreen) {
                        onBack()
                    }
                    Log.d("DownloadPlayer", "📱 Full screen: $isFullScreen")
                }

                override fun onAdStateChanged(isAdPlaying: Boolean) {
                    Log.d("DownloadPlayer", "📺 Ad playing = $isAdPlaying")
                }
            }
        )
    }
}
