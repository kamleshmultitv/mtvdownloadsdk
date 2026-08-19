package com.app.videosdk.player

import android.Manifest
import android.net.Uri
import androidx.annotation.RequiresPermission
import androidx.core.net.toUri
import com.app.videosdk.model.PlayerModel

object PlaybackSourceResolver {

    @RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
    fun resolveToPlayableUri(
        contentList: List<PlayerModel>? = null,
        selectedIndex: Int = 0
    ): Uri {
        if (contentList?.isEmpty() == true) return Uri.EMPTY

        val content = contentList?.getOrNull(selectedIndex)

        val mpd = content?.mpdUrl
        val hls = content?.hlsUrl
        val live = content?.liveUrl
        val direct = content?.videoUrl

        val primaryUrl = when {
            content?.drm == "1" && !mpd.isNullOrBlank() -> mpd
            content?.drm != "1" && !hls.isNullOrBlank() -> hls
            !live.isNullOrBlank() -> live
            !direct.isNullOrBlank() -> direct
            else -> null
        }?.trim()

        if (primaryUrl.isNullOrBlank()) return Uri.EMPTY

        return primaryUrl.toUri()
    }
}
