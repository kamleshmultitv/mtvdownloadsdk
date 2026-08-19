package com.app.videosdk.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.MappingTrackSelector
import com.app.videosdk.model.SubTitleModel
import com.app.videosdk.model.VideoQualityModel
import com.app.videosdk.utils.PlayerUtils
import com.app.videosdk.utils.SdkLogger
import com.google.gson.Gson
import kotlin.math.pow

object TrackSelectionUtils {

    @OptIn(UnstableApi::class)
    fun showAudioTrack(context: Context, exoPlayer: ExoPlayer?): List<SubTitleModel> {
        val exoPlayerInstance = exoPlayer ?: return emptyList()
        val trackSelector =
            exoPlayerInstance.trackSelector as? DefaultTrackSelector ?: return emptyList()
        val trackGroups = trackSelector.currentMappedTrackInfo?.getTrackGroups(1) ?: run {
            return emptyList()
        }
        val audioTracks = (0 until trackGroups.length).mapNotNull { index ->
            val format = trackGroups.get(index).getFormat(0)
            val label = format.language ?: format.label
            label.takeIf { it != null && it != "und" }
        }.distinct()

        return if (audioTracks.isNotEmpty()) {
            getAudioTrack(context, audioTracks)
        } else {
            emptyList()
        }
    }

    fun getAudioTrack(
        context: Context,
        audioTracks: List<String>
    ): List<SubTitleModel> {
        val json = context.assets
            .open("hls.json")
            .bufferedReader()
            .use { it.readText() }

        val subtitleArray =
            Gson().fromJson(json, Array<SubTitleModel>::class.java)
                ?: emptyArray()

        val subtitleDataList = subtitleArray.toList()
        val result = mutableListOf<SubTitleModel>()

        for (audioTrack in audioTracks) {
            for (subtitleData in subtitleDataList) {
                if (audioTrack == subtitleData.id) {
                    result.add(subtitleData)
                    break
                }
            }
        }

        return result
    }

    @OptIn(UnstableApi::class)
    fun selectAudioTrack(language: String, exoPlayer: ExoPlayer?) {
        val trackSelector = exoPlayer?.trackSelector as? DefaultTrackSelector ?: return
        val parameters = trackSelector.buildUponParameters()
            .setPreferredAudioLanguage(language)
            .build()
        trackSelector.setParameters(parameters)
    }

    fun getTextTrackOptions(exoPlayer: ExoPlayer?): List<PlayerUtils.TextTrackOption> {
        val trackOptions = mutableListOf<PlayerUtils.TextTrackOption>()
        val trackGroups = exoPlayer?.currentTracks?.groups.orEmpty()
        var hasSelectedTextTrack = false

        trackGroups
            .filter { it.type == C.TRACK_TYPE_TEXT }
            .forEachIndexed { groupIndex, group ->
                val mediaTrackGroup = group.mediaTrackGroup
                for (trackIndex in 0 until group.length) {
                    val format = group.getTrackFormat(trackIndex)
                    val language = format.language.cleanTrackText()
                    val label = format.label.cleanTrackText()
                    val displayName = label
                        ?: language
                        ?: format.id.cleanTrackText()
                        ?: "Subtitle ${trackOptions.size + 1}"
                    val isSelected = group.isTrackSelected(trackIndex)

                    if (isSelected) {
                        hasSelectedTextTrack = true
                    }

                    trackOptions.add(
                        PlayerUtils.TextTrackOption(
                            id = language
                                ?: label
                                ?: format.id.cleanTrackText()
                                ?: "text_${groupIndex}_$trackIndex",
                            displayName = displayName,
                            language = language,
                            mediaTrackGroup = mediaTrackGroup,
                            trackIndex = trackIndex,
                            isSelected = isSelected
                        )
                    )
                }
            }

        return listOf(
            PlayerUtils.TextTrackOption(
                id = "off",
                displayName = "Off",
                language = null,
                mediaTrackGroup = null,
                trackIndex = C.INDEX_UNSET,
                isOff = true,
                isSelected = !hasSelectedTextTrack
            )
        ) + trackOptions
    }

    fun selectTextTrack(option: PlayerUtils.TextTrackOption, exoPlayer: ExoPlayer?) {
        val player = exoPlayer ?: return
        val builder = player.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)

        if (option.isOff) {
            player.trackSelectionParameters = builder
                .setPreferredTextLanguages()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            return
        }

        builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)

        option.mediaTrackGroup?.let { group ->
            builder.setOverrideForType(TrackSelectionOverride(group, option.trackIndex))
        }

        val languageVariants = option.language.toPreferredTextLanguageVariants()
        if (languageVariants.isNotEmpty()) {
            builder.setPreferredTextLanguages(*languageVariants.toTypedArray())
        }

        player.trackSelectionParameters = builder.build()
    }

    @OptIn(UnstableApi::class)
    fun getSubTitleFormats(exoPlayer: ExoPlayer?): List<Format> {
        val subTitleFormatList = mutableListOf<Format>()
        val trackSelector = exoPlayer?.trackSelector as? DefaultTrackSelector
        val mappedTrackInfo = trackSelector?.currentMappedTrackInfo ?: return emptyList()

        val subtitleRendererIndices = (0 until mappedTrackInfo.rendererCount).filter { index ->
            videoRendererInfo(mappedTrackInfo, index, isSubtitle = true)
        }

        subtitleRendererIndices.forEach { subtitleRendererIndex ->
            val override = mappedTrackInfo.getTrackGroups(subtitleRendererIndex)
            subTitleFormatList.addAll(getVideoQualityList(override))
        }

        return subTitleFormatList
    }

    fun calculatePitch(speed: Float): Float {
        val basePitch = 1.0f
        val pitchChangeRatio = 0.05f
        return basePitch / speed.pow(pitchChangeRatio)
    }

    @OptIn(UnstableApi::class)
    fun getVideoFormats(exoPlayer: ExoPlayer?): List<VideoQualityModel> {
        val trackSelector = exoPlayer?.trackSelector as? DefaultTrackSelector
        val mappedTrackInfo = trackSelector?.currentMappedTrackInfo ?: return emptyList()

        val videoQualityList = mutableListOf<VideoQualityModel>()

        for (index in 0 until mappedTrackInfo.rendererCount) {
            if (videoRendererInfo(mappedTrackInfo, index, false)) {
                val formats = getVideoQualityList(mappedTrackInfo.getTrackGroups(index))
                formats.forEach { format ->
                    videoQualityList.add(
                        VideoQualityModel(
                            id = format.id.toString(),
                            height = format.height,
                            width = format.width,
                            bitrate = format.bitrate,
                            title = format.height.toString()
                        )
                    )
                }
            }
        }

        return listOf(VideoQualityModel("auto", 0, 0, 0, "Auto")) +
            videoQualityList.sortedByDescending { it.height }
    }

    @OptIn(UnstableApi::class)
    fun setAutoVideoResolution(exoPlayer: ExoPlayer?) {
        val trackSelector = exoPlayer?.trackSelector as? DefaultTrackSelector
        trackSelector?.buildUponParameters()?.clearVideoSizeConstraints()?.let {
            trackSelector.setParameters(it)
        }
    }

    @OptIn(UnstableApi::class)
    fun changeVideoResolution(exoPlayer: ExoPlayer?, width: Int, height: Int) {
        val trackSelector = exoPlayer?.trackSelector as? DefaultTrackSelector
        trackSelector?.buildUponParameters()?.setMaxVideoSize(width, height)?.let {
            trackSelector.setParameters(it)
            SdkLogger.debug("Changed video resolution to ${width}x$height")
        }
    }

    @OptIn(UnstableApi::class)
    private fun videoRendererInfo(
        mappedTrackInfo: MappingTrackSelector.MappedTrackInfo,
        rendererIndex: Int,
        isSubtitle: Boolean = false
    ): Boolean =
        mappedTrackInfo.getTrackGroups(rendererIndex).length > 0 &&
            mappedTrackInfo.getRendererType(rendererIndex) ==
            if (isSubtitle) C.TRACK_TYPE_TEXT else C.TRACK_TYPE_VIDEO

    @OptIn(UnstableApi::class)
    private fun getVideoQualityList(trackGroups: TrackGroupArray): List<Format> {
        val videoQuality = mutableListOf<Format>()
        for (groupIndex in 0 until trackGroups.length) {
            val group = trackGroups[groupIndex]
            for (trackIndex in 0 until group.length) {
                videoQuality.add(group.getFormat(trackIndex))
            }
        }
        return videoQuality
    }

    private fun String?.cleanTrackText(): String? =
        this?.trim()?.takeIf { it.isNotEmpty() && !it.equals("und", ignoreCase = true) }

    private fun String?.toPreferredTextLanguageVariants(): List<String> {
        val language = cleanTrackText() ?: return emptyList()
        val normalized = language.replace('_', '-')
        val lower = normalized.lowercase()
        return listOf(language, normalized, lower).distinct()
    }
}
