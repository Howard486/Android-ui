package com.foldspace.launcher.context

import com.foldspace.launcher.spaces.SpaceId
import com.foldspace.launcher.ui.layout.LayoutMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** §7.1 — the decision ladder, and §1.2 rule 4: AI never decides on its own. */
class RuleEngineTest {

    private fun snapshot(
        timeBucket: TimeBucket = TimeBucket.Morning,
        charging: Boolean = false,
        bluetooth: BluetoothClass = BluetoothClass.None,
        isWeekday: Boolean = true,
        calendarCategory: String? = null,
    ) = ContextSnapshot(
        timestamp = 0L,
        layoutMode = LayoutMode.Compact,
        timeBucket = timeBucket,
        isWeekday = isWeekday,
        charging = charging,
        batteryPercent = 50,
        bluetoothClass = bluetooth,
        calendarCategory = calendarCategory,
        usageScores = emptyMap(),
        powerSave = false,
        launcherForeground = true,
    )

    @Test
    fun `an explicit user rule outranks a deterministic event`() {
        val engine = RuleEngine(
            listOf(
                AutomationRule(
                    id = "car-work",
                    target = SpaceId.Work,
                    matcher = RuleMatcher(bluetoothClass = BluetoothClass.Car),
                ),
            ),
        )

        // A car connection would otherwise resolve deterministically to 通用.
        val decision = engine.evaluate(
            snapshot(bluetooth = BluetoothClass.Car),
            SwitchMode.SuggestFirst,
        )

        assertEquals(SpaceId.Work, decision.space)
        assertEquals(DecisionSource.ExplicitRule, decision.source)
    }

    @Test
    fun `only a user-authored rule may target the simplified Space`() {
        // §7.1 — 簡易 is a personal choice, so nothing below the explicit-rule
        // level is allowed to nominate it. A rule the user wrote may.
        val engine = RuleEngine(
            listOf(
                AutomationRule(
                    id = "evening-simple",
                    target = SpaceId.Simple,
                    matcher = RuleMatcher(timeBuckets = setOf(TimeBucket.Evening)),
                ),
            ),
        )

        val fromRule = engine.evaluate(
            snapshot(timeBucket = TimeBucket.Evening),
            SwitchMode.SuggestFirst,
        )
        assertEquals(SpaceId.Simple, fromRule.space)

        // With no such rule, no code path can reach it.
        val scored = RuleEngine().evaluate(
            snapshot(timeBucket = TimeBucket.Evening, isWeekday = false),
            SwitchMode.SuggestFirst,
        )
        assertNotEquals(SpaceId.Simple, scored.space)
    }

    @Test
    fun `a user rule only applies itself in Automatic mode`() {
        val engine = RuleEngine(
            listOf(
                AutomationRule(
                    id = "work-hours",
                    target = SpaceId.Work,
                    matcher = RuleMatcher(timeBuckets = setOf(TimeBucket.Morning), weekdayOnly = true),
                ),
            ),
        )

        assertFalse(engine.evaluate(snapshot(), SwitchMode.SuggestFirst).apply)
        assertTrue(engine.evaluate(snapshot(), SwitchMode.Automatic).apply)
    }

    @Test
    fun `a car connection resolves deterministically to General`() {
        val decision = RuleEngine().evaluate(
            snapshot(bluetooth = BluetoothClass.Car),
            SwitchMode.SuggestFirst,
        )

        assertEquals(SpaceId.General, decision.space)
        assertEquals(DecisionSource.DeterministicEvent, decision.source)
        // Deterministic or not, it is still only a suggestion.
        assertFalse(decision.apply)
    }

    @Test
    fun `charging at night no longer suggests a Space`() {
        // That case belongs entirely to the Power Dock's Bedside mode now
        // (§11.2); prompting for a switch that changes nothing would be noise.
        val decision = RuleEngine().evaluate(
            snapshot(timeBucket = TimeBucket.Night, charging = true, isWeekday = true),
            SwitchMode.SuggestFirst,
        )
        assertEquals(DecisionSource.None, decision.source)
    }

    @Test
    fun `a weak context score produces no suggestion at all`() {
        // Weekday afternoon is Work-leaning but on its own sits below the
        // suggest threshold once the calendar says nothing.
        val decision = RuleEngine().evaluate(
            snapshot(timeBucket = TimeBucket.Afternoon, isWeekday = true),
            SwitchMode.SuggestFirst,
        )
        assertEquals(DecisionSource.None, decision.source)
        assertEquals(null, decision.space)
    }

    @Test
    fun `weekend plus headphones adds up to General`() {
        // Neither signal clears the threshold alone; together they do.
        val decision = RuleEngine().evaluate(
            snapshot(
                timeBucket = TimeBucket.Afternoon,
                isWeekday = false,
                bluetooth = BluetoothClass.Audio,
            ),
            SwitchMode.SuggestFirst,
        )
        assertEquals(SpaceId.General, decision.space)
        assertEquals(DecisionSource.ContextScore, decision.source)
    }

    @Test
    fun `weekday work hours plus a calendar event clears the threshold`() {
        val decision = RuleEngine().evaluate(
            snapshot(timeBucket = TimeBucket.Morning, calendarCategory = "meeting"),
            SwitchMode.SuggestFirst,
        )
        assertEquals(SpaceId.Work, decision.space)
        assertEquals(DecisionSource.ContextScore, decision.source)
        assertFalse(decision.apply)
    }

    @Test
    fun `an empty matcher never fires`() {
        assertFalse(RuleMatcher().matches(snapshot()))
    }
}
