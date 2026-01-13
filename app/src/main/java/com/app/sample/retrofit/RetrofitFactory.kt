package com.app.sample.retrofit

import com.app.sample.BuildConfig
import com.app.sample.extra.createUnsafeOkHttpClient
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitFactory {

    private fun provideOkhttpClient(): OkHttpClient {
        val interceptor = HttpLoggingInterceptor()
        interceptor.apply {
            interceptor.level = HttpLoggingInterceptor.Level.BODY
        }
        val builder: OkHttpClient.Builder = OkHttpClient().newBuilder()
        builder.readTimeout(180, TimeUnit.SECONDS)
        builder.connectTimeout(180, TimeUnit.SECONDS)
        builder.writeTimeout(180, TimeUnit.SECONDS)
        if (BuildConfig.DEBUG) {
            builder.addInterceptor(interceptor)
        }
        return builder.build()
    }

    fun getRetrofit(): RetrofitService {
        val gson: Gson = GsonBuilder()
            .setLenient()
            .create()
        val retrofit: Retrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .client(provideOkhttpClient())
            .client(createUnsafeOkHttpClient())
            .build()
        return retrofit.create(RetrofitService::class.java)
    }

}
