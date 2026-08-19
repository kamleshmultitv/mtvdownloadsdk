package com.app.videosdk.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.rememberAsyncImagePainter
import com.app.videosdk.model.PlayerModel
import com.app.videosdk.utils.CastUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonSelector(
    playerModelList: List<PlayerModel>? = null,
    exoPlayer: ExoPlayer,
    onShowControls: (Boolean) -> Unit,
    pausePlayer: (Boolean) -> Unit,
    playContent: (Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var showSheet by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val castUtils = remember { CastUtils(context, exoPlayer) }
    val isCasting = castUtils.isCasting()

    Box(
        modifier = Modifier
            .wrapContentSize()
            .background(color = Color.Transparent)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount ->
                        if (dragAmount < -50) { // Swipe up → Open Bottom Sheet
                            coroutineScope.launch {
                                showSheet = true
                                sheetState.show()
                                exoPlayer.pause()
                                onShowControls(true)
                                pausePlayer(true)
                            }
                        } else if (dragAmount > 50 && showSheet) { // Swipe down → Close Bottom Sheet
                            coroutineScope.launch {
                                sheetState.hide()
                                showSheet = false
                                if (!isCasting) {
                                    exoPlayer.play()
                                }
                                onShowControls(false)
                                pausePlayer(false)
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.clickable {
                coroutineScope.launch {
                    showSheet = true
                    sheetState.show()
                    exoPlayer.pause()
                    onShowControls(true)
                    pausePlayer(true)
                }
            }
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardDoubleArrowUp,
                contentDescription = "Episodes",
                tint = Color.White
            )
            Text(
                text = "Episodes",
                color = Color.White,
                fontSize = 14.sp
            )
        }

    }

    // Bottom Sheet appears when swiped up
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                coroutineScope.launch {
                    sheetState.hide()
                    showSheet = false
                    if (!isCasting) {
                        exoPlayer.play()
                    }
                    onShowControls(false)
                    pausePlayer(false)
                }
            },
            sheetState = sheetState,
            containerColor = Color.Transparent,
            scrimColor = Color.Black.copy(alpha = 0.7f), // Dim background slightly
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f) // Adjust width for floating effect
                    .height(300.dp) // Set fixed height to appear in the middle
                    .clip(RoundedCornerShape(16.dp)) // Rounded edges
                    .background(Color.Transparent) // Set background color
                    .align(Alignment.CenterHorizontally), // Center it properly
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (playerModelList != null) {
                            items(playerModelList.size) { index ->
                                Card(
                                    modifier = Modifier
                                        .width(120.dp)
                                        .height(160.dp)
                                        .clickable {
                                            coroutineScope.launch {
                                                sheetState.hide()
                                                showSheet = false
                                                if (!isCasting) {
                                                    exoPlayer.play()
                                                }
                                                onShowControls(false)
                                            }

                                            playContent(index)
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    elevation = CardDefaults.cardElevation(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            painter = rememberAsyncImagePainter(playerModelList[index].imageUrl),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.4f)), // Semi-transparent overlay
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = playerModelList[index].title.toString(),
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White, // Make text visible on image
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }

                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}