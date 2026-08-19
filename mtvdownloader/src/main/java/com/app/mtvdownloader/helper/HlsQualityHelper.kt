package com.app.mtvdownloader.helper

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.StreamKey
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.offline.DownloadHelper
import com.app.mtvdownloader.model.DownloadQuality
import com.app.mtvdownloader.utils.DownloadSourceResolver
import com.app.mtvdownloader.utils.DownloadStreamType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object HlsQualityHelper {

    private data class VideoTrack(
        val height: Int,
        val bitrate: Int,
        val streamKey: StreamKey
    )

    @OptIn(UnstableApi::class)
    suspend fun getHlsQualities(
        context: Context,
        url: String
    ): List<DownloadQuality> = getDownloadQualities(context, url)

    @OptIn(UnstableApi::class)
    suspend fun getDownloadQualities(
        context: Context,
        url: String
    ): List<DownloadQuality> = withContext(Dispatchers.Main) {

        val streamType = DownloadSourceResolver.inferType(url)
            ?: return@withContext emptyList()

        if (!streamType.supportsQualitySelection) {
            return@withContext emptyList()
        }

        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMimeType(
                when (streamType) {
                    DownloadStreamType.HLS -> MimeTypes.APPLICATION_M3U8
                    DownloadStreamType.DASH -> MimeTypes.APPLICATION_MPD
                    DownloadStreamType.MP4 -> MimeTypes.VIDEO_MP4
                }
            )
            .build()

        val helper = DownloadHelper.forMediaItem(
            context,
            mediaItem,
            DefaultRenderersFactory(context),
            DefaultHttpDataSource.Factory()
        )

        try {
            suspendCancellableCoroutine { cont ->
                helper.prepare(object : DownloadHelper.Callback {

                    override fun onPrepared(
                        helper: DownloadHelper,
                        tracksInfoAvailable: Boolean
                    ) {
                        if (cont.isActive) cont.resume(Unit)
                    }

                    override fun onPrepareError(
                        helper: DownloadHelper,
                        e: IOException
                    ) {
                        if (cont.isActive) cont.resumeWithException(e)
                    }
                })
            }

            val videoTracks = mutableListOf<VideoTrack>()
            val supportStreamKeys = mutableListOf<StreamKey>()

            for (periodIndex in 0 until helper.periodCount) {
                val trackGroups = helper.getTrackGroups(periodIndex)

                for (groupIndex in 0 until trackGroups.length) {
                    val group = trackGroups[groupIndex]

                    for (trackIndex in 0 until group.length) {
                        val format = group.getFormat(trackIndex)
                        val streamKey = StreamKey(
                            periodIndex,
                            groupIndex,
                            trackIndex
                        )

                        if (format.height > 0) {
                            videoTracks.add(
                                VideoTrack(
                                    height = format.height,
                                    bitrate = format.bitrate,
                                    streamKey = streamKey
                                )
                            )
                        } else if (format.isAudioOrText()) {
                            supportStreamKeys.add(streamKey)
                        }
                    }
                }
            }

            return@withContext videoTracks
                .groupBy { it.height }
                .map { (height, tracks) ->
                    val streamKeys = (tracks.map { it.streamKey } + supportStreamKeys)
                        .distinct()

                    DownloadQuality(
                        height = height,
                        bitrate = tracks.maxOf { it.bitrate },
                        label = "${height}p",
                        streamKey = tracks.first().streamKey,
                        streamKeys = streamKeys
                    )
                }
                .sortedBy { it.height }

        } finally {
            helper.release()
        }
    }

    private fun androidx.media3.common.Format.isAudioOrText(): Boolean {
        val mimeType = sampleMimeType ?: return false
        return MimeTypes.isAudio(mimeType) || MimeTypes.isText(mimeType)
    }
}
