package com.app.videosdk.utils

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.app.videosdk.listener.PlayerStateListener
import com.app.videosdk.model.PlayerAnalyticsEventType
import com.app.videosdk.model.PlayerDiagnosticSeverity
import com.app.videosdk.model.PlayerModel
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaError
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.Session
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

class CastUtils(
    context: Context,
    private val exoPlayer: ExoPlayer,
    private val playerStateListener: PlayerStateListener? = null,
    private val analyticsEnabled: Boolean = false,
    private val diagnosticsEnabled: Boolean = false
) {

    private val sessionManager = CastContext.getSharedInstance(context).sessionManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var playerModel: PlayerModel? = null
    private var observedMediaClient: RemoteMediaClient? = null
    private var activeLoadToken = 0
    private var waitingForReceiverPlayback = false
    private var castPlaybackFailureDispatched = false
    private var lastLoggedStatusKey: String? = null
    private var playbackStartTimeoutRunnable: Runnable? = null
    private val _castState = MutableStateFlow(CastPlaybackState())
    val castState: StateFlow<CastPlaybackState> = _castState.asStateFlow()

    private data class CastMediaSource(
        val url: String,
        val contentType: String,
        val streamType: Int
    )

    private val remoteMediaClientCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            handleRemoteMediaStatusUpdated("status")
        }

        override fun onMetadataUpdated() {
            handleRemoteMediaStatusUpdated("metadata")
        }

        override fun onMediaError(mediaError: MediaError) {
            handleReceiverMediaError(mediaError)
        }
    }

    private val sessionListener = object : SessionManagerListener<Session> {
        override fun onSessionStarted(session: Session, sessionId: String) {
            publishCastState()
            resumeCasting()
        }

        override fun onSessionResumed(session: Session, wasSuspended: Boolean) {
            publishCastState()
            resumeCasting()
        }

        override fun onSessionEnded(session: Session, error: Int) {
            stopCasting()
        }

        override fun onSessionSuspended(session: Session, reason: Int) {
            publishCastState()
            sessionManager.currentCastSession?.let {
                if (it.isConnected) {
                    resumeCasting()
                } else {
                    startCasting(false)
                }
            }
        }

        override fun onSessionStarting(session: Session) {}
        override fun onSessionResuming(session: Session, sessionId: String) {}
        override fun onSessionEnding(session: Session) {}
        override fun onSessionResumeFailed(session: Session, error: Int) {}
        override fun onSessionStartFailed(session: Session, error: Int) {}
    }

    private fun startOrRestartCastSession(model: PlayerModel, reset: Boolean) {
        val mediaClient = getRemoteMediaClient() ?: return
        val mediaSource = resolveCastMediaSource(model) ?: run {
            SdkLogger.error("Cast load skipped: no playable URL for content id=${model.id}")
            playerStateListener.emitDiagnostic(
                enabled = diagnosticsEnabled,
                severity = PlayerDiagnosticSeverity.ERROR,
                code = "cast_no_playable_url",
                message = "Cast load skipped because the content has no playable URL.",
                contentId = model.id
            )
            return
        }
        observeRemoteMediaClient(mediaClient)
        publishCastState(mediaClient)
        activeLoadToken += 1
        val loadToken = activeLoadToken
        waitingForReceiverPlayback = true
        castPlaybackFailureDispatched = false
        lastLoggedStatusKey = null

        // Get the current playback position from ExoPlayer if available
        var currentPosition: Long = exoPlayer.currentPosition

        // If a Cast session already exists, try getting the position from the Cast client
        if (mediaClient.hasMediaSession()) {
            currentPosition = mediaClient.approximateStreamPosition
        }

        pauseLocalPlayback()
        val castCustomData = buildCastCustomData(model)
        val mediaInfo = MediaInfo.Builder(mediaSource.url).apply {
            setContentUrl(mediaSource.url)
            setStreamType(mediaSource.streamType)
            setContentType(mediaSource.contentType)
            setMetadata(buildMediaMetadata(model))
            castCustomData?.let { setCustomData(it) }
        }.build()

        val mediaLoadRequestData = MediaLoadRequestData.Builder().apply {
            setMediaInfo(mediaInfo)
            setAutoplay(true)
            castCustomData?.let { setCustomData(it) }
            setCurrentTime(
                if (reset) {
                    0L
                } else {
                    currentPosition
                }
            ) // Seek to the last known position
        }.build()

        logPreparedMediaInfo(mediaInfo, mediaSource, reset, currentPosition, castCustomData)
        schedulePlaybackStartTimeout(loadToken)

        mediaClient.load(mediaLoadRequestData).setResultCallback { result ->
            if (loadToken != activeLoadToken) return@setResultCallback

            val status = result.status
            if (status.isSuccess) {
                SdkLogger.debug("Cast media load accepted by sender; waiting for receiver playback state")
                logRemoteMediaStatus("load-accepted", mediaClient.mediaStatus, mediaClient)
                mediaClient.requestStatus()
            } else {
                dispatchCastPlaybackFailure(
                    message = "Cast media load request failed: code=${status.statusCode}, message=${status.statusMessage}",
                    mediaStatus = mediaClient.mediaStatus,
                    mediaClient = mediaClient
                )
            }
        }
    }


    /**
     * Sets up the Cast session only when an episode changes
     */
    fun setupCastSession(newPlayerModel: PlayerModel?) {
        if (newPlayerModel == null || newPlayerModel == playerModel) return // Avoid redundant setup

        this.playerModel = newPlayerModel

        sessionManager.apply {
            removeSessionManagerListener(sessionListener, Session::class.java)
            addSessionManagerListener(sessionListener, Session::class.java)
        }
        publishCastState()

        sessionManager.currentCastSession?.let {
            startCasting(true)
        }
    }

    fun release() {
        sessionManager.removeSessionManagerListener(sessionListener, Session::class.java)
        cancelPlaybackStartTimeout()
        unobserveRemoteMediaClient()
        publishCastState()
    }

    private fun startCasting(reset: Boolean) {
        playerModel?.let { model ->
            startOrRestartCastSession(model, reset)
        }
    }

    private fun stopCasting() {
        cancelPlaybackStartTimeout()
        waitingForReceiverPlayback = false
        stopMediaOnCast()
        unobserveRemoteMediaClient()
        publishCastState()
        resumeLocalPlayback()
    }

    private fun resumeCasting() {
        sessionManager.currentCastSession?.let {
            if (playerModel != null) {
                startCasting(false)
            }
        }
    }

    private fun buildMediaMetadata(model: PlayerModel): MediaMetadata {
        return MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(
                MediaMetadata.KEY_TITLE,
                model.episodeTitle
                    ?: model.title
                    ?: model.seasonTitle
                    ?: "Unknown Title"
            )
            val subtitle = model.episodeDescription
                ?: model.description
                ?: model.seasonDescription
            subtitle?.let { putString(MediaMetadata.KEY_SUBTITLE, it) }

            val imageUrl = model.imageUrl ?: model.thumbnail
            if (!imageUrl.isNullOrBlank()) {
                addImage(WebImage(Uri.parse(imageUrl)))
            }
        }
    }

    private fun buildCastCustomData(model: PlayerModel): JSONObject? {
        if (model.drm != "1") return null

        val customData = JSONObject().apply {
            put("drmScheme", WIDEVINE_SCHEME)
            put("drmType", WIDEVINE_SCHEME)
            put("protectionSystem", WIDEVINE_PROTECTION_SYSTEM)
        }

        model.drmToken
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { licenseUrl ->
                customData.put("licenseUrl", licenseUrl)
                customData.put("drmLicenseUrl", licenseUrl)
                customData.put("widevineLicenseUrl", licenseUrl)
                customData.put("drmToken", licenseUrl)
                customData.put(
                    "drm",
                    JSONObject().apply {
                        put("scheme", WIDEVINE_SCHEME)
                        put("protectionSystem", WIDEVINE_PROTECTION_SYSTEM)
                        put("licenseUrl", licenseUrl)
                    }
                )
            }

        return customData
    }

    private fun observeRemoteMediaClient(mediaClient: RemoteMediaClient) {
        if (observedMediaClient === mediaClient) return

        observedMediaClient?.unregisterCallback(remoteMediaClientCallback)
        observedMediaClient = mediaClient
        mediaClient.registerCallback(remoteMediaClientCallback)
    }

    private fun unobserveRemoteMediaClient() {
        observedMediaClient?.unregisterCallback(remoteMediaClientCallback)
        observedMediaClient = null
    }

    private fun publishCastState(mediaClient: RemoteMediaClient? = getRemoteMediaClient()) {
        val mediaStatus = mediaClient?.mediaStatus
        val mediaInfo = mediaStatus?.mediaInfo ?: mediaClient?.mediaInfo

        _castState.value = CastPlaybackState(
            isCasting = mediaClient?.hasMediaSession() == true,
            playerState = mediaStatus?.playerState
                ?: mediaClient?.playerState
                ?: MediaStatus.PLAYER_STATE_UNKNOWN,
            idleReason = mediaStatus?.idleReason
                ?: mediaClient?.idleReason
                ?: MediaStatus.IDLE_REASON_NONE,
            contentType = mediaInfo?.contentType,
            contentId = mediaInfo?.contentId,
            contentUrl = mediaInfo?.contentUrl,
            positionMs = mediaClient?.approximateStreamPosition ?: 0L,
            durationMs = mediaInfo?.streamDuration ?: 0L
        )
    }

    private fun handleRemoteMediaStatusUpdated(source: String) {
        val mediaClient = observedMediaClient ?: getRemoteMediaClient() ?: return
        val mediaStatus = mediaClient.mediaStatus
        val mediaInfo = mediaStatus?.mediaInfo ?: mediaClient.mediaInfo
        val playerState = mediaStatus?.playerState ?: mediaClient.playerState
        val idleReason = mediaStatus?.idleReason ?: mediaClient.idleReason

        publishCastState(mediaClient)
        logRemoteMediaStatus(source, mediaStatus, mediaClient)
        playerStateListener?.onCastPlaybackStateChanged(
            playerState,
            idleReason,
            mediaInfo?.contentType,
            mediaInfo?.contentId,
            mediaInfo?.contentUrl
        )
        playerStateListener.emitAnalytics(
            enabled = analyticsEnabled,
            type = PlayerAnalyticsEventType.CAST_STATE_CHANGED,
            contentId = playerModel?.id,
            positionMs = mediaClient.approximateStreamPosition,
            durationMs = mediaInfo?.streamDuration ?: 0L,
            attributes = mapOf(
                "playerState" to playerState.toString(),
                "idleReason" to idleReason.toString(),
                "contentType" to mediaInfo?.contentType.orEmpty()
            )
        )

        when (playerState) {
            MediaStatus.PLAYER_STATE_PLAYING,
            MediaStatus.PLAYER_STATE_PAUSED -> markReceiverPlaybackStarted(playerState)

            MediaStatus.PLAYER_STATE_IDLE -> {
                if (idleReason == MediaStatus.IDLE_REASON_ERROR) {
                    dispatchCastPlaybackFailure(
                        message = "Cast receiver entered IDLE with ERROR before playback started",
                        mediaStatus = mediaStatus,
                        mediaClient = mediaClient
                    )
                }
            }
        }
    }

    private fun handleReceiverMediaError(mediaError: MediaError) {
        val mediaClient = observedMediaClient ?: getRemoteMediaClient()
        val message =
            "Cast receiver media error: type=${mediaError.type}, reason=${mediaError.reason}, " +
                    "detailedCode=${mediaError.detailedErrorCode}, requestId=${mediaError.requestId}, " +
                    "customDataKeys=${customDataKeysForLog(mediaError.customData)}"

        dispatchCastPlaybackFailure(
            message = message,
            mediaStatus = mediaClient?.mediaStatus,
            mediaClient = mediaClient
        )
    }

    private fun markReceiverPlaybackStarted(playerState: Int) {
        if (waitingForReceiverPlayback) {
            SdkLogger.debug(
                "Cast receiver playback started: playerState=${playerStateName(playerState)}($playerState)"
            )
        }
        waitingForReceiverPlayback = false
        cancelPlaybackStartTimeout()
        publishCastState()
    }

    private fun schedulePlaybackStartTimeout(loadToken: Int) {
        cancelPlaybackStartTimeout()
        val timeoutRunnable = Runnable {
            handlePlaybackStartTimeout(loadToken)
        }
        playbackStartTimeoutRunnable = timeoutRunnable
        mainHandler.postDelayed(timeoutRunnable, CAST_PLAYBACK_START_TIMEOUT_MS)
    }

    private fun cancelPlaybackStartTimeout() {
        playbackStartTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        playbackStartTimeoutRunnable = null
    }

    private fun handlePlaybackStartTimeout(loadToken: Int) {
        if (loadToken != activeLoadToken || !waitingForReceiverPlayback) return

        val mediaClient = observedMediaClient ?: getRemoteMediaClient()
        val mediaStatus = mediaClient?.mediaStatus
        val playerState = mediaStatus?.playerState ?: mediaClient?.playerState
        val idleReason = mediaStatus?.idleReason ?: mediaClient?.idleReason

        if (playerState == MediaStatus.PLAYER_STATE_PLAYING ||
            playerState == MediaStatus.PLAYER_STATE_PAUSED
        ) {
            markReceiverPlaybackStarted(playerState)
            return
        }

        dispatchCastPlaybackFailure(
            message = "Cast receiver did not start playback within ${CAST_PLAYBACK_START_TIMEOUT_MS}ms; " +
                    "current playerState=${playerStateName(playerState)}(${playerState ?: "null"}), " +
                    "idleReason=${idleReasonName(idleReason)}(${idleReason ?: "null"}). " +
                    "Verify the custom receiver reads MediaInfo.contentId/contentUrl and the Chromecast device can fetch the media URL.",
            mediaStatus = mediaStatus,
            mediaClient = mediaClient
        )
    }

    private fun dispatchCastPlaybackFailure(
        message: String,
        mediaStatus: MediaStatus?,
        mediaClient: RemoteMediaClient?
    ) {
        if (castPlaybackFailureDispatched) return

        castPlaybackFailureDispatched = true
        waitingForReceiverPlayback = false
        cancelPlaybackStartTimeout()

        val mediaInfo = mediaStatus?.mediaInfo ?: mediaClient?.mediaInfo
        val playerState =
            mediaStatus?.playerState ?: mediaClient?.playerState ?: MediaStatus.PLAYER_STATE_UNKNOWN
        val idleReason =
            mediaStatus?.idleReason ?: mediaClient?.idleReason ?: MediaStatus.IDLE_REASON_NONE

        SdkLogger.error("$message; ${formatRemoteMediaStatusForLog(mediaStatus, mediaClient)}")
        playerStateListener?.onCastPlaybackFailed(
            message,
            playerState,
            idleReason,
            mediaInfo?.contentType,
            mediaInfo?.contentId,
            mediaInfo?.contentUrl
        )
        playerStateListener.emitAnalytics(
            enabled = analyticsEnabled,
            type = PlayerAnalyticsEventType.CAST_ERROR,
            contentId = playerModel?.id,
            positionMs = mediaClient?.approximateStreamPosition ?: 0L,
            durationMs = mediaInfo?.streamDuration ?: 0L,
            attributes = mapOf(
                "message" to message,
                "playerState" to playerState.toString(),
                "idleReason" to idleReason.toString(),
                "contentType" to mediaInfo?.contentType.orEmpty()
            )
        )
        playerStateListener.emitDiagnostic(
            enabled = diagnosticsEnabled,
            severity = PlayerDiagnosticSeverity.ERROR,
            code = "cast_playback_failed",
            message = message,
            contentId = playerModel?.id,
            attributes = mapOf(
                "playerState" to playerStateName(playerState),
                "idleReason" to idleReasonName(idleReason),
                "contentType" to mediaInfo?.contentType.orEmpty()
            )
        )
        publishCastState(mediaClient)
    }

    private fun logPreparedMediaInfo(
        mediaInfo: MediaInfo,
        mediaSource: CastMediaSource,
        reset: Boolean,
        currentPosition: Long,
        customData: JSONObject?
    ) {
        SdkLogger.debug(
            "Loading cast media: streamType=${streamTypeName(mediaSource.streamType)}(${mediaSource.streamType}), " +
                    "contentType=${mediaSource.contentType}, autoplay=true, reset=$reset, position=$currentPosition, " +
                    "contentId=${redactUrlForLog(mediaInfo.contentId)}, contentUrl=${redactUrlForLog(mediaInfo.contentUrl)}, " +
                    "customDataKeys=${customDataKeysForLog(customData)}"
        )
    }

    private fun logRemoteMediaStatus(
        source: String,
        mediaStatus: MediaStatus?,
        mediaClient: RemoteMediaClient?
    ) {
        val mediaInfo = mediaStatus?.mediaInfo ?: mediaClient?.mediaInfo
        val playerState =
            mediaStatus?.playerState ?: mediaClient?.playerState ?: MediaStatus.PLAYER_STATE_UNKNOWN
        val idleReason =
            mediaStatus?.idleReason ?: mediaClient?.idleReason ?: MediaStatus.IDLE_REASON_NONE
        val hasSession = mediaClient?.hasMediaSession() == true
        val statusKey =
            "$playerState|$idleReason|$hasSession|${mediaInfo?.contentType}|${mediaInfo?.contentId}|${mediaInfo?.contentUrl}"

        if (statusKey == lastLoggedStatusKey) return
        lastLoggedStatusKey = statusKey

        SdkLogger.debug("Cast MediaStatus[$source]: ${formatRemoteMediaStatusForLog(mediaStatus, mediaClient)}")
    }

    private fun formatRemoteMediaStatusForLog(
        mediaStatus: MediaStatus?,
        mediaClient: RemoteMediaClient?
    ): String {
        val mediaInfo = mediaStatus?.mediaInfo ?: mediaClient?.mediaInfo
        val playerState =
            mediaStatus?.playerState ?: mediaClient?.playerState ?: MediaStatus.PLAYER_STATE_UNKNOWN
        val idleReason =
            mediaStatus?.idleReason ?: mediaClient?.idleReason ?: MediaStatus.IDLE_REASON_NONE

        return "playerState=${playerStateName(playerState)}($playerState), " +
                "idleReason=${idleReasonName(idleReason)}($idleReason), " +
                "hasSession=${mediaClient?.hasMediaSession() == true}, " +
                "mediaInfo.contentType=${mediaInfo?.contentType}, " +
                "mediaInfo.contentId=${redactUrlForLog(mediaInfo?.contentId)}, " +
                "mediaInfo.contentUrl=${redactUrlForLog(mediaInfo?.contentUrl)}"
    }

    private fun resolveCastMediaSource(model: PlayerModel): CastMediaSource? {
        val streamType =
            if (model.isLive) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED

        val urlAndType = when {
            model.isLive && !model.liveUrl.isNullOrBlank() ->
                model.liveUrl to HLS_MIME_TYPE

            model.drm == "1" && !model.mpdUrl.isNullOrBlank() ->
                model.mpdUrl to DASH_MIME_TYPE

            !model.hlsUrl.isNullOrBlank() ->
                model.hlsUrl to HLS_MIME_TYPE

            !model.mpdUrl.isNullOrBlank() ->
                model.mpdUrl to DASH_MIME_TYPE

            !model.videoUrl.isNullOrBlank() ->
                model.videoUrl to inferContentType(model.videoUrl)

            else -> null
        } ?: return null

        return CastMediaSource(
            url = urlAndType.first.trim(),
            contentType = urlAndType.second,
            streamType = streamType
        )
    }

    private fun inferContentType(url: String): String {
        val path = url.substringBefore("?").lowercase()
        return when {
            path.endsWith(".m3u8") -> HLS_MIME_TYPE
            path.endsWith(".mpd") -> DASH_MIME_TYPE
            else -> MP4_MIME_TYPE
        }
    }

    private fun playerStateName(playerState: Int?): String {
        return when (playerState) {
            MediaStatus.PLAYER_STATE_UNKNOWN -> "UNKNOWN"
            MediaStatus.PLAYER_STATE_IDLE -> "IDLE"
            MediaStatus.PLAYER_STATE_PLAYING -> "PLAYING"
            MediaStatus.PLAYER_STATE_PAUSED -> "PAUSED"
            MediaStatus.PLAYER_STATE_BUFFERING -> "BUFFERING"
            MediaStatus.PLAYER_STATE_LOADING -> "LOADING"
            else -> "UNRECOGNIZED"
        }
    }

    private fun idleReasonName(idleReason: Int?): String {
        return when (idleReason) {
            MediaStatus.IDLE_REASON_NONE -> "NONE"
            MediaStatus.IDLE_REASON_FINISHED -> "FINISHED"
            MediaStatus.IDLE_REASON_CANCELED -> "CANCELED"
            MediaStatus.IDLE_REASON_INTERRUPTED -> "INTERRUPTED"
            MediaStatus.IDLE_REASON_ERROR -> "ERROR"
            else -> "UNRECOGNIZED"
        }
    }

    private fun streamTypeName(streamType: Int): String {
        return when (streamType) {
            MediaInfo.STREAM_TYPE_BUFFERED -> "BUFFERED"
            MediaInfo.STREAM_TYPE_LIVE -> "LIVE"
            MediaInfo.STREAM_TYPE_NONE -> "NONE"
            MediaInfo.STREAM_TYPE_INVALID -> "INVALID"
            else -> "UNRECOGNIZED"
        }
    }

    private fun customDataKeysForLog(customData: JSONObject?): String {
        if (customData == null || customData.length() == 0) return "[]"

        val keys = mutableListOf<String>()
        val iterator = customData.keys()
        while (iterator.hasNext()) {
            keys += iterator.next()
        }
        return keys.joinToString(prefix = "[", postfix = "]")
    }

    private fun redactUrlForLog(value: String?): String {
        val raw = value?.trim().orEmpty()
        if (raw.isBlank()) return "null"

        return runCatching {
            val uri = Uri.parse(raw)
            val scheme = uri.scheme
            val host = uri.host
            val hasQuery = !uri.encodedQuery.isNullOrBlank()

            if (!scheme.isNullOrBlank() && !host.isNullOrBlank()) {
                val lastPathSegment = uri.lastPathSegment
                    ?.takeIf { it.isNotBlank() }
                    ?.let { if (it.length > MAX_LOG_URL_SEGMENT_LENGTH) it.take(MAX_LOG_URL_SEGMENT_LENGTH) + "..." else it }
                    ?: "media"
                "$scheme://$host/.../$lastPathSegment${if (hasQuery) "?<redacted>" else ""}"
            } else {
                val withoutQuery = raw.substringBefore("?")
                val safeValue =
                    if (withoutQuery.length > MAX_LOG_URL_LENGTH) {
                        withoutQuery.take(MAX_LOG_URL_LENGTH) + "..."
                    } else {
                        withoutQuery
                    }
                "$safeValue${if (hasQuery || raw.contains("?")) "?<redacted>" else ""}"
            }
        }.getOrDefault("<redacted>")
    }

    fun pauseCasting() {
        getRemoteMediaClient()?.pause()
        publishCastState()
    }

    fun playCasting() {
        getRemoteMediaClient()?.play()
        publishCastState()
    }

    private fun stopMediaOnCast() {
        getRemoteMediaClient()?.stop()
        publishCastState()
    }

    fun seekOnCast(position: Long) {
        val mediaClient = getRemoteMediaClient()
        if (mediaClient != null && mediaClient.hasMediaSession()) {
            mediaClient.seek(MediaSeekOptions.Builder().setPosition(position).build())
            publishCastState(mediaClient)
        }
    }
    fun muteOnCast(mute: Boolean) {
        getRemoteMediaClient()?.setStreamMute(mute)
        publishCastState()
    }

    private fun getRemoteMediaClient(): RemoteMediaClient? {
        return sessionManager.currentCastSession?.remoteMediaClient
    }

    private fun pauseLocalPlayback() {
        exoPlayer.pause()
    }

    private fun resumeLocalPlayback() {
        if (!isCasting()) {
            exoPlayer.play()
        }
    }

    fun isCasting(): Boolean {
        publishCastState()
        return castState.value.isCasting
    }

    fun getCastPosition(): Long {
        publishCastState()
        return castState.value.positionMs
    }

    fun getCastDuration(): Long {
        publishCastState()
        return castState.value.durationMs
    }

    fun isCastTVAvailable(context: Context): Boolean {
        val mediaRouter = MediaRouter.getInstance(context)
        val selector = MediaRouteSelector.Builder()
            .addControlCategory(CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID))
            .build()
        mediaRouter.addCallback(selector, object : MediaRouter.Callback() {}, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        return mediaRouter.routes.any { route ->
            route.isEnabled && route.matchesSelector(selector)
        }
    }

    companion object {
        private const val TAG = "CastUtils"
        private const val HLS_MIME_TYPE = "application/x-mpegURL"
        private const val DASH_MIME_TYPE = "application/dash+xml"
        private const val MP4_MIME_TYPE = "video/mp4"
        private const val WIDEVINE_SCHEME = "widevine"
        private const val WIDEVINE_PROTECTION_SYSTEM = "com.widevine.alpha"
        private const val CAST_PLAYBACK_START_TIMEOUT_MS = 30_000L
        private const val MAX_LOG_URL_LENGTH = 96
        private const val MAX_LOG_URL_SEGMENT_LENGTH = 64
    }
}
