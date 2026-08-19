package com.app.mtvdownloader.utils

import com.app.mtvdownloader.model.DownloadModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadSourceResolverTest {

    @Test
    fun resolvePrefersPlayableHlsForDrmContentWhenAvailable() {
        val source = DownloadSourceResolver.resolve(
            DownloadModel(
                id = "drm-content",
                hlsUrl = "https://example.com/video.m3u8",
                mpdUrl = "https://example.com/video.mpd",
                drm = "1",
                drmToken = "https://license.example.com"
            )
        )

        assertEquals(DownloadStreamType.HLS, source?.streamType)
        assertEquals("https://example.com/video.m3u8", source?.url)
    }

    @Test
    fun resolveUsesDashForDrmContentWhenHlsIsMissing() {
        val source = DownloadSourceResolver.resolve(
            DownloadModel(
                id = "drm-content",
                mpdUrl = "https://example.com/video.mpd",
                drm = "1",
                drmToken = "https://license.example.com"
            )
        )

        assertEquals(DownloadStreamType.DASH, source?.streamType)
        assertEquals("https://example.com/video.mpd", source?.url)
    }

    @Test
    fun resolveAllowsExtensionlessDashForDrmContent() {
        val source = DownloadSourceResolver.resolve(
            DownloadModel(
                id = "drm-content",
                mpdUrl = "https://cdn.example.com/playback/manifest?id=123",
                drm = "1",
                drmToken = "https://license.example.com"
            )
        )

        assertEquals(DownloadStreamType.DASH, source?.streamType)
        assertEquals("https://cdn.example.com/playback/manifest?id=123", source?.url)
    }

    @Test
    fun resolveSupportsClearHlsDashAndMp4() {
        assertEquals(
            DownloadStreamType.HLS,
            DownloadSourceResolver.resolve(
                DownloadModel(
                    id = "hls-content",
                    hlsUrl = "https://example.com/video.m3u8"
                )
            )?.streamType
        )

        assertEquals(
            DownloadStreamType.DASH,
            DownloadSourceResolver.resolve(
                DownloadModel(
                    id = "dash-content",
                    mpdUrl = "https://example.com/video.mpd"
                )
            )?.streamType
        )

        assertEquals(
            DownloadStreamType.MP4,
            DownloadSourceResolver.resolve(
                DownloadModel(
                    id = "mp4-content",
                    mp4Url = "https://example.com/video.mp4?token=abc"
                )
            )?.streamType
        )
    }

    @Test
    fun resolveRejectsUnsupportedOrIncompleteContent() {
        assertNull(
            DownloadSourceResolver.resolve(
                DownloadModel(
                    id = "unsupported",
                    hlsUrl = "https://example.com/video.mov"
                )
            )
        )

        assertNull(
            DownloadSourceResolver.resolve(
                DownloadModel(
                    id = "drm-missing-license",
                    mpdUrl = "https://example.com/video.mpd",
                    drm = "1"
                )
            )
        )

        assertNull(
            DownloadSourceResolver.resolve(
                DownloadModel(
                    hlsUrl = "https://example.com/video.m3u8"
                )
            )
        )
    }
}
