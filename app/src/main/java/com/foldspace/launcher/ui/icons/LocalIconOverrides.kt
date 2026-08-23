package com.foldspace.launcher.ui.icons

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Hand-picked artwork, by app key.
 *
 * Only the picture is here; a renamed label is applied where the app list is
 * built, so everything downstream — search, the library, folders, the taskbar
 * — sees the new name without knowing overrides exist.
 */
val LocalIconOverrides: ProvidableCompositionLocal<Map<String, String>> =
    staticCompositionLocalOf { emptyMap() }

/**
 * Decodes a user-picked image, downsampled to the size it will be drawn at.
 *
 * Downsampling matters more than usual here: the source is whatever came out
 * of a camera or a download, often several thousand pixels square, and a full
 * decode of one of those to draw a 96dp icon is tens of megabytes for nothing.
 *
 * Returns null on anything that is not a readable image — a revoked
 * permission, a deleted file, a URI that was never an image — and the caller
 * falls back to the app's real icon rather than showing a gap.
 */
fun loadOverrideArtwork(context: Context, uri: String, targetPx: Int): Drawable? = runCatching {
    val parsed = Uri.parse(uri)

    val measure = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(parsed)?.use {
        BitmapFactory.decodeStream(it, null, measure)
    }
    val longest = maxOf(measure.outWidth, measure.outHeight)
    if (longest <= 0) return@runCatching null

    var sample = 1
    while (longest / sample > targetPx * 2 && sample < MAX_SAMPLE) sample *= 2

    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = context.contentResolver.openInputStream(parsed)?.use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: return@runCatching null

    BitmapDrawable(context.resources, bitmap)
}.getOrNull()

private const val MAX_SAMPLE = 32
