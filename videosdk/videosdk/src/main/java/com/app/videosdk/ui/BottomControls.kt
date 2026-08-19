package com.app.videosdk.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import com.app.videosdk.model.PlayerModel
import com.app.videosdk.ui.sprite.SpriteThumbnail
import com.app.videosdk.ui.sprite.SpriteUtils

@Composable
fun BottomControls(
    modifier: Modifier = Modifier,
    playerModelList: List<PlayerModel>?,
    index: Int,
    isFullScreen: Boolean,
    currentPosition: Long,
    duration: Long,
    exoPlayer: ExoPlayer,
    onSeek: (Long) -> Unit,
    onNext: (Int) -> Unit,
    cuePoints: List<CuePoint> = emptyList(),
    onDragStateChange: (Boolean) -> Unit = {}

) {
    val model = playerModelList?.getOrNull(index)
    val isLive = model?.isLive ?: false

    /* ---------- SPRITE STATE ---------- */

    var isDragging by remember { mutableStateOf(false) }
    var previewMs by remember { mutableLongStateOf(0L) }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        /* ---------- SPRITE PREVIEW ---------- */

        if (!isLive && isDragging && duration > 0) {

            val posToShow =
                if (previewMs > 0) previewMs else exoPlayer.currentPosition.coerceAtLeast(0L)

            val width = 120.dp
            val height = width * (16f / 9f)

            val spriteUrl =
                model?.spriteUrl?.takeIf { it.isNotBlank() }
                    ?: SpriteUtils.spriteUrlForVideo(model?.spriteUrl.orEmpty())

            SpriteThumbnail(
                spriteUrl = spriteUrl,
                positionMs = posToShow,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .size(width, height)
            )
        }

        /* ---------- SEEK BAR ---------- */

        CustomSlider(
            currentPosition = currentPosition,
            duration = duration,
            cuePoints = cuePoints,
            isLive = isLive,
            exoPlayer = exoPlayer,
            showControls = {},

            onDragStateChange = { dragging ->
                isDragging = dragging
                onDragStateChange(dragging) // ✅ FIX

                if (!dragging && previewMs > 0) {
                    onSeek(previewMs)
                }
            },

            onPreviewChange = { targetMs ->
                previewMs = targetMs
            },

            onSeek = onSeek
        )


        /* ---------- BOTTOM ACTION BAR ---------- */

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            if (!isLive) {

                /* ---------- LEFT : NEXT ---------- */

                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (isFullScreen && playerModelList != null && playerModelList.size > 1) {
                        val isLastItem = index >= playerModelList.lastIndex

                        Row(
                            modifier = Modifier
                                .clickable(enabled = !isLastItem) {
                                    if (!isLastItem) onNext(index + 1)
                                }
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next Episode",
                                tint = if (isLastItem) Color.Gray else Color.White
                            )
                            Text(
                                text = "Next Ep.",
                                color = if (isLastItem) Color.Gray else Color.White
                            )
                        }
                    }
                }

                /* ---------- CENTER : EPISODES ---------- */

                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (isFullScreen && playerModelList != null && playerModelList.size > 1) {
                        SeasonSelector(
                            playerModelList = playerModelList,
                            exoPlayer = exoPlayer,
                            onShowControls = {},
                            pausePlayer = {},
                            playContent = onNext
                        )
                    }
                }

            } else {
                Spacer(modifier = Modifier.weight(2f))
            }

            Box(modifier = Modifier.weight(1f))
        }
    }
}

