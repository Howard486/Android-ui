package com.foldspace.launcher.ui.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import com.foldspace.launcher.ui.theme.FoldSpaceTheme
import com.foldspace.launcher.ui.theme.MotionLevel

/**
 * iOS's wobble: what makes edit mode unmistakable at a glance.
 *
 * The phase is derived from a seed so neighbouring icons are out of step. In
 * lockstep it reads as the whole page shaking, which is a different and much
 * worse effect.
 *
 * Respects [MotionLevel] — §12.3 caps motion by Power Mode and Battery Saver
 * drops it to none, and a permanent animation across every icon on the screen
 * is exactly what that rule exists to stop.
 */
@Composable
fun Modifier.jiggle(active: Boolean, seed: Long): Modifier {
    val motion = FoldSpaceTheme.motion
    if (!active || motion == MotionLevel.None) return this

    val transition = rememberInfiniteTransition(label = "jiggle")
    val sweep = if (motion == MotionLevel.Reduced) REDUCED_DEGREES else FULL_DEGREES
    // A stable per-item offset: same icon, same phase, across recompositions.
    val phase = ((seed % PHASES) * (PERIOD_MS / PHASES)).toInt()

    val angle by transition.animateFloat(
        initialValue = -sweep,
        targetValue = sweep,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = PERIOD_MS, delayMillis = 0),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = androidx.compose.animation.core.StartOffset(phase),
        ),
        label = "jiggle-angle",
    )

    return this.rotate(angle)
}

private const val FULL_DEGREES = 1.6f
private const val REDUCED_DEGREES = 0.8f
private const val PERIOD_MS = 220
private const val PHASES = 4L
