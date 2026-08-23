package com.foldspace.launcher.ui.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

/**
 * Renders any launcher icon into one uniform squircle.
 *
 * The platform hands out two incompatible kinds of drawable and expects the
 * launcher to reconcile them:
 *
 *  - an [AdaptiveIconDrawable] is two 108-unit layers with a 72-unit safe
 *    zone, and `draw()` on the *composite* already applies both that inset and
 *    the device's own icon mask. So the layers are drawn individually here;
 *    calling `draw()` on the composite and scaling by 1.5 as well — which is
 *    what this did before — applies the inset twice and crops the artwork to
 *    its middle two thirds.
 *  - a legacy drawable already carries its own silhouette, often circular. It
 *    has to be inset onto a tile instead, or it floats at the wrong size next
 *    to its masked neighbours.
 *
 * The mask is composited with [PorterDuff.Mode.SRC_IN] against an antialiased
 * path fill rather than applied with `clipPath`. `clipPath` is not
 * antialiased, and hard-edged coverage on a curve is exactly the stair-stepped
 * edge this replaces.
 */
object IconShaper {

    /**
     * The visible fraction of an adaptive icon's layers. AOSP defines them as
     * 108 units with a 72-unit safe zone; the rest is reserved for the mask
     * and for parallax. Applied here to the *layers*, which have no inset of
     * their own.
     */
    private const val ADAPTIVE_SCALE = 108f / 72f

    /** How much of the tile a legacy icon fills. */
    private const val LEGACY_INSET = 0.72f

    /** Hairline edge weight, as a fraction of the icon's size. */
    private const val EDGE_FRACTION = 0.012f

    /**
     * Bounded by bytes, not by entry count.
     *
     * Counting entries was wrong on a foldable: folding changes the display
     * density *and* the icon size, so one app accumulates entries at several
     * pixel sizes, and 300 large bitmaps is tens of megabytes.
     */
    private val maxBytes: Int =
        (Runtime.getRuntime().maxMemory() / CACHE_FRACTION)
            .coerceIn(MIN_CACHE_BYTES, MAX_CACHE_BYTES)
            .toInt()

    private val cache = object : LruCache<String, ImageBitmap>(maxBytes) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            runCatching { value.asAndroidBitmap().allocationByteCount }.getOrDefault(1)
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
        fill: Boolean = false,
    ): ImageBitmap? {
        if (drawable == null || sizePx <= 0) return null

        val cacheKey = "$key@$sizePx@${tile.value}@$fill"
        cache.get(cacheKey)?.let { return it }

        val rendered = try {
            rasterise(drawable, sizePx, tile, fill)
        } catch (oom: OutOfMemoryError) {
            // Swallowing this silently is how icons turn into letter
            // placeholders with no explanation. Drop the cache and let the
            // caller fall back for this frame; the next one has room.
            cache.evictAll()
            null
        } catch (error: Throwable) {
            null
        } ?: return null

        cache.put(cacheKey, rendered)
        return rendered
    }

    /** Drops everything; used when the icon pack or theme changes. */
    fun clear() = cache.evictAll()

    private fun rasterise(
        drawable: Drawable,
        sizePx: Int,
        tile: Color,
        fill: Boolean,
    ): ImageBitmap {
        val artwork = drawArtwork(drawable, sizePx, tile, fill)

        val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val segments = Squircle.segmentsFor(sizePx)
        val mask = Squircle.androidPath(
            width = sizePx.toFloat(),
            height = sizePx.toFloat(),
            segments = segments,
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        // The silhouette first, antialiased, then the artwork through its
        // alpha. This is the whole reason the edges come out smooth.
        canvas.drawPath(mask, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(artwork, 0f, 0f, paint)
        paint.xfermode = null

        artwork.recycle()

        // A hairline edge, the way iOS separates an icon from a light
        // wallpaper. Inset by half its weight so the bitmap's own bounds do
        // not eat the outer half and leave a ring of uneven thickness.
        val edge = sizePx * EDGE_FRACTION
        canvas.drawPath(
            Squircle.androidPath(
                width = sizePx.toFloat(),
                height = sizePx.toFloat(),
                inset = edge / 2f,
                segments = segments,
            ),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = edge
                color = Color.Black.copy(alpha = 0.10f).toArgb()
            },
        )

        return output.asImageBitmap()
    }

    /**
     * The artwork alone, unmasked, at the icon's final size.
     *
     * [fill] covers the whole square rather than insetting onto a tile. That is
     * right for a photograph the user chose — they picked a picture, not a
     * logo, and a photo shrunk to 72% of a coloured square looks like a
     * mistake — and wrong for an app's own legacy icon, which carries its own
     * silhouette and needs the margin.
     */
    private fun drawArtwork(drawable: Drawable, sizePx: Int, tile: Color, fill: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // The drawable is a process-wide shared instance, so its bounds are
        // borrowed rather than taken.
        val saved = Rect(drawable.bounds)

        if (drawable is AdaptiveIconDrawable) {
            // The layers, never the composite: the composite would apply its
            // own inset and the device mask on top of these bounds.
            val extent = (sizePx * ADAPTIVE_SCALE).roundToInt()
            val offset = -((extent - sizePx) / 2f).roundToInt()
            val bounds = Rect(offset, offset, offset + extent, offset + extent)

            drawable.background?.drawWithin(canvas, bounds)
            drawable.foreground?.drawWithin(canvas, bounds)
        } else if (fill) {
            drawable.drawWithin(canvas, Rect(0, 0, sizePx, sizePx))
        } else {
            canvas.drawColor(tile.toArgb())
            val inset = ((sizePx * (1f - LEGACY_INSET)) / 2f).roundToInt()
            drawable.drawWithin(canvas, Rect(inset, inset, sizePx - inset, sizePx - inset))
        }

        drawable.bounds = saved
        return bitmap
    }

    private fun Drawable.drawWithin(canvas: Canvas, target: Rect) {
        val saved = Rect(bounds)
        bounds = target
        draw(canvas)
        bounds = saved
    }

    /** At most this fraction of the heap, and never outside these bounds. */
    private const val CACHE_FRACTION = 16L
    private const val MIN_CACHE_BYTES = 4L * 1024 * 1024
    private const val MAX_CACHE_BYTES = 24L * 1024 * 1024
}
