package com.app.videosdk.ui

import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.ui.PlayerView
import com.app.videosdk.listener.AdsListener
import com.app.videosdk.listener.PipListener
import com.app.videosdk.listener.PlayerStateListener
import com.app.videosdk.model.PlayerModel
import com.app.videosdk.utils.PlayerUtils
import com.app.videosdk.utils.PlayerUtils.parseDurationToMillis
import com.google.android.gms.cast.framework.CastContext
import kotlinx.coroutines.delay
import kotlin.math.max

@OptIn(UnstableApi::class)
@Composable
fun MtvVideoPlayerSdk(
    contentList: List<PlayerModel>? = null,
    index: Int? = 0,
    pipListener: PipListener? = null,
    startInFullScreen: Boolean = false,
    playerStateListener: PlayerStateListener? = null,
    onPlayerBack: (Boolean) -> Unit,
    setFullScreen: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    var contentDuration by remember { mutableLongStateOf(0L) }

    val playerView = remember {
        PlayerView(context).apply {
            useController = false
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
    }

    CastContext.getSharedInstance(context)

    val safeIndex = remember(contentList, index) {
        val size = contentList?.size ?: 0
        when {
            size == 0 -> 0
            index == null -> 0
            index < 0 -> 0
            index >= size -> size - 1
            else -> index
        }
    }

    val selectedIndex = remember { mutableIntStateOf(safeIndex) }

    LaunchedEffect(index, contentList) {
        val size = contentList?.size ?: return@LaunchedEffect
        selectedIndex.intValue = index?.coerceIn(0, size - 1) ?: 0
    }

    val playerModel = contentList?.getOrNull(selectedIndex.intValue)

    var isFullScreen by remember(startInFullScreen) {
        mutableStateOf(startInFullScreen)
    }

    LaunchedEffect(startInFullScreen) {
        if (startInFullScreen) {
            setFullScreen(true)
        }
    }

    FullScreenHandler(isFullScreen)
    var isControllerVisible by remember { mutableStateOf(false) }
    var pipEnabled by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isSettingsClick by remember { mutableStateOf(false) }
    var isAdsShowing by remember { mutableStateOf(false) }

    var isSkipIntroClicked by remember(selectedIndex.intValue) { mutableStateOf(false) }

    val imaCuePoints = remember {
        mutableStateListOf<CuePoint>()
    }

    val isLive = playerModel?.isLive == true

    val playbackUrl = remember(playerModel) {
        when {
            isLive -> playerModel.liveUrl
            !playerModel?.hlsUrl.isNullOrEmpty() -> playerModel.hlsUrl
            !playerModel?.mpdUrl.isNullOrEmpty() -> playerModel.mpdUrl
            else -> ""
        }
    }

    val subtitleUri = if (isLive) "" else playerModel?.srt.orEmpty()

    var containerSize by remember { mutableStateOf(Size.Zero) }
    var fillScale by remember { mutableFloatStateOf(1f) }
    var isFilled by remember { mutableStateOf(false) }
    var zoomAccumulator by remember { mutableFloatStateOf(1f) }

    val animatedScale by animateFloatAsState(
        targetValue = if (isFilled) fillScale else 1f,
        animationSpec = tween(220),
        label = "scale"
    )

    val adsListener = remember {
        object : AdsListener {
            override fun onAdsLoaded() {
                isControllerVisible = false
                isAdsShowing = true

            }
            override fun onAdStarted() {
                isControllerVisible = false
                isAdsShowing = true
                playerStateListener?.onAdStateChanged(true)
            }
            override fun onAdCompleted() {
                isControllerVisible = true
                isAdsShowing = false
                playerStateListener?.onAdStateChanged(false)
            }
            override fun onAllAdsCompleted() {
                isControllerVisible = true
                isAdsShowing = false
            }
            override fun onAdError(message: String) {
                isControllerVisible = true
                isAdsShowing = false
            }
        }
    }

    val playerWithAds = remember(selectedIndex.intValue, playbackUrl) {
        val model = playerModel ?: return@remember null
        PlayerUtils.createPlayer(
            context = context,
            contentList,
            selectedIndex.value,
            videoUrl = playbackUrl.toString(),
            drmToken = model.drmToken,
            srt = subtitleUri,
            playerView = playerView,
            adsConfig = model.adsConfig,
            adsListener = adsListener
        )
    }

    val exoPlayer = playerWithAds?.first
    val adsLoader = playerWithAds?.second

    // 🔥 Position Tracker for Auto-Show Controls (Intro / Next Episode)
    var hasShownNextEpisodeControls by remember(selectedIndex.intValue) { mutableStateOf(false) }
    var hasShownSkipIntroControls by remember(selectedIndex.intValue) { mutableStateOf(false) }

    DisposableEffect(exoPlayer) {
        val player = exoPlayer ?: return@DisposableEffect onDispose {}

        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width == 0 || containerSize == Size.Zero) return
                fillScale = max(
                    containerSize.width / videoSize.width,
                    containerSize.height / videoSize.height
                )
                isFilled = false
                zoomAccumulator = 1f
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> {
                        isLoading = true
                        playerStateListener?.onBuffering(true)
                    }

                    Player.STATE_READY -> {
                        val d = player.duration
                        if (d > 0) {
                            contentDuration = d
                        }

                        playerStateListener?.onBuffering(false)
                        val duration = player.duration.takeIf { it > 0 } ?: 0L
                        playerStateListener?.onPlayerReady(duration)
                    }

                    Player.STATE_ENDED -> {
                        val total = contentList?.size ?: 0
                        val nextIndex = selectedIndex.intValue + 1
                        if (nextIndex < total) {
                            selectedIndex.intValue = nextIndex
                        }

                        playerStateListener?.onPlaybackCompleted()
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playerStateListener?.onPlayStateChanged(isPlaying)
            }

            override fun onPlayerError(error: PlaybackException) {
                playerStateListener?.onPlayerError(error)
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (!timeline.isEmpty) {
                    val d = player.duration
                    if (d > 0) {
                        contentDuration = d
                    }
                }

                if (timeline.isEmpty) return
                val period = Timeline.Period()
                timeline.getPeriod(0, period)
                imaCuePoints.clear()
                for (adGroupIndex in 0 until period.adGroupCount) {
                    val timeUs = period.getAdGroupTimeUs(adGroupIndex)
                    val positionMs =
                        if (timeUs == C.TIME_END_OF_SOURCE) exoPlayer.duration else timeUs / 1000
                    imaCuePoints.add(
                        CuePoint(
                            id = "ima_$adGroupIndex",
                            positionMs = positionMs,
                            type = CueType.AD
                        )
                    )
                }
            }
        }

        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
            adsLoader?.setPlayer(null)
            player.release()
        }
    }

    LaunchedEffect(exoPlayer) {
        val player = exoPlayer ?: return@LaunchedEffect
        while (true) {
            val currentPos = player.currentPosition
            val model = playerModel

            if (model != null && !model.isLive) {
                // Skip Intro Auto-Show
                model.skipIntro?.let { intro ->
                    if (!isSkipIntroClicked && !hasShownSkipIntroControls && intro.enableSkipIntro) {
                        val startTime = intro.startTime ?: 0L
                        if (currentPos >= startTime && currentPos < startTime + 2000 && !isAdsShowing) {
                            hasShownSkipIntroControls = true
                            isControllerVisible = true
                        }
                    }
                }

                // Next Episode Auto-Show
                model.nextEpisode?.let { next ->
                    if (
                        !hasShownNextEpisodeControls &&
                        next.enableNextEpisode &&
                        contentDuration > 0
                    ) {
                        val showBeforeEndMs = parseDurationToMillis(next.showBeforeEndMs)

                        val triggerTime = (contentDuration - showBeforeEndMs)
                            .coerceAtLeast(0L)

                        if (currentPos >= triggerTime && triggerTime != 0L && !isAdsShowing) {
                            hasShownNextEpisodeControls = true
                            isControllerVisible = true
                        }
                    }
                }

            }
            delay(1000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isFullScreen) Modifier.fillMaxSize() else Modifier.height(configuration.screenWidthDp.dp * 9 / 16))
            .background(Color.Black)
            .onSizeChanged { containerSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    zoomAccumulator = 1f
                    while (true) {
                        val event = awaitPointerEvent()
                        val zoomChange = event.calculateZoom()
                        if (zoomChange != 1f) {
                            zoomAccumulator *= zoomChange
                            if (zoomAccumulator > 1.15f && !isFilled) isFilled = true
                            if (zoomAccumulator < 0.85f && isFilled) isFilled = false
                        }
                        if (event.changes.all { !it.pressed }) break
                    }
                }
            }
    ) {
        AndroidView(
            factory = { playerView },
            update = { if (it.player !== exoPlayer) it.player = exoPlayer },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = animatedScale
                    scaleY = animatedScale
                }
                .pointerInput(Unit) {
                    detectTapGestures {
                        if (!pipEnabled && !isSettingsClick) {
                            isControllerVisible = !isControllerVisible
                        }
                    }
                }
        )

        if (!isControllerVisible && isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        }

        AnimatedVisibility(
            visible = isControllerVisible && !pipEnabled,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            exoPlayer?.let {
                CustomPlayerController(
                    playerModelList = contentList,
                    index = selectedIndex.intValue,
                    totalDuration = contentDuration,
                    pipListener = pipListener,
                    isFullScreen = { full ->
                        isFullScreen = full
                        setFullScreen(full)
                        playerStateListener?.onFullScreenChanged(full)
                    },
                    isCurrentlyFullScreen = isFullScreen,
                    exoPlayer = it,
                    modifier = Modifier.fillMaxSize(),
                    onShowControls = { isControllerVisible = it },
                    isPipEnabled = { pipEnabled = it },
                    onSettingsButtonClick = { isSettingsClick = it },
                    isLoading = isLoading,
                    onBackPressed = {
                        if (isFullScreen) {
                            isFullScreen = false
                            setFullScreen(false)
                            playerStateListener?.onFullScreenChanged(false)
                        } else {
                            onPlayerBack(true)
                        }
                    },
                    cuePoints = imaCuePoints,
                    playContent = { selectedIndex.intValue = it },
                    isSkipIntroClicked = isSkipIntroClicked,
                    onSkipIntroClicked = { isSkipIntroClicked = it },
                    onNextEpisodeClick = { selectedIndex.intValue = it }
                )
            }
        }

        if (isSettingsClick) {
            SelectorHeader(exoPlayer) { isSettingsClick = it }
        }
    }
}