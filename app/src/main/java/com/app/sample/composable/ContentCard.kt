package com.app.sample.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.mtvdownloader.helper.ReelDownloadHelper
import com.app.mtvdownloader.viewmodel.DownloadViewModel
import com.app.mtvdownloader.worker.DownloadWorker
import com.app.sample.R
import com.app.sample.model.ContentItem
import com.app.sample.utils.FileUtils.buildDownloadContentList

@Composable
fun ContentCard(
    content: ContentItem?,
    playContent: () -> Unit,
    downloadViewModel: DownloadViewModel
) {
    val context = LocalContext.current

    val downloadModel = remember(content) {
        buildDownloadContentList(context, content)
    }

    val downloadState by downloadViewModel
        .observeDownload(content?.id.orEmpty())
        .collectAsState(initial = null)

    val status = downloadState?.downloadStatus
    val progress = (downloadState?.downloadProgress ?: 0) / 100f

    val iconRes = remember(status) {
        when (status) {
            DownloadWorker.DOWNLOAD_STATUS_QUEUED ->
                R.drawable.ic_downlaod_queue
            DownloadWorker.DOWNLOAD_STATUS_COMPLETED ->
                R.drawable.ic_download_done
            else ->
                R.drawable.ic_download
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Text(
            modifier = Modifier
                .weight(1f)
                .clickable { playContent() },
            text = content?.title.orEmpty(),
            fontSize = 16.sp,
            color = colorResource(id = R.color.white),
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {

            if (status == DownloadWorker.DOWNLOAD_STATUS_DOWNLOADING) {
                CircularProgressIndicator(
                    progress = progress,
                    strokeWidth = 2.dp,
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White
                )
            }

            IconButton(
                onClick = {
                  //  if (status == null) {
                        downloadModel?.let {
                            ReelDownloadHelper.handleDownloadClick(
                                context = context,
                                contentItem = it
                            )
                        }
                   /* } else {
                        Toast.makeText(
                            context,
                            status,
                            Toast.LENGTH_SHORT
                        ).show()
                    }*/
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = "Download",
                    tint = Color.White
                )
            }
        }
    }
}

