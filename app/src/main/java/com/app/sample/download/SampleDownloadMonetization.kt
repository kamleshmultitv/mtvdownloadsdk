package com.app.sample.download

import android.content.Context
import android.util.Log
import com.app.mtvdownloader.model.DownloadModel
import com.app.mtvdownloader.model.DownloadMonetizationGate
import kotlinx.coroutines.delay

object SampleDownloadMonetization {
    val gate = DownloadMonetizationGate { context, content ->
        val hasSubscription = hasActiveSubscription(content)

        if (hasSubscription) {
            true
        } else {
            showRewardedAdPlaceholder(context, content)
        }
    }

    private fun hasActiveSubscription(content: DownloadModel): Boolean {
        return content.id?.startsWith("premium_", ignoreCase = true) != true
    }

    private suspend fun showRewardedAdPlaceholder(
        context: Context,
        content: DownloadModel
    ): Boolean {
        delay(250)
        Log.d(
            "SampleDownloadGate",
            "Rewarded ad placeholder completed for ${content.id} in ${context.packageName}"
        )
        return true
    }
}
