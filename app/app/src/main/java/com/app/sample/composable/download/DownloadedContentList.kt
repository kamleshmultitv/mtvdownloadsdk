package com.app.sample.composable.download

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import com.app.mtvdownloader.local.entity.DownloadedContentEntity
import com.app.sample.R

@Composable
fun DownloadedContentList(
    downloadContentList: SnapshotStateList<DownloadedContentEntity>,
    onItemClick: (DownloadedContentEntity) -> Unit,
    onBackClick: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorResource(R.color.ic_launcher_background))
    ) {

        // ✅ Action Bar
        DownloadedContentTopBar(
            onBackClick = onBackClick
        )

        // ✅ List
        LazyColumn {
            items(downloadContentList, key = { it.contentId }) { item ->
                DownloadedContentRow(
                    item = item,
                    onItemClick = {
                        onItemClick(item)

                    }
                )
            }
        }
    }
}