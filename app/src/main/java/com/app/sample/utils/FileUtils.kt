package com.app.sample.utils

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.paging.compose.LazyPagingItems
import com.app.mtvdownloader.DownloadUtil
import com.app.mtvdownloader.local.entity.DownloadedContentEntity
import com.app.mtvdownloader.model.DownloadModel
import com.app.sample.BuildConfig.DRM_LICENSE_URL
import com.app.sample.extra.ApiConstant.DRM_AUTHORIZATION_SOURCE
import com.app.sample.extra.ApiConstant.DRM_PACKAGE_ID
import com.app.sample.extra.ApiConstant.DRM_TYPE
import com.app.sample.extra.ApiConstant.DRM_USER_ID
import com.app.sample.extra.ApiConstant.PAID
import com.app.sample.extra.ApiConstant.TOKEN
import com.app.sample.model.ContentItem
import com.app.sample.model.OverrideContent
import com.app.videosdk.model.AdsConfig
import com.app.videosdk.model.NextEpisode
import com.app.videosdk.model.PlayerModel
import com.app.videosdk.model.SkipIntro
import org.json.JSONObject
import java.nio.charset.StandardCharsets

object FileUtils {

    private const val TAG = "SampleDrmLicense"
    private const val FALLBACK_DRM_USER_ID = "943592"
    private const val FALLBACK_DRM_PACKAGE_ID = "2"

    private data class AuthClaims(
        val jwt: String? = null,
        val nestedToken: String? = null,
        val userId: String? = null,
        val expiresAtSeconds: Long? = null
    )

    /* ---------------------------------- */
    /* DRM TOKEN                           */
    /* ---------------------------------- */

    private fun getSecondFromDays(downloadDays: String?): Int {
        return downloadDays
            ?.takeIf { it != "0" }
            ?.toIntOrNull()
            ?.times(24 * 60 * 60)
            ?: 0
    }

    private fun getDownloadExpirySeconds(contentItems: ContentItem?): Int {
        return getSecondFromDays(contentItems?.downloadExpiry)
            .takeIf { it > 0 }
            ?: getSecondFromDays("30")
    }

    private fun String?.asManifestUrl(extension: String): String? {
        val url = this?.takeIf { it.isNotBlank() } ?: return null
        val path = url.substringBefore('?').substringBefore('#')
        return url.takeIf { path.endsWith(extension, ignoreCase = true) }
    }

    private fun ContentItem.isDrmContent(): Boolean = drm == "1"

    private fun getDrmTokenOrNull(
        context: Context,
        contentItems: ContentItem?,
        offline: Boolean
    ): String? {
        return if (contentItems?.isDrmContent() == true) {
            getDrmToken(context, contentItems, offline)
        } else {
            null
        }
    }

    private fun readAuthClaims(): AuthClaims {
        return tokenCandidates(TOKEN)
            .firstNotNullOfOrNull(::readJwtClaims)
            ?: AuthClaims()
    }

    private fun tokenCandidates(authorization: String): Sequence<String> = sequence {
        yield(authorization)
        runCatching {
            String(
                Base64.decode(authorization, Base64.DEFAULT),
                StandardCharsets.UTF_8
            )
        }.getOrNull()?.let { decoded ->
            yield(decoded)
        }
    }

    private fun readJwtClaims(token: String): AuthClaims? {
        val payload = token.split(".").getOrNull(1) ?: return null
        val normalizedPayload = payload.padEnd(payload.length + (4 - payload.length % 4) % 4, '=')

        return runCatching {
            val json = JSONObject(
                String(
                    Base64.decode(normalizedPayload, Base64.URL_SAFE or Base64.NO_WRAP),
                    StandardCharsets.UTF_8
                )
            )

            AuthClaims(
                jwt = token,
                nestedToken = json.optString("token")
                    .takeIf { it.isNotBlank() },
                userId = json.optString("user_id")
                    .takeIf { it.isNotBlank() },
                expiresAtSeconds = json.optLong("exp")
                    .takeIf { it > 0L }
            )
        }.getOrNull()
    }

