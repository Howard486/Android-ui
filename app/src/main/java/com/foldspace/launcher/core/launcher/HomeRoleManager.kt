package com.foldspace.launcher.core.launcher

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * §5.1 — becoming the default Home app.
 *
 * `RoleManager.ROLE_HOME` (API 29+) is the supported path and gives a single
 * system dialog. Where the role request is unavailable or declined we fall
 * back to the Home settings screen rather than pestering the user again.
 */
class HomeRoleManager(private val context: Context) {

    private val roleManager: RoleManager? =
        context.getSystemService(Context.ROLE_SERVICE) as? RoleManager

    val isSupported: Boolean
        get() = roleManager?.isRoleAvailable(RoleManager.ROLE_HOME) == true

    /** True when FoldSpace is the current default Home app. */
    val isDefaultHome: Boolean
        get() = roleManager?.isRoleHeld(RoleManager.ROLE_HOME) == true

    /**
     * Intent for the system's "make this the default Home app" dialog, or null
     * when the role is not available on this device.
     */
    fun createRequestRoleIntent(): Intent? =
        roleManager?.takeIf { it.isRoleAvailable(RoleManager.ROLE_HOME) }
            ?.createRequestRoleIntent(RoleManager.ROLE_HOME)

    /** Settings fallback — also the only route to *un*set a default Home app. */
    fun homeSettingsIntent(): Intent =
        Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
