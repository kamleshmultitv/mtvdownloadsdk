package com.app.mtvdownloader.utils

import androidx.annotation.OptIn
import androidx.media3.common.StreamKey
import androidx.media3.common.util.UnstableApi

object StreamKeyUtil {

    @OptIn(UnstableApi::class)
    fun toString(keys: List<StreamKey>): String =
        keys.joinToString("|") {
            "${it.periodIndex},${it.groupIndex},${it.streamIndex}"
        }

    @OptIn(UnstableApi::class)
    fun fromString(value: String): List<StreamKey> =
        value
            .takeIf { it.isNotBlank() }
            ?.split("|")
            ?.mapNotNull { item ->
                val parts = item.split(",")
                if (parts.size != 3) return@mapNotNull null

                val periodIndex = parts[0].toIntOrNull() ?: return@mapNotNull null
                val groupIndex = parts[1].toIntOrNull() ?: return@mapNotNull null
                val streamIndex = parts[2].toIntOrNull() ?: return@mapNotNull null

                StreamKey(
                    periodIndex,
                    groupIndex,
                    streamIndex
                )
            }
            .orEmpty()
}