    private fun resolvePayloadUserId(claims: AuthClaims): Pair<String, String> {
        DRM_USER_ID.takeIf { it.isNotBlank() }?.let { return it to "configured" }
        claims.userId?.let { return it to "token_user_id" }
        return FALLBACK_DRM_USER_ID to "fallback"
    }

    private fun resolvePayloadPackageId(contentItems: ContentItem?): Pair<String, String> {
        DRM_PACKAGE_ID.takeIf { it.isNotBlank() }?.let { return it to "configured" }
        return FALLBACK_DRM_PACKAGE_ID to "fallback"
    }

    private fun resolveAuthorizationToken(claims: AuthClaims): Pair<String, String> {
        return when (DRM_AUTHORIZATION_SOURCE.lowercase()) {
            "jwt" -> claims.jwt?.let { it to "jwt" } ?: (TOKEN to "stored")
            "claim_token" -> claims.nestedToken?.let { it to "claim_token" } ?: (TOKEN to "stored")
            else -> TOKEN to "stored"
        }
    }

    private fun DownloadedContentEntity.contentUrlFor(
        mimeType: String,
        vararg extensions: String
    ): String? {
        val url = contentUrl.takeIf { it.isNotBlank() } ?: return null
        if (contentMimeType == mimeType) return url

        val path = url.substringBefore('?').substringBefore('#')
        return url.takeIf {
            extensions.any { extension ->
                path.endsWith(extension, ignoreCase = true)
            }
        }
    }

    fun getDrmToken(
        context: Context,
        contentItems: ContentItem?,
        offline: Boolean = true
    ): String {
        val accessType = if (contentItems?.accessType == PAID) "1" else "0"
        val downloadExpiry = getDownloadExpirySeconds(contentItems)
        val claims = readAuthClaims()
        val (payloadUserId, payloadUserIdSource) = resolvePayloadUserId(claims)
        val (payloadPackageId, payloadPackageIdSource) = resolvePayloadPackageId(contentItems)
        val (authorizationToken, authorizationSource) = resolveAuthorizationToken(claims)
        val downloadValue = if (offline) "1" else "0"
        val canRenew = offline

        val payload = JSONObject().apply {
            put("content_id", contentItems?.id)
            put("k_id", contentItems?.kId)
            put("user_id", payloadUserId)
            put("package_id", payloadPackageId)
            put("licence_duration", downloadExpiry)
            put("security_level", "0")
            put("rental_duration", "0")
            put("content_type", accessType)
            put("download", downloadValue)
            put("can_renew", canRenew)
        }

        val deviceId = GUIDGenerator.generateGUID(context)

        val payloadBase64 = ApiEncryptionHelper.convertStringToBase64(payload.toString())
        val licenseUri = buildString {
            append(DRM_LICENSE_URL)
            if (!DRM_LICENSE_URL.endsWith("?") && !DRM_LICENSE_URL.endsWith("&")) {
                append(if (DRM_LICENSE_URL.contains("?")) "&" else "?")
            }
            append("user_id=").append(deviceId)
            append("&type=").append(DRM_TYPE)
            append("&authorization=").append(authorizationToken)
            append("&payload=").append(payloadBase64)
        }
        val safeLicenseUri = Uri.parse(licenseUri)

        Log.i(
            TAG,
            "DRM_LICENSE_URL_BUILT contentId=${contentItems?.id.orEmpty()} " +
                "mode=${if (offline) "offline" else "online"} " +
                "kIdPresent=${!contentItems?.kId.isNullOrBlank()} " +
                "payloadUserIdSource=$payloadUserIdSource " +
                "packageIdSource=$payloadPackageIdSource " +
                "authorizationSource=$authorizationSource " +
                "contentType=$accessType durationSec=$downloadExpiry " +
                "download=$downloadValue canRenew=$canRenew authExp=${claims.expiresAtSeconds} " +
                "licenseUri=${safeLicenseUri.scheme}://${safeLicenseUri.host}${safeLicenseUri.path}" +
                "?queryKeys=${safeLicenseUri.queryParameterNames.sorted().joinToString(",")}"
        )

        return licenseUri
    }

