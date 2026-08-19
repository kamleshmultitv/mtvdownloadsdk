package com.app.mtvdownloader.model

data class DownloadSdkConfig(
    val maxCacheBytes: Long = DEFAULT_MAX_CACHE_BYTES,
    val maxParallelDownloads: Int = DEFAULT_MAX_PARALLEL_DOWNLOADS,
    val minRetryCount: Int = DEFAULT_MIN_RETRY_COUNT
) {
    internal fun sanitized(): DownloadSdkConfig {
        return copy(
            maxCacheBytes = maxCacheBytes.coerceAtLeast(MIN_CACHE_BYTES),
            maxParallelDownloads = maxParallelDownloads.coerceAtLeast(1),
            minRetryCount = minRetryCount.coerceAtLeast(0)
        )
    }

    companion object {
        const val DEFAULT_MAX_CACHE_BYTES = 500L * 1024L * 1024L
        const val DEFAULT_MAX_PARALLEL_DOWNLOADS = 1
        const val DEFAULT_MIN_RETRY_COUNT = 3

        private const val MIN_CACHE_BYTES = 50L * 1024L * 1024L
    }
}
