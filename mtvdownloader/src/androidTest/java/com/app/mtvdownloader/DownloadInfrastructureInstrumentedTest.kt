package com.app.mtvdownloader

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.app.mtvdownloader.init.DownloadSdk
import com.app.mtvdownloader.model.DownloadSdkConfig
import com.app.mtvdownloader.service.MediaDownloadService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadInfrastructureInstrumentedTest {

    @Test
    fun sdkInitializesWorkManagerAndDownloadServiceInfrastructure() {
        val application = InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .applicationContext as Application

        DownloadSdk.init(
            application = application,
            config = DownloadSdkConfig(
                maxCacheBytes = 100L * 1024L * 1024L,
                maxParallelDownloads = 1,
                minRetryCount = 1
            )
        )

        assertNotNull(WorkManager.getInstance(application))
        assertEquals(
            "com.app.mtvdownloader.service.MediaDownloadService",
            MediaDownloadService::class.java.name
        )
    }
}
