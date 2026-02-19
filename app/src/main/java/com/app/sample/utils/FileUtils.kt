package com.app.sample.utils

import android.content.Context
import android.text.TextUtils
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.paging.compose.LazyPagingItems
import com.app.mtvdownloader.entity.DownloadEntity
import com.app.sample.AppClass
import com.app.sample.BuildConfig.DRM_LICENSE_URL
import com.app.sample.R
import com.app.sample.extra.ApiConstant.DRM_TYPE
import com.app.sample.extra.ApiConstant.PAID
import com.app.sample.extra.ApiConstant.TOKEN
import com.app.sample.model.ContentItem
import com.app.sample.model.OverrideContent
import com.app.videosdk.model.AdsConfig
import com.app.videosdk.model.Chapter
import com.app.videosdk.model.GAMAdsConfig
import com.app.videosdk.model.NextEpisode
import com.app.videosdk.model.PlayerCustomControls
import com.app.videosdk.model.PlayerModel
import com.app.videosdk.model.SkipIntro
import okhttp3.internal.platform.PlatformRegistry.applicationContext
import org.json.JSONObject

object FileUtils {

    /* ---------------------------------- */
    /* DRM TOKEN                           */
    /* ---------------------------------- */

    fun getSecondFromDays(downloadDays: String?): Int {
        return if (downloadDays != null && !TextUtils.isEmpty(downloadDays) && !downloadDays.equals(
                "0",
                ignoreCase = true
            )
        ) {
            downloadDays.toInt() * 24 * 60 * 60
        } else {
            0
        }
    }

    private fun getDrmToken(context: Context, contentItems: ContentItem?): String {
        var accessType = contentItems?.accessType
        accessType = if (accessType.equals(PAID)) "1"
        else "0"
        val downloadExpiry = if (getSecondFromDays(contentItems?.downloadExpiry) == 0) {
            getSecondFromDays("30")
        } else {
            getSecondFromDays(contentItems?.downloadExpiry)
        }

        val jsonObject = JSONObject()
        jsonObject.put("content_id", "" + contentItems?.id)
        jsonObject.put("k_id", "" + contentItems?.kId)
        jsonObject.put("user_id", "943592")
        jsonObject.put("package_id", "2")
        jsonObject.put("licence_duration", "" + downloadExpiry)
        jsonObject.put("security_level", "0")
        jsonObject.put("rental_duration", "0")
        jsonObject.put("content_type", accessType)
        jsonObject.put("download", "1")
        jsonObject.put("can_renew", true)
        jsonObject.put("allow_persistent_license", true)
        val androidDeviceUniqueId = GUIDGenerator.generateGUID(context)
        val drmToken =
            DRM_LICENSE_URL + "" + "user_id=" + androidDeviceUniqueId + "&type=" + DRM_TYPE + "&" + "authorization=" +
                    TOKEN + "&payload=" + ApiEncryptionHelper.convertStringToBase64(
                jsonObject.toString()
            )

        Log.d("MtvVideoPlayerSdk", "drmToken: $drmToken")

        return drmToken
    }

    /* ---------------------------------- */
    /* PLAYER MODEL BUILDER                */
    /* ---------------------------------- */

    @OptIn(UnstableApi::class)
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

                    //  val hls = content.hlsUrl?.takeIf { it.isNotBlank() }
                    val hls = content.url?.takeIf { it.isNotBlank() }
                    val mpd = content.url?.takeIf { it.isNotBlank() }
                    if (hls == null && mpd == null) return@mapNotNull null

