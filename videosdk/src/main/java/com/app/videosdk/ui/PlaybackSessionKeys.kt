package com.app.videosdk.ui

import android.net.Uri
import com.app.videosdk.model.PlayerModel

internal fun buildPrerollKey(
    playerModel: PlayerModel?,
    adTagUrl: String?,
    playbackUrl: String?
): String? {
    val adTag = adTagUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val contentParts = listOfNotNull(
        playerModel?.id?.trim()?.takeIf { it.isNotBlank() },
        playerModel?.hlsUrl?.toPrerollKeyUrl(),
        playbackUrl.toPrerollKeyUrl()
    ).distinct()

    if (contentParts.isEmpty()) return null

    return (contentParts + adTag).joinToString(separator = "|")
}

internal fun buildPlaybackSessionKey(
    playerModel: PlayerModel?,
    playbackUrl: String?,
    activeIndex: Int,
    playRequestId: Long
): String {
    val contentParts = listOfNotNull(
        playerModel?.id?.trim()?.takeIf { it.isNotBlank() },
        playerModel?.hlsUrl?.toPrerollKeyUrl(),
        playbackUrl.toPrerollKeyUrl()
    ).distinct()

    return (contentParts + activeIndex.toString() + playRequestId.toString())
        .joinToString(separator = "|")
}

private fun String?.toPrerollKeyUrl(): String? =
    this
        ?.trim()
        ?.substringBefore("?")
        ?.takeIf { it.isNotBlank() }

internal fun String?.redactUrlForLog(): String {
    val raw = this?.trim().orEmpty()
    if (raw.isBlank()) return "null"

    return runCatching {
        val uri = Uri.parse(raw)
        val scheme = uri.scheme
        val host = uri.host
        val hasQuery = !uri.encodedQuery.isNullOrBlank()

        if (!scheme.isNullOrBlank() && !host.isNullOrBlank()) {
            val lastPathSegment = uri.lastPathSegment
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    if (it.length > MAX_PLAYBACK_LOG_URL_SEGMENT_LENGTH) {
                        it.take(MAX_PLAYBACK_LOG_URL_SEGMENT_LENGTH) + "..."
                    } else {
                        it
                    }
                }
                ?: "media"
            "$scheme://$host/.../$lastPathSegment${if (hasQuery) "?<redacted>" else ""}"
        } else {
            val withoutQuery = raw.substringBefore("?")
            val safeValue =
                if (withoutQuery.length > MAX_PLAYBACK_LOG_URL_LENGTH) {
                    withoutQuery.take(MAX_PLAYBACK_LOG_URL_LENGTH) + "..."
                } else {
                    withoutQuery
                }
            "$safeValue${if (hasQuery || raw.contains("?")) "?<redacted>" else ""}"
        }
    }.getOrDefault("<redacted>")
}

private const val MAX_PLAYBACK_LOG_URL_LENGTH = 96
private const val MAX_PLAYBACK_LOG_URL_SEGMENT_LENGTH = 64
