package com.app.sample.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.app.sample.model.PlaybackConfig
import com.app.sample.model.StreamInfo
import com.app.sample.utils.StreamDetector
import com.app.sample.utils.StreamType
import com.app.sample.utils.PlaybackMode

@Composable
fun CardWithTextFieldsAndButton(
    initialConfig: PlaybackConfig,
    onSubmit: (PlaybackConfig) -> Unit,
    expanded: (Boolean) -> Unit
) {
    var config by remember { mutableStateOf(initialConfig) }
    var streamInfo by remember { mutableStateOf<StreamInfo?>(null) }
    val scrollState = rememberScrollState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            /* ---------------- URL ---------------- */

            TextField(
                value = config.url,
                onValueChange = {
                    config = config.copy(url = it)
                    streamInfo = null
                },
                label = { Text("Enter DASH / HLS / Live URL") },
                modifier = Modifier.fillMaxWidth()
            )

            LaunchedEffect(config.url) {
                if (config.url.isNotBlank()) {
                    streamInfo = StreamDetector.detectStreamInfo(config.url)
                    config = config.copy(
                        isLive = streamInfo?.playbackMode == PlaybackMode.LIVE
                    )
                }
            }

            streamInfo?.let {
                Text(
                    text = "Detected: ${it.streamType} • ${it.playbackMode}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            /* ---------------- DRM ---------------- */

            if (streamInfo?.streamType == StreamType.DASH) {
                TextField(
                    value = config.drmToken,
                    onValueChange = {
                        config = config.copy(drmToken = it)
                    },
                    label = { Text("DRM Token (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider()

            /* ================= ADS ================= */

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enable Ads", modifier = Modifier.weight(1f))
                Switch(
                    checked = config.adsConfig.enableAds,
                    onCheckedChange = {
                        config = config.copy(
                            adsConfig = config.adsConfig.copy(enableAds = it)
                        )
                    }
                )
            }

            if (config.adsConfig.enableAds) {
                TextField(
                    value = config.adsConfig.adTagUrl.toString(),
                    onValueChange = {
                        config = config.copy(
                            adsConfig = config.adsConfig.copy(adTagUrl = it)
                        )
                    },
                    label = { Text("Ad Tag URL") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider()

            /* ================= SKIP INTRO ================= */

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enable Skip Intro", modifier = Modifier.weight(1f))
                Switch(
                    checked = config.skipIntro.enableSkipIntro,
                    onCheckedChange = {
                        config = config.copy(
                            skipIntro = config.skipIntro.copy(enableSkipIntro = it)
                        )
                    }
                )
            }

            if (config.skipIntro.enableSkipIntro) {
                TextField(
                    value = config.skipIntro.startTime.toString(),
                    onValueChange = {
                        config = config.copy(
                            skipIntro = config.skipIntro.copy(
                                startTime = it.toLongOrNull() ?: 0L
                            )
                        )
                    },
                    label = { Text("Start Time (ms)") },
                    modifier = Modifier.fillMaxWidth()
                )

                TextField(
                    value = config.skipIntro.endTime.toString(),
                    onValueChange = {
                        config = config.copy(
                            skipIntro = config.skipIntro.copy(
                                endTime = it.toLongOrNull() ?: 0L
                            )
                        )
                    },
                    label = { Text("End Time (ms)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider()

            /* ================= NEXT EPISODE ================= */

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enable Next Episode", modifier = Modifier.weight(1f))
                Switch(
                    checked = config.nextEpisode.enableNextEpisode,
                    onCheckedChange = {
                        config = config.copy(
                            nextEpisode = config.nextEpisode.copy(enableNextEpisode = it)
                        )
                    }
                )
            }

            if (config.nextEpisode.enableNextEpisode) {
                TextField(
                    value = config.nextEpisode.showBeforeEndMs.toString(),
                    onValueChange = {
                        config = config.copy(
                            nextEpisode = config.nextEpisode.copy(showBeforeEndMs = it)
                        )
                    },
                    label = { Text("Show Before End (ms)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.padding(top = 8.dp))

            /* -------- SUBMIT -------- */

            Button(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                enabled = true,
                onClick = {
                    onSubmit(config)   // ✅ APPLY ONCE
                    expanded(false)
                }
            ) {
                Text("Submit")
            }
        }
    }
}


