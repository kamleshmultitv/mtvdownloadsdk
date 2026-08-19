package com.app.videosdk.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.exoplayer.ExoPlayer
import com.app.videosdk.listener.PipListener
import com.app.videosdk.listener.PlayerStateListener
import com.app.videosdk.model.CuePoint
import com.app.videosdk.model.EpisodeNowPlayingStyle
import com.app.videosdk.model.PlayerAnalyticsEventType
import com.app.videosdk.model.PlayerControlsConfig
import com.app.videosdk.model.PlayerModel
import com.app.videosdk.utils.CastPlaybackState
import com.app.videosdk.utils.CastUtils
import com.app.videosdk.utils.PlayerUtils.timeToMillis
import com.app.videosdk.utils.emitAnalytics
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun CustomPlayerController(
    playerModelList: List<PlayerModel>? = null,
    index: Int,
    totalDuration: Long,
    playbackPositionMs: Long? = null,
    playbackIsPlaying: Boolean? = null,
    contentId: String? = null,
    analyticsEnabled: Boolean = false,
    playerStateListener: PlayerStateListener? = null,
    pipListener: PipListener? = null,
    isFullScreen: (Boolean) -> Unit,
    isLockScreen: (Boolean) -> Unit,
    isCurrentlyFullScreen: Boolean,
    isCurrentlyLockScreen: Boolean,
    exoPlayer: ExoPlayer,
    externalCastUtils: CastUtils? = null,
    castEnabled: Boolean = true,
    episodeNowPlayingStyle: EpisodeNowPlayingStyle = EpisodeNowPlayingStyle(),
    modifier: Modifier,
    isControlsVisible: Boolean,
    onShowControls: (Boolean) -> Unit,
    isPipEnabled: (Boolean) -> Unit = {},
    onSettingsButtonClick: (Boolean) -> Unit = {},
    isLoading: Boolean,
    onBackPressed: () -> Unit = {},
    cuePoints: List<CuePoint> = emptyList(),
    playContent: (Int) -> Unit,
    isSkipIntroClicked: Boolean,
    onSkipIntroClicked: (Boolean) -> Unit,
    onNextEpisodeClick: (Int) -> Unit,
    showContentTitle: Boolean = true,
    onChapterClick: () -> Unit = {},
    onCutClick: () -> Unit = {},
    controlsConfig: PlayerControlsConfig = PlayerControlsConfig(),
    showPreviousControl: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val showControlsState = rememberUpdatedState(onShowControls)
    val fullScreenState = rememberUpdatedState(isFullScreen)
    val lockScreenState = rememberUpdatedState(isLockScreen)

    val rememberedCastUtils = remember(context, exoPlayer, castEnabled) {
        if (castEnabled) CastUtils(context, exoPlayer) else null
    }
    val castUtils = externalCastUtils ?: rememberedCastUtils
    val castState by castUtils
        ?.castState
        ?.collectAsState()
        ?: remember { mutableStateOf(CastPlaybackState()) }
    val isCasting = castState.isCasting

    var isZoomed by remember { mutableStateOf(false) }
    var showForwardIcon by remember { mutableStateOf(false) }
    var showRewindIcon by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(playbackIsPlaying ?: exoPlayer.isPlaying) }

    var currentPosition by remember { mutableLongStateOf(playbackPositionMs ?: 0L) }
    val duration by rememberUpdatedState(
        if (totalDuration > 0) totalDuration else 0L
    )
    var nextEpisodeClicked by remember(index) { mutableStateOf(false) }
    var isDraggingSeekbar by remember { mutableStateOf(false) }
    var onSeek by remember { mutableStateOf(false) }
    val currentPlayerModel = playerModelList?.getOrNull(index)
    var expandSheet by remember { mutableStateOf(false) }

    fun dispatchSeekStarted(positionMs: Long) {
        playerStateListener?.onSeekStarted(positionMs)
        playerStateListener.emitAnalytics(
            enabled = analyticsEnabled,
            type = PlayerAnalyticsEventType.SEEK_STARTED,
            contentId = contentId,
            positionMs = positionMs,
            durationMs = duration
        )
    }

    fun dispatchSeekCompleted(positionMs: Long) {
        playerStateListener?.onSeekCompleted(positionMs)
        playerStateListener.emitAnalytics(
            enabled = analyticsEnabled,
            type = PlayerAnalyticsEventType.SEEK_COMPLETED,
            contentId = contentId,
            positionMs = positionMs,
            durationMs = duration
        )
    }

    LaunchedEffect(castUtils, currentPlayerModel, externalCastUtils) {
        if (castEnabled && castUtils != null && externalCastUtils == null) {
            castUtils.setupCastSession(currentPlayerModel)
        }
    }

    DisposableEffect(castUtils, externalCastUtils) {
        onDispose {
            if (castEnabled && externalCastUtils == null) {
                castUtils?.release()
            }
        }
    }

    LaunchedEffect(isCurrentlyFullScreen, expandSheet) {
        if (!isCurrentlyFullScreen && expandSheet) {
            expandSheet = false
        }
    }

    LaunchedEffect(playbackPositionMs, playbackIsPlaying) {
        playbackPositionMs?.let { currentPosition = it }
        playbackIsPlaying?.let { isPlaying = it }
    }


    /* ---------------- SKIP INTRO ---------------- */

    val showSkipIntro by remember(currentPosition, currentPlayerModel, isSkipIntroClicked) {
        derivedStateOf {
            currentPlayerModel?.skipIntro?.let { intro ->
                !currentPlayerModel.isLive &&
                        intro.enableSkipIntro &&
                        !isSkipIntroClicked &&
                        currentPosition in (intro.startTime ?: 0L)..(intro.endTime ?: 0L)
            } ?: false
        }
    }

    /* ---------------- NEXT EPISODE WINDOW ---------------- */

    val showNextEpisode by remember(
        currentPosition,
        currentPlayerModel,
        nextEpisodeClicked,
        duration,
        controlsConfig.next
    ) {
        derivedStateOf {
            val next = currentPlayerModel?.nextEpisode ?: return@derivedStateOf false

            if (
                !controlsConfig.next ||
                exoPlayer.isPlayingAd ||          // 🔥 KEY FIX
                duration <= 0L ||
                currentPosition <= 0L ||
                currentPosition >= duration ||
                currentPlayerModel.isLive ||
                !next.enableNextEpisode ||
                nextEpisodeClicked
            ) return@derivedStateOf false

            val startTime = timeToMillis(duration.toString(), next.showBeforeEndMs)

            currentPosition in startTime..startTime.plus(10_000L)
        }
    }


    /* ⭐ FIX 1: PLAYBACK-TIME BASED WINDOW (NOT animation based) */
    val isInNextEpisodeWindow by remember(
        currentPosition,
        currentPlayerModel,
        nextEpisodeClicked,
        duration,
        controlsConfig.next
    ) {
        derivedStateOf {
            val next = currentPlayerModel?.nextEpisode ?: return@derivedStateOf false

            if (
                !controlsConfig.next ||
                exoPlayer.isPlayingAd ||          // 🔥 KEY FIX
                duration <= 0L ||
                currentPosition <= 0L ||
                currentPosition >= duration ||
                currentPlayerModel.isLive ||
                !next.enableNextEpisode ||
                nextEpisodeClicked
            ) return@derivedStateOf false

            val startTime = timeToMillis(duration.toString(), next.showBeforeEndMs)

            currentPosition in startTime..startTime.plus(10_000L)
        }
    }


    // ⭐ FIX 4: FORCE SHOW CONTROLS EXACTLY AT startTime
    LaunchedEffect(isInNextEpisodeWindow) {
        if (isInNextEpisodeWindow) {
            showControlsState.value(true)
        }
    }


    /* ---------------- PROGRESS ANIMATION (UNCHANGED) ---------------- */
    val animationDurationMs by remember(currentPosition, currentPlayerModel) {
        derivedStateOf {
            currentPlayerModel?.nextEpisode?.let { next ->
                ((timeToMillis(
                    duration.toString(),
                    next.showBeforeEndMs
                ).plus(100_00L)) - currentPosition).coerceIn(0L, 10_000L)
            } ?: 0L
        }
    }

    val progress by animateFloatAsState(
        targetValue = if (showNextEpisode) 1f else 0f,
        animationSpec = tween(
            durationMillis = animationDurationMs.toInt().coerceAtLeast(1),
            easing = LinearEasing
        ),
        label = "NextEpisodeProgress"
    )

    /* ⭐ FIX 2: SINGLE SOURCE OF TRUTH FOR VISIBILITY */
    val shouldForceShowControls by remember(
        isInNextEpisodeWindow,
        showSkipIntro,
        expandSheet
    ) {
        derivedStateOf {
            isInNextEpisodeWindow || showSkipIntro || expandSheet
        }
    }

    /* ---------------- PLAYBACK OBSERVER ---------------- */

    LaunchedEffect(exoPlayer, isCasting, playbackPositionMs, playbackIsPlaying) {
        if (playbackPositionMs != null && playbackIsPlaying != null) {
            return@LaunchedEffect
        }

        while (true) {
            if (playbackPositionMs == null) {
                currentPosition =
                    if (isCasting && castUtils != null) castUtils.getCastPosition()
                    else exoPlayer.currentPosition
            }

            if (playbackIsPlaying == null) {
                isPlaying = exoPlayer.isPlaying
            }
            delay(1000.milliseconds)
        }
    }


    /* ⭐ FIX 3: AUTO-SHOW / AUTO-HIDE CONTROLS (FINAL LOGIC) */
    LaunchedEffect(
        isPlaying,
        shouldForceShowControls,
        isDraggingSeekbar,
        onSeek
    ) {
        // ✅ While skip intro OR next episode window → NEVER auto hide
        if (shouldForceShowControls || isDraggingSeekbar || onSeek) {
            showControlsState.value(true)
            return@LaunchedEffect
        }

        // ⏱ Normal behavior
        delay(3000.milliseconds)
        showControlsState.value(!isPlaying)
    }

    LaunchedEffect(onSeek) {
        if (onSeek) {
            delay(500.milliseconds)
            onSeek = false
        }
    }


    DisposableEffect(lifecycleOwner) {
        val observer = object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                exoPlayer.pause()
                showControlsState.value(false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var hasShownNextEpisodeControls by remember(index) { mutableStateOf(false) }

    LaunchedEffect(currentPosition, currentPlayerModel, controlsConfig.next) {
        val next = currentPlayerModel?.nextEpisode ?: return@LaunchedEffect

        if (
            controlsConfig.next &&
            !hasShownNextEpisodeControls &&
            !currentPlayerModel.isLive &&
            next.enableNextEpisode &&
            currentPosition >= timeToMillis(duration.toString(), next.showBeforeEndMs)
        ) {
            hasShownNextEpisodeControls = true

            // 🔥 FORCE SHOW CONTROLS EXACTLY AT startTime
            showControlsState.value(true)
        }
    }

    val playerModel = playerModelList?.getOrNull(index)


    /* ---------------- UI (UNCHANGED) ---------------- */

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        if (isControlsVisible && !expandSheet) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.00f to Color.Black.copy(alpha = 0.86f),
                                0.18f to Color.Black.copy(alpha = 0.52f),
                                0.45f to Color.Black.copy(alpha = 0.34f),
                                0.62f to Color.Black.copy(alpha = 0.34f),
                                0.84f to Color.Black.copy(alpha = 0.62f),
                                1.00f to Color.Black.copy(alpha = 0.90f)
                            )
                        )
                    )
            )
        }

        /* ---- TOP BAR ---- */
        AnimatedVisibility(
            visible = isControlsVisible && !expandSheet,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(2f),

            enter = slideInVertically(
                initialOffsetY = { -it },   // 🔥 start ABOVE the screen
                animationSpec = tween(
                    durationMillis = 700,
                    easing = LinearOutSlowInEasing
                )
            ) + fadeIn(
                animationSpec = tween(
                    durationMillis = 600,
                    delayMillis = 100,
                    easing = LinearEasing
                )
            ),

            exit = slideOutVertically(
                targetOffsetY = { -it },    // 🔥 slide back UP
                animationSpec = tween(
                    durationMillis = 600,
                    easing = FastOutLinearInEasing
                )
            ) + fadeOut(
                animationSpec = tween(
                    durationMillis = 400,
                    easing = LinearEasing
                )
            )
        ) {
            TopBar(
                playerModel = playerModel,
                isFullScreen = isCurrentlyFullScreen,
                context = context,
                castUtils = castUtils,
                pipListener = pipListener,
                controlsConfig = controlsConfig,
                showContentTitle = showContentTitle,
                isPipEnabled = isPipEnabled,
                onBackPressed = onBackPressed,
                onSettingsClick = {
                    exoPlayer.pause()
                    showControlsState.value(true)
                    onSettingsButtonClick(true)
                },
                onFullScreenToggle = {
                    fullScreenState.value(!isCurrentlyFullScreen)
                },
                onLockScreenToggle = {
                    lockScreenState.value(!isCurrentlyLockScreen)
                },
                onChapterClick = {
                    onChapterClick()
                },
                onCutClick = {
                    onCutClick()

                }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {

        /* ---------- CENTER AREA ---------- */

        if (isCurrentlyFullScreen && !expandSheet) {

            Row(modifier = Modifier.fillMaxSize()) {

                /* ---- BRIGHTNESS (LEFT) ---- */
                AnimatedVisibility(
                    visible = isControlsVisible,
                    modifier = Modifier
                        .weight(0.1f)
                        .fillMaxHeight(),

                    enter = slideInHorizontally(
                        initialOffsetX = { it },   // 🔥 Start from RIGHT outside screen
                        animationSpec = tween(
                            durationMillis = 600,
                            easing = LinearOutSlowInEasing
                        )
                    ) + fadeIn(
                        animationSpec = tween(
                            durationMillis = 500,
                            easing = LinearEasing
                        )
                    ),

                    exit = slideOutHorizontally(
                        targetOffsetX = { -it },   // 🔥 Move to LEFT when hiding
                        animationSpec = tween(
                            durationMillis = 500,
                            easing = FastOutLinearInEasing
                        )
                    ) + fadeOut(
                        animationSpec = tween(
                            durationMillis = 300,
                            easing = LinearEasing
                        )
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.1f),
                        contentAlignment = Alignment.Center
                    ) {

                        CustomBrightnessController(
                            playerModel = playerModel,
                            onShowControls = showControlsState.value
                        )
                    }
                }


                /* Box(
                     modifier = Modifier
                         .weight(0.1f)
                         .fillMaxHeight(),
                     contentAlignment = Alignment.Center
                 ) {
                     CustomBrightnessController(
                         playerModel = playerModel,
                         onShowControls = showControlsState.value
                     )
                 }*/

                /* ---- CENTER CONTROLS ---- */
                Box(modifier = Modifier.weight(0.8f)) {
                    CenterControls(
                        playerModel = playerModel,
                        isLoading = isLoading,
                        exoPlayer = exoPlayer,
                        castUtils = castUtils,
                        isCasting = isCasting,
                        isFullScreen = isCurrentlyFullScreen,
                        verticalOffset = (-6).dp,
                        onShowControls = showControlsState.value,
                        onForward = { showForwardIcon = true },
                        onRewind = { showRewindIcon = true },
                        onForwardHide = { showForwardIcon = false },
                        onRewindHide = { showForwardIcon = false },
                        onSeekStarted = ::dispatchSeekStarted,
                        onSeekCompleted = ::dispatchSeekCompleted,
                        isZoomed = isZoomed,
                        onZoomChange = { isZoomed = it },
                        controlsConfig = controlsConfig
                    )
                }

                /* ---- VOLUME (RIGHT) ---- */
                AnimatedVisibility(
                    visible = isControlsVisible && (controlsConfig.mute || controlsConfig.unmute),
                    modifier = Modifier
                        .weight(0.1f)
                        .fillMaxHeight(),

                    enter = slideInHorizontally(
                        initialOffsetX = { -it },   // 🔥 Start from LEFT outside screen
                        animationSpec = tween(
                            durationMillis = 600,
                            easing = LinearOutSlowInEasing
                        )
                    ) + fadeIn(
                        animationSpec = tween(
                            durationMillis = 500,
                            easing = LinearEasing
                        )
                    ),

                    exit = slideOutHorizontally(
                        targetOffsetX = { it },     // 🔥 Move to RIGHT when hiding
                        animationSpec = tween(
                            durationMillis = 500,
                            easing = FastOutLinearInEasing
                        )
                    ) + fadeOut(
                        animationSpec = tween(
                            durationMillis = 300,
                            easing = LinearEasing
                        )
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.1f),
                        contentAlignment = Alignment.Center
                    ) {

                        CustomVolumeController(
                            playerModel = playerModel,
                            exoPlayer = exoPlayer,
                            onShowControls = showControlsState.value,
                            controlsConfig = controlsConfig
                        )
                    }
                }

            }

        } else {

            /* ---- NON FULLSCREEN CENTER ---- */
            CenterControls(
                playerModel = playerModel,
                isLoading = isLoading,
                exoPlayer = exoPlayer,
                castUtils = castUtils,
                isCasting = isCasting,
                isFullScreen = isCurrentlyFullScreen,
                verticalOffset = 0.dp,
                onShowControls = showControlsState.value,
                onForward = { showForwardIcon = true },
                onRewind = { showRewindIcon = true },
                onForwardHide = { showForwardIcon = false },
                onRewindHide = { showForwardIcon = false },
                onSeekStarted = ::dispatchSeekStarted,
                onSeekCompleted = ::dispatchSeekCompleted,
                isZoomed = isZoomed,
                onZoomChange = { isZoomed = it },
                controlsConfig = controlsConfig
            )
        }

        /* ---------- SKIP INTRO BUTTON ---------- */

        AnimatedVisibility(
            visible = showSkipIntro && !expandSheet,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = if (isCurrentlyFullScreen) 56.dp else 32.dp, start = 8.dp)
        ) {
            val skipIntroButtonShape = RoundedCornerShape(8.dp)

            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.60f), skipIntroButtonShape)
                    .border(1.dp, Color.White.copy(alpha = 0.95f), skipIntroButtonShape)
                    .clickable {
                        onSkipIntroClicked(true)
                        currentPlayerModel?.skipIntro?.endTime?.let { endTime ->
                            dispatchSeekStarted(currentPosition)
                            if (isCasting && castUtils != null) castUtils.seekOnCast(endTime)
                            else exoPlayer.seekTo(endTime)
                            dispatchSeekCompleted(endTime)
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "Skip Intro",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        /* ---------- Next Episode BUTTON ---------- */

        AnimatedVisibility(
            visible = controlsConfig.next && showNextEpisode && !expandSheet,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = if (isCurrentlyFullScreen) 56.dp else 32.dp)
        ) {
            if (playerModelList != null && playerModelList.size > 1) {
                val isLastItem = index >= playerModelList.lastIndex
                val nextEpisodeButtonShape = RoundedCornerShape(8.dp)

                Box(
                    modifier = Modifier
                        .background(Color.Gray.copy(alpha = 0.60f), nextEpisodeButtonShape)
                        .border(1.dp, Color.White.copy(alpha = 0.95f), nextEpisodeButtonShape)
                        .clickable(enabled = !isLastItem) {
                            if (!isLastItem) {
                                nextEpisodeClicked = true
                                hasShownNextEpisodeControls = false // 👈 RESET
                                onNextEpisodeClick(index + 1)
                            }
                        }
                ) {

                    // 🔥 Animated progress overlay (LEFT → RIGHT)
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(nextEpisodeButtonShape)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress) // 👈 animation happens here
                                .background(Color.White)
                        )
                    }

                    // Text on top
                    Text(
                        text = "Next Episode",
                        modifier = Modifier
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .align(Alignment.Center),
                        color = Color.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }


        /* ---------- BOTTOM CONTROLS ---------- */

        AnimatedVisibility(
            visible = isControlsVisible && !expandSheet,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = if (isCurrentlyFullScreen) 0.dp else 8.dp),

            enter = slideInVertically(
                initialOffsetY = { it },   // from bottom
                animationSpec = tween(
                    durationMillis = 700,
                    easing = LinearOutSlowInEasing   // smooth start, slow end
                )
            ) + fadeIn(
                animationSpec = tween(
                    durationMillis = 600,
                    delayMillis = 100,
                    easing = LinearEasing
                )
            ),

            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(
                    durationMillis = 600,
                    easing = FastOutLinearInEasing   // smooth exit
                )
            ) + fadeOut(
                animationSpec = tween(
                    durationMillis = 400,
                    easing = LinearEasing
                )
            )
        ) {
            BottomControls(
                modifier = Modifier.align(Alignment.BottomCenter),
                playerModelList = playerModelList,
                index = index,
                isFullScreen = isCurrentlyFullScreen,
                currentPosition = currentPosition,
                duration = duration,
                exoPlayer = exoPlayer,
                onSeek = {
                    onSeek = true
                    showControlsState.value(true)
                    if (isCasting && castUtils != null) castUtils.seekOnCast(it)
                    else exoPlayer.seekTo(it)
                },
                onNext = playContent,
                cuePoints = cuePoints,
                onDragStateChange = { dragging ->
                    isDraggingSeekbar = dragging
                    if (dragging) {
                        showControlsState.value(true)
                    }
                },
                expandSheet = {
                    expandSheet = if (isCurrentlyFullScreen) {
                        it
                    } else {
                        false
                    }
                },
                onPrevious = playContent,
                onSeekStarted = ::dispatchSeekStarted,
                onSeekCompleted = ::dispatchSeekCompleted,
                controlsConfig = controlsConfig,
                showPreviousControl = showPreviousControl,
                castUtils = castUtils,
                isCasting = isCasting
            )
        }

        }

        if (expandSheet && isCurrentlyFullScreen) {
            EpisodeSelectionSheet(
                expandSheet = true,
                playerModelList = playerModelList,
                currentIndex = index,
                nowPlayingStyle = episodeNowPlayingStyle,
                isCasting = isCasting,
                exoPlayer = exoPlayer,
                onDismiss = { expandSheet = false },
                onShowControls = onShowControls,
                playContent = playContent
            )

        }
    }
}
