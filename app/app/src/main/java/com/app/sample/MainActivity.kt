package com.app.sample

import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.app.mtvdownloader.utils.NotificationPermission
import com.app.sample.composable.ContentScreen
import com.app.sample.viewModel.ContentViewModel
import com.app.videosdk.listener.PipListener

class MainActivity : ComponentActivity(), PipListener {
    private val viewModel: ContentViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationPermission.requestIfRequired(this)
        enableEdgeToEdge()
        setContent {
            ContentScreen(viewModel = viewModel, this@MainActivity)
        }
    }

    override fun onPipRequested() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val aspectRatio = Rational(16, 9) // Adjust based on your video aspect ratio
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .build()
            enterPictureInPictureMode(params)
        }
    }

}