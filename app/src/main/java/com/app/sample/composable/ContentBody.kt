package com.app.sample.composable

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import com.app.mtvdownloader.local.entity.DownloadedContentEntity
import com.app.sample.R
import com.app.sample.composable.download.DownloadPlayer
import com.app.sample.composable.download.DownloadedContentList
import com.app.sample.model.ContentItem
import com.app.sample.model.OverrideContent
import com.app.sample.utils.FileUtils.buildPlayerContentList
import com.app.videosdk.listener.PipListener
import com.app.videosdk.listener.PlayerStateListener
import com.app.videosdk.ui.MtvVideoPlayerSdk

@Composable
fun ContentBody(
    context: Context,
    pagingItems: LazyPagingItems<ContentItem>,
    selectedIndex: MutableIntState,
    overrideContent: OverrideContent?,
    pipListener: PipListener,
    isFullScreen: Boolean,
    onFullScreenChange: (Boolean) -> Unit,
    onOverrideContent: (OverrideContent?) -> Unit
) {

    /* ---------------------------------------------------------
     * ✅ STABLE CONTENT LIST
     * Rebuild ONLY when overrideContent changes
     * --------------------------------------------------------- */
    val contentList = remember(overrideContent) {
        buildPlayerContentList(
            context = context,
            pagingItems = pagingItems,
            overrideContent = overrideContent
        )
    }

    /* ---------------------------------------------------------
     * ✅ DOWNLOADED CONTENT STATE
     * --------------------------------------------------------- */
    val downloadedContentList = remember {
        mutableStateListOf<DownloadedContentEntity>()
    }

    var showDownloadedList by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<DownloadedContentEntity?>(null) }

    /* ---------------------------------------------------------
     * ✅ STABLE PLAYER KEY
     * Player recreates ONLY when real playback content changes
     * --------------------------------------------------------- */
    val playerKey = remember(
        overrideContent?.url,
        overrideContent?.drmToken,
        selectedIndex.intValue
    ) {
        "${overrideContent?.url ?: "list"}_${selectedIndex.intValue}"
    }

    /* ---------------------------------------------------------
     * ✅ STABLE LISTENER (VERY IMPORTANT)
     * --------------------------------------------------------- */
    val playerStateListener = remember {
        object : PlayerStateListener {

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
                Log.d("CLIENT", "Full screen: $isFullScreen")
            }

            override fun onAdStateChanged(isAdPlaying: Boolean) {
                Log.d("CLIENT", "Ad playing = $isAdPlaying")
            }
        }
    }

    /* ---------------------------------------------------------
     * ✅ UI
     * --------------------------------------------------------- */
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.black))
    ) {

        Column(modifier = Modifier.fillMaxSize()) {

            /* ---------------------------------------------------------
             * 🎬 VIDEO PLAYER (KEYED CORRECTLY)
             * --------------------------------------------------------- */
            key(playerKey) {
                Log.d("PLAYER", "MtvVideoPlayerSdk COMPOSED")

                MtvVideoPlayerSdk(
                    contentList = contentList,
                    index = selectedIndex.intValue,
                    pipListener = pipListener,
                    onPlayerBack = {},
                    setFullScreen = onFullScreenChange,
                    playerStateListener = playerStateListener
                )
            }

            /* ---------------------------------------------------------
             * 📜 CONTENT LIST
             * --------------------------------------------------------- */
            ContentList(
                pagingItems = pagingItems,
                onItemClick = { index ->
                    selectedIndex.intValue = index
                    onOverrideContent(null)
                },
                downloadContentList = { list ->
                    downloadedContentList.clear()
                    downloadedContentList.addAll(list)
                }
            )
        }

        /* ---------------------------------------------------------
         * ➕ FLOATING BUTTON (OVERRIDE CONTENT)
         * --------------------------------------------------------- */
        if (!isFullScreen) {
            FloatButton { config ->

                if (config.url.isBlank()) {
                    onOverrideContent(
                        OverrideContent(
                            url = null,
                            drmToken = null,
                            isLive = false,
                            adsConfig = config.adsConfig,
                            skipIntro = config.skipIntro,
                            nextEpisode = config.nextEpisode
                        )
                    )
                } else {
                    selectedIndex.intValue = 0
                    onOverrideContent(
                        OverrideContent(
                            url = config.url,
                            drmToken = config.drmToken,
                            isLive = config.isLive,
                            adsConfig = config.adsConfig,
                            skipIntro = config.skipIntro,
                            nextEpisode = config.nextEpisode
                        )
                    )
                }
            }
        }

        /* ---------------------------------------------------------
         * ⬇️ DOWNLOADED CONTENT FAB
         * --------------------------------------------------------- */
        if (downloadedContentList.isNotEmpty()) {
            FloatingActionButton(
                onClick = { showDownloadedList = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 32.dp, bottom = 160.dp),
                containerColor = colorResource(R.color.black),
                contentColor = colorResource(R.color.white),
                shape = androidx.compose.foundation.shape.CircleShape
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Downloads"
                )
            }
        }

        /* ---------------------------------------------------------
         * 📂 DOWNLOADED LIST
         * --------------------------------------------------------- */
        if (showDownloadedList) {
            DownloadedContentList(
                downloadContentList = downloadedContentList,
                onItemClick = { item ->
                    selectedItem = item
                },
                onBackClick = {
                    showDownloadedList = false
                }
            )
        }

        /* ---------------------------------------------------------
         * ▶️ DOWNLOAD PLAYER
         * --------------------------------------------------------- */
        selectedItem?.let { item ->
            DownloadPlayer(
                item,
                onBack = {
                    selectedItem = null
                }
            )
        }
    }
}

