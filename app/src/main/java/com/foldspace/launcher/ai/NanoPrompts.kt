package com.foldspace.launcher.ai

import com.foldspace.launcher.context.ContextSnapshot
import com.foldspace.launcher.spaces.NotificationTier
import com.foldspace.launcher.spaces.SpaceId

/**
 * Prompt construction and, more importantly, response parsing.
 *
 * §8.4 says the UI must never bind to a model's natural language. The way to
 * guarantee that is not to ask nicely in the prompt — models ignore format
 * instructions — but to refuse anything that is not exactly the shape
 * expected. Every function here returns null rather than a best guess.
 *
 * Two rules the parser enforces that the prompt only requests:
 *  - a label must be one FoldSpace already knows; a model cannot name a new
 *    Space or a new tier into existence;
 *  - 簡易 is never accepted, whatever the model says. Simplified mode is a
 *    person's decision about their own interface (see SpaceId).
 */
object NanoPrompts {

    /** Only these may be nominated; 簡易 is deliberately absent. */
    private val SELECTABLE = SpaceId.entries.filterNot { it.isUserSelectableOnly }

    fun contextPrompt(snapshot: ContextSnapshot): String = buildString {
        appendLine("Classify the user's current situation into exactly one label.")
        appendLine("Answer with one line: LABEL|CONFIDENCE")
        appendLine("LABEL must be one of: " + SELECTABLE.joinToString(",") { it.key })
        appendLine("CONFIDENCE is a decimal between 0 and 1.")
        appendLine("No explanation. No other text.")
        appendLine()
        appendLine("Signals:")
        appendLine("time_bucket=" + snapshot.timeBucket.name)
        appendLine("weekday=" + snapshot.isWeekday)
        appendLine("charging=" + snapshot.charging)
        appendLine("bluetooth=" + snapshot.bluetoothClass.name)
        appendLine("fold=" + snapshot.layoutMode.name)
        snapshot.calendarCategory?.let { appendLine("calendar=$it") }
        val topApps = snapshot.usageScores.entries
            .sortedByDescending { it.value }
            .take(TOP_APPS)
            .joinToString(",") { it.key }
        if (topApps.isNotBlank()) appendLine("recent_apps=$topApps")
    }

    /** Null when the reply is anything other than `label|confidence`. */
    fun parseContext(raw: String?): NanoContextResult? {
        val line = raw?.lineSequence()?.map(String::trim)?.firstOrNull { it.isNotBlank() }
            ?: return null
        val parts = line.split("|")
        if (parts.size != 2) return null

        val space = SELECTABLE.firstOrNull { it.key.equals(parts[0].trim(), ignoreCase = true) }
            ?: return null
        val confidence = parts[1].trim().toFloatOrNull()?.takeIf { it in 0f..1f } ?: return null

        return NanoContextResult(
            space = space,
            confidence = confidence,
            // A fixed code, never the model's words: §8.4 maps reason codes to
            // strings in the UI, so a free-text reason could not be rendered
            // even if one were offered.
            reasonCode = "NANO_CLASSIFICATION",
        )
    }

    fun notificationPrompt(items: List<NanoNotificationInput>): String = buildString {
        appendLine("Triage notifications. One output line per input line.")
        appendLine("Each output line: INDEX|TIER|CONFIDENCE")
        appendLine("TIER must be one of: " + NotificationTier.entries.joinToString(",") { it.name })
        appendLine("CONFIDENCE is a decimal between 0 and 1.")
        appendLine("No explanation. No other text.")
        appendLine()
        items.forEachIndexed { index, item ->
            append(index).append('|').append(item.packageName).append('|')
            // The body is omitted where the user excluded the app from content
            // analysis (§10.4); the prompt simply has less to go on.
            append(item.title.orEmpty().singleLine()).append('|')
            append(item.body.orEmpty().singleLine()).append('|')
            append("actions=").append(item.hasActions).append('|')
            appendLine("ongoing=" + item.isOngoing)
        }
    }

    /**
     * Parses per-notification verdicts, keeping only lines that name a real
     * index and a real tier. A model that returns six lines for five inputs,
     * or invents an index, loses the bad lines and nothing else.
     */
    fun parseNotifications(
        raw: String?,
        items: List<NanoNotificationInput>,
    ): List<NanoNotificationResult> {
        if (raw == null) return emptyList()
        val results = mutableMapOf<String, NanoNotificationResult>()

        for (line in raw.lineSequence()) {
            val parts = line.trim().split("|")
            if (parts.size != 3) continue
            val index = parts[0].trim().toIntOrNull() ?: continue
            val item = items.getOrNull(index) ?: continue
            val tier = NotificationTier.entries
                .firstOrNull { it.name.equals(parts[1].trim(), ignoreCase = true) } ?: continue
            val confidence = parts[2].trim().toFloatOrNull()?.takeIf { it in 0f..1f } ?: continue

            // First verdict wins: a model repeating an index is contradicting
            // itself, and picking the later one would be arbitrary.
            results.putIfAbsent(
                item.key,
                NanoNotificationResult(key = item.key, tier = tier, confidence = confidence),
            )
        }
        return results.values.toList()
    }

    private fun String.singleLine(): String =
        replace('\n', ' ').replace('|', '/').take(MAX_FIELD)

    private const val TOP_APPS = 5
    private const val MAX_FIELD = 120
}
