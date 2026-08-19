package com.app.videosdk.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import com.app.videosdk.listener.PlayerStateListener
import com.app.videosdk.model.OptionItemModel
import com.app.videosdk.model.PlayerAnalyticsEventType
import com.app.videosdk.model.PlayerDiagnosticSeverity
import com.app.videosdk.model.PlayerModel
import com.app.videosdk.model.SpeedControlModel
import com.app.videosdk.model.SubTitleModel
import com.app.videosdk.model.VideoQualityModel
import com.app.videosdk.player.TrackSelectionUtils.calculatePitch
import com.app.videosdk.player.TrackSelectionUtils.changeVideoResolution
import com.app.videosdk.player.TrackSelectionUtils.getTextTrackOptions
import com.app.videosdk.player.TrackSelectionUtils.getVideoFormats
import com.app.videosdk.player.TrackSelectionUtils.selectAudioTrack
import com.app.videosdk.player.TrackSelectionUtils.selectTextTrack
import com.app.videosdk.player.TrackSelectionUtils.setAutoVideoResolution
import com.app.videosdk.player.TrackSelectionUtils.showAudioTrack
import com.app.videosdk.utils.emitAnalytics
import com.app.videosdk.utils.emitDiagnostic
import com.app.videosdk.viewmodel.VideoViewModel

