package com.app.videosdk.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.videosdk.model.PlayerModel

@Composable
fun LockExpandableButton(
    playerModel: PlayerModel? = null,
    showUnlockConfirm: Boolean,
    onClick: () -> Unit
) {

    val height = 40.dp
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    val customControls = playerModel?.customControls
    // 🔥 Pre-measure text width (always available)
    val textLayoutResult = textMeasurer.measure(
        text = "Unlock Controls?",
        style = TextStyle(
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    )

    val textWidthDp = with(density) {
        textLayoutResult.size.width.toDp()
    }

    val expandedWidth = textWidthDp + 28.dp + 12.dp + 32.dp

    val animatedWidth by animateDpAsState(
        targetValue = if (showUnlockConfirm) expandedWidth else height,
        animationSpec = tween(300),
        label = ""
    )

    Box(
        modifier = Modifier
            .height(height)
            .width(animatedWidth)
            .background(
                color = Color.White.copy(alpha = 0.15f),
                shape = RoundedCornerShape(height / 2)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            CustomIcon(
                resId = if (showUnlockConfirm)
                    customControls?.unlockIconRes
                else
                    customControls?.lockIconRes,
                defaultIcon = if (showUnlockConfirm)
                    Icons.Default.LockOpen
                else
                    Icons.Default.Lock,
                contentDescription = "Lock",
                modifier = Modifier.size(24.dp),
                tint = customControls?.iconTintRes
            )

            AnimatedVisibility(visible = showUnlockConfirm) {
                Row {
                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = "Unlock Controls?",
                        color = Color.White,
                        fontSize = 16.sp,
                        maxLines = 1,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}