package com.app.videosdk.listener

interface AdsListener {
    fun onAdsLoaded()
    fun onAdStarted()
    fun onAdCompleted()
    fun onAllAdsCompleted()
    fun onAdError(message: String)
}
