package com.foldspace.launcher.pairs

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build
import com.foldspace.launcher.core.launcher.AppEntry
import com.foldspace.launcher.core.launcher.LauncherAppsRepository

/**
 * Opens two apps side by side — as far as the platform allows a third-party
 * launcher to.
 *
 * The honest position, up front: **Android has no public API for a launcher
 * to start a split-screen pair from a single screen.**
 * `FLAG_ACTIVITY_LAUNCH_ADJACENT` only places an activity in the *other* pane
 * of a split that already exists; it does nothing from a full-screen home.
 * Samsung's own launcher does this through internal APIs FoldSpace cannot
 * call, and the system-side entry point (`ActivityTaskManager`) is signature
 * permission only.
 *
 * So this does the one thing that does work everywhere: launches the first
 * app, then launches the second with LAUNCH_ADJACENT. On a device already in
 * multi-window — which on a Fold is one gesture away — the second lands
 * beside the first. Everywhere else the second simply takes over, and the
 * caller is told that is what will happen rather than being left to discover
 * it.
 *
 * The alternative would be to advertise "App Pair" and quietly deliver "opens
 * two apps one after another", which is the kind of silent substitution this
 * project has refused elsewhere (see the Google Discover provider).
 */
class SplitLauncher(
    private val context: Context,
    private val launcherApps: LauncherAppsRepository,
) {

    /** What the UI should promise the user on this device. */
    enum class Support {
        /** The system is in multi-window now; the pair will land side by side. */
        Adjacent,

        /** Sequential launch only — the second app will cover the first. */
        Sequential,
    }

    fun support(inMultiWindow: Boolean): Support =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && inMultiWindow) {
            Support.Adjacent
        } else {
            Support.Sequential
        }

    /**
     * Launches both halves. Returns false when either app is missing, so the
     * caller can offer to repair the pair rather than showing nothing.
     */
    fun launch(pair: AppPair, apps: List<AppEntry>): Boolean {
        val byKey = apps.associateBy { it.key }
        val first = byKey[pair.firstKey] ?: return false
        val second = byKey[pair.secondKey] ?: return false

        launcherApps.launch(first)

        val intent = context.packageManager.getLaunchIntentForPackage(second.packageName)
            ?: return false
        intent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_MULTIPLE_TASK or
                Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT,
        )

        return runCatching {
            context.startActivity(intent, ActivityOptions.makeBasic().toBundle())
        }.isSuccess
    }
}