    fun buildContentListFromDownloaded(
        context: Context,
        downloadedContentEntity: DownloadedContentEntity
    ): List<PlayerModel> {
        val hlsUrl = downloadedContentEntity.contentUrlFor(
            mimeType = "application/x-mpegURL",
            ".m3u8"
        )
        val mpdUrl = downloadedContentEntity.contentUrlFor(
            mimeType = "application/dash+xml",
            ".mpd"
        )
        val mp4Url = downloadedContentEntity.contentUrlFor(
            mimeType = "video/mp4",
            ".mp4",
            ".m4v"
        )

        return listOf(
            PlayerModel(
                // Playback URL
                id = downloadedContentEntity.contentId,
                hlsUrl = hlsUrl,
                mpdUrl = mpdUrl,
                videoUrl = mp4Url,

                // DRM
                drm = if (downloadedContentEntity.licenseUri.isNotBlank()) "1" else null,
                drmToken = downloadedContentEntity.licenseUri.takeIf { it.isNotBlank() },

                // Artwork
                imageUrl = downloadedContentEntity.thumbnailUrl
                    ?: downloadedContentEntity.seasonImage,

                // Metadata
                title = downloadedContentEntity.title,
                episodeTitle = downloadedContentEntity.title,
                seasonTitle = downloadedContentEntity.seasonName,

                // Quality preference (fallback to 1080)
                selectedVideoQuality = downloadedContentEntity.videoHeight ?: 1080,

                // Downloaded content is not live.
                isLive = false,
                downloadManager = DownloadUtil.getDownloadManager(context),
                downloadCache = DownloadUtil.getDownloadCache(context),
                drmOfflineKeySetId = downloadedContentEntity.drmOfflineKeySetId,
                drmOfflineKeySetIdBase64 = downloadedContentEntity.drmOfflineKeySetIdBase64
            )
        )
    }


    /* ---------------------------------- */
    /* PLAYER MODEL BUILDER                */
    /* ---------------------------------- */

