package com.app.mtvdownloader.helper

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.StreamKey
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.offline.DownloadHelper
import com.app.mtvdownloader.model.DownloadQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object HlsQualityHelper {

    suspend fun getHlsQualities(
        context: Context,
        hlsUrl: String
    ): List<DownloadQuality> = withContext(Dispatchers.IO) {

        val mediaItem = MediaItem.Builder()
            .setUri(hlsUrl)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()

        val helper = DownloadHelper.forMediaItem(
            context,
            mediaItem,
            DefaultRenderersFactory(context),
            DefaultHttpDataSource.Factory()
        )

        // ✅ FIX: async prepare with coroutine
        suspendCancellableCoroutine { cont ->
            helper.prepare(
                object : DownloadHelper.Callback {
                    override fun onPrepared(
                        helper: DownloadHelper,
                        tracksInfoAvailable: Boolean
                    ) {
                        cont.resume(Unit)
                    }

                    override fun onPrepareError(
                        helper: DownloadHelper,
                        e: java.io.IOException
                    ) {
                        cont.resumeWithException(e)
                    }
                }
            )
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

        withContext(Dispatchers.Main) {
            helper.release()
        }


        qualities
            .distinctBy { it.height }
            .sortedBy { it.height }
    }
}
