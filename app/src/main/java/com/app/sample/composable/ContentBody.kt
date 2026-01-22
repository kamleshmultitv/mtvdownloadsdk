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
    // 🔥 IMPORTANT: no derivedStateOf here
    val contentList = remember(
        pagingItems.itemSnapshotList.items,
        overrideContent
    ) {
        buildPlayerContentList(
            context = context,
            pagingItems = pagingItems,
            overrideContent = overrideContent
        )
    }

    val downloadedContentList = remember {
        mutableStateListOf<DownloadedContentEntity>()
    }

    var showDownloadedList by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<DownloadedContentEntity?>(null) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.black))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // 🎬 SDK Video Player (KEYED)
            key(
                contentList,
                selectedIndex.intValue
            ) {
                MtvVideoPlayerSdk(
                    contentList = contentList,
                    index = selectedIndex.intValue,
                    pipListener = pipListener,
                    onPlayerBack = {  },
                    setFullScreen = onFullScreenChange,
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
                            Log.d("CLIENT", "Full screen: $isFullScreen")
                        }

                        override fun onAdStateChanged(isAdPlaying: Boolean) {
                            Log.d("CLIENT", "Ad playing = $isAdPlaying")
                        }
                    }
                )
            }

            // 📜 Content List
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

        // ➕ Floating Action Button
        if (!isFullScreen) {
            FloatButton { config ->

                if (config.url.isBlank()) {
                    // ✅ APPLY CONFIG TO EXISTING API CONTENT
                    onOverrideContent(
                        OverrideContent(
                            url = null,                 // 👈 IMPORTANT
                            drmToken = null,
                            isLive = false,
                            adsConfig = config.adsConfig,
                            skipIntro = config.skipIntro,
                            nextEpisode = config.nextEpisode
                        )
                    )
                } else {
                    // ✅ OVERRIDE CONTENT
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

        if (downloadedContentList.isNotEmpty()) {
            FloatingActionButton(
                onClick = {
                    showDownloadedList = true
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 32.dp, bottom = 160.dp),
                containerColor = colorResource(R.color.black),
                contentColor = colorResource(R.color.white),
                shape = androidx.compose.foundation.shape.CircleShape
            ) {
                // ✅ FAB content (ICON / TEXT REQUIRED)
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Downloads"
                )
            }
        }

        if (showDownloadedList) {
            DownloadedContentList(downloadContentList = downloadedContentList,
                onItemClick = { item ->
                    selectedItem = item
                },
                onBackClick = {
                    showDownloadedList = false
                })
        }

        selectedItem?.let { item ->
            DownloadPlayer(item,
                onBack = {
                    selectedItem = null
                })
        }
    }
}
