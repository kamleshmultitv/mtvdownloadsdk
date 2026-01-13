package com.app.sample

import android.app.Application
import android.os.Build
import androidx.annotation.RequiresApi
import com.app.mtvdownloader.init.DownloadSdk

class AppClass : Application() {

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()

        DownloadSdk.init(this)
    }
}
