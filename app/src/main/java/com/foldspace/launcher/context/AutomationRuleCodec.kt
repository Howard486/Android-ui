package com.foldspace.launcher.context

import com.foldspace.launcher.spaces.SpaceId

/**
 * Encodes user-authored rules for storage.
 *
 * Hand-rolled rather than serialised with a library: these are five scalar
 * fields, the launcher already avoids dependencies it does not need, and a
 * text format that survives a bad field is what the decoder wants — a rule
 * that fails to parse should be dropped, never take the whole rule list with
 * it.
 */
object AutomationRuleCodec {

    /** Separators no user-supplied field can contain. */
    private const val FIELD = "\u001F"
    private const val LIST = ","

    fun encode(rule: AutomationRule): String = listOf(
        rule.id,
        rule.target.key,
        if (rule.enabled) "1" else "0",
        rule.matcher.timeBuckets.joinToString(LIST) { it.name },
        if (rule.matcher.weekdayOnly) "1" else "0",
        rule.matcher.charging?.let { if (it) "1" else "0" }.orEmpty(),
        rule.matcher.bluetoothClass?.name.orEmpty(),
        rule.matcher.calendarCategory.orEmpty(),
    ).joinToString(FIELD)

    /** Null when the line is unusable, so the caller can skip just that one. */
    fun decode(raw: String): AutomationRule? {
        val parts = raw.split(FIELD)
        if (parts.size < 8) return null

        val id = parts[0].takeIf { it.isNotBlank() } ?: return null
        val target = SpaceId.entries.firstOrNull { it.key == parts[1] } ?: return null

        val matcher = RuleMatcher(
            timeBuckets = parts[3]
                .split(LIST)
                .filter { it.isNotBlank() }
                .mapNotNullTo(mutableSetOf()) { name ->
                    TimeBucket.entries.firstOrNull { it.name == name }
                },
            weekdayOnly = parts[4] == "1",
            charging = when (parts[5]) {
                "1" -> true
                "0" -> false
                else -> null
            },
            bluetoothClass = parts[6]
                .takeIf { it.isNotBlank() }
                ?.let { name -> BluetoothClass.entries.firstOrNull { it.name == name } },
            calendarCategory = parts[7].takeIf { it.isNotBlank() },
        )

        // An inert matcher fires on nothing, so storing one would hand the
        // user a rule that silently never applies. Better to refuse it.
        if (!matcher.hasAnyCondition()) return null

        return AutomationRule(
            id = id,
            target = target,
            matcher = matcher,
            enabled = parts[2] == "1",
        )
    }

    fun encodeAll(rules: List<AutomationRule>): Set<String> =
        rules.mapTo(mutableSetOf(), ::encode)

    fun decodeAll(raw: Set<String>): List<AutomationRule> =
        raw.mapNotNull(::decode).sortedBy { it.id }
}
