package com.app.videosdk.ui

import android.content.Context
import android.os.Looper
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.app.videosdk.model.PlayerModel

/**
 * XML-compatible player host.
 *
 * Add this view to XML and call `player.play(video)`. The complete [PlayerModel],
 * including its backend-provided age rating, is passed into the SDK renderer.
 */
class MtvVideoPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val currentVideo = mutableStateOf<PlayerModel?>(null)
    private val pipMode = mutableStateOf(false)

    private val composeView = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val video = currentVideo.value ?: return@setContent
            MtvVideoPlayerSdk(
                contentList = listOf(video),
                index = 0,
                isInPipMode = pipMode.value,
                onPlayerBack = { onBackRequested?.invoke() },
                setFullScreen = { onFullScreenChanged?.invoke(it) }
            )
        }
    }

    /** Optional callbacks for controls owned by the host Activity/Fragment. */
    var onBackRequested: (() -> Unit)? = null
    var onFullScreenChanged: ((Boolean) -> Unit)? = null

    init {
        addView(composeView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** Starts a video and reads all player metadata directly from [video]. */
    fun play(video: PlayerModel) {
        runOnMainThread { currentVideo.value = video }
    }

    /** Forward the Activity/Fragment PiP callback so SDK overlays are suppressed. */
    fun setInPictureInPictureMode(inPictureInPicture: Boolean) {
        runOnMainThread { pipMode.value = inPictureInPicture }
    }

    private inline fun runOnMainThread(crossinline action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else post { action() }
    }
}
