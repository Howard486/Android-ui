package com.foldspace.launcher.desktop

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Whether this device will honour a launch rectangle.
 *
 * The reason this exists at all: when freeform windowing is off,
 * `setLaunchBounds` is **silently ignored**. The app opens full screen, no
 * exception is thrown, nothing reports a failure. Shipping desktop mode
 * without this check would mean a mode that looks implemented and does
 * nothing — which is the substitution this project refused for Google
 * Discover and for App Pairs, and refuses again here.
 */
enum class FreeformState {
    /** Windows will open where FoldSpace puts them. */
    Available,

    /** The switch exists; the user has to turn it on and reboot. */
    NeedsDeveloperOption,

    /** Samsung DeX is running and is already doing this job properly. */
    DeskModeActive,
}

object FreeformSupport {

    /**
     * The AOSP developer-options switch, read directly.
     *
     * Not a constant in the SDK — `Settings.Global` does not expose it — so
     * the string is the interface. It has been stable since Nougat.
     */
    private const val ENABLE_FREEFORM = "enable_freeform_support"

    fun state(context: Context): FreeformState = when {
        isDeskModeActive(context) -> FreeformState.DeskModeActive
        hasFreeformFeature(context) || isFreeformSettingOn(context) -> FreeformState.Available
        else -> FreeformState.NeedsDeveloperOption
    }

    private fun hasFreeformFeature(context: Context): Boolean = runCatching {
        context.packageManager
            .hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)
    }.getOrDefault(false)

    private fun isFreeformSettingOn(context: Context): Boolean = runCatching {
        Settings.Global.getInt(context.contentResolver, ENABLE_FREEFORM, 0) == 1
    }.getOrDefault(false)

    /**
     * Samsung DeX, detected through the field One UI adds to `Configuration`.
     *
     * Reflection because it is not in the public SDK and never will be. If the
     * field moves or disappears this reports false, which is the safe answer:
     * FoldSpace then offers its own desktop mode rather than standing aside
     * from something that is not running.
     *
     * Standing aside is the point. DeX has real window management — moving,
     * resizing, a title bar, a task switcher — and FoldSpace has none of that.
     * Drawing a second taskbar over it would be worse than useless.
     */
    private fun isDeskModeActive(context: Context): Boolean = runCatching {
        val configuration = context.resources.configuration
        val field = configuration.javaClass.getField("semDesktopModeEnabled")
        val enabled = field.getInt(configuration)
        val on = configuration.javaClass.getField("SEM_DESKTOP_MODE_ENABLED").getInt(null)
        enabled == on
    }.getOrDefault(false)
}
