package com.foldspace.launcher.powerdock

import com.foldspace.launcher.context.BluetoothClass
import com.foldspace.launcher.context.ContextSnapshot
import com.foldspace.launcher.context.TimeBucket
import com.foldspace.launcher.ui.layout.LayoutMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** §11.2 — the Power Dock trigger table, and §21.1's plug/unplug gate. */
class PowerDockResolverTest {

    private fun snapshot(
        charging: Boolean,
        timeBucket: TimeBucket = TimeBucket.Afternoon,
        layoutMode: LayoutMode = LayoutMode.Expanded,
        powerSave: Boolean = false,
    ) = ContextSnapshot(
        timestamp = 0L,
        layoutMode = layoutMode,
        timeBucket = timeBucket,
        isWeekday = true,
        charging = charging,
        batteryPercent = 64,
        bluetoothClass = BluetoothClass.None,
        calendarCategory = null,
        usageScores = emptyMap(),
        powerSave = powerSave,
        launcherForeground = true,
    )

    @Test
    fun `not charging means no dock`() {
        val state = PowerDockResolver.resolve(snapshot(charging = false), 600_000L)
        assertFalse(state.active)
    }

    @Test
    fun `night charging is Bedside regardless of posture`() {
        val unfolded = PowerDockResolver.resolve(
            snapshot(charging = true, timeBucket = TimeBucket.Night, layoutMode = LayoutMode.Expanded),
            null,
        )
        val folded = PowerDockResolver.resolve(
            snapshot(charging = true, timeBucket = TimeBucket.Night, layoutMode = LayoutMode.Compact),
            null,
        )
        assertEquals(PowerDockMode.Bedside, unfolded.mode)
        assertEquals(PowerDockMode.Bedside, folded.mode)
    }

    @Test
    fun `folded charging by day is the compact readout`() {
        val state = PowerDockResolver.resolve(
            snapshot(charging = true, layoutMode = LayoutMode.Compact),
            null,
        )
        assertTrue(state.active)
        assertEquals(PowerDockMode.CompactCharge, state.mode)
    }

    @Test
    fun `tabletop charging is Cyber`() {
        val state = PowerDockResolver.resolve(
            snapshot(charging = true, layoutMode = LayoutMode.Tabletop),
            null,
        )
        assertEquals(PowerDockMode.Cyber, state.mode)
    }

    @Test
    fun `battery saver suppresses the charge estimate`() {
        val state = PowerDockResolver.resolve(
            snapshot(charging = true, powerSave = true),
            600_000L,
        )
        assertNull(state.chargeEtaMillis)
        assertNull(state.etaText)
    }

    @Test
    fun `a missing estimate renders as no text rather than a guess`() {
        val state = PowerDockResolver.resolve(snapshot(charging = true), null)
        assertNull(state.etaText)
    }

    @Test
    fun `an estimate under an hour is shown in minutes`() {
        val state = PowerDockResolver.resolve(snapshot(charging = true), 25 * 60_000L)
        assertEquals("約 25 分鐘充滿", state.etaText)
    }
}
