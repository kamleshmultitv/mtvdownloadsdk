package com.app.videosdk.ui.ads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.app.videosdk.utils.SdkLogger
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.admanager.AdManagerAdRequest
import com.google.android.gms.ads.admanager.AdManagerAdView

@Composable
fun LBandBanner(
    adUnitId: String,
    adSize: AdSize,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            AdManagerAdView(context).apply {
                setAdSizes(adSize)
                this.adUnitId = adUnitId

                val request = AdManagerAdRequest.Builder().build()

                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        SdkLogger.debug("GAM banner ad loaded")
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        SdkLogger.error("GAM banner ad failed: ${error.message}")
                    }
                }

                loadAd(request)
            }
        },
        update = { view ->
            if (view.adUnitId != adUnitId) {
                view.adUnitId = adUnitId
                view.loadAd(AdManagerAdRequest.Builder().build())
            }
        },
        onRelease = { view ->
            view.adListener = object : AdListener() {}
            view.destroy()
        }
    )
}
