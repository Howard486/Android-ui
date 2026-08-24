package com.foldspace.launcher.ui.widgets

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.foldspace.launcher.ui.theme.FoldSpaceTheme
import com.foldspace.launcher.widgets.WidgetHostController

/**
 * A hosted third-party App Widget.
 *
 * `AppWidgetHostView` is a platform View, so this is the one place the
 * launcher drops out of Compose. The size push on every layout change is what
 * makes widgets survive a fold: without it the widget keeps rendering at the
 * folded width on the inner screen (§4).
 */
@Composable
fun WidgetCell(
    controller: WidgetHostController,
    appWidgetId: Int,
    widthDp: Int,
    heightDp: Int,
    modifier: Modifier = Modifier,
) {
    val tokens = FoldSpaceTheme.tokens
    val hostView = remember(appWidgetId) { controller.createView(appWidgetId) }

    if (hostView == null) {
        // A widget whose provider was uninstalled, or one whose binding was
        // revoked. Drawn as an explicit placeholder rather than nothing, so
        // the empty space is legible as a problem the user can act on.
        Box(
            modifier
                .clip(MaterialTheme.shapes.medium)
                .background(tokens.surfaceElevated.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "小工具無法載入",
                style = MaterialTheme.typography.labelSmall,
                color = tokens.textMuted,
                modifier = Modifier.padding(8.dp),
            )
        }
        return
    }

    DisposableEffect(appWidgetId, widthDp, heightDp) {
        controller.updateSize(appWidgetId, widthDp, heightDp)
        onDispose { }
    }

    AndroidView(
        factory = { context ->
            // Wrapped rather than hosted directly, so the wrapper can claim
            // the gesture on behalf of a widget that scrolls. See
            // [ScrollAwareWidgetHost].
            ScrollAwareWidgetHost(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                (hostView.parent as? ViewGroup)?.removeView(hostView)
                hostView.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                addView(hostView)
            }
        },
        modifier = modifier.clip(MaterialTheme.shapes.medium),
        update = { controller.updateSize(appWidgetId, widthDp, heightDp) },
    )
}

/**
 * Lets a widget that scrolls actually scroll.
 *
 * A collection widget — Outlook's inbox, a calendar agenda, a news list — has
 * a `ListView` inside it. Sitting inside a Compose page that scrolls, the
 * page's gesture detector wins the vertical drag and that list never moves.
 * `requestDisallowInterceptTouchEvent` is the platform's answer, and Compose's
 * host view honours it, so a drag that starts on the widget belongs to the
 * widget.
 *
 * Claimed only when there is something to scroll. A clock or a weather widget
 * has no scrollable descendant, and taking the gesture from those would mean
 * the page could not be scrolled by dragging on them — a dead patch of screen
 * for no benefit. The check is a tree walk on ACTION_DOWN, over a view
 * hierarchy that is a handful of nodes deep.
 */
private class ScrollAwareWidgetHost(context: Context) : FrameLayout(context) {

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && canScrollAnywhere(this)) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        return false
    }

    private fun canScrollAnywhere(view: View): Boolean {
        if (view.canScrollVertically(1) || view.canScrollVertically(-1)) return true
        if (view !is ViewGroup) return false
        for (index in 0 until view.childCount) {
            if (canScrollAnywhere(view.getChildAt(index))) return true
        }
        return false
    }
}
