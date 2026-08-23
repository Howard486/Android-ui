package com.foldspace.launcher.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import kotlin.math.ceil

/**
 * §13 — hosting third-party App Widgets.
 *
 * Android's own guidance for a home-screen replacement is to implement an
 * `AppWidgetHost`, and the awkward part is binding: only a system app holds
 * `BIND_APPWIDGET`, so every widget an ordinary launcher adds needs the user
 * to approve it through a system dialog. That flow is driven from the
 * Activity; this class owns everything either side of it.
 */
class WidgetHostController(private val context: Context) {

    private val widgetManager: AppWidgetManager = AppWidgetManager.getInstance(context)

    val host: AppWidgetHost = AppWidgetHost(context, HOST_ID)

    private var listening = false

    /**
     * Widgets only update while the host is listening, so this is tied to the
     * launcher being visible rather than being left on: a backgrounded
     * launcher receiving widget updates is exactly the kind of standing cost
     * §12 exists to avoid.
     */
    fun startListening() {
        if (listening) return
        runCatching { host.startListening() }.onSuccess { listening = true }
    }

    fun stopListening() {
        if (!listening) return
        runCatching { host.stopListening() }
        listening = false
    }

    fun installedProviders(): List<AppWidgetProviderInfo> =
        runCatching { widgetManager.installedProviders }.getOrDefault(emptyList())

    fun providerFor(appWidgetId: Int): AppWidgetProviderInfo? =
        runCatching { widgetManager.getAppWidgetInfo(appWidgetId) }.getOrNull()

    fun allocateId(): Int = host.allocateAppWidgetId()

    fun releaseId(appWidgetId: Int) {
        runCatching { host.deleteAppWidgetId(appWidgetId) }
    }

    /**
     * True when we may skip the consent dialog. Binding without permission
     * throws, so this is checked rather than attempted.
     */
    fun canBindWithoutPrompt(info: AppWidgetProviderInfo, appWidgetId: Int): Boolean =
        runCatching {
            widgetManager.bindAppWidgetIdIfAllowed(appWidgetId, info.profile, info.provider, null)
        }.getOrDefault(false)

    /** The system dialog that asks the user to allow this specific binding. */
    fun bindPermissionIntent(appWidgetId: Int, info: AppWidgetProviderInfo): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
        }

    /** Widgets that declare a configuration activity must run it before use. */
    fun needsConfiguration(info: AppWidgetProviderInfo): Boolean = info.configure != null

    fun createView(appWidgetId: Int): AppWidgetHostView? {
        val info = providerFor(appWidgetId) ?: return null
        return runCatching { host.createView(context, appWidgetId, info) }.getOrNull()
    }

    /**
     * §4 — a fold changes the cell size under a widget, so the host has to be
     * told the new box. Without this the widget keeps rendering for the folded
     * width on the inner screen, which is the single most visible way a
     * launcher can look broken on a foldable.
     */
    fun updateSize(appWidgetId: Int, widthDp: Int, heightDp: Int) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                widgetManager.updateAppWidgetOptions(
                    appWidgetId,
                    Bundle().apply {
                        putParcelableArrayList(
                            AppWidgetManager.OPTION_APPWIDGET_SIZES,
                            arrayListOf(SizeF(widthDp.toFloat(), heightDp.toFloat())),
                        )
                    },
                )
            } else {
                widgetManager.updateAppWidgetOptions(
                    appWidgetId,
                    Bundle().apply {
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
                    },
                )
            }
        }
    }

    /**
     * How many grid cells a provider wants, given the cell size in dp.
     *
     * `minWidth`/`minHeight` arrive already resolved to pixels, so they are
     * converted back before being compared with a dp cell. Rounded up, not up
     * by one: a widget asking for exactly two cells' worth used to be given
     * three.
     */
    fun defaultSpan(info: AppWidgetProviderInfo, cellWidthDp: Int, cellHeightDp: Int): Pair<Int, Int> {
        val density = context.resources.displayMetrics.density.takeIf { it > 0f } ?: 1f
        val widthDp = info.minWidth.coerceAtLeast(1) / density
        val heightDp = info.minHeight.coerceAtLeast(1) / density
        val spanX = ceil(widthDp / cellWidthDp.coerceAtLeast(1)).toInt()
        val spanY = ceil(heightDp / cellHeightDp.coerceAtLeast(1)).toInt()
        return spanX.coerceAtLeast(1) to spanY.coerceAtLeast(1)
    }

    private companion object {
        /** Any stable non-zero id; it only has to be unique within this app. */
        const val HOST_ID = 0x0F01D
    }
}
