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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.media3.exoplayer.ExoPlayer
import com.app.videosdk.model.OptionItemModel
import com.app.videosdk.utils.PlayerUtils.calculatePitch
import com.app.videosdk.utils.PlayerUtils.changeVideoResolution
import com.app.videosdk.utils.PlayerUtils.getAudioTrack
import com.app.videosdk.utils.PlayerUtils.getSubTitleFormats
import com.app.videosdk.utils.PlayerUtils.getVideoFormats
import com.app.videosdk.utils.PlayerUtils.selectAudioTrack
import com.app.videosdk.utils.PlayerUtils.setAutoVideoResolution
import com.app.videosdk.utils.PlayerUtils.showAudioTrack
import com.app.videosdk.viewmodel.VideoViewModel

@Composable
fun SelectorHeader(exoPlayer: ExoPlayer?, closeOptionCard: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val viewModel: VideoViewModel = viewModel()
    val selectedItems = remember { mutableStateMapOf<Int, Int>() }
    val options by viewModel.options.collectAsState()
    var selectedOption by remember { mutableStateOf(options.firstOrNull()?.id) }

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
                items(options.size) { index ->
                    val option = options[index]
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
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            thickness = 1.dp,
            color = Color.Gray
        )

        Box(modifier = Modifier.fillMaxSize().padding(top = 16.dp)) {
            when (selectedOption) {
                1 -> {
                    val audioTrackList = remember(context, exoPlayer) { showAudioTrack(context, exoPlayer) }
                    SelectionList(
                        items = audioTrackList.map { it.name.toString() },
                        selectedIndex = selectedItems[selectedOption] ?: -1
                    ) { index ->
                        selectedItems[selectedOption!!] = index
                        selectAudioTrack(audioTrackList[index].id.toString(), exoPlayer)
                    }
                }

                2 -> {
                    val subTitleList = remember(exoPlayer) { getSubTitleFormats(exoPlayer) }
                    val availableList = remember(context, subTitleList) {
                        getAudioTrack(
                            context, listOf("off") + subTitleList.mapNotNull { it.language ?: it.label }
                        )
                    }

                    SelectionList(
                        items = availableList.map { it.name.toString() },
                        selectedIndex = selectedItems[selectedOption] ?: -1
                    ) { index ->
                        selectedItems[selectedOption!!] = index
                        exoPlayer?.trackSelectionParameters =
                            exoPlayer.trackSelectionParameters.buildUpon()
                                .setTrackTypeDisabled(
                                    C.TRACK_TYPE_TEXT,
                                    availableList[index].id == "off"
                                )
                                .apply {
                                    if (availableList[index].id != "off") {
                                        setPreferredTextLanguages(availableList[index].id ?: "en")
                                    }
                                }.build()
                    }
                }

                3 -> {
                    viewModel.getSpeedData()
                    val speedData by viewModel.speedControlData.observeAsState(emptyList())

                    SelectionList(
                        items = speedData.map { it.speedTitle },
                        selectedIndex = selectedItems[selectedOption] ?: -1
                    ) { index ->
                        selectedItems[selectedOption!!] = index
                        val param = PlaybackParameters(
                            speedData[index].speed,
                            calculatePitch(speedData[index].speed)
                        )
                        exoPlayer?.playbackParameters = param
                    }
                }

                4 -> {
                    val qualityList = remember(exoPlayer) { getVideoFormats(exoPlayer) }

                    SelectionList(
                        items = qualityList.map { if (it.id == "auto") it.title.toString() else "${it.title}p" },
                        selectedIndex = selectedItems[selectedOption] ?: -1
                    ) { index ->
                        selectedItems[selectedOption!!] = index
                        if (index == 0) {
                            setAutoVideoResolution(exoPlayer)
                        } else {
                            changeVideoResolution(
                                exoPlayer,
                                qualityList[index].width,
                                qualityList[index].height
                            )
                        }
                    }
                }
            }
        }
    }
}

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
                Text(
                    text = items[index],
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )

                if (selectedIndex == index) {
                    Text(
                        text = "✔",
                        color = Color.Green,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
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
