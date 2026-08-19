package com.app.videosdk.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.app.videosdk.R
import com.google.android.gms.cast.framework.CastButtonFactory

@Composable
fun CastButton() {
    val context = LocalContext.current
    val themedContext =
        remember { android.view.ContextThemeWrapper(context, R.style.ThemeOverlay_CastButton) }
    val mediaRouteButton = remember {
        MediaRouteButton(themedContext).apply {
            CastButtonFactory.setUpMediaRouteButton(themedContext, this)
        }
    }
    AndroidView(
        factory = { mediaRouteButton }
    )
}