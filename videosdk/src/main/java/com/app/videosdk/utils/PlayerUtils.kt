package com.app.videosdk.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.annotation.RequiresPermission
import androidx.core.content.FileProvider
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.TrackGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.ima.ImaAdsLoader
import androidx.media3.ui.PlayerView
import com.app.videosdk.listener.AdsListener
import com.app.videosdk.model.AdsConfig
import com.app.videosdk.model.PlayerModel
import com.app.videosdk.model.SubTitleModel
import com.app.videosdk.model.VideoQualityModel
import com.app.videosdk.player.PlaybackSourceResolver
import com.app.videosdk.player.PlayerFactory
import com.app.videosdk.player.PlayerTimeUtils
import com.app.videosdk.player.TrackSelectionUtils
import java.io.File
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.Transformer


object PlayerUtils {

    data class TextTrackOption(
        val id: String,
        val displayName: String,
        val language: String?,
        val mediaTrackGroup: TrackGroup?,
        val trackIndex: Int,
        val isOff: Boolean = false,
        val isSelected: Boolean = false
    )

    /* =========================================================
       PLAYER + IMA
       ========================================================= */
    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    @OptIn(UnstableApi::class)
    fun createPlayer(
        context: Context,
        contentList: List<PlayerModel>? = null,
        selectedIndex: Int = 0,
        videoUrl: String,
        drmToken: String? = null,
        srt: String? = null,
        playerView: PlayerView? = null,
        adsConfig: AdsConfig? = null,
        adsListener: AdsListener? = null,
        existingAdsLoader: ImaAdsLoader? = null,
        playWhenReady: Boolean = true
    ): Pair<ExoPlayer, ImaAdsLoader?> =
        PlayerFactory.createPlayer(
            context = context,
            contentList = contentList,
            selectedIndex = selectedIndex,
            videoUrl = videoUrl,
            drmToken = drmToken,
            srt = srt,
            playerView = playerView,
            adsConfig = adsConfig,
            adsListener = adsListener,
            existingAdsLoader = existingAdsLoader,
            playWhenReady = playWhenReady
        )

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun resolveToPlayableUri(
        contentList: List<PlayerModel>? = null,
        selectedIndex: Int = 0
    ): Uri = PlaybackSourceResolver.resolveToPlayableUri(contentList, selectedIndex)

    fun showAudioTrack(context: Context, exoPlayer: ExoPlayer?): List<SubTitleModel> =
        TrackSelectionUtils.showAudioTrack(context, exoPlayer)

    fun getAudioTrack(
        context: Context,
        audioTracks: List<String>
    ): List<SubTitleModel> = TrackSelectionUtils.getAudioTrack(context, audioTracks)


    fun selectAudioTrack(language: String, exoPlayer: ExoPlayer?) =
        TrackSelectionUtils.selectAudioTrack(language, exoPlayer)

    fun getTextTrackOptions(exoPlayer: ExoPlayer?): List<TextTrackOption> =
        TrackSelectionUtils.getTextTrackOptions(exoPlayer)

    fun selectTextTrack(option: TextTrackOption, exoPlayer: ExoPlayer?) =
        TrackSelectionUtils.selectTextTrack(option, exoPlayer)

    fun getSubTitleFormats(exoPlayer: ExoPlayer?): List<Format> =
        TrackSelectionUtils.getSubTitleFormats(exoPlayer)

    fun calculatePitch(speed: Float): Float =
        TrackSelectionUtils.calculatePitch(speed)

    fun getVideoFormats(exoPlayer: ExoPlayer?): List<VideoQualityModel> =
        TrackSelectionUtils.getVideoFormats(exoPlayer)

    fun setAutoVideoResolution(exoPlayer: ExoPlayer?) =
        TrackSelectionUtils.setAutoVideoResolution(exoPlayer)

    fun changeVideoResolution(exoPlayer: ExoPlayer?, width: Int, height: Int) =
        TrackSelectionUtils.changeVideoResolution(exoPlayer, width, height)

    fun formatTime(milliseconds: Long): String =
        PlayerTimeUtils.formatTime(milliseconds)

    fun timeToMillis(
        time: String?,
        offset: String? = "0"
    ): Long = PlayerTimeUtils.timeToMillis(time, offset)

    @JvmStatic
    fun parseDurationToMillis(input: String?): Long =
        PlayerTimeUtils.parseDurationToMillis(input)

    // share clip
    @OptIn(UnstableApi::class)
    fun exportClip(
        context: Context,
        videoUri: Uri,
        clipStart: Long
    ) {

        val clipDuration = 120_000L

        val mediaItem = MediaItem.Builder()
            .setUri(videoUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clipStart)
                    .setEndPositionMs(clipStart + clipDuration)
                    .build()
            )
            .build()

        val editedMediaItem = EditedMediaItem.Builder(mediaItem).build()

        val transformer = Transformer.Builder(context).build()

        val outputFile = File(
            context.cacheDir,
            "clip_${System.currentTimeMillis()}.mp4"
        )

        transformer.addListener(object : Transformer.Listener {

            override fun onCompleted(
                composition: androidx.media3.transformer.Composition,
                exportResult: ExportResult
            ) {
                SdkLogger.info("Clip export completed")
                shareVideo(context, outputFile)
            }

            override fun onError(
                composition: androidx.media3.transformer.Composition,
                exportResult: ExportResult,
                exception: ExportException
            ) {
                SdkLogger.error("Clip export failed: ${exception.message}", exception)
            }
        })

        transformer.start(
            editedMediaItem,
            outputFile.absolutePath
        )
    }


    fun shareVideo(context: Context, file: File) {

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            Intent.createChooser(intent, "Share Clip")
        )
    }

    fun createShareUrl(
        contentId: String? = null,
        url: String? = null,
        clipStart: Long,
        clipEnd: Long,
        totalClipDuration: Long
    ): String {
        return "https://www.artofliving.app/watch?url=$url&contentId=$contentId&start=$clipStart&end=$clipEnd&totalClipDuration=$totalClipDuration"
    }

    fun shareLink(
        context: Context,
        shareUrl: String
    ) {

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareUrl)
        }

        context.startActivity(
            Intent.createChooser(intent, "Share Clip")
        )
    }
}
