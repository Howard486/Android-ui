package com.foldspace.launcher.context

import com.foldspace.launcher.spaces.SpaceId
import com.foldspace.launcher.ui.layout.LayoutMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
                    id = "focus-night",
                    target = SpaceId.Focus,
                    matcher = RuleMatcher(timeBuckets = setOf(TimeBucket.Night)),
                ),
            ),
        )

        // Charging at night would otherwise resolve to Night.
        val decision = engine.evaluate(
            snapshot(timeBucket = TimeBucket.Night, charging = true),
            SwitchMode.SuggestFirst,
        )

        assertEquals(SpaceId.Focus, decision.space)
        assertEquals(DecisionSource.ExplicitRule, decision.source)
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
    fun `charging at night resolves to Night without any rule`() {
        val decision = RuleEngine().evaluate(
            snapshot(timeBucket = TimeBucket.Night, charging = true),
            SwitchMode.SuggestFirst,
        )

        assertEquals(SpaceId.Night, decision.space)
        assertEquals(DecisionSource.DeterministicEvent, decision.source)
        // Deterministic or not, it is still only a suggestion.
        assertFalse(decision.apply)
    }

    @Test
    fun `a car connection resolves to Travel`() {
        val decision = RuleEngine().evaluate(
            snapshot(bluetooth = BluetoothClass.Car),
            SwitchMode.SuggestFirst,
        )
        assertEquals(SpaceId.Travel, decision.space)
    }

    @Test
    fun `a weak context score produces no suggestion at all`() {
        // Weekend afternoon with nothing else: below the suggest threshold.
        val decision = RuleEngine().evaluate(
            snapshot(timeBucket = TimeBucket.Afternoon, isWeekday = false),
            SwitchMode.SuggestFirst,
        )
        assertEquals(DecisionSource.None, decision.source)
        assertEquals(null, decision.space)
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
