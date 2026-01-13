package com.app.sample.composable

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.paging.compose.LazyPagingItems
import com.app.sample.R
import com.app.sample.model.ContentItem
import com.app.sample.model.OverrideContent
import com.app.sample.utils.FileUtils.buildPlayerContentList
import com.app.videosdk.listener.PipListener
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.black))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // 🎬 SDK Video Player (KEYED)
            androidx.compose.runtime.key(
                contentList,
                selectedIndex.intValue
            ) {
                MtvVideoPlayerSdk(
                    contentList = contentList,
                    index = selectedIndex.intValue,
                    pipListener = pipListener,
                    onPlayerBack = {},
                    setFullScreen = onFullScreenChange
                )
            }

            // 📜 Content List
            ContentList(
                pagingItems = pagingItems,
                onItemClick = { index ->
                    selectedIndex.intValue = index
                    onOverrideContent(null)
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
    }
}
