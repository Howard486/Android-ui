package com.foldspace.launcher.ui.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

/**
 * Renders any launcher icon into one uniform squircle.
 *
 * This is the single biggest reason a page of Android icons looks ragged next
 * to a page of iOS ones, and it is not about taste: the platform hands out two
 * incompatible kinds of drawable and expects the launcher to reconcile them.
 *
 *  - An [AdaptiveIconDrawable] is authored as two 108-unit layers with only
 *    the middle 72 guaranteed visible. Drawn at its natural bounds it shows
 *    the whole background layer, bleed and all — which is what FoldSpace did
 *    before this existed.
 *  - A legacy drawable already carries its own silhouette, often circular,
 *    often with its own baked-in shadow. Masking it does nothing useful; it
 *    has to be inset onto a tile instead, or it floats at the wrong size next
 *    to its masked neighbours.
 *
 * Both come out of here as the same shape at the same size, which is the
 * whole point.
 */
object IconShaper {

    /**
     * The visible fraction of an adaptive icon's layers. AOSP defines the
     * layers as 108 units with a 72-unit safe zone; anything outside is
     * reserved for the mask and for parallax.
     */
    private const val ADAPTIVE_SCALE = 108f / 72f

    /** How much of the tile a legacy icon fills. */
    private const val LEGACY_INSET = 0.72f

    private val cache = object : LruCache<String, ImageBitmap>(CACHE_ENTRIES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = 1
    }

    /**
     * @param key stable per app, so the cache survives scrolling
     * @param tile the colour drawn behind a legacy icon; adaptive icons bring
     *   their own background layer and ignore it
     */
    fun render(
        drawable: Drawable?,
        sizePx: Int,
        key: String,
        tile: Color,
    ): ImageBitmap? {
        if (drawable == null || sizePx <= 0) return null

        val cacheKey = "$key@$sizePx@${tile.value}"
        cache.get(cacheKey)?.let { return it }

        val rendered = runCatching { rasterise(drawable, sizePx, tile) }.getOrNull() ?: return null
        cache.put(cacheKey, rendered)
        return rendered
    }

    /** Drops everything; used when the icon pack or theme changes. */
    fun clear() = cache.evictAll()

    private fun rasterise(drawable: Drawable, sizePx: Int, tile: Color): ImageBitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val mask = Squircle.androidPath(sizePx.toFloat(), sizePx.toFloat())

        canvas.save()
        canvas.clipPath(mask)

        if (drawable is AdaptiveIconDrawable) {
            // Oversize the layers and centre them, so the mask cuts through
            // the reserved margin rather than through the artwork.
            val extent = (sizePx * ADAPTIVE_SCALE).roundToInt()
            val offset = -((extent - sizePx) / 2)
            drawable.setBounds(offset, offset, offset + extent, offset + extent)
            drawable.draw(canvas)
        } else {
            canvas.drawColor(tile.toArgb())
            val inset = ((sizePx * (1f - LEGACY_INSET)) / 2f).roundToInt()
            drawable.setBounds(inset, inset, sizePx - inset, sizePx - inset)
            drawable.draw(canvas)
        }

        canvas.restore()

        // A hairline edge, the way iOS separates an icon from a light
        // wallpaper. Without it a pale icon dissolves into a pale background.
        canvas.drawPath(
            mask,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = sizePx * 0.012f
                color = Color.Black.copy(alpha = 0.10f).toArgb()
            },
        )

        return bitmap.asImageBitmap()
    }

    /** Enough for a couple of full pages at two sizes; icons are small. */
    private const val CACHE_ENTRIES = 300
}
