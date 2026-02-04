package com.app.sample.model

import com.google.gson.annotations.SerializedName

data class ContentResponse(

	@field:SerializedName("result")
	val result: Result? = null,

	@field:SerializedName("code")
	val code: Int? = null
)

data class ImageSizeItem(

	@field:SerializedName("identifier")
	val identifier: String? = null,

	@field:SerializedName("width")
	val width: String? = null,

	@field:SerializedName("url")
	val url: String? = null,

	@field:SerializedName("height")
	val height: String? = null
)

data class GroupInfo(

	@field:SerializedName("season_banner")
	val seasonBanner: List<SeasonBannerItem?>? = null,

	@field:SerializedName("global_thumb")
	val globalThumb: List<GlobalThumbItem?>? = null,

	@field:SerializedName("name")
	val name: String? = null,

	@field:SerializedName("id")
	val id: String? = null,

	@field:SerializedName("child")
	val child: List<ChildItem?>? = null,

	@field:SerializedName("thumbs")
	val thumbs: List<ThumbsItem?>? = null
)

data class LayoutThumbsItem(

	@field:SerializedName("layout")
	val layout: String? = null,

	@field:SerializedName("image_size")
	val imageSize: List<ImageSizeItem?>? = null,

	@field:SerializedName("id")
	val id: String? = null
)

data class ContentItem(

	@field:SerializedName("comment_count")
	val commentCount: String? = null,

	@field:SerializedName("access_type")
	val accessType: String? = null,

	@field:SerializedName("series_des")
	val seriesDes: String? = null,

	@field:SerializedName("hls_url")
	val hlsUrl: String? = null,

	@field:SerializedName("source")
	val source: String? = null,

	@field:SerializedName("is_favourite")
	val isFavourite: String? = null,

	@field:SerializedName("package_id")
	val packageId: Int? = null,

	@field:SerializedName("title")
	val title: String? = null,

	@field:SerializedName("duration")
	val duration: String? = null,

	@field:SerializedName("series_title")
	val seriesTitle: String? = null,

	@field:SerializedName("episode_number")
	val episodeNumber: String? = null,

	@field:SerializedName("des")
	val des: String? = null,

	@field:SerializedName("content_type")
	val contentType: String? = null,

	@field:SerializedName("season_des")
	val seasonDes: String? = null,

	@field:SerializedName("meta_info")
	val metaInfo: Any? = null,

	@field:SerializedName("genre")
	val genre: List<Any?>? = null,

	@field:SerializedName("download_path")
	val downloadPath: String? = null,

	@field:SerializedName("favorite_count")
	val favoriteCount: String? = null,

	@field:SerializedName("id")
	val id: String? = null,

	@field:SerializedName("category_ids")
	val categoryIds: List<String?>? = null,

	@field:SerializedName("categories")
	val categories: String? = null,

	@field:SerializedName("groupInfo")
	val groupInfo: GroupInfo? = null,

	@field:SerializedName("k_id")
	val kId: String? = null,

	@field:SerializedName("drm")
	val drm: String? = null,

	@field:SerializedName("package_mode")
	val packageMode: String? = null,

	@field:SerializedName("like_count")
	val likeCount: Int? = null,

	@field:SerializedName("indexing")
	val indexing: String? = null,

	@field:SerializedName("season_title")
	val seasonTitle: String? = null,

	@field:SerializedName("is_user_favourite")
	val isUserFavourite: String? = null,

	@field:SerializedName("download_expiry")
	val downloadExpiry: String? = null,

	@field:SerializedName("season_number")
	val seasonNumber: String? = null,

	@field:SerializedName("category_type")
	val categoryType: String? = null,

	@field:SerializedName("ebook_callback_url")
	val ebookCallbackUrl: String? = null,

	@field:SerializedName("season_id")
	val seasonId: String? = null,

	@field:SerializedName("content_veiws")
	val contentVeiws: Int? = null,

	@field:SerializedName("url")
	val url: String? = null,

	@field:SerializedName("is_group")
	val isGroup: String? = null,

	@field:SerializedName("series_id")
	val seriesId: String? = null,

	@field:SerializedName("is_event")
	val isEvent: String? = null,

	@field:SerializedName("play_duration")
	val playDuration: String? = null,

	@field:SerializedName("share_url")
	val shareUrl: String? = null,

	@field:SerializedName("subtitle")
	val subtitle: ArrayList<Subtitle?>? = null,

	@field:SerializedName("layout_thumbs")
	val layoutThumbs: List<LayoutThumbsItem?>? = null,

	@field:SerializedName("short_desc")
	val shortDesc: String? = null,

	@field:SerializedName("permalink")
	val permalink: String? = null,
	@SerializedName("downloadStatus")
	var downloadStatus: Int? = null,

	@SerializedName("isVideoDownloaded")
	var isVideoDownloaded: Boolean? = null
)

data class Subtitle(
	@SerializedName("lang")
	val lang: String?,
	@SerializedName("srt")
	val srt: String?,
	@SerializedName("langId")
	val langId: String?
)

data class ThumbsItem(

	@field:SerializedName("layout")
	val layout: String? = null,

	@field:SerializedName("image_size")
	val imageSize: List<ImageSizeItem?>? = null,

	@field:SerializedName("platform")
	val platform: String? = null,

	@field:SerializedName("id")
	val id: String? = null
)

data class EpisodeArraysItem(

	@field:SerializedName("short_title")
	val shortTitle: String? = null,

	@field:SerializedName("offset")
	val offset: String? = null,

	@field:SerializedName("limit")
	val limit: String? = null,

	@field:SerializedName("long_title")
	val longTitle: String? = null
)

data class BannerItem(

	@field:SerializedName("layout")
	val layout: String? = null,

	@field:SerializedName("image_size")
	val imageSize: List<ImageSizeItem?>? = null,

	@field:SerializedName("id")
	val id: String? = null,

	@field:SerializedName("platform")
	val platform: String? = null
)

data class Result(

	@field:SerializedName("offset")
	val offset: Int? = null,

	@field:SerializedName("totalCount")
	val totalCount: Int? = null,

	@field:SerializedName("version")
	val version: String? = null,

	@field:SerializedName("content")
	val content: List<ContentItem>? = null
)

data class ChildItem(

	@field:SerializedName("episode_arrays")
	val episodeArrays: List<EpisodeArraysItem?>? = null,

	@field:SerializedName("season_number")
	val seasonNumber: String? = null,

	@field:SerializedName("banner")
	val banner: List<BannerItem?>? = null,

	@field:SerializedName("total_episode")
	val totalEpisode: Int? = null,

	@field:SerializedName("id")
	val id: String? = null,

	@field:SerializedName("title")
	val title: String? = null,

	@field:SerializedName("thumbs")
	val thumbs: List<ThumbsItem?>? = null
)

data class GlobalThumbItem(

	@field:SerializedName("layout")
	val layout: String? = null,

	@field:SerializedName("image_size")
	val imageSize: List<ImageSizeItem?>? = null,

	@field:SerializedName("id")
	val id: String? = null,

	@field:SerializedName("platform")
	val platform: String? = null
)

data class SeasonBannerItem(

	@field:SerializedName("layout")
	val layout: String? = null,

	@field:SerializedName("image_size")
	val imageSize: List<ImageSizeItem?>? = null,

	@field:SerializedName("id")
	val id: String? = null,

	@field:SerializedName("platform")
	val platform: String? = null
)
