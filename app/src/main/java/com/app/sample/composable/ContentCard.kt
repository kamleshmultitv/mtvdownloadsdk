package com.app.sample.composable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.mtvdownloader.ui.DownloadButton
import com.app.sample.R
import com.app.sample.model.ContentItem
import com.app.sample.utils.FileUtils.buildDownloadContentList

@Composable
fun ContentCard(
    content: ContentItem?,
    playContent: () -> Unit,
) {
    val context = LocalContext.current

    val downloadModel = remember(content) {
        buildDownloadContentList(context, content)
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

        DownloadButton(
            context = context,
            contentItem = downloadModel,
            modifier = Modifier.size(40.dp)
        )
    }
}


