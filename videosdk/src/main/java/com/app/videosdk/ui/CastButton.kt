package com.app.videosdk.ui

import android.content.Context
import android.content.ContextWrapper
import android.view.ContextThemeWrapper
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.mediarouter.app.MediaRouteButton
import com.app.videosdk.R
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastState
import com.google.android.gms.cast.framework.CastStateListener

@Composable
fun CastButton(
    resId: Int? = null,
    connectedResId: Int? = null,
    tint: Int? = null
) {
    val context = LocalContext.current
    val fragmentActivity = remember(context) { context.findFragmentActivity() } ?: return
    val themedContext =
        remember(fragmentActivity) { ContextThemeWrapper(fragmentActivity, R.style.ThemeOverlay_CastButton) }
    val mediaRouteButton = remember {
        MediaRouteButton(themedContext).apply {
            CastButtonFactory.setUpMediaRouteButton(themedContext, this)
            alpha = 0f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    }
    val castContext = remember(context) {
        runCatching { CastContext.getSharedInstance(context.applicationContext) }.getOrNull()
    }
    var castState by remember(castContext) {
        mutableIntStateOf(castContext?.castState ?: CastState.NOT_CONNECTED)
    }

    DisposableEffect(castContext) {
        if (castContext == null) {
            onDispose { }
        } else {
            val listener = CastStateListener { state -> castState = state }
            castState = castContext.castState
            castContext.addCastStateListener(listener)
            onDispose { castContext.removeCastStateListener(listener) }
        }
    }

    val isConnected = castState == CastState.CONNECTED

    Box {
        AndroidView(
            modifier = Modifier
                .size(1.dp)
                .alpha(0f),
            factory = { mediaRouteButton },
            update = { button ->
                button.alpha = 0f
                button.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
        )

        IconButton(
            onClick = {
                if (!mediaRouteButton.showDialog()) {
                    mediaRouteButton.performClick()
                }
            }
        ) {
            CustomIcon(
                resId = if (isConnected) connectedResId else resId,
                defaultIcon = if (isConnected) Icons.Default.CastConnected else Icons.Default.Cast,
                contentDescription = if (isConnected) "Cast connected" else "Cast",
                modifier = Modifier.size(24.dp),
                tint = tint
            )
        }
    }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? {
    return when (this) {
        is FragmentActivity -> this
        is ContextWrapper -> baseContext.findFragmentActivity()
        else -> null
    }
}
