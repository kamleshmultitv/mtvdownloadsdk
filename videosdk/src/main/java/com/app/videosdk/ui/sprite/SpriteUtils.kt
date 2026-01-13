package com.app.videosdk.ui.sprite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.app.videosdk.model.VttCue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern
import kotlin.math.abs

object SpriteUtils {

    private const val TAG = "SpriteThumbnail"

    // Generic media detection (vtt/jpg/png/webp) for parsing manifests.
    private val mediaPattern = Pattern.compile(
        """(?i)(https?://[^\s'"]+?\.(?:vtt|jpe?g|png|webp)(?:#[^\s'"]*)?)|([\w\-./]+?\.(?:vtt|jpe?g|png|webp)(?:#[^\s'"]*)?)"""
    )

    private val attrPattern = Pattern.compile(
        """(?i)URI\s*=\s*["']?([^"'\s>]+)["']?"""
    )

    fun parseSpriteUrlsFromManifest(manifestText: String, manifestUrl: String): List<String> {
        if (manifestText.isBlank()) return emptyList()
        val found = mutableListOf<String>()

        val m = mediaPattern.matcher(manifestText)
        while (m.find()) {
            val token = m.group(1) ?: m.group(2)
            token?.let { found.add(resolveRelativeUrl(it, manifestUrl)) }
        }

        val a = attrPattern.matcher(manifestText)
        while (a.find()) {
            val uri = a.group(1)
            if (uri != null && (uri.endsWith(".vtt", true) ||
                        uri.endsWith(".jpg", true) ||
                        uri.endsWith(".jpeg", true) ||
                        uri.endsWith(".png", true) ||
                        uri.endsWith(".webp", true)
                        )
            ) {
                found.add(resolveRelativeUrl(uri, manifestUrl))
            }
        }

        return found.distinct()
    }

    fun spriteUrlForVideo(videoUrl: String): String {
        val candidates = buildSpriteCandidates(videoUrl)
        return candidates.firstOrNull() ?: (videoUrl.trimEnd('/') + "/sprite.jpg")
    }

    fun buildSpriteCandidates(videoUrl: String): List<String> {
        val cleaned = stripQueryAndFragment(videoUrl)
        val list = mutableListOf<String>()

        if (cleaned.endsWith(".m3u8", true)) {
            val dir = cleaned.substringBeforeLast('/') + "/"
            val baseName = cleaned.substringAfterLast('/').removeSuffix(".m3u8")
            list += listOf(
                "${dir}sprite.jpg",
                "$dir${baseName}-sprite.jpg",
                "${dir}${baseName}_sprite.jpg",
                "${dir}thumbnails.jpg",
                "${dir}master_sprite.jpg",
                "${dir}master-sprite.jpg",
                cleaned.substringBeforeLast('.') + ".jpg"
            )
            return list.distinct()
        }

        val lastSlash = cleaned.lastIndexOf('/')
        val lastDot = cleaned.lastIndexOf('.')
        if (lastDot > lastSlash) {
            val prefix = cleaned.take(lastDot)
            list += listOf("$prefix-sprite.jpg", "${prefix}_sprite.jpg", "$prefix.jpg")
        } else {
            if (cleaned.contains('/')) list.add(cleaned.substringBeforeLast('/') + "/sprite.jpg")
            list.add("$cleaned-sprite.jpg")
        }

        return list.distinct()
    }

    /**
     * Resolve relative or absolute media URL against a base URL.
     * (Merged duplicate implementations; logic unchanged.)
     */
    fun resolveRelativeUrl(token: String, baseUrl: String): String {
        return try {
            if (token.startsWith("http://", true) || token.startsWith("https://", true)) {
                token
            } else {
                URL(URL(baseUrl), token).toString()
            }
        } catch (_: Exception) {
            // manual fallback if URL(...) fails
            return try {
                val basePrefix = baseUrl.substringBefore("://") + "://"
                basePrefix + (
                        baseUrl.substringAfter("://")
                            .substringBeforeLast('/') +
                                "/" +
                                token
                        ).trimStart('/')
            } catch (_: Exception) {
                token
            }
        }
    }

