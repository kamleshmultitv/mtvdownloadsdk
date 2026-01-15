package com.app.sample.viewModel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.app.sample.dataSource.ContentPagingSource
import com.app.sample.model.GetContentModel
import com.app.sample.extra.ApiConstant.TOKEN

/**
 * Created by kamle on 03,September,2024,DownloadSdk
 */

open class ContentViewModel : ViewModel() {
    private var getContentModel by mutableStateOf<GetContentModel?>(null)

    val contentListData = Pager(PagingConfig(pageSize = 10, enablePlaceholders = false)) {
        ContentPagingSource(getContentModel)
    }.flow.cachedIn(viewModelScope)

    fun setContent() {
        getContentModel = GetContentModel(
            url = "https://api.artofliving.app/artoflivingapi/v10/content/list",
            contentId = "114080",
            token = TOKEN,
            seasonId = "2171",
           // seasonId = "2225",
            offset = "0",
            limit = "5",
            isGroup = "1"

           /* url = "https://api.artofliving.app/artoflivingapi/v10/content/list",
            contentId = "113742",
            token = TOKEN,
            seasonId = "2159",
            offset = "0",
            limit = "5",
            isGroup = "1"*/
        )
    }
}


