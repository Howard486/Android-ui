package com.foldspace.launcher.ui.icons

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser

/** One installed icon pack, as offered to the user. */
data class IconPackInfo(val packageName: String, val label: String)

/**
 * Third-party icon packs, in the de-facto Android format.
 *
 * There is no official icon-pack API. What exists is a convention every
 * launcher settled on years ago: a pack is an ordinary APK that declares an
 * intent filter (ADW's or Nova's), and carries `res/xml/appfilter.xml`
 * mapping `ComponentInfo{pkg/cls}` to a drawable name. Reading that is the
 * only way to support the packs people already own.
 *
 * A pack supplies artwork; it does not decide shape. The drawable still goes
 * through [IconShaper], so a pack whose icons are square, round and
 * teardrop-shaped in the same set still comes out as one uniform grid — which
 * is the entire reason the grid looks the way it does now.
 */
class IconPackRepository(private val context: Context) {

    /** Packs installed on this device. */
    fun installed(): List<IconPackInfo> {
        val packageManager = context.packageManager
        val seen = mutableMapOf<String, IconPackInfo>()

        for (action in PACK_ACTIONS) {
            val matches = runCatching {
                packageManager.queryIntentActivities(Intent(action), 0)
            }.getOrDefault(emptyList())

            for (resolved in matches) {
                val name = resolved.activityInfo?.packageName ?: continue
                if (name in seen) continue
                val label = runCatching {
                    packageManager.getApplicationInfo(name, 0).loadLabel(packageManager).toString()
                }.getOrDefault(name)
                seen[name] = IconPackInfo(name, label)
            }
        }
        return seen.values.sortedBy { it.label.lowercase() }
    }

    /** Loads a pack's component map, or null when it cannot be read. */
    fun load(packageName: String): LoadedIconPack? {
        val resources = runCatching {
            context.packageManager.getResourcesForApplication(packageName)
        }.getOrNull() ?: return null

        val filter = runCatching { parseAppFilter(resources, packageName) }.getOrNull()
        if (filter == null || filter.isEmpty) return null
        return LoadedIconPack(packageName, resources, filter)
    }

    /**
     * Reads both entry kinds in one pass.
     *
     * This used to require a `drawable` attribute and `continue` otherwise,
     * which silently dropped every `<calendar>` tag — those carry a `prefix`
     * instead, and that is the whole dynamic-calendar convention.
     */
    private fun parseAppFilter(
        resources: Resources,
        packageName: String,
    ): AppFilterMap {
        val id = resources.getIdentifier("appfilter", "xml", packageName)
        if (id == 0) return AppFilterMap()

        val parser = resources.getXml(id)
        val items = mutableMapOf<String, String>()
        val calendars = mutableMapOf<String, String>()

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue

            val component = parser.getAttributeValue(null, "component") ?: continue
            val key = componentKeyOf(component) ?: continue

            when (parser.name) {
                "item" -> parser.getAttributeValue(null, "drawable")?.let { items[key] = it }
                "calendar" -> parser.getAttributeValue(null, "prefix")?.let { calendars[key] = it }
            }
        }
        return AppFilterMap(items = items, calendars = calendars)
    }

    private companion object {
        val PACK_ACTIONS = listOf(
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.gau.go.launcherex.theme",
        )
    }
}

/** A pack with its map resolved, ready to answer icon lookups. */
class LoadedIconPack(
    val packageName: String,
    private val resources: Resources,
    private val filter: AppFilterMap,
) {
    /**
     * Null means the pack has no art for this app; the caller falls back to
     * the platform drawable.
     *
     * [dayOfMonth] only matters for an app the pack declared as a dynamic
     * calendar; for everything else it is ignored, so callers can pass today
     * unconditionally.
     */
    fun iconFor(component: ComponentName, dayOfMonth: Int = 1): Drawable? {
        val key = "${component.packageName}/${component.className}"
        for (name in filter.drawableNamesFor(key, dayOfMonth)) {
            val id = resources.getIdentifier(name, "drawable", packageName)
            if (id == 0) continue
            @Suppress("DEPRECATION")
            val drawable = runCatching { resources.getDrawable(id, null) }.getOrNull()
            if (drawable != null) return drawable
        }
        return null
    }

    /** Whether this app's art changes with the date — see [AppFilterMap]. */
    fun isDynamic(component: ComponentName): Boolean =
        filter.isDynamic("${component.packageName}/${component.className}")

    val size: Int get() = filter.size
}

/** Reads a pack's own icon for the settings list. */
fun PackageManager.iconPackPreview(packageName: String): Drawable? =
    runCatching { getApplicationIcon(packageName) }.getOrNull()
