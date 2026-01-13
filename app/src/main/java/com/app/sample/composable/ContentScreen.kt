package com.app.sample.composable

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.app.sample.model.OverrideContent
import com.app.sample.viewModel.ContentViewModel
import com.app.videosdk.listener.PipListener

@Composable
fun ContentScreen(
    viewModel: ContentViewModel,
    pipListener: PipListener
) {
    val context = LocalContext.current
    val pagingItems = viewModel.contentListData.collectAsLazyPagingItems()

    val selectedIndex = remember { mutableIntStateOf(0) }
    var isFullScreen by remember { mutableStateOf(false) }

    var overrideContent by remember { mutableStateOf<OverrideContent?>(null) }

    LaunchedEffect(Unit) {
        viewModel.setContent()
    }

    when (pagingItems.loadState.refresh) {
        LoadState.Loading -> LoadingView()
        is LoadState.Error -> ErrorView()
        else -> ContentBody(
            context = context,
            pagingItems = pagingItems,
            selectedIndex = selectedIndex,
            overrideContent = overrideContent,
            pipListener = pipListener,
            isFullScreen = isFullScreen,
            onFullScreenChange = { isFullScreen = it },
            onOverrideContent = { overrideContent = it }
        )
    }
}

