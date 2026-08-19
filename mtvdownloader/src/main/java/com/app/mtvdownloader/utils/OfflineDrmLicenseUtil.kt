package com.app.mtvdownloader.utils

import android.content.Context
import android.util.Log
import android.util.Pair
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.DrmInitData
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.StreamKey
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.drm.DrmSessionEventListener
import androidx.media3.exoplayer.drm.OfflineLicenseHelper
import androidx.media3.exoplayer.offline.DownloadHelper
import com.app.mtvdownloader.DownloadUtil
import com.app.mtvdownloader.utils.Constants.FAILURE_DRM_AUTH_TOKEN_EXPIRED
import com.app.mtvdownloader.utils.Constants.FAILURE_DRM_INIT_DATA_MISSING
import com.app.mtvdownloader.utils.Constants.FAILURE_DRM_KEYSETID_EMPTY
import com.app.mtvdownloader.utils.Constants.FAILURE_DRM_MANIFEST_PREPARE_FAILED
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@OptIn(UnstableApi::class)
internal object OfflineDrmLicenseUtil {

    private const val TAG = "OfflineDrmLicenseUtil"

    data class OfflineLicenseResult(
        val keySetId: ByteArray,
        val keySetIdBase64: String,
        val expiresAt: Long?
    )

    suspend fun ensureWidevineOfflineLicense(
        context: Context,
        contentId: String,
        contentUri: String,
        licenseUri: String,
        streamKeys: List<StreamKey>,
        existingKeySetId: ByteArray?,
        forceNewLicense: Boolean = false
    ): OfflineLicenseResult {
        val appContext = context.applicationContext
        val authResult = DrmLicenseAuthValidator.inspect(licenseUri)

        if (authResult.expired) {
            val failure = IllegalStateException(FAILURE_DRM_AUTH_TOKEN_EXPIRED)
            DrmDebugLogger.failure(
                stage = FAILURE_DRM_AUTH_TOKEN_EXPIRED,
                contentId = contentId,
                throwable = failure
            )
            DrmDebugLogger.stage(
                stage = FAILURE_DRM_AUTH_TOKEN_EXPIRED,
                contentId = contentId,
                message = "expiredAt=${authResult.expiresAtMillis}"
            )
            throw failure
        }

        val helper = createLicenseHelper(appContext, licenseUri)

        try {
            DrmDebugLogger.logPackageSigning(appContext, contentId, licenseUri)

            if (existingKeySetId != null && !forceNewLicense) {
                val existingDuration = runCatching {
                    helper.getLicenseDurationRemainingSec(existingKeySetId)
                }.getOrNull()

                if (existingDuration.isUsable()) {
                    val keySetIdBase64 = DrmKeySetUtil.encode(existingKeySetId)
                        ?: throw IllegalStateException(FAILURE_DRM_KEYSETID_EMPTY)

                    DrmDebugLogger.stage(
                        stage = "DRM_OFFLINE_LICENSE_SUCCESS",
                        contentId = contentId,
                        message = "reused=true keySetBytes=${existingKeySetId.size} " +
                            "expiresAt=${existingDuration.toExpiresAt()}"
                    )
                    return OfflineLicenseResult(
                        keySetId = existingKeySetId,
                        keySetIdBase64 = keySetIdBase64,
                        expiresAt = existingDuration.toExpiresAt()
                    )
                }

                val renewedKeySetId = runCatching {
                    helper.renewLicense(existingKeySetId)
                }.getOrNull()

                if (renewedKeySetId != null) {
                    val renewedDuration = runCatching {
                        helper.getLicenseDurationRemainingSec(renewedKeySetId)
                    }.getOrNull()
                    val keySetIdBase64 = DrmKeySetUtil.encode(renewedKeySetId)
                        ?: throw IllegalStateException(FAILURE_DRM_KEYSETID_EMPTY)

                    DrmDebugLogger.stage(
                        stage = "DRM_OFFLINE_LICENSE_SUCCESS",
                        contentId = contentId,
                        message = "renewedExisting=true keySetBytes=${renewedKeySetId.size} " +
                            "expiresAt=${renewedDuration.toExpiresAt()}"
                    )

                    return OfflineLicenseResult(
                        keySetId = renewedKeySetId,
                        keySetIdBase64 = keySetIdBase64,
                        expiresAt = renewedDuration.toExpiresAt()
                    )
                }
            }

            val drmFormat = loadDashDrmFormat(
                context = appContext,
                contentId = contentId,
                contentUri = contentUri,
                licenseUri = licenseUri,
                streamKeys = streamKeys
            )

            DrmDebugLogger.stage(
                stage = "DRM_OFFLINE_LICENSE_START",
                contentId = contentId,
                message = "streamKeys=${streamKeys.size} ${DrmDebugLogger.licenseSummary(licenseUri)}"
            )

            val keySetId = withContext(Dispatchers.IO) {
                helper.downloadLicense(drmFormat)
            }

            if (keySetId.isEmpty()) {
                throw IllegalStateException(FAILURE_DRM_KEYSETID_EMPTY)
            }

            val duration = runCatching {
                helper.getLicenseDurationRemainingSec(keySetId)
            }.getOrNull()
            val keySetIdBase64 = DrmKeySetUtil.encode(keySetId)
                ?: throw IllegalStateException(FAILURE_DRM_KEYSETID_EMPTY)

            DrmDebugLogger.stage(
                stage = "DRM_OFFLINE_LICENSE_SUCCESS",
                contentId = contentId,
                message = "keySetBytes=${keySetId.size} expiresAt=${duration.toExpiresAt()}"
            )

            return OfflineLicenseResult(
                keySetId = keySetId,
                keySetIdBase64 = keySetIdBase64,
                expiresAt = duration.toExpiresAt()
            )
        } finally {
            helper.release()
        }
    }

