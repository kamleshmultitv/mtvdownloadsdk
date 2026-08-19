package com.app.videosdk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.videosdk.model.PlayerModel

@Composable
fun LockScreenOverlay(
    playerModel: PlayerModel? = null,
    isLocked: Boolean,
    showUnlockConfirm: Boolean,
    onUnlockRequest: () -> Unit,
    onConfirmUnlock: () -> Unit
) {

    if (!isLocked) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.BottomCenter
    ) {

        Column(
            modifier = Modifier.padding(bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            LockExpandableButton(
                playerModel = playerModel,
                showUnlockConfirm = showUnlockConfirm,
                onClick = {
                    if (!showUnlockConfirm) {
                        onUnlockRequest()
                    } else {
                        onConfirmUnlock()
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Screen Locked",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Tap to Unlock",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
        }
    }
}