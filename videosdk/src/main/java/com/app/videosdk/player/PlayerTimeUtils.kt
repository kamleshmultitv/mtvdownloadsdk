package com.app.videosdk.player

import android.annotation.SuppressLint

object PlayerTimeUtils {

    @SuppressLint("DefaultLocale")
    fun formatTime(milliseconds: Long): String {
        val totalSeconds = (milliseconds / 1000).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    fun timeToMillis(
        time: String?,
        offset: String? = "0"
    ): Long {
        val durationMs = parseDurationToMillis(time)
        val offsetMs = parseDurationToMillis(offset)

        return (durationMs - offsetMs).coerceAtLeast(0L)
    }

    @JvmStatic
    fun parseDurationToMillis(input: String?): Long {
        if (input.isNullOrBlank()) return 0L

        val value = input.trim()

        if (value.all { it.isDigit() }) {
            return value.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        }

        val parts = value.split(":")
        val numbers = parts.map { it.toLongOrNull() ?: return 0L }

        return when (numbers.size) {
            3 -> {
                val (h, m, s) = numbers
                (h * 3600 + m * 60 + s) * 1000
            }

            2 -> {
                val (m, s) = numbers
                (m * 60 + s) * 1000
            }

            else -> 0L
        }
    }
}