    private fun stripQueryAndFragment(url: String): String {
        return try {
            val u = URL(url)
            if (u.port == -1) URL(u.protocol, u.host, u.path).toString()
            else URL(u.protocol, u.host, u.port, u.path).toString()
        } catch (_: Throwable) {
            url.substringBefore('?').substringBefore('#')
        }
    }

    /**
     * Minimal fetch helper for text (manifests / vtt). Caller should call from IO or a coroutine.
     * Returns null on error.
     */
    fun fetchTextFromUrl(urlStr: String, timeoutMs: Int = 5000): String? {
        return try {
            val url = URL(urlStr)
            (url.openConnection() as? HttpURLConnection)?.run {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Dramashorts/1.0")
                connect()
                if (responseCode in 200..299) {
                    BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
                } else null
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Fetch manifest text and try to extract referenced sprite/vtt URLs.
     * If VTTs are referenced this will also fetch them and parse image refs inside.
     */
    fun fetchAndParseManifest(manifestUrl: String): List<String> {
        val txt = fetchTextFromUrl(manifestUrl) ?: return emptyList()
        val parsed = parseSpriteUrlsFromManifest(txt, manifestUrl)
        val extra = mutableListOf<String>()
        for (p in parsed) {
            if (p.endsWith(".vtt", true)) {
                val vtt = fetchTextFromUrl(p)
                if (!vtt.isNullOrEmpty()) {
                    // find image refs inside vtt quickly using regex for .jpg/.png/webp
                    val pattern =
                        Pattern.compile("""(?i)(https?://[^\s'"]+?\.(?:jpe?g|png|webp))|([\w\-./]+?\.(?:jpe?g|png|webp))""")
                    val m = pattern.matcher(vtt)
                    while (m.find()) {
                        val token = m.group(1) ?: m.group(2)
                        token?.let { extra.add(resolveRelativeUrl(it, p)) }
                    }
                }
            }
        }
        return (parsed + extra).distinct()
    }

    /* ---------------------------
       VTT parser & helpers
       --------------------------- */

    fun parseTimeToMs(token: String): Long {
        val t = token.trim()
        return try {
            if (t.contains(":")) {
                val parts = t.split(":")
                val last = parts.last()
                val secParts = last.split(".")
                val seconds = secParts.getOrNull(0)?.toLongOrNull() ?: 0L
                val ms = secParts.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toIntOrNull() ?: 0
                var total = seconds * 1000 + ms
                if (parts.size >= 2) total += (parts[parts.size - 2].toLongOrNull() ?: 0L) * 60_000
                if (parts.size >= 3) total += (parts[parts.size - 3].toLongOrNull()
                    ?: 0L) * 3_600_000
                total
            } else {
                val d = t.toDoubleOrNull() ?: 0.0
                (d * 1000.0).toLong()
            }
        } catch (_: Exception) {
            0L
        }
    }

    suspend fun loadBitmapWithFallback(
        ctx: Context,
        imageUrl: String
    ): Bitmap? {
        // 1) Coil attempt
        try {
            val fromCoil = withContext(Dispatchers.IO) {
                try {
                    val loader = ImageLoader(ctx)
                    val req = ImageRequest.Builder(ctx)
                        .data(imageUrl)
                        .allowHardware(false)
                        .build()
                    val result = loader.execute(req)
                    if (result is SuccessResult) result.drawable.toBitmap() else null
                } catch (e: CancellationException) {
                    // cancelled (e.g. scrubbing) — treat quietly
                    null
                } catch (e: Exception) {
                    val msg = e.message ?: ""
                    if (msg.contains("left the composition", ignoreCase = true)) {
                        null
                    } else {
                        Log.w(TAG, "Coil load failed for $imageUrl: ${e.message}")
                        null
                    }
                }
            }
            if (fromCoil != null) return fromCoil
        } catch (e: CancellationException) {
            return null
        } catch (_: Exception) {
            // fall through to HTTP fallback
        }

        // 2) HttpURLConnection fallback
        try {
            val fromHttp = withContext(Dispatchers.IO) {
                try {
                    val url = URL(imageUrl)
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 6000
                        readTimeout = 6000
                        instanceFollowRedirects = true
                        requestMethod = "GET"
                        setRequestProperty("User-Agent", "Dramashorts/1.0")
                    }
                    conn.connect()
                    if (conn.responseCode in 200..299) {
                        BufferedInputStream(conn.inputStream).use { bis ->
                            BitmapFactory.decodeStream(bis)
                        }
                    } else {
                        Log.w(TAG, "HTTP fallback failed (code ${conn.responseCode}) for $imageUrl")
                        null
                    }
                } catch (e: CancellationException) {
                    null
                } catch (e: Exception) {
                    val msg = e.message ?: ""
                    if (msg.contains("left the composition", ignoreCase = true)) {
                        null
                    } else {
                        Log.w(TAG, "HTTP fallback exception for $imageUrl: ${e.message}")
                        null
                    }
                }
            }
            if (fromHttp != null) return fromHttp
        } catch (e: CancellationException) {
            return null
        } catch (_: Exception) {
            // ignore and return null below
        }

        // if both methods failed or were cancelled
        return null
    }

    fun parseVttString(vttText: String, baseUrl: String): List<VttCue> {
        val lines = vttText.lines().map { it.trim() }
        val raws = mutableListOf<Triple<Long, Long, String>>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.isEmpty() || line.startsWith("WEBVTT", true) || line.startsWith("NOTE")) {
                i++
                continue
            }
            if (line.contains("-->")) {
                val parts = line.split("-->")
                val startTok = parts.getOrNull(0)?.trim() ?: ""
                val endTok = parts.getOrNull(1)?.trim()
                    ?.split("\\s+".toRegex())?.getOrNull(0)?.trim() ?: ""
                val sMs = parseTimeToMs(startTok)
                val eMs = parseTimeToMs(endTok)
                var imageRef: String? = null
                val tail = line.substringAfter("-->").substringAfter(endTok).trim()
                if (tail.isNotBlank()) imageRef = tail.split("\\s+".toRegex())[0].trim()
                var j = i + 1
                while (imageRef == null && j < lines.size) {
                    val ln = lines[j]
                    if (ln.isNotBlank()) {
                        imageRef = ln.split("\\s+".toRegex())[0].trim()
                    }
                    j++
                }
                if (imageRef != null) raws.add(Triple(sMs, eMs, imageRef))
                i = j
                continue
            }
            i++
        }

        return raws.map { (s, e, ref) ->
            val parts = ref.split("#", limit = 2)
            val imgPart = parts[0]
            val frag = parts.getOrNull(1)
            var x: Int? = null
            var y: Int? = null
            var w: Int? = null
            var h: Int? = null
            if (!frag.isNullOrBlank() && frag.startsWith("xywh=", true)) {
                val coords = frag.substringAfter("xywh=").split(",")
                if (coords.size >= 4) {
                    try {
                        x = coords[0].toInt()
                        y = coords[1].toInt()
                        w = coords[2].toInt()
                        h = coords[3].toInt()
                    } catch (_: Exception) {
                    }
                }
            }
            val resolved = resolveRelativeUrl(imgPart, baseUrl)
            VttCue(s, e, resolved, x, y, w, h)
        }
    }

    fun findCueForPosition(cues: List<VttCue>, positionMs: Long): VttCue? {
        if (cues.isEmpty()) return null
        val found = cues.firstOrNull { positionMs >= it.startMs && positionMs < it.endMs }
        if (found != null) return found
        return cues.minByOrNull { abs(it.startMs - positionMs) }
    }
}
