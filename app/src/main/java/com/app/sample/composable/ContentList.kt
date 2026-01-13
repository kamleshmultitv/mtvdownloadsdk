package com.app.sample.composable

import android.app.Application
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemsIndexed
import com.app.mtvdownloader.viewmodel.DownloadViewModel
import com.app.sample.model.ContentItem

@Composable
fun ContentList(
    pagingItems: LazyPagingItems<ContentItem>,
    modifier: Modifier = Modifier,
    onItemClick: (Int) -> Unit
) {

    val context = LocalContext.current

    // ✅ Create ViewModel ONCE per screen
    val downloadViewModel = remember {
        DownloadViewModel(context.applicationContext as Application)
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth()
    ) {
        itemsIndexed(
            items = pagingItems,
            key = { _, item -> item.id ?: "" }
        ) { index, item ->
            item?.let {
                ContentCard(
                    content = it,
                    playContent = {
                        onItemClick(index)
                    },
                    downloadViewModel = downloadViewModel
                )
            }
        }
    }
}
