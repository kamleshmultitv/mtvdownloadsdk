package com.app.videosdk.ui.chapter

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.app.videosdk.model.Chapter
import com.app.videosdk.utils.PlayerUtils.formatTime

@Composable
fun ChapterItemCard(
    chapter: Chapter,
    isSelected: Boolean,
    progress: Float = 0f, // 0f..1f
    onClick: () -> Unit
) {

    val safeProgress = progress.coerceIn(0f, 1f)

    // Animation
    val animatedElevation by animateDpAsState(
        targetValue = if (isSelected) 12.dp else 4.dp,
        label = "elevation"
    )

    val animatedScale by animateFloatAsState(
        targetValue = if (isSelected) 1.03f else 1f,
        label = "scale"
    )

    val containerColor by animateColorAsState(
        targetValue = if (isSelected)
            Color(0xFF262626)  // Selected dark
        else
            Color(0xFF1E1E1E), // Normal dark
        label = "containerColor"
    )

    val titleColor by animateColorAsState(
        targetValue = if (isSelected)
            Color(0xFF4DA3FF)  // Accent Blue
        else
            Color.White,
        label = "titleColor"
    )

    val secondaryTextColor = Color(0xFFB0B0B0)
    val iconTint = if (isSelected)
        Color(0xFF4DA3FF)
    else
        Color(0xFF8A8A8A)

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
            },
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = animatedElevation),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {

        Column {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                // 🔵 Play Circle
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            color = if (isSelected)
                                Color(0xFF4DA3FF)
                            else
                                Color(0xFF2C2C2C),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (isSelected)
                            Color.Black
                        else
                            Color(0xFF8A8A8A),
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(18.dp))

                // 📄 Title + Time
                Column(modifier = Modifier.weight(1f)) {

                    Text(
                        text = chapter.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = titleColor,
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = formatTime(chapter.startMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryTextColor
                    )
                }

                // ➜ Chevron
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            // 🔥 Progress Bar (Only When Selected)
            AnimatedVisibility(visible = isSelected) {
                LinearProgressIndicator(
                    progress = { safeProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = Color(0xFF4DA3FF),
                    trackColor = Color(0xFF2A2A2A)
                )
            }
        }
    }
}





