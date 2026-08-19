package com.app.videosdk.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SkipNext
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
import com.app.videosdk.model.CuePoint
import com.app.videosdk.model.PlayerControlsConfig
import com.app.videosdk.model.PlayerModel
import com.app.videosdk.ui.sprite.SpriteThumbnail
import com.app.videosdk.utils.CastUtils

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
    onPrevious: (Int) -> Unit = {},
    cuePoints: List<CuePoint> = emptyList(),
    onDragStateChange: (Boolean) -> Unit = {},
    expandSheet: (Boolean) -> Unit = {},
    onSeekStarted: (Long) -> Unit = {},
    onSeekCompleted: (Long) -> Unit = {},
    controlsConfig: PlayerControlsConfig = PlayerControlsConfig(),
    showPreviousControl: Boolean = false,
    castUtils: CastUtils? = null,
    isCasting: Boolean = false
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

            model?.spriteUrl?.takeIf { it.isNotBlank() }?.let { spriteSource ->
                SpriteThumbnail(
                    spriteUrl = spriteSource,
                    positionMs = posToShow,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .size(width, height)
                )
            }
        }

        /* ---------- CHAPTER LABEL ---------- */

        /* ---------- SEEK BAR ---------- */

        CustomSlider(
            playerModel = model,
            currentPosition = currentPosition,
            duration = duration,
            cuePoints = cuePoints,
            isLive = isLive,
            exoPlayer = exoPlayer,
            showControls = {},

            onDragStateChange = { dragging ->
                isDragging = dragging
                onDragStateChange(dragging)

                if (!dragging) {
                    previewMs = 0L
                }
            },

            onPreviewChange = { targetMs ->
                previewMs = targetMs
            },

            onSeek = onSeek,
            onSeekStarted = onSeekStarted,
            onSeekCompleted = onSeekCompleted,
            chapters = model?.chapters ?: emptyList()
        )

        /* ---------- BOTTOM ACTION BAR (FULLSCREEN ONLY) ---------- */

        if (isFullScreen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                if (!isLive) {

                    /* ---------- LEFT : NEXT ---------- */

                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (controlsConfig.next &&
                            playerModelList != null &&
                            playerModelList.size > 1
                        ) {
                            val isLastItem = index >= playerModelList.lastIndex

                            Row(
                                modifier = Modifier
                                    .clickable(enabled = !isLastItem) {
                                        if (!isLastItem) onNext(index + 1)
                                    }
                                    .padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CustomIcon(
                                    resId = model?.customControls?.nextEpisodeIconRes,
                                    defaultIcon = Icons.Default.SkipNext,
                                    contentDescription = "Next Episode",
                                    modifier = Modifier.size(16.dp),
                                    tint = model?.customControls?.iconTintRes
                                )

                                Text(
                                    modifier = Modifier.padding(start = 4.dp),
                                    text = "Next Ep.",
                                    color = if (isLastItem) Color.Gray else Color.White
                                )
                            }
                        }
                    }

                    /* ---------- CENTER : EPISODES ---------- */

                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (controlsConfig.seasonSelector && playerModelList != null && playerModelList.size > 1) {
                            SeasonSelector(
                                playerModel = model,
                                exoPlayer = exoPlayer,
                                castUtils = castUtils,
                                isCasting = isCasting,
                                onShowControls = {},
                                pausePlayer = {},
                                expandSheet = { expandSheet(it) }
                            )
                        }
                    }

                } else {
                    Spacer(modifier = Modifier.weight(2f))
                }

                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    if (!isLive &&
                        showPreviousControl &&
                        controlsConfig.previous &&
                        playerModelList != null &&
                        playerModelList.size > 1
                    ) {
                        val isFirstItem = index <= 0

                        Row(
                            modifier = Modifier
                                .clickable(enabled = !isFirstItem) {
                                    if (!isFirstItem) onPrevious(index - 1)
                                }
                                .padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CustomIcon(
                                resId = null,
                                defaultIcon = Icons.Default.SkipPrevious,
                                contentDescription = "Previous Episode",
                                modifier = Modifier.size(16.dp),
                                tint = model?.customControls?.iconTintRes
                            )

                            Text(
                                modifier = Modifier.padding(start = 4.dp),
                                text = "Prev Ep.",
                                color = if (isFirstItem) Color.Gray else Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
