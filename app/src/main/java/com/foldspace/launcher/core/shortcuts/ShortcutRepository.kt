package com.foldspace.launcher.core.shortcuts

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.UserHandle
import com.foldspace.launcher.core.launcher.AppEntry

/** One entry in the long-press menu. */
data class AppShortcut(
    val id: String,
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val user: UserHandle,
)

/**
 * §5.3 — the shortcuts an app publishes, shown on long-press.
 *
 * "New message", "Search", "Scan" — the entries every other launcher shows
 * and FoldSpace did not, because nothing ever queried them. Long-press went
 * straight to App Info, which is the least useful of the actions available.
 *
 * The query needs [LauncherApps.hasShortcutHostPermission], granted only to
 * the default home app. That makes an empty menu ambiguous — no shortcuts, or
 * not the default launcher? — so [available] answers that separately and the
 * UI says which it is.
 */
class ShortcutRepository(context: Context) {

    private val launcherApps =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    /** False when FoldSpace is not the default home app. */
    fun available(): Boolean =
        runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)

    fun shortcutsFor(entry: AppEntry): List<AppShortcut> {
        if (!available()) return emptyList()

        val query = LauncherApps.ShortcutQuery()
            .setPackage(entry.packageName)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
            )

        val found = runCatching { launcherApps.getShortcuts(query, entry.user) }
            .getOrNull()
            .orEmpty()
            .filterNotNull()

        return found
            .filter { it.isEnabled }
            // Static shortcuts carry a rank the app chose; dynamic ones are
            // ordered by the app too. Respecting both beats re-sorting them.
            .sortedBy { it.rank }
            .take(MAX_SHORTCUTS)
            .map { info ->
                AppShortcut(
                    id = info.id,
                    packageName = info.`package`,
                    label = info.shortLabel?.toString()
                        ?: info.longLabel?.toString()
                        ?: info.id,
                    icon = runCatching { launcherApps.getShortcutIconDrawable(info, 0) }.getOrNull(),
                    user = info.userHandle,
                )
            }
    }

    /** Launches a shortcut placed on the home screen, by id. */
    fun launch(packageName: String, shortcutId: String, user: UserHandle): Boolean = runCatching {
        launcherApps.startShortcut(packageName, shortcutId, null, null, user)
        true
    }.getOrDefault(false)

    fun launch(shortcut: AppShortcut): Boolean = runCatching {
        launcherApps.startShortcut(
            shortcut.packageName,
            shortcut.id,
            null,
            null,
            shortcut.user,
        )
        true
    }.getOrDefault(false)

    /**
     * Accepts an app's request to pin a shortcut. The system hands over a
     * [LauncherApps.PinItemRequest]; accepting it is what actually creates
     * the shortcut, and until then the app has been told nothing.
     */
    fun acceptPinnedShortcut(request: LauncherApps.PinItemRequest): ShortcutInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        if (request.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) return null
        val info = request.shortcutInfo ?: return null
        return if (runCatching { request.accept() }.getOrDefault(false)) info else null
    }

    private companion object {
        /** Android itself caps published shortcuts at a handful; so does the menu. */
        const val MAX_SHORTCUTS = 5
    }
}
