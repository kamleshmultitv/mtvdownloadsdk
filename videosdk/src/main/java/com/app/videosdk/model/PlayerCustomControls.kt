package com.app.videosdk.model

import androidx.annotation.ColorRes

data class PlayerCustomControls(

    // 🎯 Global Tint (applies to all icons if provided)
    @ColorRes val iconTintRes: Int? = null,

    // Center Controls
    val playIconRes: Int? = null,
    val pauseIconRes: Int? = null,
    val forwardIconRes: Int? = null,
    val rewindIconRes: Int? = null,

    // Top Bar
    val backIconRes: Int? = null,
    val settingsIconRes: Int? = null,
    val castIconRes: Int? = null,
    val pipIconRes: Int? = null,
    val fullScreenIconRes: Int? = null,
    val exitFullScreenIconRes: Int? = null,

    // Lock Controls
    val lockIconRes: Int? = null,
    val unlockIconRes: Int? = null,

    val muteIconRes: Int? = null,
    val unMuteIconRes: Int? = null,
    val crossFadeIconRes: Int? = null,
    val seasonSelectorIconRes: Int? = null,
    val brightnessIconRes: Int? = null,
    val nextEpisodeIconRes: Int? = null,
    val castConnectedIconRes: Int? = null,

)
