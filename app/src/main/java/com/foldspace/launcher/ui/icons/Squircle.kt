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
 * Sampled rather than approximated with Béziers — at icon sizes the segment
 * count below is already finer than a pixel, and a closed form is much easier
 * to check than four hand-fitted control points.
 */
object Squircle {

    /** Apple's silhouette sits near n = 5; lower is boxier, higher rounder. */
    const val EXPONENT = 5.0

    private const val SEGMENTS = 96

    /** A closed superellipse filling [width] x [height]. */
    fun androidPath(
        width: Float,
        height: Float,
        exponent: Double = EXPONENT,
    ): android.graphics.Path {
        val path = android.graphics.Path()
        val halfWidth = width / 2f
        val halfHeight = height / 2f
        val power = 2.0 / exponent

        for (index in 0..SEGMENTS) {
            val angle = 2.0 * Math.PI * index / SEGMENTS
            val cosine = cos(angle)
            val sine = sin(angle)
            val x = halfWidth + halfWidth * abs(cosine).pow(power).withSign(cosine)
            val y = halfHeight + halfHeight * abs(sine).pow(power).withSign(sine)
            if (index == 0) {
                path.moveTo(x.toFloat(), y.toFloat())
            } else {
                path.lineTo(x.toFloat(), y.toFloat())
            }
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
            path.asAndroidPath().set(androidPath(size.width, size.height))
            return Outline.Generic(path)
        }
    }
}
