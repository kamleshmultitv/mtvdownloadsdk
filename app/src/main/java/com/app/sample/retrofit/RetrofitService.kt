package com.app.sample.retrofit

import com.app.sample.model.ContentResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url

/**
 * Created by kamle on 03,September,2024,DownloadSdk
 */
interface RetrofitService {

    @GET
    suspend fun getContentList(
        @Url url: String,
        @Header("Authorization") token: String?,
    ): ContentResponse?
}