package com.foldspace.launcher.home

/** What one tile in a quick-launch block does. */
enum class QuickTileKind(val key: String) {
    /** Opens an installed app. `target` is `packageName/className`. */
    App("app"),

    /**
     * Opens one of an app's published shortcuts — "New message", a saved
     * conversation. `target` is `packageName/shortcutId`.
     */
    Shortcut("shortcut"),

    /** Opens a web address. `target` is the URL. */
    Url("url"),
    ;

    companion object {
        fun fromKey(key: String?): QuickTileKind? = entries.firstOrNull { it.key == key }
    }
}

/**
 * One tile.
 *
 * [label] is what the user typed, or empty to let the block work one out — an
 * app's own name, a shortcut's own name, a URL's host.
 */
data class QuickTile(
    val kind: QuickTileKind,
    val target: String,
    val label: String = "",
) {
    val packageName: String? get() = target.substringBefore('/', "").takeIf { it.isNotBlank() }

    val component: String? get() = target.takeIf { kind == QuickTileKind.App && '/' in it }

    val shortcutId: String?
        get() = target.substringAfter('/', "").takeIf { kind == QuickTileKind.Shortcut && it.isNotBlank() }

    /**
     * What to draw when the user gave no name.
     *
     * For a URL that is the host, not the whole address: "mail.google.com" is
     * a label, "https://mail.google.com/mail/u/0/#inbox" is a tooltip nobody
     * asked for.
     */
    fun displayLabel(resolved: String? = null): String = when {
        label.isNotBlank() -> label
        !resolved.isNullOrBlank() -> resolved
        kind == QuickTileKind.Url -> hostOf(target)
        else -> target.substringAfterLast('/', target).substringAfterLast('.', target)
    }

    private fun hostOf(url: String): String = url
        .substringAfter("://", url)
        .substringBefore('/')
        .removePrefix("www.")
        .ifBlank { url }
}

/**
 * The tiles a quick-launch block holds, as one string.
 *
 * Launcher X exists on iOS because iOS will not let anything but a widget sit
 * on the home screen. Android has no such problem — the desktop already places
 * apps — so what is worth taking is not the container but the tiles: an
 * address or one of an app's own shortcuts is something the desktop cannot
 * hold, and eight of them fit in the space of four icons.
 *
 * Same property as every other codec here: a damaged record costs that record
 * and nothing else. The separator is a unit separator because a label is typed
 * by a person and a URL contains almost every other punctuation mark there is.
 */
object QuickTileCodec {

    private const val FIELD = "\u001F"
    private const val RECORD = "\u001E"
    private const val FIELDS = 3

    fun encode(tiles: List<QuickTile>): String = tiles.joinToString(RECORD) { tile ->
        listOf(tile.kind.key, tile.target, tile.label)
            .joinToString(FIELD) { it.replace(FIELD, " ").replace(RECORD, " ") }
    }

    fun decode(raw: String?): List<QuickTile> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(RECORD).mapNotNull { record ->
            val parts = record.split(FIELD)
            if (parts.size != FIELDS) return@mapNotNull null
            val kind = QuickTileKind.fromKey(parts[0]) ?: return@mapNotNull null
            val target = parts[1].takeIf { it.isNotBlank() } ?: return@mapNotNull null
            QuickTile(kind = kind, target = target, label = parts[2])
        }
    }
}

/**
 * How many columns of tiles to draw inside a block of a given span.
 *
 * Pure, because the alternative is deciding it inside a composable where the
 * only way to find out it is wrong is to look at a phone. Two per cell across
 * and two down: a 2x2 block holds sixteen, which is the point of it — the same
 * area as four icons.
 */
object QuickLaunchGrid {

    const val TILES_PER_CELL = 2

    fun columns(spanX: Int): Int = (spanX.coerceAtLeast(1) * TILES_PER_CELL)

    fun rows(spanY: Int): Int = (spanY.coerceAtLeast(1) * TILES_PER_CELL)

    fun capacity(spanX: Int, spanY: Int): Int = columns(spanX) * rows(spanY)

    /** Tiles actually drawn, in order, for a block of this size. */
    fun visible(tiles: List<QuickTile>, spanX: Int, spanY: Int): List<QuickTile> =
        tiles.take(capacity(spanX, spanY))

    /** How many were left out, so the editor can say so rather than hide them. */
    fun hidden(tiles: List<QuickTile>, spanX: Int, spanY: Int): Int =
        (tiles.size - capacity(spanX, spanY)).coerceAtLeast(0)
}
