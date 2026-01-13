package com.app.videosdk.cast

import android.content.Context
import com.app.videosdk.R
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider


class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions {
        return CastOptions.Builder()
            .setReceiverApplicationId(context.getString(R.string.app_id_prod))
            .setStopReceiverApplicationWhenEndingSession(false) // Keeps session alive in background
            .setResumeSavedSession(true) // Allows session resumption when app restarts
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? {
        return null
    }
}
