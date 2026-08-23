package com.foldspace.launcher.context

import com.foldspace.launcher.spaces.SpaceId

/**
 * §7.1 decision ladder. Lower ordinal wins; the engine stops at the first
 * source that produces an answer, so Nano is only ever reached when
 * everything deterministic has declined to decide.
 */
enum class DecisionSource {
    UserOverride,
    ExplicitRule,
    DeterministicEvent,
    ContextScore,
    NanoClassification,
    None,
}

/**
 * What the engine decided. [ContextDecision.apply] distinguishes "switch now"
 * from "offer a switch" — §7.2 defaults to suggest-first, and AI results are
 * never allowed to switch on their own.
 */
data class ContextDecision(
    val space: SpaceId?,
    val source: DecisionSource,
    val confidence: Float,
    val reasonCode: String,
    val apply: Boolean,
) {
    companion object {
        val NoAction = ContextDecision(
            space = null,
            source = DecisionSource.None,
            confidence = 0f,
            reasonCode = "NO_SIGNAL",
            apply = false,
        )
    }
}

/** §7.2 — how much authority the engine has. */
enum class SwitchMode { ManualOnly, SuggestFirst, Automatic }

/**
 * A user-authored automation rule (§7.1 level 2). These are the only rules
 * allowed to switch a Space outright, because the user wrote them.
 */
data class AutomationRule(
    val id: String,
    val target: SpaceId,
    val matcher: RuleMatcher,
    val enabled: Boolean = true,
)

data class RuleMatcher(
    val timeBuckets: Set<TimeBucket> = emptySet(),
    val weekdayOnly: Boolean = false,
    val charging: Boolean? = null,
    val bluetoothClass: BluetoothClass? = null,
    val calendarCategory: String? = null,
) {
    fun matches(snapshot: ContextSnapshot): Boolean {
        if (timeBuckets.isNotEmpty() && snapshot.timeBucket !in timeBuckets) return false
        if (weekdayOnly && !snapshot.isWeekday) return false
        charging?.let { if (snapshot.charging != it) return false }
        bluetoothClass?.let { if (snapshot.bluetoothClass != it) return false }
        calendarCategory?.let { if (!snapshot.calendarCategory.equals(it, ignoreCase = true)) return false }
        // An all-empty matcher would fire on everything; treat it as inert.
        return hasAnyCondition()
    }

    /**
     * Whether this matcher constrains anything at all.
     *
     * `weekdayOnly` alone does not count: on its own it would fire every
     * weekday, all day, which is not a rule anyone means to write.
     */
    fun hasAnyCondition(): Boolean = timeBuckets.isNotEmpty() || charging != null ||
        bluetoothClass != null || calendarCategory != null

    /** The rule's condition in the user's words, for the editor. */
    fun describe(): String {
        val parts = mutableListOf<String>()
        if (timeBuckets.isNotEmpty()) {
            parts += timeBuckets.sortedBy { it.ordinal }.joinToString("、") { it.label() }
        }
        if (weekdayOnly) parts += "平日"
        charging?.let { parts += if (it) "充電中" else "未充電" }
        bluetoothClass?.let { parts += it.label() }
        calendarCategory?.let { parts += "行事曆：" + it }
        return if (parts.isEmpty()) "（沒有條件）" else parts.joinToString(" · ")
    }
}

/** Display names for the rule editor. */
fun TimeBucket.label(): String = when (this) {
    TimeBucket.EarlyMorning -> "清晨"
    TimeBucket.Morning -> "上午"
    TimeBucket.Afternoon -> "下午"
    TimeBucket.Evening -> "傍晚"
    TimeBucket.Night -> "夜間"
}

fun BluetoothClass.label(): String = when (this) {
    BluetoothClass.None -> "未連線藍牙"
    BluetoothClass.Audio -> "連上耳機"
    BluetoothClass.Car -> "連上車用裝置"
    BluetoothClass.Wearable -> "連上穿戴裝置"
    BluetoothClass.Other -> "連上其他藍牙裝置"
}

