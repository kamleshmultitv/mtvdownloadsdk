package com.app.videosdk.ui.sprite

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.app.videosdk.model.Quad
import com.app.videosdk.model.VttCue
import com.app.videosdk.ui.sprite.SpriteUtils.findCueForPosition
import com.app.videosdk.ui.sprite.SpriteUtils.loadBitmapWithFallback
import com.app.videosdk.ui.sprite.SpriteUtils.parseVttString
import com.app.videosdk.ui.sprite.SpriteUtils.resolveRelativeUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "SpriteThumbnail"

@Composable
fun SpriteThumbnail(
    spriteUrl: String,
    positionMs: Long,
    modifier: Modifier = Modifier,
    widthDp: Int = 120,              // width of 16:9 card
    columns: Int = 5,
    thumbnailsEachMs: Int = 5000,
    thumbnailsCount: Int? = null,
    cornerRadiusDp: Int = 8
) {
    val ctx = LocalContext.current

    var spriteBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var sprW by remember { mutableIntStateOf(0) }
    var sprH by remember { mutableIntStateOf(0) }

    var cues by remember { mutableStateOf<List<VttCue>>(emptyList()) }
    var activeCue by remember { mutableStateOf<VttCue?>(null) }
    var lastLoadedImageUrl by remember { mutableStateOf<String?>(null) }

    /* ---------------- LOAD REMOTE DATA ---------------- */

    LaunchedEffect(spriteUrl) {
        spriteBitmap = null
        sprW = 0
        sprH = 0
        cues = emptyList()
        activeCue = null
        lastLoadedImageUrl = null

        if (spriteUrl.isBlank()) return@LaunchedEffect

        try {
            if (spriteUrl.endsWith(".vtt", true)) {
                val vttText = withContext(Dispatchers.IO) {
                    SpriteUtils.fetchTextFromUrl(spriteUrl)
                }

                if (!vttText.isNullOrBlank()) {
                    cues = parseVttString(vttText, spriteUrl)
                    activeCue = findCueForPosition(cues, positionMs)

                    val imgRef = activeCue?.imageUrl ?: cues.firstOrNull()?.imageUrl
                    val resolved = imgRef?.let { resolveRelativeUrl(it, spriteUrl) }

                    resolved?.let {
                        loadBitmapWithFallback(ctx, it)?.let { bmp ->
                            spriteBitmap = bmp
                            sprW = bmp.width
                            sprH = bmp.height
                            lastLoadedImageUrl = it
                        }
                    }
                }

                if (spriteBitmap == null) {
                    val fallback = spriteUrl.substringBeforeLast('.') + ".jpg"
                    loadBitmapWithFallback(ctx, fallback)?.let { bmp ->
                        spriteBitmap = bmp
                        sprW = bmp.width
                        sprH = bmp.height
                        lastLoadedImageUrl = fallback
                    }
                }
            } else {
                loadBitmapWithFallback(ctx, spriteUrl)?.let { bmp ->
                    spriteBitmap = bmp
                    sprW = bmp.width
                    sprH = bmp.height
                    lastLoadedImageUrl = spriteUrl
                }
            }
        } catch (_: CancellationException) {
        } catch (e: Exception) {
            Log.w(TAG, "Sprite load error: ${e.message}")
        }
    }

    /* ---------------- UPDATE CUE ON SCRUB ---------------- */

    LaunchedEffect(positionMs, cues) {
        if (cues.isEmpty()) return@LaunchedEffect

        val cue = findCueForPosition(cues, positionMs)
        if (cue != activeCue) {
            activeCue = cue
            val resolved = cue?.imageUrl?.let { resolveRelativeUrl(it, spriteUrl) }
            if (!resolved.isNullOrBlank() && resolved != lastLoadedImageUrl) {
                loadBitmapWithFallback(ctx, resolved)?.let { bmp ->
                    spriteBitmap = bmp
                    sprW = bmp.width
                    sprH = bmp.height
                    lastLoadedImageUrl = resolved
                }
            }
        }
    }

    /* ---------------- SOURCE RECT ---------------- */

    val srcRect = remember(spriteBitmap, sprW, sprH, cues, activeCue, positionMs) {
        if (spriteBitmap == null || sprW == 0 || sprH == 0) {
            Quad(0, 0, 1, 1)
        } else if (activeCue?.x != null) {
            Quad(activeCue!!.x!!, activeCue!!.y!!, activeCue!!.w!!, activeCue!!.h!!)
        } else {
            val usedCols = max(1, columns)
            val total = thumbnailsCount ?: usedCols
            val rows = (total + usedCols - 1) / usedCols
            val index = ((positionMs / thumbnailsEachMs).toInt())
                .coerceIn(0, total - 1)

            val col = index % usedCols
            val row = index / usedCols
            val cellW = sprW / usedCols
            val cellH = sprH / rows

            Quad(col * cellW, row * cellH, cellW, cellH)
        }
    }

    /* ---------------- FORCE 16:9 CARD ---------------- */

    val targetWidthDp = widthDp
    val targetHeightDp = (widthDp * 9f / 16f).roundToInt()

    if (spriteBitmap == null) {
        Box(
            modifier = modifier
                .requiredSize(targetWidthDp.dp, targetHeightDp.dp)
                .clip(RoundedCornerShape(cornerRadiusDp.dp))
                .background(Color(0x22000000))
        )
        return
    }

    /* ---------------- DRAW ---------------- */

    val img = spriteBitmap!!.asImageBitmap()

    Box(
        modifier = modifier
            .requiredSize(targetWidthDp.dp, targetHeightDp.dp)
            .clip(RoundedCornerShape(cornerRadiusDp.dp))
            .background(Color(0x22000000))
    ) {
        Canvas(Modifier.matchParentSize()) {

            val srcWf = srcRect.w.toFloat()
            val srcHf = srcRect.h.toFloat()

            // 🎬 CENTER-CROP (video player style)
            val scale = max(size.width / srcWf, size.height / srcHf)

            val dstW = (srcWf * scale).roundToInt()
            val dstH = (srcHf * scale).roundToInt()

            val dstLeft = ((size.width - dstW) / 2f).roundToInt()
            val dstTop = ((size.height - dstH) / 2f).roundToInt()

            drawImage(
                img,
                srcOffset = IntOffset(srcRect.x, srcRect.y),
                srcSize = IntSize(srcRect.w, srcRect.h),
                dstOffset = IntOffset(dstLeft, dstTop),
                dstSize = IntSize(dstW, dstH)
            )
        }
    }
}
