package com.foldspace.launcher.core.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * §5.2 — the app inventory, built on `LauncherApps` so cross-profile listing
 * and launching work the way the platform intends.
 *
 * The list is rebuilt from a package callback, never from a timer. Icons are
 * loaded on the IO dispatcher because `loadIcon` hits the package manager and
 * decodes a drawable per app, which is far too slow for the main thread on a
 * device with a few hundred apps.
 */
class LauncherAppsRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    private val launcherApps =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    private val userManager =
        context.getSystemService(Context.USER_SERVICE) as UserManager

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = refresh()
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = refresh()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = refresh()

        override fun onPackagesAvailable(
            packageNames: Array<out String>?,
            user: UserHandle?,
            replacing: Boolean,
        ) = refresh()

        override fun onPackagesUnavailable(
            packageNames: Array<out String>?,
            user: UserHandle?,
            replacing: Boolean,
        ) = refresh()
    }

    fun start() {
        launcherApps.registerCallback(callback)
        refresh()
    }

    fun stop() {
        runCatching { launcherApps.unregisterCallback(callback) }
    }

    fun refresh() {
        scope.launch {
            val loaded = withContext(Dispatchers.IO) { loadAll() }
            _apps.value = loaded
            _loading.value = false
        }
    }

    private fun loadAll(): List<AppEntry> {
        val self = context.packageName
        val entries = mutableListOf<AppEntry>()

        for (user in profiles()) {
            val serial = userManager.getSerialNumberForUser(user)
            val profileType = profileTypeOf(user)
            val activities = runCatching {
                launcherApps.getActivityList(null, user)
            }.getOrDefault(emptyList())

            for (activity in activities) {
                // Hiding ourselves keeps "FoldSpace" out of its own drawer,
                // which is otherwise the first thing every tester reports.
                if (activity.applicationInfo.packageName == self) continue
                entries += activity.toEntry(serial, profileType)
            }
        }

        return entries.sortedWith(
            compareBy(String.CASE_INSENSITIVE_ORDER) { it.label },
        )
    }

    private fun LauncherActivityInfo.toEntry(serial: Long, profile: ProfileType): AppEntry {
        val density = context.resources.displayMetrics.densityDpi
        return AppEntry(
            key = AppEntry.keyOf(componentName.packageName, componentName.className, serial),
            packageName = componentName.packageName,
            className = componentName.className,
            label = label?.toString().orEmpty().ifBlank { componentName.packageName },
            icon = runCatching { getBadgedIcon(density) }.getOrNull(),
            user = user,
            profile = profile,
        )
    }

    /**
     * §14.2 — Private Space profiles are only visible to the default home app
     * holding ACCESS_HIDDEN_PROFILES. `getUserProfiles()` already reflects
     * that, so an ineligible launcher simply sees fewer profiles rather than
     * throwing.
     */
    private fun profiles(): List<UserHandle> =
        runCatching { launcherApps.profiles }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: userManager.userProfiles

    private fun profileTypeOf(user: UserHandle): ProfileType {
        if (user == android.os.Process.myUserHandle()) return ProfileType.Personal
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            val isPrivate = runCatching {
                launcherApps.getLauncherUserInfo(user)?.userType ==
                    UserManager.USER_TYPE_PROFILE_PRIVATE
            }.getOrDefault(false)
            if (isPrivate) return ProfileType.Private
        }
        return ProfileType.Work
    }

    /** §14.2 — a locked Private Space must not leak apps through search. */
    fun isProfileLocked(user: UserHandle): Boolean =
        runCatching { userManager.isQuietModeEnabled(user) }.getOrDefault(false)

    fun launch(entry: AppEntry, sourceBounds: Rect? = null, opts: Bundle? = null): Boolean =
        runCatching {
            launcherApps.startMainActivity(
                ComponentName(entry.packageName, entry.className),
                entry.user,
                sourceBounds,
                opts,
            )
            true
        }.getOrDefault(false)

    fun openAppInfo(entry: AppEntry, sourceBounds: Rect? = null): Boolean =
        runCatching {
            launcherApps.startAppDetailsActivity(
                ComponentName(entry.packageName, entry.className),
                entry.user,
                sourceBounds,
                null,
            )
            true
        }.getOrDefault(false)

    /** Fallback used when an app details activity is unavailable for a profile. */
    fun openSystemAppSettings(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(android.net.Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
}
