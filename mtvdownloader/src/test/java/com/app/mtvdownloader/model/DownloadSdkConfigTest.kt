package com.app.mtvdownloader.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadSdkConfigTest {

    @Test
    fun sanitizedKeepsValidValues() {
        val config = DownloadSdkConfig(
            maxCacheBytes = 200L * 1024L * 1024L,
            maxParallelDownloads = 2,
            minRetryCount = 5
        ).sanitized()

        assertEquals(200L * 1024L * 1024L, config.maxCacheBytes)
        assertEquals(2, config.maxParallelDownloads)
        assertEquals(5, config.minRetryCount)
    }

    @Test
    fun sanitizedClampsUnsafeValues() {
        val config = DownloadSdkConfig(
            maxCacheBytes = 1L,
            maxParallelDownloads = 0,
            minRetryCount = -1
        ).sanitized()

        assertEquals(50L * 1024L * 1024L, config.maxCacheBytes)
        assertEquals(1, config.maxParallelDownloads)
        assertEquals(0, config.minRetryCount)
    }
}