    suspend fun releaseWidevineOfflineLicense(
        context: Context,
        licenseUri: String,
        keySetId: ByteArray
    ) {
        val helper = createLicenseHelper(context.applicationContext, licenseUri)
        try {
            withContext(Dispatchers.IO) {
                helper.releaseLicense(keySetId)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to release offline DRM license: ${t.message}", t)
        } finally {
            helper.release()
        }
    }

    private fun createLicenseHelper(
        context: Context,
        licenseUri: String
    ): OfflineLicenseHelper {
        return OfflineLicenseHelper.newWidevineInstance(
            licenseUri,
            false,
            DownloadUtil.getHttpFactory(context),
            DrmSessionEventListener.EventDispatcher()
        )
    }

    private suspend fun loadDashDrmFormat(
        context: Context,
        contentId: String,
        contentUri: String,
        licenseUri: String,
        streamKeys: List<StreamKey>
    ): Format = withContext(Dispatchers.Main) {
        DrmDebugLogger.stage(
            stage = "DRM_MANIFEST_PREPARE_START",
            contentId = contentId,
            message = "mimeType=${MimeTypes.APPLICATION_MPD} streamKeys=${streamKeys.size}"
        )

        val mediaItem = MediaItem.Builder()
            .setUri(contentUri)
            .setMimeType(MimeTypes.APPLICATION_MPD)
            .setDrmConfiguration(
                MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                    .setLicenseUri(licenseUri.toUri())
                    .build()
            )
            .build()

        val helper = DownloadHelper.forMediaItem(
            context,
            mediaItem,
            DefaultRenderersFactory(context),
            DownloadUtil.getHttpFactory(context)
        )

        try {
            try {
                suspendCancellableCoroutine<Unit> { cont ->
                    helper.prepare(object : DownloadHelper.Callback {
                        override fun onPrepared(
                            helper: DownloadHelper,
                            tracksInfoAvailable: Boolean
                        ) {
                            if (cont.isActive) cont.resume(Unit)
                        }

                        override fun onPrepareError(
                            helper: DownloadHelper,
                            e: IOException
                        ) {
                            if (cont.isActive) cont.resumeWithException(e)
                        }
                    })
                }
            } catch (t: Throwable) {
                DrmDebugLogger.failure(
                    stage = FAILURE_DRM_MANIFEST_PREPARE_FAILED,
                    contentId = contentId,
                    throwable = t
                )
                throw IllegalStateException(FAILURE_DRM_MANIFEST_PREPARE_FAILED, t)
            }

            DrmDebugLogger.stage(
                stage = "DRM_MANIFEST_PREPARED",
                contentId = contentId,
                message = "periodCount=${helper.periodCount}"
            )

            val drmFormat = findDrmFormat(helper, streamKeys)
                ?: findDrmFormat(helper, emptyList())
                ?: run {
                    val failure = IllegalStateException(FAILURE_DRM_INIT_DATA_MISSING)
                    DrmDebugLogger.failure(
                        stage = FAILURE_DRM_INIT_DATA_MISSING,
                        contentId = contentId,
                        throwable = failure
                    )
                    throw failure
                }

            DrmDebugLogger.stage(
                stage = "DRM_INIT_DATA_FOUND",
                contentId = contentId,
                message = "sampleMimeType=${drmFormat.sampleMimeType.orEmpty()} " +
                    "drmSchemeCount=${drmFormat.drmInitData?.schemeDataCount ?: 0}"
            )

            drmFormat
        } finally {
            helper.release()
        }
    }

    private fun findDrmFormat(
        helper: DownloadHelper,
        streamKeys: List<StreamKey>
    ): Format? {
        val selectedKeys = streamKeys.toSet()
        var firstDrmFormat: Format? = null
        var mergedDrmInitData: DrmInitData? = null

        for (periodIndex in 0 until helper.periodCount) {
            val trackGroups = helper.getTrackGroups(periodIndex)

            for (groupIndex in 0 until trackGroups.length) {
                val group = trackGroups[groupIndex]

                for (trackIndex in 0 until group.length) {
                    if (
                        selectedKeys.isNotEmpty() &&
                        StreamKey(periodIndex, groupIndex, trackIndex) !in selectedKeys
                    ) {
                        continue
                    }

                    val format = group.getFormat(trackIndex)
                    val drmInitData = format.drmInitData ?: continue

                    firstDrmFormat = firstDrmFormat ?: format
                    mergedDrmInitData = mergedDrmInitData?.merge(drmInitData) ?: drmInitData
                }
            }
        }

        val drmFormat = firstDrmFormat ?: return null
        return drmFormat
            .buildUpon()
            .setDrmInitData(mergedDrmInitData)
            .build()
    }

    private fun Pair<Long, Long>?.isUsable(): Boolean {
        return this != null && first > 0L && second > 0L
    }

    private fun Pair<Long, Long>?.toExpiresAt(): Long? {
        val duration = this ?: return null
        if (duration.first <= 0L || duration.second <= 0L) return null
        return System.currentTimeMillis() + minOf(duration.first, duration.second) * 1000L
    }
}
