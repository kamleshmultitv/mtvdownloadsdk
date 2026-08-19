package com.app.videosdk.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.rememberAsyncImagePainter
import com.app.videosdk.model.EpisodeNowPlayingStyle
import com.app.videosdk.model.PlayerModel

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun EpisodeSelectionSheet(
    expandSheet: Boolean,
    playerModelList: List<PlayerModel>?,
    currentIndex: Int,
    nowPlayingStyle: EpisodeNowPlayingStyle = EpisodeNowPlayingStyle(),
    isCasting: Boolean,
    exoPlayer: ExoPlayer,
    onDismiss: () -> Unit,
    onShowControls: (Boolean) -> Unit,
    playContent: (Int) -> Unit
) {

    fun closeSheet() {
        if (!isCasting) exoPlayer.play()
        onShowControls(false)
        onDismiss()
    }

    BackHandler(enabled = expandSheet) {
        closeSheet()
    }

    val transitionState = remember {
        MutableTransitionState(false)
    }.apply {
        targetState = expandSheet
    }

    AnimatedVisibility(
        visibleState = transitionState,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(
                durationMillis = 450,
                easing = FastOutSlowInEasing
            )
        ) + fadeIn(
            animationSpec = tween(350)
        ) + scaleIn(
            initialScale = 0.98f,
            animationSpec = tween(450)
        ),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(
                durationMillis = 350,
                easing = FastOutSlowInEasing
            )
        ) + fadeOut(
            animationSpec = tween(250)
        ) + scaleOut(
            targetScale = 0.98f,
            animationSpec = tween(300)
        )
    ) {

        val backgroundAlpha by animateFloatAsState(
            targetValue = if (expandSheet) 1f else 0f,
            animationSpec = tween(400),
            label = "backgroundAlpha"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = backgroundAlpha))
                .zIndex(20f)
                // 🔒 Block background clicks
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { }
        ) {

            Column(
                modifier = Modifier.fillMaxSize()
            ) {

                /* ---------- TOP BAR ---------- */

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, start = 24.dp, end = 24.dp)
                ) {

                    Text(
                        text = "Episodes",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )

                    Text(
                        text = "✕",
                        color = Color.White,
                        fontSize = 20.sp,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .clickable {
                                closeSheet()
                            }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.Gray.copy(alpha = 0.4f))
                )

                Spacer(modifier = Modifier.height(24.dp))

                /* ---------- CENTERED EPISODE LIST ---------- */

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {

                    BoxWithConstraints(
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        val cardWidth = maxWidth / 3.25f        // 👈 3.25 cards visible
                        val cardHeight = cardWidth * 9f / 16f  // 👈 16:9 ratio

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {

                            playerModelList?.let { list ->

                                itemsIndexed(list) { index, item ->

                                    val isNowPlaying = index == currentIndex

                                    Column(
                                        modifier = Modifier.width(cardWidth)
                                    ) {

                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(cardHeight)
                                                .then(
                                                    if (isNowPlaying && nowPlayingStyle.cardBorderWidth > 0.dp) {
                                                        Modifier.border(
                                                            width = nowPlayingStyle.cardBorderWidth,
                                                            color = nowPlayingStyle.cardBorderColor,
                                                            shape = RoundedCornerShape(8.dp)
                                                        )
                                                    } else {
                                                        Modifier
                                                    }
                                                )
                                                .clickable {
                                                    if (!isCasting) exoPlayer.play()
                                                    onShowControls(false)
                                                    onDismiss()
                                                    playContent(index)
                                                },
                                            shape = RoundedCornerShape(8.dp),
                                            elevation = CardDefaults.cardElevation(2.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = Color.Black
                                            )
                                        ) {

                                            Box(modifier = Modifier.fillMaxSize()) {
                                                Image(
                                                    painter = rememberAsyncImagePainter(item.imageUrl),
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )

                                                if (isNowPlaying) {
                                                    Row(
                                                        modifier = Modifier
                                                            .align(Alignment.TopEnd)
                                                            .padding(8.dp)
                                                            .background(
                                                                nowPlayingStyle.pillBackgroundColor,
                                                                RoundedCornerShape(50)
                                                            )
                                                            .padding(horizontal = 8.dp, vertical = 5.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(6.dp)
                                                                .background(
                                                                    nowPlayingStyle.pillDotColor,
                                                                    RoundedCornerShape(50)
                                                                )
                                                        )

                                                        Text(
                                                            text = nowPlayingStyle.pillText,
                                                            color = nowPlayingStyle.pillTextColor,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Text(
                                            text = item.episodeTitle.orEmpty(),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            textAlign = TextAlign.Start,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        val seasonText = item.seasonNumber
                                            ?.replace(Regex("[^0-9]"), "")   // remove S or any non-number
                                            ?.toIntOrNull()
                                            ?.takeIf { it != 0 }
                                            ?.let { "S$it" }

                                        val episodeText = item.episodeNumber
                                            ?.replace(Regex("[^0-9]"), "")
                                            ?.toIntOrNull()
                                            ?.takeIf { it != 0 }
                                            ?.let { "E$it" }

                                        val metaText = listOfNotNull(seasonText, episodeText)
                                            .joinToString(" ")

                                        val finalText = when {
                                            item.duration.isNullOrBlank() -> metaText
                                            metaText.isBlank() -> item.duration
                                            else -> "$metaText • ${item.duration}"
                                        }

                                        Text(
                                            text = finalText,
                                            fontSize = 12.sp,
                                            color = Color.LightGray,
                                            textAlign = TextAlign.Start,
                                            modifier = Modifier.fillMaxWidth()
                                        )


                                        Spacer(modifier = Modifier.height(4.dp))

                                        Text(
                                            text = item.episodeDescription.orEmpty(),
                                            fontSize = 12.sp,
                                            color = Color.Gray,
                                            textAlign = TextAlign.Start,
                                            modifier = Modifier.fillMaxWidth()
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
