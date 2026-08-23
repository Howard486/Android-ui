package com.foldspace.launcher.desktop

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager

/**
 * Opens apps as windows, when the device allows it.
 *
 * The whole mechanism is one public call — `ActivityOptions.setLaunchBounds`,
 * public since API 24 — and one caveat that matters more than the call: when
 * freeform windowing is off the bounds are **ignored without an error**. So
 * [optionsFor] returns null in that case rather than a Bundle that does
 * nothing, and the UI is expected to have already said why.
 */
class DesktopLauncher(private val context: Context) {

    fun state(): FreeformState = FreeformSupport.state(context)

    /**
     * Launch options for the [index]th window this session, or null when this
     * device will not honour them.
     */
    fun optionsFor(index: Int): Bundle? {
        if (state() != FreeformState.Available) return null

        val metrics = runCatching {
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .currentWindowMetrics
                .bounds
        }.getOrNull() ?: return null

        val density = context.resources.displayMetrics.density
        val bounds = DesktopWindows.cascade(
            index = index,
            screenWidth = metrics.width(),
            screenHeight = metrics.height(),
            taskbarHeight = (TASKBAR_DP * density).toInt(),
            stepPx = (CASCADE_STEP_DP * density).toInt(),
            marginPx = (MARGIN_DP * density).toInt(),
        )

        return runCatching {
            ActivityOptions.makeBasic()
                .setLaunchBounds(Rect(bounds.left, bounds.top, bounds.right, bounds.bottom))
                .toBundle()
        }.getOrNull()
    }

    /**
     * Developer options, where the freeform switch lives.
     *
     * There is no deep link to the individual switch and no way for an app to
     * set it — this is as close as the platform allows anyone to get.
     */
    fun openFreeformSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private companion object {
        /** Must match `ui/desktop/TASKBAR_HEIGHT`, or windows open under it. */
        const val TASKBAR_DP = 52f
        const val CASCADE_STEP_DP = 36f
        const val MARGIN_DP = 24f
    }
}
