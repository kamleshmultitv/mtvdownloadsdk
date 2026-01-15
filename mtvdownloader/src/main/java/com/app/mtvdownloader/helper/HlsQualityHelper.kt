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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object HlsQualityHelper {

    private enum class StreamType { HLS, DASH }

    private fun getStreamType(url: String): StreamType {
        return when {
            url.contains(".m3u8", true) -> StreamType.HLS
            url.contains(".mpd", true) -> StreamType.DASH
            else -> throw IllegalArgumentException("Unsupported stream type")
        }
    }

    @OptIn(UnstableApi::class)
    suspend fun getHlsQualities(
        context: Context,
        url: String
    ): List<DownloadQuality> = withContext(Dispatchers.Main) {

        val streamType = try {
            getStreamType(url)
        } catch (_: Exception) {
            return@withContext emptyList()
        }

        val mediaItem = MediaItem.Builder()
            .setUri(url)
            .setMimeType(
                when (streamType) {
                    StreamType.HLS -> MimeTypes.APPLICATION_M3U8
                    StreamType.DASH -> MimeTypes.APPLICATION_MPD
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
                        cont.resume(Unit)
                    }

                    override fun onPrepareError(
                        helper: DownloadHelper,
                        e: IOException
                    ) {
                        cont.resumeWithException(e)
                    }
                })
            }

            val qualities = mutableListOf<DownloadQuality>()

            for (periodIndex in 0 until helper.periodCount) {
                val trackGroups = helper.getTrackGroups(periodIndex)

                for (groupIndex in 0 until trackGroups.length) {
                    val group = trackGroups[groupIndex]

                    for (trackIndex in 0 until group.length) {
                        val format = group.getFormat(trackIndex)

                        if (format.height > 0) {
                            qualities.add(
                                DownloadQuality(
                                    height = format.height,
                                    bitrate = format.bitrate,
                                    label = "${format.height}p",
                                    streamKey = StreamKey(
                                        periodIndex,
                                        groupIndex,
                                        trackIndex
                                    )
                                )
                            )
                        }
                    }
                }
            }

            return@withContext qualities
                .distinctBy { it.height }
                .sortedBy { it.height }

        } finally {
            helper.release()
        }
    }
}
