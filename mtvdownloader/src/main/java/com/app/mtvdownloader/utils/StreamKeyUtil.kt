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
        value.split("|").map {
            val (p, g, s) = it.split(",")
            StreamKey(
                p.toInt(),
                g.toInt(),
                s.toInt()
            )
        }
}
