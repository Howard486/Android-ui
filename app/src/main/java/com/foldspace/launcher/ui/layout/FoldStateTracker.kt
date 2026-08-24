package com.foldspace.launcher.ui.layout

import android.app.Activity
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * §4 — turns `WindowInfoTracker`'s layout info into a [FoldWindowState].
 *
 * This is the only place in the app that knows about `FoldingFeature`. It is a
 * pure event stream: it emits when the posture actually changes and does no
 * polling of its own (§12.1 rule 1).
 */
class FoldStateTracker(private val activity: Activity) {

    /**
     * Configuration changes, as a second source of truth.
     *
     * `windowLayoutInfo` emits when *display features* change, and folding a
     * Fold shut moves the activity to a different display entirely. On the
     * hardware that does not emit for that, nothing ever re-read the width and
     * the launcher stayed in its unfolded layout on the cover screen.
     *
     * MainActivity declares `configChanges`, so the swap arrives there as
     * `onConfigurationChanged` and nothing was listening. Now it is.
     */
    private val configurationChanges =
        MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)

    fun onConfigurationChanged() {
        configurationChanges.tryEmit(Unit)
    }

    fun states(): Flow<FoldWindowState> =
        combine(
            WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity),
            configurationChanges.onStart { emit(Unit) },
        ) { info, _ -> info }
            // Resolved on every trigger, because the width is read from the
            // display at that moment rather than carried in the event.
            .map(::resolve)
            .distinctUntilChanged()

    private fun resolve(info: WindowLayoutInfo): FoldWindowState {
        val metrics = activity.resources.displayMetrics
        val density = metrics.density.takeIf { it > 0f } ?: 1f
        val widthDp = (metrics.widthPixels / density).toInt()
        val heightDp = (metrics.heightPixels / density).toInt()

        val fold = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()

        if (fold == null) {
            // No folding feature: fall back to width classes, so FoldSpace is
            // still usable on a phone or tablet (§4, fallback branch).
            return FoldWindowState(
                layoutMode = if (widthDp >= EXPANDED_WIDTH_DP) LayoutMode.Expanded
                else LayoutMode.Compact,
                hingeBoundsPx = null,
                widthDp = widthDp,
                heightDp = heightDp,
            )
        }

        val isVerticalHinge = fold.orientation == FoldingFeature.Orientation.VERTICAL
        val mode = when (fold.state) {
            FoldingFeature.State.HALF_OPENED ->
                if (isVerticalHinge) LayoutMode.Book else LayoutMode.Tabletop

            // FLAT means unfolded — but a flat *outer* screen is still narrow,
            // so width has the final say on whether a workspace fits.
            else ->
                if (widthDp >= EXPANDED_WIDTH_DP) LayoutMode.Expanded else LayoutMode.Compact
        }

        return FoldWindowState(
            layoutMode = mode,
            hingeBoundsPx = HingeBounds(
                left = fold.bounds.left,
                top = fold.bounds.top,
                right = fold.bounds.right,
                bottom = fold.bounds.bottom,
                isOccluding = fold.occlusionType == FoldingFeature.OcclusionType.FULL,
                isVertical = isVerticalHinge,
            ),
            widthDp = widthDp,
            heightDp = heightDp,
        )
    }

    private companion object {
        /** Material 3 "medium" width class — below this a 2-column dashboard is cramped. */
        const val EXPANDED_WIDTH_DP = 600
    }
}