@Composable
fun SelectorHeader(
    playerModel: PlayerModel? = null,
    exoPlayer: ExoPlayer?,
    subtitlesEnabled: Boolean = true,
    playbackSpeedEnabled: Boolean = true,
    qualitySelectionEnabled: Boolean = true,
    contentId: String? = null,
    analyticsEnabled: Boolean = false,
    diagnosticsEnabled: Boolean = false,
    playerStateListener: PlayerStateListener? = null,
    selectedItems: MutableMap<Int, Int>,
    closeOptionCard: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: VideoViewModel = viewModel()
    val options by viewModel.options.collectAsState()
    val availableOptions = remember(
        options,
        subtitlesEnabled,
        playbackSpeedEnabled,
        qualitySelectionEnabled
    ) {
        options.filter { option ->
            when (option.id) {
                2 -> subtitlesEnabled
                3 -> playbackSpeedEnabled
                4 -> qualitySelectionEnabled
                else -> true
            }
        }
    }
    var selectedOption by remember(availableOptions) {
        mutableStateOf(availableOptions.firstOrNull()?.id)
    }
    var captionOptions by remember(exoPlayer) { mutableStateOf(getTextTrackOptions(exoPlayer)) }

    DisposableEffect(exoPlayer) {
        val player = exoPlayer ?: return@DisposableEffect onDispose {}

        captionOptions = getTextTrackOptions(player)

        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                captionOptions = getTextTrackOptions(player)
            }
        }

        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Intercept Back button to close the header and resume video
    BackHandler(enabled = true) {
        exoPlayer?.play()
        closeOptionCard(false)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 24.dp, end = 24.dp)
            .background(Color.Black.copy(alpha = 0.9f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(availableOptions.size) { index ->
                    val option = availableOptions[index]
                    OptionItem(
                        option = option,
                        isSelected = selectedOption == option.id
                    ) { clickedId ->
                        selectedOption = clickedId
                    }
                }
            }

            IconButton(
                onClick = {
                    exoPlayer?.play()
                    closeOptionCard(false)
                }
            ) {
                CustomIcon(
                    resId = playerModel?.customControls?.crossFadeIconRes,
                    defaultIcon = Icons.Default.Close,
                    contentDescription = "Close",
                    modifier = Modifier.size(24.dp),
                    tint = playerModel?.customControls?.iconTintRes
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            thickness = 1.dp,
            color = Color.Gray
        )

        Box(modifier = Modifier
            .fillMaxSize()
            .padding(top = 16.dp)) {
            val activeOption = selectedOption
            when (activeOption) {
                1 -> {
                    val audioTrackList =
                        remember(context, exoPlayer) { showAudioTrack(context, exoPlayer) }
                    SelectionList(
                        items = audioTrackList.map { it.name.toString() },
                        selectedIndex = selectedItems[activeOption]
                            ?: selectedAudioTrackIndex(audioTrackList, exoPlayer)
                    ) { index ->
                        selectedItems[activeOption] = index
                        selectAudioTrack(audioTrackList[index].id.toString(), exoPlayer)
                    }
                }

                2 -> {
                    val selectedCaptionIndex = selectedItems[activeOption]?.takeIf {
                        it in captionOptions.indices
                    }
                        ?: captionOptions.indexOfFirst { it.isSelected }.takeIf { it >= 0 }
                        ?: 0

                    SelectionList(
                        items = captionOptions.map { it.displayName },
                        selectedIndex = selectedCaptionIndex
                    ) { index ->
                        selectedItems[activeOption] = index
                        selectTextTrack(captionOptions[index], exoPlayer)
                        val selectedCaption = captionOptions[index]
                        playerStateListener?.onSubtitleChanged(
                            selectedCaption.language,
                            selectedCaption.displayName,
                            enabled = !selectedCaption.isOff
                        )
                        playerStateListener.emitAnalytics(
                            enabled = analyticsEnabled,
                            type = PlayerAnalyticsEventType.SUBTITLE_CHANGED,
                            contentId = contentId,
                            positionMs = exoPlayer?.currentPosition ?: 0L,
                            durationMs = exoPlayer?.duration ?: 0L,
                            attributes = mapOf(
                                "language" to selectedCaption.language.orEmpty(),
                                "label" to selectedCaption.displayName,
                                "enabled" to (!selectedCaption.isOff).toString()
                            )
                        )
                    }
                }

                3 -> {
                    viewModel.getSpeedData()
                    val speedData by viewModel.speedControlData.observeAsState(emptyList())

                    SelectionList(
                        items = speedData.map { it.speedTitle },
                        selectedIndex = selectedItems[activeOption]
                            ?: selectedSpeedIndex(speedData, exoPlayer)
                    ) { index ->
                        selectedItems[activeOption] = index
                        val param = PlaybackParameters(
                            speedData[index].speed,
                            calculatePitch(speedData[index].speed)
                        )
                        exoPlayer?.playbackParameters = param
                        playerStateListener?.onPlaybackSpeedChanged(speedData[index].speed)
                        playerStateListener.emitAnalytics(
                            enabled = analyticsEnabled,
                            type = PlayerAnalyticsEventType.PLAYBACK_SPEED_CHANGED,
                            contentId = contentId,
                            positionMs = exoPlayer?.currentPosition ?: 0L,
                            durationMs = exoPlayer?.duration ?: 0L,
                            attributes = mapOf("speed" to speedData[index].speed.toString())
                        )
                    }
                }

                4 -> {
                    val qualityList = remember(exoPlayer) { getVideoFormats(exoPlayer) }

                    SelectionList(
                        items = qualityList.map { if (it.id == "auto") it.title.toString() else "${it.title}p" },
                        selectedIndex = selectedItems[activeOption]
                            ?: selectedQualityIndex(qualityList, exoPlayer)
                    ) { index ->
                        selectedItems[activeOption] = index
                        if (qualityList[index].id == "auto") {
                            setAutoVideoResolution(exoPlayer)
                            playerStateListener?.onQualityChanged(0, 0, "Auto")
                            playerStateListener.emitAnalytics(
                                enabled = analyticsEnabled,
                                type = PlayerAnalyticsEventType.QUALITY_CHANGED,
                                contentId = contentId,
                                positionMs = exoPlayer?.currentPosition ?: 0L,
                                durationMs = exoPlayer?.duration ?: 0L,
                                attributes = mapOf("label" to "Auto")
                            )
                        } else {
                            changeVideoResolution(
                                exoPlayer,
                                qualityList[index].width,
                                qualityList[index].height
                            )
                            playerStateListener?.onQualityChanged(
                                qualityList[index].width,
                                qualityList[index].height,
                                qualityList[index].title.toString()
                            )
                            playerStateListener.emitAnalytics(
                                enabled = analyticsEnabled,
                                type = PlayerAnalyticsEventType.QUALITY_CHANGED,
                                contentId = contentId,
                                positionMs = exoPlayer?.currentPosition ?: 0L,
                                durationMs = exoPlayer?.duration ?: 0L,
                                attributes = mapOf(
                                    "width" to qualityList[index].width.toString(),
                                    "height" to qualityList[index].height.toString(),
                                    "label" to qualityList[index].title.toString()
                                )
                            )
                        }
                    }
                }

                null -> {
                    playerStateListener.emitDiagnostic(
                        enabled = diagnosticsEnabled,
                        severity = PlayerDiagnosticSeverity.WARNING,
                        code = "settings_no_options",
                        message = "No settings options are available for the active feature gates.",
                        contentId = contentId
                    )
                }
            }
        }
    }
}

