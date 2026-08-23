package com.foldspace.launcher.ui.icons

/**
 * An icon pack's `appfilter.xml`, reduced to what a lookup actually needs.
 *
 * Kept free of Android types so the resolution rules below can be tested
 * without a device — the parsing around it cannot be, since `appfilter.xml` is
 * a compiled resource read through the platform's pull parser.
 *
 * Two kinds of entry live in that file:
 *
 *  - `<item component="ComponentInfo{pkg/cls}" drawable="whatsapp" />`, the
 *    ordinary one-app-one-drawable mapping every pack has had for a decade.
 *  - `<calendar component="ComponentInfo{pkg/cls}" prefix="calendar_" />`,
 *    the dynamic-calendar convention Nova introduced and the other launchers
 *    adopted. The pack ships `calendar_1` … `calendar_31` — unpadded, all
 *    thirty-one required — and it is the *launcher's* job to notice the date
 *    changing and ask for a different drawable.
 */
data class AppFilterMap(
    val items: Map<String, String> = emptyMap(),
    val calendars: Map<String, String> = emptyMap(),
) {
    val isEmpty: Boolean get() = items.isEmpty() && calendars.isEmpty()

    val size: Int get() = items.size + calendars.size

    /**
     * Drawable names to try for an app, best first.
     *
     * The convention says a `calendar` entry makes any `item` entry for the
     * same component irrelevant, and packs rely on that: they keep the `item`
     * tag around so launchers without dynamic-calendar support still show
     * *something*. So the day's drawable leads — but the static one is still
     * returned behind it, because a pack missing `calendar_31` should fall
     * back to its own calendar artwork rather than to the platform icon.
     */
    fun drawableNamesFor(componentKey: String, dayOfMonth: Int): List<String> {
        val prefix = calendars[componentKey]
        val item = items[componentKey]
        if (prefix == null) return listOfNotNull(item)
        return listOfNotNull(prefix + dayOfMonth.coerceIn(FIRST_DAY, LAST_DAY), item)
    }

    /**
     * Whether this app's artwork depends on the date.
     *
     * The caller needs to know, because a rasterised icon is cached under a
     * key: a dynamic one has to carry the day in that key or today's art is
     * served until something else evicts it.
     */
    fun isDynamic(componentKey: String): Boolean = componentKey in calendars

    private companion object {
        const val FIRST_DAY = 1
        const val LAST_DAY = 31
    }
}

/** `ComponentInfo{pkg/cls}` is the format packs actually ship. */
fun componentKeyOf(raw: String): String? {
    val inner = raw.substringAfter('{', "").substringBefore('}', "")
    if (inner.isBlank() || '/' !in inner) return null
    return inner
}
