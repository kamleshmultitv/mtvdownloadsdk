package com.app.sample.utils

import android.content.Context
import androidx.paging.compose.LazyPagingItems
import com.app.mtvdownloader.local.entity.DownloadedContentEntity
import com.app.mtvdownloader.model.DownloadModel
import com.app.sample.BuildConfig.DRM_LICENSE_URL
import com.app.sample.extra.ApiConstant.DRM_TYPE
import com.app.sample.extra.ApiConstant.PAID
import com.app.sample.extra.ApiConstant.TOKEN
import com.app.sample.model.ContentItem
import com.app.sample.model.OverrideContent
import com.app.videosdk.model.AdsConfig
import com.app.videosdk.model.NextEpisode
import com.app.videosdk.model.PlayerModel
import com.app.videosdk.model.SkipIntro
import org.json.JSONObject

object FileUtils {

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

    fun getDrmToken(context: Context, contentItems: ContentItem?): String {
        val accessType = if (contentItems?.accessType == PAID) "1" else "0"

        val downloadExpiry =
            getSecondFromDays(contentItems?.downloadExpiry)
                .takeIf { it > 0 }
                ?: getSecondFromDays("30")

        val payload = JSONObject().apply {
            put("content_id", contentItems?.id)
            put("k_id", contentItems?.kId)
            put("user_id", "943592")
            put("package_id", "2")
            put("licence_duration", downloadExpiry)
            put("security_level", "0")
            put("rental_duration", "0")
            put("content_type", accessType)
            put("download", "1")
        }

        val deviceId = GUIDGenerator.generateGUID(context)

        return DRM_LICENSE_URL +
                "user_id=$deviceId" +
                "&type=$DRM_TYPE" +
                "&authorization=$TOKEN" +
                "&payload=${ApiEncryptionHelper.convertStringToBase64(payload.toString())}"
    }

    fun buildContentListFromDownloaded(
        downloadedContentEntity: DownloadedContentEntity
    ): List<PlayerModel> {
        return listOf(
            PlayerModel(
                // ▶️ Playback URL
                hlsUrl = downloadedContentEntity.contentUrl,
                mpdUrl = downloadedContentEntity.contentUrl,

                // 🔐 DRM
                drmToken = downloadedContentEntity.licenseUri,

                // 🖼️ Artwork
                imageUrl = downloadedContentEntity.thumbnailUrl
                    ?: downloadedContentEntity.seasonImage,

                // 📝 Metadata
                title = downloadedContentEntity.title,
                seasonTitle = downloadedContentEntity.seasonName,

                // 🎞️ Quality preference (fallback to 1080)
                selectedVideoQuality = downloadedContentEntity.videoHeight ?: 1080,

                // 📡 Downloaded content is NOT live
                isLive = false
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

                        drmToken = getDrmToken(context, content),

                        imageUrl = content.layoutThumbs
                            ?.firstOrNull()
                            ?.imageSize
                            ?.firstOrNull()
                            ?.url.orEmpty(),

                        title = content.title.orEmpty(),
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

            return listOf(
                PlayerModel(
                    hlsUrl = if (!override.isLive) override.url else null,
                    liveUrl = if (override.isLive) override.url else null,
                    mpdUrl = override.url,
                    drmToken = override.drmToken.orEmpty(),
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

                drmToken = getDrmToken(context, content),

                imageUrl = content.layoutThumbs
                    ?.firstOrNull()
                    ?.imageSize
                    ?.firstOrNull()
                    ?.url.orEmpty(),

                title = content.title.orEmpty(),
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
        val mpdUrl = contentItem.url?.takeIf { it.isNotBlank() }

        // Skip if no playable URL is available
        if (hlsUrl == null && mpdUrl == null) return null

        return DownloadModel(
            id = contentItem.id.orEmpty(),
            seasonId = contentItem.seasonId.orEmpty(),
            hlsUrl = hlsUrl,
            mpdUrl = mpdUrl,
            drm = contentItem.drm,
            drmToken = getDrmToken(context, contentItem),
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