private fun selectedAudioTrackIndex(
    audioTrackList: List<SubTitleModel>,
    exoPlayer: ExoPlayer?
): Int {
    val selectedAudioValues = exoPlayer?.currentTracks?.groups.orEmpty()
        .filter { it.type == C.TRACK_TYPE_AUDIO }
        .flatMap { group ->
            (0 until group.length)
                .filter { trackIndex -> group.isTrackSelected(trackIndex) }
                .flatMap { trackIndex ->
                    val format = group.getTrackFormat(trackIndex)
                    listOfNotNull(
                        format.language.cleanTrackText(),
                        format.label.cleanTrackText(),
                        format.id.cleanTrackText()
                    )
                }
        }

    if (selectedAudioValues.isEmpty()) return -1

    return audioTrackList.indexOfFirst { option ->
        selectedAudioValues.any { selected ->
            selected.equals(option.id, ignoreCase = true) ||
                selected.equals(option.name, ignoreCase = true)
        }
    }
}

private fun selectedSpeedIndex(
    speedData: List<SpeedControlModel>,
    exoPlayer: ExoPlayer?
): Int {
    val currentSpeed = exoPlayer?.playbackParameters?.speed ?: return -1
    return speedData.indexOfFirst { speed ->
        kotlin.math.abs(speed.speed - currentSpeed) < 0.01f
    }
}

private fun selectedQualityIndex(
    qualityList: List<VideoQualityModel>,
    exoPlayer: ExoPlayer?
): Int {
    val parameters = exoPlayer?.trackSelectionParameters ?: return -1
    val autoIndex = qualityList.indexOfFirst { it.id == "auto" }
    val usesAutoQuality =
        parameters.maxVideoWidth == Int.MAX_VALUE &&
            parameters.maxVideoHeight == Int.MAX_VALUE

    if (usesAutoQuality) return autoIndex

    return qualityList.indexOfFirst { quality ->
        quality.id != "auto" &&
            quality.width == parameters.maxVideoWidth &&
            quality.height == parameters.maxVideoHeight
    }.takeIf { it >= 0 }
        ?: qualityList.indexOfFirst { quality ->
            quality.id != "auto" && quality.height == parameters.maxVideoHeight
        }.takeIf { it >= 0 }
        ?: autoIndex
}

private fun String?.cleanTrackText(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() && !it.equals("und", ignoreCase = true) }

@Composable
fun SelectionList(
    items: List<String>,
    selectedIndex: Int,
    onItemClick: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        items(items.size) { index ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onItemClick(index) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isSelected = selectedIndex == index

                Text(
                    text = if (isSelected) "✔" else "",
                    color = Color.Green,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(0.5f)
                )

                Text(
                    text = items[index],
                    color = if (isSelected) Color.Green else Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.weight(9.5f)
                )
            }
        }
    }
}

@Composable
fun OptionItem(
    option: OptionItemModel,
    isSelected: Boolean,
    onItemClick: (Int) -> Unit
) {
    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color.Yellow else Color.White,
        animationSpec = tween(durationMillis = 300)
    )

    Box(
        modifier = Modifier
            .padding(8.dp)
            .background(
                if (isSelected) Color.White.copy(alpha = 0.1f) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable { onItemClick(option.id) }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = option.title,
            fontSize = 16.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = textColor
        )
    }
}
