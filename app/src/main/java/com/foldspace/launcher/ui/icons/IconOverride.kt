package com.foldspace.launcher.ui.icons

/**
 * One app's overridden appearance.
 *
 * Both fields are optional and independent: renaming an app without changing
 * its picture is the common case, and a picture without a rename is the other.
 */
data class IconOverride(
    val appKey: String,
    val label: String? = null,
    val iconUri: String? = null,
) {
    /** An override with nothing in it is the same as not having one. */
    val isEmpty: Boolean get() = label.isNullOrBlank() && iconUri.isNullOrBlank()
}

/**
 * Text form for the overrides, with the same property as the other codecs in
 * this project: a damaged line costs only that line.
 *
 * The separator is a unit separator rather than a comma or a pipe, because an
 * app key contains '/', '#' and '.', and a user-typed name can contain
 * anything at all — including, if they are feeling adventurous, a pipe.
 */
object IconOverrideCodec {

    private const val SEPARATOR = "\u001F"

    fun encode(override: IconOverride): String = listOf(
        override.appKey,
        override.label.orEmpty(),
        override.iconUri.orEmpty(),
    ).joinToString(SEPARATOR) { it.replace(SEPARATOR, " ") }

    fun decode(raw: String): IconOverride? {
        val parts = raw.split(SEPARATOR)
        if (parts.size != FIELDS) return null
        val key = parts[0].takeIf { it.isNotBlank() } ?: return null
        val override = IconOverride(
            appKey = key,
            label = parts[1].takeIf { it.isNotBlank() },
            iconUri = parts[2].takeIf { it.isNotBlank() },
        )
        return override.takeUnless { it.isEmpty }
    }

    fun encodeAll(overrides: Collection<IconOverride>): Set<String> =
        overrides.filterNot { it.isEmpty }.map(::encode).toSet()

    fun decodeAll(raw: Set<String>): Map<String, IconOverride> =
        raw.mapNotNull(::decode).associateBy { it.appKey }

    private const val FIELDS = 3
}
