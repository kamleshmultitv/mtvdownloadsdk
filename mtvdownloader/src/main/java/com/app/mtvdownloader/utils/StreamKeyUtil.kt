package com.app.mtvdownloader.utils

import androidx.media3.common.StreamKey

object StreamKeyUtil {

    fun toString(keys: List<StreamKey>): String =
        keys.joinToString("|") {
            "${it.periodIndex},${it.groupIndex},${it.streamIndex}"
        }

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
