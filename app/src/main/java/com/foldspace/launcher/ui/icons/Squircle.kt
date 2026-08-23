package com.foldspace.launcher.ui.icons

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.withSign

/**
 * The superellipse behind iOS's icon silhouette.
 *
 * A rounded rectangle is not the same curve: its corners meet the straight
 * edges at a discontinuity in curvature, which is what makes a grid of
 * rounded-rect icons read as slightly lumpy next to a grid of squircles. The
 * superellipse `|x|^n + |y|^n = 1` has no such joint.
 *
 * Sampled rather than approximated with Béziers — a closed form is far easier
 * to check than four hand-fitted control points. The sample count scales with
 * the size being drawn: a fixed 96 segments is finer than a pixel at 52dp but
 * about eight pixels per segment at a 96dp icon on a Fold's inner display,
 * which is visible faceting.
 */
object Squircle {

    /** Apple's silhouette sits near n = 5; lower is boxier, higher rounder. */
    const val EXPONENT = 5.0

    private const val MIN_SEGMENTS = 96
    private const val MAX_SEGMENTS = 512

    /** Roughly two-thirds of a pixel per segment, bounded at both ends. */
    fun segmentsFor(sizePx: Int): Int =
        ((sizePx * 1.5f).toInt()).coerceIn(MIN_SEGMENTS, MAX_SEGMENTS)

    /**
     * The curve's sample points in the unit square, first point repeated last.
     *
     * Pure arithmetic, deliberately separated from any Android type so the
     * geometry can be checked off-device — everything else in this file needs
     * a real Canvas to mean anything.
     */
    fun unitPoints(segments: Int, exponent: Double = EXPONENT): List<Pair<Float, Float>> {
        val count = segments.coerceAtLeast(8)
        val power = 2.0 / exponent
        return (0..count).map { index ->
            val angle = 2.0 * Math.PI * index / count
            val cosine = cos(angle)
            val sine = sin(angle)
            val x = 0.5 + 0.5 * abs(cosine).pow(power).withSign(cosine)
            val y = 0.5 + 0.5 * abs(sine).pow(power).withSign(sine)
            x.toFloat() to y.toFloat()
        }
    }

    /**
     * A closed superellipse filling [width] x [height], pulled in by [inset].
     *
     * The inset exists for the hairline edge: a stroke centred on a path that
     * lies exactly on the bitmap border loses its outer half to the bitmap's
     * own bounds, which renders as a ring of uneven weight.
     */
    fun androidPath(
        width: Float,
        height: Float,
        inset: Float = 0f,
        segments: Int = MIN_SEGMENTS,
        exponent: Double = EXPONENT,
    ): android.graphics.Path {
        val path = android.graphics.Path()
        val innerWidth = (width - inset * 2f).coerceAtLeast(0f)
        val innerHeight = (height - inset * 2f).coerceAtLeast(0f)

        unitPoints(segments, exponent).forEachIndexed { index, (unitX, unitY) ->
            val x = inset + unitX * innerWidth
            val y = inset + unitY * innerHeight
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    /** The same curve as a Compose [Shape], for tiles, folders and the dock. */
    val Shape: Shape = object : Shape {
        override fun createOutline(
            size: Size,
            layoutDirection: LayoutDirection,
            density: Density,
        ): Outline {
            val path = Path()
            // One implementation of the curve, borrowed by the Compose path,
            // rather than two that can drift apart.
            path.asAndroidPath().set(
                androidPath(
                    width = size.width,
                    height = size.height,
                    segments = segmentsFor(maxOf(size.width, size.height).toInt()),
                ),
            )
            return Outline.Generic(path)
        }
    }
}
