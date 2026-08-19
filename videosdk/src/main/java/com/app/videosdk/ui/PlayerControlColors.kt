package com.app.videosdk.ui

import androidx.annotation.ColorRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource

@Composable
internal fun customControlTintColor(
    @ColorRes tintRes: Int?,
    fallback: Color
): Color = tintRes?.let { colorResource(id = it) } ?: fallback
