package com.app.mtvdownloader.utils

import androidx.media3.common.MimeTypes
import com.app.mtvdownloader.model.DownloadModel

internal enum class DownloadStreamType(
    val mimeType: String,
    val supportsQualitySelection: Boolean
) {
    HLS(MimeTypes.APPLICATION_M3U8, true),
    DASH(MimeTypes.APPLICATION_MPD, true),
    MP4(MimeTypes.VIDEO_MP4, false)
}

internal data class ResolvedDownloadSource(
    val url: String,
    val streamType: DownloadStreamType,
    val licenseUri: String?
)

internal object DownloadSourceResolver {

    fun resolve(contentItem: DownloadModel?): ResolvedDownloadSource? {
        val item = contentItem ?: return null
        if (item.id.isNullOrBlank()) return null

        val drmEnabled = item.drm == "1"
        val licenseUri = item.drmToken?.takeIf { it.isNotBlank() }

        if (drmEnabled) {
            item.hlsUrl?.toResolvedSource(licenseUri = null)?.let { return it }

            val drmUrl = item.mpdUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
            if (licenseUri.isNullOrBlank()) return null

            val inferredType = inferType(drmUrl)
            if (inferredType == DownloadStreamType.HLS || inferredType == DownloadStreamType.MP4) {
                return null
            }

            return ResolvedDownloadSource(
                url = drmUrl,
                streamType = inferredType ?: DownloadStreamType.DASH,
                licenseUri = licenseUri
            )
        }

        return listOfNotNull(
            item.hlsUrl,
            item.mpdUrl,
            item.mp4Url
        ).firstNotNullOfOrNull { it.toResolvedSource(licenseUri = null) }
    }

    fun inferType(url: String): DownloadStreamType? {
        val normalized = url.lowercase()
        val cleanUrl = normalized.substringBefore('?').substringBefore('#')
        return when {
            cleanUrl.contains(".m3u8") ||
                normalized.contains("application/x-mpegurl") ||
                normalized.contains("application/vnd.apple.mpegurl") -> DownloadStreamType.HLS

            cleanUrl.contains(".mpd") ||
                normalized.contains("application/dash+xml") ||
                normalized.contains("mime=mpd") ||
                normalized.contains("format=mpd") -> DownloadStreamType.DASH

            cleanUrl.contains(".mp4") ||
                cleanUrl.contains(".m4v") ||
                normalized.contains("video/mp4") ||
                normalized.contains("mime=mp4") ||
                normalized.contains("format=mp4") -> DownloadStreamType.MP4

            else -> null
        }
    }

    private fun String.toResolvedSource(licenseUri: String?): ResolvedDownloadSource? {
        val trimmedUrl = trim()
        if (trimmedUrl.isBlank()) return null

        val streamType = inferType(trimmedUrl) ?: return null
        return ResolvedDownloadSource(
            url = trimmedUrl,
            streamType = streamType,
            licenseUri = licenseUri
        )
    }
}
