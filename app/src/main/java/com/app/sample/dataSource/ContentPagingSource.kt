package com.app.sample.dataSource

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.app.sample.model.ContentItem
import com.app.sample.extra.ApiConstant
import com.app.sample.extra.ApiPathKeys
import com.app.sample.extra.Encryption
import com.app.sample.model.GetContentModel
import com.app.sample.retrofit.RetrofitFactory
import androidx.core.net.toUri

/**
 * Created by kamle on 03,September,2024,DownloadSdk
 */
class ContentPagingSource(
    private val model: GetContentModel?
) : PagingSource<Int, ContentItem>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ContentItem> {
        val offset = params.key ?: 0
        val limit = model?.limit?.toIntOrNull() ?: 10

        return try {
            val url = model?.url?.toUri()?.buildUpon()?.apply {
                appendPath(ApiPathKeys.DEVICE.value)
                appendPath(ApiConstant.CURRENT_PLATFORM.value)
                appendPath(ApiPathKeys.CURRENT_OFFSET.value)
                appendPath(offset.toString())
                appendPath(ApiPathKeys.MAX_COUNTER.value)
                appendPath(limit.toString())  // Fixed: Ensures correct limit is passed
                appendPath(ApiPathKeys.ENCRYPTION.value)
                appendPath(Encryption.FALSE.value)
                if (model.isGroup.isNullOrEmpty() || model.isGroup == "0") {
                    appendPath(ApiPathKeys.GENRE_ID.value)
                    appendPath(model.genreId)
                    appendPath(ApiPathKeys.CID.value)
                    appendPath(model.contentId)
                } else {
                    appendPath(ApiPathKeys.SEASON_ID.value)
                    appendPath(model.seasonId)
                }
            }.toString()

            val data = RetrofitFactory.getRetrofit().getContentList(url, model?.token)
            val contentItems = data?.result?.content ?: emptyList()
            val totalCount = data?.result?.totalCount ?: 0

            Log.d(
                "Paging",
                "Offset: $offset, Limit: $limit, Retrieved: ${contentItems.size}, Total: $totalCount"
            )

            LoadResult.Page(
                data = contentItems,
                prevKey = if (offset == 0) null else offset - limit,
                nextKey = if (contentItems.size == limit) offset + limit else null  // Fixed nextKey logic
            )
        } catch (e: Exception) {
            Log.e("PagingError", "Error loading data", e)
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, ContentItem>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.nextKey?.minus(model?.limit?.toIntOrNull() ?: 10)
                ?: anchorPage?.prevKey?.plus(model?.limit?.toIntOrNull() ?: 10)
        }
    }
}
