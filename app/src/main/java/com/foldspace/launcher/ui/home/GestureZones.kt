package com.foldspace.launcher.ui.home

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * §6 Gesture arbitration.
 *
 * The ordering the spec fixes is: system/payment gestures > Android navigation
 * > FoldSpace gestures > theme animation. The only way for a third-party
 * launcher to honour that is *not to claim the contested region at all* — so
 * the bottom [WALLET_ZONE_HEIGHT] is left as padding that nothing draws into
 * or listens on, and FoldSpace's own swipe handler sits above it.
 *
 * FoldSpace never simulates or forwards a payment gesture (§6.1); if One UI
 * and the launcher disagree, the settings screen points the user at Wallet
 * rather than working around it.
 */
object GestureZones {

    /**
     * Height of the reserved strip. Samsung's Quick Access target sits at the
     * very bottom edge; 48dp covers it without eating a dock row.
     *
     * §22 lists this figure as a Release Gate item — it has to be confirmed on
     * real Galaxy Z Fold hardware, because One UI versions are not guaranteed
     * to agree on the target size.
     */
    val WALLET_ZONE_HEIGHT: Dp = 48.dp

    fun reservedBottomPadding(walletCompatibilityEnabled: Boolean): Dp =
        if (walletCompatibilityEnabled) WALLET_ZONE_HEIGHT else 0.dp
}

/**
 * FoldSpace's own vertical gestures (§6): swipe up opens the drawer, swipe
 * down expands the notification shade.
 *
 * Applied to the home container rather than to an overlay, so it cannot sit in
 * front of the dock and swallow taps. Nothing is consumed until the drag has
 * clearly exceeded slop, which is what keeps a tap on an icon a tap.
 */
fun Modifier.launcherVerticalGestures(
    enabled: Boolean = true,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
): Modifier = if (!enabled) this else this.pointerInput(Unit) {
    var totalDrag = 0f
    detectVerticalDragGestures(
        onDragStart = { totalDrag = 0f },
        onDragEnd = {
            when {
                totalDrag < -SWIPE_THRESHOLD_PX -> onSwipeUp()
                totalDrag > SWIPE_THRESHOLD_PX -> onSwipeDown()
            }
        },
        onDragCancel = { totalDrag = 0f },
        onVerticalDrag = { change, amount ->
            totalDrag += amount
            if (abs(totalDrag) > SWIPE_SLOP_PX) change.consume()
        },
    )
}

/** §6 — left/right switches Space. Separate from the vertical detector so the
 *  two never fight over the same pointer stream. */
fun Modifier.spaceSwipeGestures(
    enabled: Boolean = true,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
): Modifier = if (!enabled) this else this.pointerInput(Unit) {
    var totalDrag = 0f
    detectHorizontalDragGestures(
        onDragStart = { totalDrag = 0f },
        onDragEnd = {
            when {
                totalDrag < -SWIPE_THRESHOLD_PX -> onNext()
                totalDrag > SWIPE_THRESHOLD_PX -> onPrevious()
            }
        },
        onDragCancel = { totalDrag = 0f },
        onHorizontalDrag = { change, amount ->
            totalDrag += amount
            if (abs(totalDrag) > SWIPE_SLOP_PX) change.consume()
        },
    )
}

private const val SWIPE_THRESHOLD_PX = 120f
private const val SWIPE_SLOP_PX = 24f
