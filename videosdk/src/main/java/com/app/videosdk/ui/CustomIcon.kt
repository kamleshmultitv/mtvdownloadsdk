package com.app.videosdk.ui

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource

@Composable
fun CustomIcon(
    resId: Int?,
    defaultIcon: ImageVector,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    tint: Int? = null
) {
    val tintColor = customControlTintColor(tint, Color.White)
    if (resId != null) {

        Icon(
            painter = painterResource(resId),
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tintColor
        )
    } else {
        Icon(
            imageVector = defaultIcon,
            contentDescription = contentDescription,
            modifier = modifier,
            tint = tintColor
        )
    }
}