                    PlayerModel(
                        hlsUrl = hls,
                        mpdUrl = mpd,
                        liveUrl = null,
                        isLive = false,

                        drm = content.drm,
                        drmToken = getDrmToken(context, content),

                        imageUrl = content.layoutThumbs
                            ?.firstOrNull()
                            ?.imageSize
                            ?.firstOrNull()
                            ?.url.orEmpty(),

                        episodeTitle = content.title.orEmpty(),
                        episodeDescription = content.seriesDes.orEmpty(),
                        seasonTitle = content.seasonTitle.orEmpty(),
                        seasonDescription = content.seasonDes.orEmpty(),
                        description = content.des.orEmpty(),
                        seasonNumber = content.seasonNumber.orEmpty(),
                        episodeNumber = content.episodeNumber.orEmpty(),
                        duration = content.duration.orEmpty(),
                        srt = content.subtitle?.firstOrNull()?.srt.orEmpty(),

                        // 🔥 APPLY SUBMITTED TOGGLES
                        adsConfig = override.adsConfig ?: AdsConfig(enableAds = false),
                        skipIntro = override.skipIntro ?: SkipIntro(enableSkipIntro = false),
                        nextEpisode = override.nextEpisode
                            ?: NextEpisode(enableNextEpisode = false),
                        customControls = PlayerCustomControls(
                            iconTintRes = R.color.white,
                            playIconRes = R.drawable.ic_play,
                            pauseIconRes = R.drawable.ic_pause,
                            forwardIconRes = R.drawable.ic_forward,
                            rewindIconRes = R.drawable.ic_rewined,
                            backIconRes = R.drawable.ic_back_arrow,
                            settingsIconRes = R.drawable.ic_settings,
                            pipIconRes = R.drawable.ic_pip,
                            fullScreenIconRes = R.drawable.ic_collapse,
                            exitFullScreenIconRes = R.drawable.ic_expand,
                            lockIconRes = R.drawable.ic_lock,
                            unlockIconRes = R.drawable.ic_unlock,
                            muteIconRes = R.drawable.ic_mute,
                            unMuteIconRes = R.drawable.ic_unmute,
                            crossFadeIconRes = R.drawable.ic_cross,
                            seasonSelectorIconRes = R.drawable.ic_episode,
                            brightnessIconRes = R.drawable.ic_brightness,
                            nextEpisodeIconRes = R.drawable.ic_next_episode,
                        )
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
                    nextEpisode = override.nextEpisode ?: NextEpisode(enableNextEpisode = false),
                    customControls = PlayerCustomControls(
                        iconTintRes = R.color.white,
                        playIconRes = R.drawable.ic_play,
                        pauseIconRes = R.drawable.ic_pause,
                        forwardIconRes = R.drawable.ic_forward,
                        rewindIconRes = R.drawable.ic_rewined,
                        backIconRes = R.drawable.ic_back_arrow,
                        settingsIconRes = R.drawable.ic_settings,
                        pipIconRes = R.drawable.ic_pip,
                        fullScreenIconRes = R.drawable.ic_collapse,
                        exitFullScreenIconRes = R.drawable.ic_expand,
                        lockIconRes = R.drawable.ic_lock,
                        unlockIconRes = R.drawable.ic_unlock,
                        muteIconRes = R.drawable.ic_mute,
                        unMuteIconRes = R.drawable.ic_unmute,
                        crossFadeIconRes = R.drawable.ic_cross,
                        seasonSelectorIconRes = R.drawable.ic_episode,
                        brightnessIconRes = R.drawable.ic_brightness,
                        nextEpisodeIconRes = R.drawable.ic_next_episode,
                    )
                )
            )
        }

        /* =========================================================
           CASE 3 : NO SUBMIT (pure API data, defaults only)
           ========================================================= */

        return pagingItems.itemSnapshotList.items.mapNotNull { content ->

            // val hls = content.hlsUrl?.takeIf { it.isNotBlank() }
            val hls = content.url?.takeIf { it.isNotBlank() }
            val mpd = content.url?.takeIf { it.isNotBlank() }
            if (hls == null && mpd == null) return@mapNotNull null

            PlayerModel(
                hlsUrl = hls,
                mpdUrl = mpd,
                liveUrl = null,
                isLive = false,

                drm = content.drm,
                drmToken = getDrmToken(context, content),

                imageUrl = content.layoutThumbs
                    ?.firstOrNull()
                    ?.imageSize
                    ?.firstOrNull()
                    ?.url.orEmpty(),

                episodeTitle = content.title.orEmpty(),
                episodeDescription = content.seriesDes.orEmpty(),
                seasonTitle = content.seasonTitle.orEmpty(),
                seasonDescription = content.seasonDes.orEmpty(),
                description = content.des.orEmpty(),
                seasonNumber = content.seasonNumber.orEmpty(),
                episodeNumber = content.episodeNumber.orEmpty(),
                duration = content.duration.orEmpty(),
                srt = content.subtitle?.firstOrNull()?.srt.orEmpty(),

                // ✅ DEFAULTS (no submit yet)
                adsConfig = AdsConfig(
                    adTagUrl = "https://pubads.g.doubleclick.net/gampad/ads?iu=/21775744923/external/vmap_ad_samples&sz=640x480&cust_params=sample_ar%3Dpremidpost&ciu_szs=300x250&gdfp_req=1&ad_rule=1&output=vmap&unviewed_position_start=1&env=vp&cmsid=496&vid=short_onecue&correlator=",
                    enableAds = false
                ),
                gamAdsConfig = GAMAdsConfig(
                    verticalBan = "ca-app-pub-3940256099942544/6300978111",
                    horizontalBan = "/21775744923/example/fixed-size-banner",
                    timeIntervalInMilliseconds = 600000,
                    isAdsEnabled = false
                ),
                skipIntro = SkipIntro(
                    startTime = 5000L,
                    endTime = 95000L,
                    enableSkipIntro = true
                ),
                nextEpisode = NextEpisode(
                    showBeforeEndMs = "160000",
                    enableNextEpisode = true
                ),
                cacheFactory = null,
                isChapterEnabled = true,
                chapters = listOf(
                    Chapter("intro", "Intro", 0L),
                    Chapter("main", "Main Content", 186000L),
                    Chapter("end", "Special Thanks", 2004000L)
                ),
                customControls = PlayerCustomControls(
                    iconTintRes = R.color.white,
                    playIconRes = R.drawable.ic_play,
                    pauseIconRes = R.drawable.ic_pause,
                    forwardIconRes = R.drawable.ic_forward,
                    rewindIconRes = R.drawable.ic_rewined,
                    backIconRes = R.drawable.ic_back_arrow,
                    settingsIconRes = R.drawable.ic_settings,
                    pipIconRes = R.drawable.ic_pip,
                    fullScreenIconRes = R.drawable.ic_collapse,
                    exitFullScreenIconRes = R.drawable.ic_expand,
                    lockIconRes = R.drawable.ic_lock,
                    unlockIconRes = R.drawable.ic_unlock,
                    muteIconRes = R.drawable.ic_mute,
                    unMuteIconRes = R.drawable.ic_unmute,
                    crossFadeIconRes = R.drawable.ic_cross,
                    seasonSelectorIconRes = R.drawable.ic_episode,
                    brightnessIconRes = R.drawable.ic_brightness,
                    nextEpisodeIconRes = R.drawable.ic_next_episode,
                )
            )
        }
    }

    @OptIn(UnstableApi::class)
    fun buildContentListFromDownloaded(
        downloadedContentEntity: DownloadEntity
    ): List<PlayerModel> {

        val downloadCache = (applicationContext as AppClass).downloadCache
        val cacheFactory = (applicationContext as AppClass).cacheDataSourceFactory
        val downloadManager = (applicationContext as AppClass).downloadManager

        // ✅ Determine if content is DRM: if licenseUri exists, it's DRM content
        val isDrm = downloadedContentEntity.drm?.isNotBlank()

        return listOf(
            PlayerModel(
                id = downloadedContentEntity.contentId,
                // ▶️ Playback URL
                hlsUrl = downloadedContentEntity.hlsUrl,
                mpdUrl = downloadedContentEntity.mpdUrl,

                // 🔐 DRM
                drm = if (isDrm == true) "1" else "0",
                drmToken = downloadedContentEntity.drmToken,

                // 🖼️ Artwork
                imageUrl = downloadedContentEntity.imageUrl
                    ?: downloadedContentEntity.seasonBanner,

                // 📝 Metadata
                episodeTitle = downloadedContentEntity.title.orEmpty(),

                // 🎞️ Quality preference (fallback to 1080)
                selectedVideoQuality = downloadedContentEntity.videoHeight ?: 1080,

                // 📡 Downloaded content is NOT live
                isLive = false,
                cacheFactory = cacheFactory,
                downloadManager = downloadManager,
                downloadCache = downloadCache,
                customControls = PlayerCustomControls(
                    iconTintRes = R.color.white,
                    playIconRes = R.drawable.ic_play,
                    pauseIconRes = R.drawable.ic_pause,
                    forwardIconRes = R.drawable.ic_forward,
                    rewindIconRes = R.drawable.ic_rewined,
                    backIconRes = R.drawable.ic_back_arrow,
                    settingsIconRes = R.drawable.ic_settings,
                    pipIconRes = R.drawable.ic_pip,
                    fullScreenIconRes = R.drawable.ic_collapse,
                    exitFullScreenIconRes = R.drawable.ic_expand,
                    lockIconRes = R.drawable.ic_lock,
                    unlockIconRes = R.drawable.ic_unlock,
                    muteIconRes = R.drawable.ic_mute,
                    unMuteIconRes = R.drawable.ic_unmute,
                    crossFadeIconRes = R.drawable.ic_cross,
                    seasonSelectorIconRes = R.drawable.ic_episode,
                    brightnessIconRes = R.drawable.ic_brightness,
                    nextEpisodeIconRes = R.drawable.ic_next_episode,
                )
            )
        )
    }


    fun buildDownloadContentList(
        context: Context,
        contentItem: ContentItem?
    ): DownloadEntity? {

        if (contentItem == null) return null

        val hlsUrl = contentItem.hlsUrl?.takeIf { it.isNotBlank() }
        val mpdUrl = contentItem.hlsUrl?.takeIf { it.isNotBlank() }

        // Skip if no playable URL is available
        if (hlsUrl == null && mpdUrl == null) return null

        return DownloadEntity(
            contentId = contentItem.id.orEmpty(),
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
            srt = contentItem.subtitle?.firstOrNull()?.srt.orEmpty()
        )
    }
}