/**
 * §7 Rule Engine. Deterministic, cheap, and always consulted before the AI —
 * "規則能解決，不跑 AI" (§1.2 rule 2).
 *
 * [rules] is a var because the user edits them at runtime. It used to be
 * constructor-only, and the app built the engine with an empty list — so
 * §7.1 level 2, the one level allowed to switch a Space outright, could never
 * fire at all.
 */
class RuleEngine(@Volatile var rules: List<AutomationRule> = emptyList()) {

    fun evaluate(snapshot: ContextSnapshot, switchMode: SwitchMode): ContextDecision {
        // Level 2 — explicit user automation. Allowed to apply directly, but
        // still only when the user has opted into Automatic. A rule the user
        // wrote themselves may target 簡易; nothing below this level may.
        rules.firstOrNull { it.enabled && it.matcher.matches(snapshot) }?.let { rule ->
            return ContextDecision(
                space = rule.target,
                source = DecisionSource.ExplicitRule,
                confidence = 1f,
                reasonCode = "USER_RULE_${rule.id}",
                apply = switchMode == SwitchMode.Automatic,
            )
        }

        // Level 3 — deterministic system events that need no scoring at all.
        deterministic(snapshot)?.let { return it }

        // Level 4 — context score. Suggest only; never applied on its own.
        return score(snapshot)
    }

    private fun deterministic(snapshot: ContextSnapshot): ContextDecision? {
        // Connecting to a car is the one unambiguous "you have stopped working"
        // signal in the table, and it needs no model to read.
        //
        // There is no night-charging rule here any more: with 夜間 gone as a
        // Space, that case is entirely the Power Dock's (§11.2 Bedside), and
        // duplicating it as a Space suggestion would prompt the user for a
        // switch that changes nothing they can see.
        if (snapshot.bluetoothClass == BluetoothClass.Car) {
            return ContextDecision(
                space = SpaceId.General,
                source = DecisionSource.DeterministicEvent,
                confidence = 0.9f,
                reasonCode = "BT_CAR_CONNECTED",
                apply = false,
            )
        }
        return null
    }

    private fun score(snapshot: ContextSnapshot): ContextDecision {
        val scores = mutableMapOf<SpaceId, Float>()

        if (snapshot.isWeekday &&
            snapshot.timeBucket in setOf(TimeBucket.Morning, TimeBucket.Afternoon)
        ) {
            scores.merge(SpaceId.Work, 0.45f, Float::plus)
        }
        if (snapshot.calendarCategory != null) {
            scores.merge(SpaceId.Work, 0.3f, Float::plus)
        }
        // Everything that says "not working" lands on 通用. Individually these
        // are weak; together — evening plus headphones, say — they clear the
        // threshold, which is the behaviour we want.
        if (snapshot.timeBucket == TimeBucket.Evening) {
            scores.merge(SpaceId.General, 0.35f, Float::plus)
        }
        if (snapshot.timeBucket == TimeBucket.Night) {
            scores.merge(SpaceId.General, 0.4f, Float::plus)
        }
        if (!snapshot.isWeekday) {
            scores.merge(SpaceId.General, 0.3f, Float::plus)
        }
        if (snapshot.bluetoothClass == BluetoothClass.Audio) {
            scores.merge(SpaceId.General, 0.3f, Float::plus)
        }

        // §7.1 — 簡易 is never scored into; see SpaceId.isUserSelectableOnly.
        val best = scores.entries
            .filterNot { it.key.isUserSelectableOnly }
            .maxByOrNull { it.value }
            ?: return ContextDecision.NoAction

        if (best.value < SUGGEST_THRESHOLD) return ContextDecision.NoAction

        return ContextDecision(
            space = best.key,
            source = DecisionSource.ContextScore,
            confidence = best.value.coerceAtMost(1f),
            reasonCode = "CONTEXT_SCORE",
            apply = false,
        )
    }

    private companion object {
        /** Below this the engine says nothing rather than nagging. */
        const val SUGGEST_THRESHOLD = 0.6f
    }
}
