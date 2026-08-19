package com.app.videosdk.ui

import android.view.LayoutInflater
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.toColorInt
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.app.videosdk.R
import com.app.videosdk.listener.AdsListener
import com.app.videosdk.listener.PipListener
import com.app.videosdk.listener.PlayerController
import com.app.videosdk.listener.PlayerStateListener
import com.app.videosdk.model.AgeRatingResolver
import com.app.videosdk.model.Chapter
import com.app.videosdk.model.CuePoint
import com.app.videosdk.model.EpisodeNowPlayingStyle
import com.app.videosdk.model.PlayerAdsConfig
import com.app.videosdk.model.PlayerAnalyticsEventType
import com.app.videosdk.model.PlayerConfig
import com.app.videosdk.model.PlayerDiagnosticSeverity
import com.app.videosdk.model.PlayerFeatureTier
import com.app.videosdk.model.PlayerModel
import com.app.videosdk.model.resolveFeatureSet
import com.app.videosdk.model.withResolvedFeatures
import com.app.videosdk.player.PlayerFactory
import com.app.videosdk.player.PlaybackRecoveryAction
import com.app.videosdk.player.PlaybackRecoveryState
import com.app.videosdk.ui.ads.LShapeAdContainer
import com.app.videosdk.ui.chapter.ChapterDrawer
import com.app.videosdk.utils.CastPlaybackState
import com.app.videosdk.utils.CastUtils
import com.app.videosdk.utils.PlayerMode
import com.app.videosdk.utils.SdkLogger
import com.app.videosdk.utils.emitAnalytics
import com.app.videosdk.utils.emitDiagnostic
import com.google.android.gms.cast.framework.CastContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

