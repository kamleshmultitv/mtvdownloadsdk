package com.app.sample.composable

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemsIndexed
import com.app.mtvdownloader.local.entity.DownloadedContentEntity
import com.app.sample.model.ContentItem

@Composable
fun ContentList(
    pagingItems: LazyPagingItems<ContentItem>,
    modifier: Modifier = Modifier,
    onItemClick: (Int) -> Unit,
    downloadContentList: (List<DownloadedContentEntity>) -> Unit
) {
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
                    downloadContentList = { list ->
                        downloadContentList(list)
                    }
                )
            }
        }
    }
}
