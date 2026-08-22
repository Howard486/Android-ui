package com.foldspace.launcher.core.launcher

import android.graphics.drawable.Drawable
import android.os.UserHandle

/** §14 — which profile an app belongs to, for the drawer's tabs and badging. */
enum class ProfileType { Personal, Work, Private }

/**
 * One launchable activity. [key] is stable across reboots and profile changes,
 * so it is what gets persisted in a dock or Space config — never the Drawable.
 */
data class AppEntry(
    val key: String,
    val packageName: String,
    val className: String,
    val label: String,
    val icon: Drawable?,
    val user: UserHandle,
    val profile: ProfileType,
) {
    /** Case-folded once at construction; the drawer filters on every keystroke. */
    val searchLabel: String = label.lowercase()

    companion object {
        fun keyOf(packageName: String, className: String, userSerial: Long): String =
            "$packageName/$className#$userSerial"
    }
}