    fun buildPlayerContentList(
        context: Context,
        pagingItems: LazyPagingItems<ContentItem>,
        overrideContent: OverrideContent?
    ): List<PlayerModel> {

        /* =========================================================
           CASE 1 & 2 : SUBMIT WAS PRESSED
           ========================================================= */

        overrideContent?.let { override ->

            /* ---------- CASE 1: Submit WITHOUT URL (apply config to API data) ---------- */

            if (override.url.isNullOrBlank()) {
                return pagingItems.itemSnapshotList.items.mapNotNull { content ->

                    val hls = content.hlsUrl?.takeIf { it.isNotBlank() }
                    val mpd = content.url?.takeIf { it.isNotBlank() }
                    if (hls == null && mpd == null) return@mapNotNull null

                    PlayerModel(
                        hlsUrl = hls,
                        mpdUrl = mpd,
                        liveUrl = null,
                        isLive = false,

                        drmToken = getDrmTokenOrNull(context, content, offline = true),

                        imageUrl = content.layoutThumbs
                            ?.firstOrNull()
                            ?.imageSize
                            ?.firstOrNull()
                            ?.url.orEmpty(),

                        title = content.title.orEmpty(),
                        episodeTitle = content.title.orEmpty(),
                        description = content.shortDesc.orEmpty(),
                        srt = content.subtitle?.firstOrNull()?.srt.orEmpty(),

                        // 🔥 APPLY SUBMITTED TOGGLES
                        adsConfig = override.adsConfig ?: AdsConfig(enableAds = false),
                        skipIntro = override.skipIntro ?: SkipIntro(enableSkipIntro = false),
                        nextEpisode = override.nextEpisode ?: NextEpisode(enableNextEpisode = false)
                    )
                }
            }

            /* ---------- CASE 2: Submit WITH URL (single override playback) ---------- */

            val overrideDrmToken = override.drmToken.takeIf { !it.isNullOrBlank() }

            return listOf(
                PlayerModel(
                    hlsUrl = if (!override.isLive) override.url else null,
                    liveUrl = if (override.isLive) override.url else null,
                    mpdUrl = override.url,
                    drm = if (overrideDrmToken != null) "1" else null,
                    drmToken = overrideDrmToken,
                    isLive = override.isLive,
                    adsConfig = override.adsConfig ?: AdsConfig(enableAds = false),
                    skipIntro = override.skipIntro ?: SkipIntro(enableSkipIntro = false),
                    nextEpisode = override.nextEpisode ?: NextEpisode(enableNextEpisode = false)
                )
            )
        }

        /* =========================================================
           CASE 3 : NO SUBMIT (pure API data, defaults only)
           ========================================================= */

        return pagingItems.itemSnapshotList.items.mapNotNull { content ->

            val hls = content.hlsUrl?.takeIf { it.isNotBlank() }
            val mpd = content.url?.takeIf { it.isNotBlank() }
            if (hls == null && mpd == null) return@mapNotNull null

            PlayerModel(
                hlsUrl = hls,
                mpdUrl = mpd,
                liveUrl = null,
                isLive = false,

                drmToken = getDrmTokenOrNull(context, content, offline = true),

                imageUrl = content.layoutThumbs
                    ?.firstOrNull()
                    ?.imageSize
                    ?.firstOrNull()
                    ?.url.orEmpty(),

                title = content.title.orEmpty(),
                episodeTitle = content.title.orEmpty(),
                description = content.shortDesc.orEmpty(),
                srt = content.subtitle?.firstOrNull()?.srt.orEmpty(),

                // ✅ DEFAULTS (no submit yet)
                adsConfig = AdsConfig(enableAds = false),
                skipIntro = SkipIntro(enableSkipIntro = false),
                nextEpisode = NextEpisode(enableNextEpisode = false)
            )
        }
    }

    fun buildDownloadContentList(
        context: Context,
        contentItem: ContentItem?
    ): DownloadModel? {

        if (contentItem == null) return null

        val hlsUrl = contentItem.hlsUrl?.takeIf { it.isNotBlank() }
        val mpdUrl = if (contentItem.isDrmContent()) {
            contentItem.url?.takeIf { it.isNotBlank() }
        } else {
            contentItem.url.asManifestUrl(".mpd")
        }
        val mp4Url = contentItem.url.asManifestUrl(".mp4")
            ?: contentItem.url.asManifestUrl(".m4v")

        // Skip if no playable URL is available
        if (hlsUrl == null && mpdUrl == null && mp4Url == null) return null

        val drmLicenseExpiresAt = if (contentItem.drm == "1") {
            System.currentTimeMillis() + getDownloadExpirySeconds(contentItem) * 1000L
        } else {
            null
        }

        return DownloadModel(
            id = contentItem.id.orEmpty(),
            seasonId = contentItem.seasonId.orEmpty(),
            hlsUrl = hlsUrl,
            mpdUrl = mpdUrl,
            mp4Url = mp4Url,
            drm = contentItem.drm.takeIf { contentItem.isDrmContent() },
            drmToken = getDrmTokenOrNull(context, contentItem, offline = true),
            drmLicenseExpiresAt = drmLicenseExpiresAt,
            drmKeyId = contentItem.kId,
            drmScheme = DRM_TYPE,
            imageUrl = contentItem.layoutThumbs
                ?.firstOrNull()
                ?.imageSize
                ?.firstOrNull()
                ?.url
                .orEmpty(),

            title = contentItem.title.orEmpty(),
            description = contentItem.shortDesc.orEmpty(),
            srt = contentItem.subtitle
                ?.firstOrNull()
                ?.srt
                .orEmpty()
        )
    }
}
