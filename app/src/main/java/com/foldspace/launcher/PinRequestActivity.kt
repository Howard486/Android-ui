package com.foldspace.launcher

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.foldspace.launcher.home.HomeSurface
import com.foldspace.launcher.home.Posture
import kotlinx.coroutines.launch

/**
 * Handles an app asking to put something on the home screen.
 *
 * `requestPinShortcut` and `requestPinAppWidget` are how an app says "add me";
 * the system routes the confirmation to whichever launcher declares these
 * actions. FoldSpace declared neither, so every such request silently did
 * nothing — the user tapped "Add to home screen" and no icon ever appeared.
 *
 * The activity is invisible. A full confirmation screen for "may I add this
 * icon" is more ceremony than the action deserves, and the requesting app has
 * already shown its own prompt by the time this runs. The outcome is a toast.
 */
class PinRequestActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            finish()
            return
        }

        val launcherApps = getSystemService(LAUNCHER_APPS_SERVICE) as? LauncherApps
        val request = runCatching { launcherApps?.getPinItemRequest(intent) }.getOrNull()
        if (request == null || !request.isValid) {
            finish()
            return
        }

        when (request.requestType) {
            LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT -> acceptShortcut(request)
            LauncherApps.PinItemRequest.REQUEST_TYPE_APPWIDGET -> acceptWidget(request)
            else -> finish()
        }
    }

    private fun acceptShortcut(request: LauncherApps.PinItemRequest) {
        val container = appContainer
        val info = container.shortcuts.acceptPinnedShortcut(request)
        if (info == null) {
            report(R.string.pin_failed)
            return
        }

        lifecycleScope.launch {
            container.homeLayout.addPinnedShortcut(
                surface = HomeSurface.Desktop,
                posture = currentPosture(),
                choice = container.settings.settings.value.gridChoice,
                shortcutId = info.id,
                packageName = info.`package`,
                label = info.shortLabel?.toString() ?: info.longLabel?.toString() ?: info.id,
            )
            report(R.string.pin_added)
        }
    }

    /**
     * The launcher allocates the widget id and hands it back through
     * `accept()`. Accepting without one leaves the provider bound to nothing,
     * which is the failure mode this flow exists to avoid.
     */
    private fun acceptWidget(request: LauncherApps.PinItemRequest) {
        val container = appContainer
        val info = request.getAppWidgetProviderInfo(this)
        if (info == null) {
            report(R.string.pin_failed)
            return
        }

        val appWidgetId = container.widgetHost.allocateId()
        val accepted = runCatching {
            request.accept(
                Bundle().apply { putInt(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId) },
            )
        }.getOrDefault(false)

        if (!accepted) {
            // An id that never got bound would leak for the life of the host.
            container.widgetHost.releaseId(appWidgetId)
            report(R.string.pin_failed)
            return
        }

        lifecycleScope.launch {
            val choice = container.settings.settings.value.gridChoice
            val posture = currentPosture()
            val cell = container.cellSizeDp(posture, choice)
            val (spanX, spanY) = container.widgetHost.defaultSpan(info, cell.first, cell.second)
            container.homeLayout.addPinnedWidget(
                surface = HomeSurface.Desktop,
                posture = posture,
                choice = choice,
                appWidgetId = appWidgetId,
                provider = info.provider.flattenToString(),
                spanX = spanX,
                spanY = spanY,
            )
            report(R.string.pin_added)
        }
    }

    /**
     * The pin flow has no window of its own to measure, so posture comes from
     * the configuration. The threshold is the same one the fold tracker uses
     * to call a window Expanded.
     */
    private fun currentPosture(): Posture =
        if (resources.configuration.screenWidthDp >= UNFOLDED_WIDTH_DP) {
            Posture.Unfolded
        } else {
            Posture.Folded
        }

    private fun report(messageRes: Int) {
        Toast.makeText(this, messageRes, Toast.LENGTH_SHORT).show()
        finish()
    }

    private companion object {
        const val UNFOLDED_WIDTH_DP = 600
    }
}
