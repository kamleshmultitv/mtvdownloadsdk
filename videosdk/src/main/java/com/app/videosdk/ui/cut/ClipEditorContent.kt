package com.app.videosdk.ui.cut

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.app.videosdk.utils.PlayerUtils

@Composable
fun ClipEditorContent(
    contentId: String? = null,
    url: String? = null,
    duration: Long
) {

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {

        Text("Select clip (30s - 2m)")

        Spacer(modifier = Modifier.height(12.dp))

        var startMs by remember { mutableLongStateOf(0L) }
        var endMs by remember { mutableLongStateOf(120_000L) }
        var totalClipDuration by remember { mutableLongStateOf(120_000L) }

        YoutubeStyleTrimBar(
            duration = duration,
            modifier = Modifier.fillMaxWidth()
        ) { start, end, total ->   // ✅ FIXED (3 params)
            startMs = start
            endMs = end
            totalClipDuration = total
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                val shareUrl = PlayerUtils.createShareUrl(
                    contentId = contentId,
                    url = url,
                    clipStart = startMs,
                    clipEnd = endMs,
                    totalClipDuration = totalClipDuration
                )
                PlayerUtils.shareLink(context, shareUrl)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Share Clip")
        }
    }
}