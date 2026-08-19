package com.app.videosdk.utils

import android.net.Uri
import android.util.Log
import com.app.videosdk.listener.PlayerStateListener
import com.app.videosdk.model.PlayerAnalyticsEvent
import com.app.videosdk.model.PlayerAnalyticsEventType
import com.app.videosdk.model.PlayerDiagnosticEvent
import com.app.videosdk.model.PlayerDiagnosticSeverity
import com.app.videosdk.model.SdkLogLevel
import com.app.videosdk.model.SdkLoggingConfig

object SdkLogger {
    @Volatile
    private var level: SdkLogLevel = SdkLogLevel.OFF

    @Volatile
    private var tag: String = "MtvVideoPlayerSdk"

    fun configure(config: SdkLoggingConfig) {
        level = config.level
        tag = config.tag.ifBlank { "MtvVideoPlayerSdk" }
    }

    fun debug(message: String) {
        if (level == SdkLogLevel.DEBUG) {
            Log.d(tag, redact(message))
        }
    }

    fun info(message: String) {
        if (level == SdkLogLevel.INFO || level == SdkLogLevel.DEBUG) {
            Log.i(tag, redact(message))
        }
    }

    fun error(message: String, throwable: Throwable? = null) {
        if (level != SdkLogLevel.OFF) {
            Log.e(tag, redact(message), throwable)
        }
    }

    fun redact(message: String): String =
        message
            .replace(URL_REGEX) { redactUrl(it.value) }
            .replace(SENSITIVE_PAIR_REGEX) { match ->
                "${match.groupValues[1]}${match.groupValues[2]}${match.groupValues[3]}<redacted>"
            }

    fun redactUrl(value: String?): String {
        val raw = value?.trim().orEmpty()
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
                        if (it.length > MAX_LOG_URL_SEGMENT_LENGTH) {
                            it.take(MAX_LOG_URL_SEGMENT_LENGTH) + "..."
                        } else {
                            it
                        }
                    }
                    ?: "media"

                "$scheme://$host/.../$lastPathSegment${if (hasQuery) "?<redacted>" else ""}"
            } else {
                val withoutQuery = raw.substringBefore("?")
                val safeValue =
                    if (withoutQuery.length > MAX_LOG_URL_LENGTH) {
                        withoutQuery.take(MAX_LOG_URL_LENGTH) + "..."
                    } else {
                        withoutQuery
                    }
                "$safeValue${if (hasQuery || raw.contains("?")) "?<redacted>" else ""}"
            }
        }.getOrDefault("<redacted>")
    }

    fun redactAttributes(attributes: Map<String, String>): Map<String, String> =
        attributes.mapValues { (key, value) ->
            if (SENSITIVE_KEY_REGEX.containsMatchIn(key)) {
                "<redacted>"
            } else {
                redact(value)
            }
        }

    private val URL_REGEX = Regex("""https?://[^\s,;)"']+""")
    private val SENSITIVE_PAIR_REGEX =
        Regex(
            """(?i)(^|[?&;,\s])([a-z0-9_.-]*(?:token|license|adtag|url|authorization|auth|payload|signature|secret|password|bearer|apikey|api_key|accesskey|access_key|session|cookie)[a-z0-9_.-]*)(\s*[:=]\s*)([^&,\s;)"']+)"""
        )
    private val SENSITIVE_KEY_REGEX =
        Regex("""(?i)(token|license|adtag|url|contenturl|contentid|authorization|auth|payload|signature|secret|password|bearer|apikey|api_key|accesskey|access_key|session|cookie)""")
    private const val MAX_LOG_URL_LENGTH = 96
    private const val MAX_LOG_URL_SEGMENT_LENGTH = 64
}

internal fun PlayerStateListener?.emitAnalytics(
    enabled: Boolean,
    type: PlayerAnalyticsEventType,
    contentId: String?,
    positionMs: Long = 0L,
    durationMs: Long = 0L,
    attributes: Map<String, String> = emptyMap()
) {
    if (!enabled) return

    this?.onAnalyticsEvent(
        PlayerAnalyticsEvent(
            type = type,
            contentId = contentId,
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L),
            attributes = SdkLogger.redactAttributes(attributes)
        )
    )
}

internal fun PlayerStateListener?.emitDiagnostic(
    enabled: Boolean,
    severity: PlayerDiagnosticSeverity,
    code: String,
    message: String,
    contentId: String?,
    attributes: Map<String, String> = emptyMap()
) {
    if (!enabled) return

    this?.onDiagnosticEvent(
        PlayerDiagnosticEvent(
            severity = severity,
            code = code,
            message = SdkLogger.redact(message),
            contentId = contentId,
            attributes = SdkLogger.redactAttributes(attributes)
        )
    )
}
