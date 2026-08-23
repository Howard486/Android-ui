package com.foldspace.launcher.pairs

/**
 * Two apps the user wants opened side by side.
 *
 * Stored by component key rather than by anything the system hands out: a
 * pair has to survive a reboot and an app update, and there is no durable
 * system-side identifier for "these two, split".
 */
data class AppPair(
    val id: String,
    val firstKey: String,
    val secondKey: String,
    val label: String,
)

/** Same hand-rolled, skip-a-bad-line approach as the other two formats. */
object AppPairCodec {

    private const val FIELD = "\u001F"

    fun encode(pair: AppPair): String = listOf(
        pair.id,
        pair.firstKey,
        pair.secondKey,
        pair.label.filter { it.code >= 0x20 },
    ).joinToString(FIELD)

    fun decode(raw: String): AppPair? {
        val parts = raw.split(FIELD)
        if (parts.size < 4) return null
        return AppPair(
            id = parts[0].ifBlank { return null },
            firstKey = parts[1].ifBlank { return null },
            secondKey = parts[2].ifBlank { return null },
            label = parts[3],
        )
    }

    fun encodeAll(pairs: List<AppPair>): Set<String> = pairs.mapTo(mutableSetOf(), ::encode)

    fun decodeAll(raw: Set<String>): List<AppPair> = raw.mapNotNull(::decode).sortedBy { it.id }
}
