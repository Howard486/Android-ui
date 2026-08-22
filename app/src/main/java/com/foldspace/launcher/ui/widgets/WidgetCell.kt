package com.foldspace.launcher.ui.widgets

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
        factory = {
            hostView.apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        modifier = modifier.clip(MaterialTheme.shapes.medium),
        update = { controller.updateSize(appWidgetId, widthDp, heightDp) },
    )
}
