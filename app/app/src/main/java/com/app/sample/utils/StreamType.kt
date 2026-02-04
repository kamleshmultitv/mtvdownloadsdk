package com.app.sample.utils

import com.app.sample.model.StreamInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

// ---------------- ENUMS ----------------

enum class StreamType {
    HLS,
    DASH,
    UNKNOWN
}

enum class PlaybackMode {
    LIVE,
    VOD
}

// ---------------- DETECTOR ----------------

object StreamDetector {

    suspend fun detectStreamInfo(url: String): StreamInfo =
        withContext(Dispatchers.IO) {
            val type = detectStreamType(url)
            val mode = detectPlaybackMode(url, type)
            StreamInfo(type, mode)
        }

    // -------- Stream Type --------
    private fun detectStreamType(url: String): StreamType {
        return try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                requestMethod = "HEAD"
                setRequestProperty("User-Agent", "MtvPlayerSdk")
                connect()
            }

            val contentType = conn.contentType.orEmpty()

            when {
                contentType.contains("mpegurl", true) -> StreamType.HLS
                contentType.contains("dash+xml", true) -> StreamType.DASH
                else -> detectFromBody(url)
            }
        } catch (e: Exception) {
            StreamType.UNKNOWN
        }
    }

    private fun detectFromBody(url: String): StreamType {
        return try {
            URL(url).openStream().bufferedReader().useLines { lines ->
                lines.take(20).forEach {
                    if (it.contains("#EXTM3U")) return StreamType.HLS
                    if (it.contains("<MPD")) return StreamType.DASH
                }
            }
            StreamType.UNKNOWN
        } catch (e: Exception) {
            StreamType.UNKNOWN
        }
    }

    // -------- LIVE / VOD (FIXED) --------
    private fun detectPlaybackMode(
        url: String,
        streamType: StreamType
    ): PlaybackMode {
        return try {
            val content = URL(url)
                .openStream()
                .bufferedReader()
                .readLines()
                .take(100)
                .joinToString("\n")

            when (streamType) {

                // ✅ FIXED HLS LOGIC
                StreamType.HLS -> detectHlsPlaybackMode(content)

                StreamType.DASH ->
                    if (content.contains("type=\"dynamic\"", true))
                        PlaybackMode.LIVE
                    else
                        PlaybackMode.VOD

                else -> PlaybackMode.VOD
            }
        } catch (e: Exception) {
            PlaybackMode.VOD
        }
    }

    private fun detectHlsPlaybackMode(content: String): PlaybackMode {
        return when {
            // Master playlist → cannot decide → assume VOD
            content.contains("#EXT-X-STREAM-INF") ->
                PlaybackMode.VOD

            content.contains("#EXT-X-ENDLIST") ->
                PlaybackMode.VOD

            else ->
                PlaybackMode.LIVE
        }
    }
}
