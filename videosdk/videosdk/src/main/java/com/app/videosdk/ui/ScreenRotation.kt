package com.app.videosdk.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalConfiguration

@Composable
fun ScreenRotation(
    setOrientation: (Boolean) -> Unit
) {
    val configuration = LocalConfiguration.current

    LaunchedEffect(configuration.orientation) {
        when (configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                setOrientation(true)
            }

            Configuration.ORIENTATION_PORTRAIT -> {
                setOrientation(false)
            }

            // Other states can be safely ignored
            Configuration.ORIENTATION_SQUARE,
            Configuration.ORIENTATION_UNDEFINED -> Unit
        }
    }
}