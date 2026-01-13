package com.app.videosdk.utils

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.app.videosdk.model.PlayerModel
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.Session
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient

class CastUtils(context: Context, private val exoPlayer: ExoPlayer) {

    private val sessionManager = CastContext.getSharedInstance(context).sessionManager
    private var playerModel: PlayerModel? = null

    private val sessionListener = object : SessionManagerListener<Session> {
        override fun onSessionStarted(session: Session, sessionId: String) {
            resumeCasting()
        }

        override fun onSessionResumed(session: Session, wasSuspended: Boolean) {
            resumeCasting()
        }

        override fun onSessionEnded(session: Session, error: Int) {
            stopCasting()
        }

        override fun onSessionSuspended(session: Session, reason: Int) {
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

        // Get the current playback position from ExoPlayer if available
        var currentPosition: Long = exoPlayer.currentPosition

        // If a Cast session already exists, try getting the position from the Cast client
        if (sessionManager.currentCastSession != null) {
            currentPosition = mediaClient.approximateStreamPosition
        }

        pauseLocalPlayback()
        val mediaInfo = MediaInfo.Builder(model.hlsUrl ?: return).apply {
            setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            setContentType("application/x-mpegURL") // Correct MIME type for HLS
            setMetadata(buildMediaMetadata(model))
        }.build()

        val mediaLoadRequestData = MediaLoadRequestData.Builder().apply {
            setMediaInfo(mediaInfo)
            setAutoplay(true)
            setCurrentTime(
                if (reset) {
                    0L
                } else {
                    currentPosition
                }
            ) // Seek to the last known position
        }.build()

        mediaClient.load(mediaLoadRequestData)
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

        sessionManager.currentCastSession?.let {
            startCasting(true)
        }
    }

    private fun startCasting(reset: Boolean) {
        playerModel?.let { model ->
            startOrRestartCastSession(model, reset)
        }
    }

    private fun stopCasting() {
        stopMediaOnCast()
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
            putString(MediaMetadata.KEY_TITLE, model.title ?: "Unknown Title")
        }
    }

    fun pauseCasting() {
        getRemoteMediaClient()?.pause()
    }

    fun playCasting() {
        getRemoteMediaClient()?.play()
    }

    private fun stopMediaOnCast() {
        getRemoteMediaClient()?.stop()
    }

    fun seekOnCast(position: Long) {
        val mediaClient = getRemoteMediaClient()
        if (mediaClient != null && mediaClient.hasMediaSession()) {
            mediaClient.seek(MediaSeekOptions.Builder().setPosition(position).build())
        }
    }
    fun muteOnCast(mute: Boolean) {
        getRemoteMediaClient()?.setStreamMute(mute)
    }

    private fun getRemoteMediaClient(): RemoteMediaClient? {
        return sessionManager.currentCastSession?.remoteMediaClient
    }

    private fun pauseLocalPlayback() {
        exoPlayer.pause()
    }

    private fun resumeLocalPlayback() {
        if (!isCasting()){
            exoPlayer.play()
        }
    }

    fun isCasting(): Boolean {
        val remoteMediaClient = getRemoteMediaClient()
        return remoteMediaClient?.hasMediaSession() == true
    }

    fun getCastPosition(): Long {
        val remoteMediaClient = getRemoteMediaClient()
        return remoteMediaClient?.approximateStreamPosition ?: 0L
    }

    fun getCastDuration(): Long {
        val remoteMediaClient = getRemoteMediaClient()
        return remoteMediaClient?.mediaInfo?.streamDuration ?: 0L
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
}