@OptIn(UnstableApi::class)
@Composable
fun MtvVideoPlayerSdk(
    contentList: List<PlayerModel>? = null,
    index: Int? = 0,
    pipListener: PipListener? = null,
    isInPipMode: Boolean = false,
    isDeepLink: Boolean? = false,
    playerMode: PlayerMode = PlayerMode.MINI,
    playerStateListener: PlayerStateListener? = null,
    controller: PlayerController? = null,
    isMutedInitially: Boolean = true,
    onPlayerBack: (Boolean) -> Unit = {},
    setFullScreen: (Boolean) -> Unit = {},
    onIndexChanged: (Int) -> Unit = {},
    episodeNowPlayingStyle: EpisodeNowPlayingStyle = EpisodeNowPlayingStyle(),
    showControls: Boolean = true,
    playerConfig: PlayerConfig = PlayerConfig(),
    onCurrentIndexChanged: (Int) -> Unit = {},
    onPreviewPrimaryAction: () -> Unit = {},
    onPreviewSecondaryAction: () -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    var contentDuration by remember { mutableLongStateOf(0L) }


    val playerView = remember {
        (LayoutInflater.from(context)
            .inflate(R.layout.mtv_video_player_view, null, false) as PlayerView).apply {
            useController = false
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
    }

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

    fun notifyIndexChanged(newIndex: Int) {
        onIndexChanged(newIndex)
        onCurrentIndexChanged(newIndex)
    }

    LaunchedEffect(index, contentList) {
        val size = contentList?.size ?: 0
        if (size == 0) {
            selectedIndex.intValue = 0
            return@LaunchedEffect
        }

        val safeExternalIndex = index?.coerceIn(0, size - 1) ?: 0
        selectedIndex.intValue = safeExternalIndex

        if (index != null && index != safeExternalIndex) {
            notifyIndexChanged(safeExternalIndex)
        }
    }

    fun changeSelectedIndex(newIndex: Int) {
        val size = contentList?.size ?: 0
        if (size == 0) return

        val safeIndex = newIndex.coerceIn(0, size - 1)
        if (selectedIndex.intValue == safeIndex) return

        selectedIndex.intValue = safeIndex
        notifyIndexChanged(safeIndex)
    }

    var requestedVideo by remember { mutableStateOf<PlayerModel?>(null) }
    var playRequestId by remember { mutableLongStateOf(0L) }
    val resolvedFeatures = remember(playerConfig) {
        playerConfig.resolveFeatureSet()
    }
    val effectivePlayerConfig = remember(playerConfig, resolvedFeatures) {
        playerConfig.withResolvedFeatures(resolvedFeatures)
    }

    LaunchedEffect(effectivePlayerConfig.logging) {
        SdkLogger.configure(effectivePlayerConfig.logging)
    }

    DisposableEffect(controller) {
        val listener: (PlayerModel, Long) -> Unit = { video, requestId ->
            requestedVideo = video
            playRequestId = requestId
        }
        controller?.attachPlayRequestListener(listener)
        onDispose { controller?.detachPlayRequestListener(listener) }
    }

    val rawPlayerModel = requestedVideo ?: contentList?.getOrNull(selectedIndex.intValue)
    val playerModel = remember(rawPlayerModel, resolvedFeatures) {
        rawPlayerModel?.withResolvedFeatures(resolvedFeatures)
    }
    val ageRating = remember(playerModel) { AgeRatingResolver.resolve(playerModel) }
    val activeContentList = remember(requestedVideo, contentList, playerModel, resolvedFeatures) {
        if (requestedVideo != null && playerModel != null) {
            listOf(playerModel)
        } else {
            contentList?.map { it.withResolvedFeatures(resolvedFeatures) }
        }
    }
    val activeIndex = if (requestedVideo != null) 0 else selectedIndex.intValue

    var chromeModeState by remember {
        mutableStateOf(
            PlayerChromeModeState.initial(
                mode = playerMode,
                orientation = configuration.orientation
            )
        )
    }

    LaunchedEffect(playerMode) {
        chromeModeState =
            chromeModeState.withRequestedMode(playerMode, configuration.orientation)
    }

    LaunchedEffect(chromeModeState.requestedMode, configuration.orientation) {
        chromeModeState = chromeModeState.withOrientation(configuration.orientation)
    }

    LaunchedEffect(isInPipMode) {
        chromeModeState = chromeModeState.withPip(isInPipMode)
        playerStateListener?.onPipModeChanged(isInPipMode)
        playerStateListener.emitAnalytics(
            enabled = effectivePlayerConfig.analyticsEnabled,
            type = PlayerAnalyticsEventType.PIP_CHANGED,
            contentId = playerModel?.id,
            attributes = mapOf("enabled" to isInPipMode.toString())
        )
    }

    val currentMode = chromeModeState.requestedMode
    val displayMode = chromeModeState.displayMode
    val isFullScreen = chromeModeState.isFullScreen
    val isLockScreen = chromeModeState.isLocked
    val showUnlockConfirm = chromeModeState.showUnlockConfirm
    val isLockOverlayVisible = chromeModeState.isLockOverlayVisible
    val pipEnabled = chromeModeState.isInPip

    FullScreenHandler(isFullScreen)
    var isControllerVisible by remember { mutableStateOf(false) }
    LaunchedEffect(showControls) {
        if (!showControls) {
            isControllerVisible = false
        }
    }

    var isLoading by remember { mutableStateOf(false) }
    var isSettingsClick by remember { mutableStateOf(false) }
    var isAdsShowing by remember { mutableStateOf(false) }
    var showLShapeAd by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var lastVideoSize by remember { mutableStateOf<VideoSize?>(null) }
    var hasRenderedFirstFrame by remember(playerModel, activeIndex, playRequestId) {
        mutableStateOf(false)
    }
    var currentChapter by remember { mutableStateOf<Chapter?>(null) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var hasActivatedFill by remember { mutableStateOf(false) }

    var isSkipIntroClicked by remember(selectedIndex.intValue) { mutableStateOf(false) }
    var isPreviewDialogVisible by remember(playerModel, activeIndex, playRequestId) {
        mutableStateOf(false)
    }

    val imaCuePoints = remember {
        mutableStateListOf<CuePoint>()
    }

    val isLive = playerModel?.isLive == true
    val defaultPlayerConfig = remember { PlayerConfig() }
    val defaultAdsConfig = remember { PlayerAdsConfig() }
    val isDefaultPlayerConfig = effectivePlayerConfig == defaultPlayerConfig
    val hasExplicitAdsConfig = effectivePlayerConfig.ads != defaultAdsConfig
    val hasExplicitControlsConfig =
        effectivePlayerConfig.controls != defaultPlayerConfig.controls
    val consumedPrerollKeys = remember { mutableSetOf<String>() }

    val effectiveVmapAdsEnabled =
        if (hasExplicitAdsConfig) {
            effectivePlayerConfig.ads.googleAdsEnabled && effectivePlayerConfig.ads.vmapAdsEnabled
        } else {
            playerModel?.adsConfig?.enableAds == true
        }

    val effectiveBannerAdsEnabled =
        if (hasExplicitAdsConfig) {
            effectivePlayerConfig.ads.googleAdsEnabled &&
                effectivePlayerConfig.ads.bannerAdsEnabled
        } else {
            playerModel?.gamAdsConfig?.isAdsEnabled == true
        }

    val effectiveAdsConfig = remember(playerModel?.adsConfig, effectiveVmapAdsEnabled) {
        playerModel?.adsConfig?.copy(enableAds = effectiveVmapAdsEnabled)
    }

    val playbackUrl = remember(playerModel) {
        val url = when {
            // Live content always uses liveUrl
            isLive && !playerModel.liveUrl.isNullOrEmpty() -> playerModel.liveUrl

            // DRM content must use MPD
            playerModel?.drm == "1" && !playerModel.mpdUrl.isNullOrEmpty() -> playerModel.mpdUrl

            // Non‑DRM: prefer HLS, then MPD as fallback
            !playerModel?.hlsUrl.isNullOrEmpty() -> playerModel.hlsUrl
            !playerModel?.mpdUrl.isNullOrEmpty() -> playerModel.mpdUrl
            !playerModel?.videoUrl.isNullOrEmpty() -> playerModel.videoUrl

            else -> ""
        }

        SdkLogger.debug(
            "Selected playbackUrl: ${url.redactUrlForLog()} (DRM=${playerModel?.drm}, isLive=$isLive)"
        )

        url
    }
    val settingsSelectedItems = remember(selectedIndex.intValue, playbackUrl) {
        mutableStateMapOf<Int, Int>()
    }

    val effectiveSubtitleEnabled =
        resolvedFeatures.subtitles &&
            (playerConfig.featureTier != PlayerFeatureTier.LEGACY_COMPAT ||
                isDefaultPlayerConfig ||
                effectivePlayerConfig.subtitleEnabled)
    val subtitleUri = if (isLive || !effectiveSubtitleEnabled) "" else playerModel?.srt.orEmpty()
    val prerollKey = remember(playerModel, effectiveAdsConfig?.adTagUrl, playbackUrl) {
        buildPrerollKey(
            playerModel = playerModel,
            adTagUrl = effectiveAdsConfig?.adTagUrl,
            playbackUrl = playbackUrl
        )
    }
    val playbackSessionKey = remember(playerModel?.id, playbackUrl, activeIndex, playRequestId) {
        buildPlaybackSessionKey(
            playerModel = playerModel,
            playbackUrl = playbackUrl,
            activeIndex = activeIndex,
            playRequestId = playRequestId
        )
    }
    val shouldAutoPlay = remember(
        playbackUrl,
        currentMode,
        isDefaultPlayerConfig,
        effectivePlayerConfig
    ) {
        if (playbackUrl.isNullOrBlank()) {
            false
        } else if (isDefaultPlayerConfig) {
            true
        } else {
            when (currentMode) {
                PlayerMode.REELS -> effectivePlayerConfig.autoPlayAssets
                PlayerMode.MINI ->
                    effectivePlayerConfig.autoPlayFeature ||
                        effectivePlayerConfig.autoPlayDetail

                PlayerMode.FULL_SCREEN ->
                    effectivePlayerConfig.autoPlayDetail ||
                        effectivePlayerConfig.autoPlayFeature
            }
        }
    }

    var containerSize by remember { mutableStateOf(Size.Zero) }
    var fillScale by remember { mutableFloatStateOf(1f) }
    var isFilled by remember { mutableStateOf(false) }
    var zoomAccumulator by remember { mutableFloatStateOf(1f) }

    val animatedScale by animateFloatAsState(
        targetValue = if (isFilled) fillScale else 1f,
        animationSpec = tween(220),
        label = "scale"
    )

    val adsListener = remember(prerollKey, playerStateListener) {
        object : AdsListener {

            override fun onAdsLoaded() {
                isControllerVisible = false
                isAdsShowing = true
                playerStateListener.emitAnalytics(
                    enabled = effectivePlayerConfig.analyticsEnabled,
                    type = PlayerAnalyticsEventType.AD_LOADED,
                    contentId = playerModel?.id
                )
            }

            override fun onAdStarted() {
                prerollKey?.let { consumedPrerollKeys.add(it) }
                isControllerVisible = false
                isAdsShowing = true
                showLShapeAd = false
                playerStateListener?.onAdStateChanged(true)
                playerStateListener.emitAnalytics(
                    enabled = effectivePlayerConfig.analyticsEnabled,
                    type = PlayerAnalyticsEventType.AD_STARTED,
                    contentId = playerModel?.id
                )
            }

            override fun onAdCompleted() {
                isControllerVisible = true
                isAdsShowing = false
                playerStateListener?.onAdStateChanged(false)
                playerStateListener.emitAnalytics(
                    enabled = effectivePlayerConfig.analyticsEnabled,
                    type = PlayerAnalyticsEventType.AD_COMPLETED,
                    contentId = playerModel?.id
                )

                showLShapeAd = true

                coroutineScope.launch {
                    delay(200000)
                    showLShapeAd = false
                }
            }

            override fun onAllAdsCompleted() {
                isControllerVisible = true
                isAdsShowing = false
                playerStateListener.emitAnalytics(
                    enabled = effectivePlayerConfig.analyticsEnabled,
                    type = PlayerAnalyticsEventType.AD_COMPLETED,
                    contentId = playerModel?.id,
                    attributes = mapOf("allAdsCompleted" to "true")
                )
            }

            override fun onAdError(message: String) {
                isControllerVisible = true
                isAdsShowing = false
                SdkLogger.error("Ad error: $message")
                playerStateListener.emitAnalytics(
                    enabled = effectivePlayerConfig.analyticsEnabled,
                    type = PlayerAnalyticsEventType.AD_ERROR,
                    contentId = playerModel?.id,
                    attributes = mapOf("message" to message)
                )
            }
        }
    }


    var playbackRecoveryState by remember(playbackSessionKey) {
        mutableStateOf(PlaybackRecoveryState())
    }

    val playerWithAds = remember(
        playbackSessionKey,
        subtitleUri
    ) {
        val model = playerModel ?: return@remember null
        SdkLogger.configure(effectivePlayerConfig.logging)
        val urlString = playbackUrl
        val isPrerollConsumed = prerollKey != null && consumedPrerollKeys.contains(prerollKey)
        val adsConfigForNewPlayer =
            effectiveAdsConfig?.copy(enableAds = effectiveVmapAdsEnabled && !isPrerollConsumed)

        SdkLogger.debug(
            "Creating player with url: ${urlString.redactUrlForLog()}, drmToken: ${if (model.drmToken.isNullOrBlank()) "null" else "present"}"
        )

        PlayerFactory.createPlayer(
            context = context,
            activeContentList,
            activeIndex,
            videoUrl = urlString,
            drmToken = model.drmToken,
            srt = subtitleUri,
            playerView = playerView,
            adsConfig = adsConfigForNewPlayer,
            adsListener = adsListener,
            playWhenReady = shouldAutoPlay,
            onRecoveryStateChanged = { state ->
                coroutineScope.launch {
                    playbackRecoveryState = state
                }
            }
        )
    }

    LaunchedEffect(playbackRecoveryState) {
        if (playbackRecoveryState.action != PlaybackRecoveryAction.NONE) {
            isLoading = true
            playerStateListener.emitDiagnostic(
                enabled = effectivePlayerConfig.diagnosticsEnabled,
                severity = PlayerDiagnosticSeverity.WARNING,
                code = playbackRecoveryState.reason.name,
                message = playbackRecoveryState.message.orEmpty()
                    .ifBlank { "Playback recovery action applied." },
                contentId = playerModel?.id,
                attributes = mapOf(
                    "action" to playbackRecoveryState.action.name,
                    "attempt" to playbackRecoveryState.attempt.toString()
                )
            )
        }
    }

    val exoPlayer = playerWithAds?.first
    val adsLoader = playerWithAds?.second
    val freePreview = effectivePlayerConfig.freePreview
    val freePreviewEnd = effectivePlayerConfig.freePreviewEnd
    if (resolvedFeatures.cast) {
        CastContext.getSharedInstance(context)
    }
    val castUtils = remember(context, exoPlayer, playerStateListener, resolvedFeatures.cast) {
        if (resolvedFeatures.cast) {
            exoPlayer?.let {
                CastUtils(
                    context = context,
                    exoPlayer = it,
                    playerStateListener = playerStateListener,
                    analyticsEnabled = effectivePlayerConfig.analyticsEnabled,
                    diagnosticsEnabled = effectivePlayerConfig.diagnosticsEnabled
                )
            }
        } else {
            null
        }
    }
    val castPlaybackState = castUtils
        ?.castState
        ?.collectAsState()
        ?: remember { mutableStateOf(CastPlaybackState()) }
    val isCasting = castPlaybackState.value.isCasting
    var playbackProgress by remember(playbackSessionKey) {
        mutableStateOf(PlaybackProgressState())
    }

    LaunchedEffect(castUtils, playerModel) {
        castUtils?.setupCastSession(playerModel)
    }

    DisposableEffect(castUtils) {
        onDispose { castUtils?.release() }
    }

    LaunchedEffect(isMutedInitially, exoPlayer) {
        exoPlayer?.volume = if (isMutedInitially) 0f else 1f
    }

    PlaybackProgressObserver(
        exoPlayer = exoPlayer,
        castUtils = castUtils,
        isCasting = isCasting,
        resetKey = playbackSessionKey,
        onProgressChanged = { progress ->
            playbackProgress = progress
            if (progress.durationMs > 0L) {
                contentDuration = progress.durationMs
            }
        }
    )

    PreviewLimitController(
        player = exoPlayer,
        currentPositionMs = playbackProgress.positionMs,
        freePreview = freePreview,
        freePreviewEnd = freePreviewEnd,
        resetKey = playbackSessionKey,
        onPreviewLimitReached = { showDialog ->
            if (showDialog) {
                isPreviewDialogVisible = true
            }
        }
    )

    LaunchedEffect(exoPlayer) {
        controller?.exoPlayer = exoPlayer
    }

    LaunchedEffect(exoPlayer, currentMode) {
        exoPlayer?.repeatMode =
            if (currentMode == PlayerMode.REELS) {
                Player.REPEAT_MODE_ONE
            } else {
                Player.REPEAT_MODE_OFF
            }
    }

    var ageRatingPresentationKey by remember(playerModel, activeIndex, playRequestId) {
        mutableLongStateOf(0L)
    }
    var isAgeRatingPresentationActive by remember(playerModel, activeIndex, playRequestId) {
        mutableStateOf(false)
    }

    PlayerEventObserver(
        exoPlayer = exoPlayer,
        adsLoader = adsLoader,
        ageRating = ageRating,
        containerSize = containerSize,
        contentListSize = contentList?.size ?: 0,
        selectedIndex = selectedIndex.intValue,
        contentId = playerModel?.id,
        analyticsEnabled = effectivePlayerConfig.analyticsEnabled,
        diagnosticsEnabled = effectivePlayerConfig.diagnosticsEnabled,
        playerStateListener = playerStateListener,
        imaCuePoints = imaCuePoints,
        onLoadingChanged = { isLoading = it },
        onContentDurationChanged = { contentDuration = it },
        onFirstFrameRendered = { hasRenderedFirstFrame = true },
        onVideoSizeChanged = { lastVideoSize = it },
        onFillScaleChanged = { fillScale = it },
        onFilledChanged = { isFilled = it },
        onZoomAccumulatorChanged = { zoomAccumulator = it },
        onActivatedFillChanged = { hasActivatedFill = it },
        onAgeRatingPresentationRequested = { ageRatingPresentationKey++ },
        onPlayNextContent = { changeSelectedIndex(selectedIndex.intValue + 1) }
    )

    LaunchedEffect(containerSize, showLShapeAd) {

        val videoSize = lastVideoSize ?: return@LaunchedEffect

        if (videoSize.width == 0 || containerSize == Size.Zero) return@LaunchedEffect

        fillScale = max(
            containerSize.width / videoSize.width,
            containerSize.height / videoSize.height
        )

        isFilled = false
        zoomAccumulator = 1f
    }

    LaunchedEffect(selectedIndex.intValue) {
        imaCuePoints.clear()
    }

    PlayerTimingObserver(
        exoPlayer = exoPlayer,
        playerModel = playerModel,
        contentDuration = contentDuration,
        currentPositionMs = playbackProgress.positionMs,
        resetKey = playbackSessionKey,
        effectiveBannerAdsEnabled = effectiveBannerAdsEnabled,
        hasExplicitAdsConfig = hasExplicitAdsConfig,
        adsConfig = effectivePlayerConfig.ads,
        isAdsShowing = isAdsShowing,
        pipEnabled = pipEnabled,
        isFullScreen = isFullScreen,
        isSkipIntroClicked = isSkipIntroClicked,
        onShowControls = { isControllerVisible = true },
        onShowLShapeAd = { showLShapeAd = true },
        onHideLShapeAd = { showLShapeAd = false },
        onCurrentChapterChanged = { currentChapter = it }
    )

    LaunchedEffect(isLockScreen, isLockOverlayVisible) {
        if (isLockScreen && isLockOverlayVisible) {
            delay(3000)
            chromeModeState = chromeModeState.withLockOverlayVisible(false)
        }
    }

    LaunchedEffect(isFullScreen) {
        if (!isFullScreen) {
            zoomAccumulator = 1f
            isFilled = false
            hasActivatedFill = false
        }
    }

    val playerSizeModifier = when (displayMode) {
        PlayerMode.FULL_SCREEN -> Modifier.fillMaxSize()
        PlayerMode.REELS -> Modifier.fillMaxSize()
        PlayerMode.MINI -> Modifier.aspectRatio(16f / 9f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(durationMillis = 260))
            .then(playerSizeModifier)
    ) {

        // Chapter drawer opens from the right while player chrome remains LTR.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {

            ModalNavigationDrawer(
                drawerState = drawerState,
                gesturesEnabled = true,
                drawerContent = {
                    if (playerModel?.isChapterEnabled == true &&
                        playerModel.chapters?.isNotEmpty() == true
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            ChapterDrawer(
                                chapters = playerModel.chapters,
                                currentChapter = currentChapter,
                                onChapterClick = { chapter ->
                                    exoPlayer?.seekTo(chapter.startMs)
                                    coroutineScope.launch { drawerState.close() }
                                }
                            )
                        }
                    }
                }

            ) {

                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {

                    LShapeAdContainer(
                        playerModel = playerModel,
                        isFullScreen = isFullScreen,
                        isVisible = showLShapeAd && !isAdsShowing && !pipEnabled,
                        bannerAdsEnabled = effectiveBannerAdsEnabled,
                        closeButtonEnabled =
                            hasExplicitAdsConfig && effectivePlayerConfig.ads.closeButtonEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(animationSpec = tween(durationMillis = 260))
                            .then(playerSizeModifier)
                            .background(Color.Black),
                        onCloseClick = { showLShapeAd = false }
                    ) {

                        AndroidView(
                            factory = { playerView },
                            update = { view ->

                                if (view.player !== exoPlayer) {
                                    view.player = exoPlayer
                                }

                                view.subtitleView?.apply {

                                    setApplyEmbeddedStyles(false)
                                    setApplyEmbeddedFontSizes(false)

                                    setStyle(
                                        CaptionStyleCompat(
                                            android.graphics.Color.WHITE,
                                            "#80000000".toColorInt(),
                                            android.graphics.Color.TRANSPARENT,
                                            CaptionStyleCompat.EDGE_TYPE_NONE,
                                            android.graphics.Color.TRANSPARENT,
                                            android.graphics.Typeface.DEFAULT
                                        )
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .onSizeChanged { size ->
                                    containerSize = Size(
                                        width = size.width.toFloat(),
                                        height = size.height.toFloat()
                                    )
                                }
                                .graphicsLayer {

                                    val finalScale =
                                        if (isFullScreen) zoomAccumulator else 1f

                                    scaleX = finalScale
                                    scaleY = finalScale
                                }
                        )

                        if (isLive && !hasRenderedFirstFrame) {
                            LiveImagePlaceholder(effectivePlayerConfig.liveImageUrl)
                        }

                        PlayerWatermarkOverlay(effectivePlayerConfig.watermark)

                        PlayerGestureLayer(
                            isFullScreen = isFullScreen,
                            pipEnabled = pipEnabled,
                            isAdsShowing = isAdsShowing,
                            isLockScreen = isLockScreen,
                            isSettingsClick = isSettingsClick,
                            showControls = showControls,
                            fillScale = fillScale,
                            zoomAccumulator = zoomAccumulator,
                            hasActivatedFill = hasActivatedFill,
                            onActivatedFillChanged = { hasActivatedFill = it },
                            onFilledChanged = { isFilled = it },
                            onZoomAccumulatorChanged = { zoomAccumulator = it },
                            onLockOverlayVisibleChanged = {
                                chromeModeState = chromeModeState.withLockOverlayVisible(it)
                            },
                            onToggleControllerVisibility = {
                                isControllerVisible = !isControllerVisible
                            }
                        )

                        PlayerChrome(
                            showControls = showControls,
                            isControllerVisible = isControllerVisible,
                            pipEnabled = pipEnabled,
                            isLockScreen = isLockScreen,
                            isLockOverlayVisible = isLockOverlayVisible,
                            isInPipMode = isInPipMode,
                            isLoading = isLoading,
                            exoPlayer = exoPlayer,
                            activeContentList = activeContentList,
                            activeIndex = activeIndex,
                            contentDuration = contentDuration,
                            playbackProgress = playbackProgress,
                            contentId = playerModel?.id,
                            analyticsEnabled = effectivePlayerConfig.analyticsEnabled,
                            diagnosticsEnabled = effectivePlayerConfig.diagnosticsEnabled,
                            playerStateListener = playerStateListener,
                            pipListener = pipListener,
                            isFullScreen = isFullScreen,
                            playerModel = playerModel,
                            castUtils = castUtils,
                            castEnabled = resolvedFeatures.cast,
                            episodeNowPlayingStyle = episodeNowPlayingStyle,
                            cuePoints = imaCuePoints,
                            isSkipIntroClicked = isSkipIntroClicked,
                            ageRating = ageRating,
                            ageRatingPresentationKey = ageRatingPresentationKey,
                            isAgeRatingPresentationActive = isAgeRatingPresentationActive,
                            showUnlockConfirm = showUnlockConfirm,
                            isSettingsClick = isSettingsClick,
                            settingsSelectedItems = settingsSelectedItems,
                            playbackUrl = playbackUrl,
                            playerConfig = effectivePlayerConfig,
                            featureSet = resolvedFeatures,
                            hasExplicitControlsConfig = hasExplicitControlsConfig,
                            currentMode = currentMode,
                            onFullScreenChanged = { full ->
                                setFullScreen(full)
                                playerStateListener?.onFullScreenChanged(full)
                                playerStateListener.emitAnalytics(
                                    enabled = effectivePlayerConfig.analyticsEnabled,
                                    type = PlayerAnalyticsEventType.FULLSCREEN_CHANGED,
                                    contentId = playerModel?.id,
                                    positionMs = playbackProgress.positionMs,
                                    durationMs = playbackProgress.durationMs,
                                    attributes = mapOf("enabled" to full.toString())
                                )
                            },
                            onCurrentModeChanged = {
                                chromeModeState =
                                    chromeModeState.withRequestedMode(
                                        it,
                                        configuration.orientation
                                    )
                            },
                            onLockScreenChanged = {
                                chromeModeState = chromeModeState.withLock(it)
                            },
                            onShowUnlockConfirmChanged = {
                                chromeModeState = chromeModeState.withUnlockConfirm(it)
                            },
                            onPipEnabledChanged = {
                                chromeModeState = chromeModeState.withPip(it)
                                playerStateListener?.onPipModeChanged(it)
                                playerStateListener.emitAnalytics(
                                    enabled = effectivePlayerConfig.analyticsEnabled,
                                    type = PlayerAnalyticsEventType.PIP_CHANGED,
                                    contentId = playerModel?.id,
                                    positionMs = playbackProgress.positionMs,
                                    durationMs = playbackProgress.durationMs,
                                    attributes = mapOf("enabled" to it.toString())
                                )
                            },
                            onSettingsClickChanged = { isSettingsClick = it },
                            onBackPressed = { onPlayerBack(true) },
                            onShowControlsChanged = { isControllerVisible = it },
                            onPlayContent = { changeSelectedIndex(it) },
                            onSkipIntroClicked = { isSkipIntroClicked = it },
                            onNextEpisodeClick = { changeSelectedIndex(it) },
                            onAgeRatingPresentationActiveChanged = {
                                isAgeRatingPresentationActive = it
                            },
                            onChapterClick = {
                                coroutineScope.launch { drawerState.open() }
                            }
                        )

                    }
                }
            }
        }
    }

    if (isPreviewDialogVisible) {
        PreviewLimitDialog(
            freePreview = freePreview,
            freePreviewEnd = freePreviewEnd,
            onPrimary = {
                isPreviewDialogVisible = false
                onPreviewPrimaryAction()
            },
            onSecondary = {
                isPreviewDialogVisible = false
                onPreviewSecondaryAction()
            },
            onDismiss = {
                isPreviewDialogVisible = false
            }
        )
    }

}
