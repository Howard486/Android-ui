package com.foldspace.launcher.context

import com.foldspace.launcher.ui.layout.LayoutMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * §21.2 — "同 Context Fingerprint 不重跑 Nano" is only enforceable if the
 * fingerprint is genuinely stable against noise. These tests pin that down.
 */
class ContextFingerprintTest {

    private val base = ContextSnapshot(
        timestamp = 1_000L,
        layoutMode = LayoutMode.Compact,
        timeBucket = TimeBucket.Morning,
        isWeekday = true,
        charging = false,
        batteryPercent = 80,
        bluetoothClass = BluetoothClass.None,
        calendarCategory = null,
        usageScores = mapOf("a" to 0.9f, "b" to 0.5f),
        powerSave = false,
        launcherForeground = true,
    )

    @Test
    fun `timestamp does not affect the fingerprint`() {
        assertEquals(
            base.fingerprint(),
            base.copy(timestamp = 9_999_999L).fingerprint(),
        )
    }

    @Test
    fun `a battery percent tick within the same bucket does not affect it`() {
        // 80 and 79 are both in the 60-79/80-99 boundary region; pick two that
        // share a bucket to prove ticking alone does not invalidate the cache.
        assertEquals(
            base.copy(batteryPercent = 85).fingerprint(),
            base.copy(batteryPercent = 81).fingerprint(),
        )
    }

    @Test
    fun `crossing a battery bucket does affect it`() {
        assertNotEquals(
            base.copy(batteryPercent = 85).fingerprint(),
            base.copy(batteryPercent = 55).fingerprint(),
        )
    }

    @Test
    fun `usage score drift without a ranking change does not affect it`() {
        assertEquals(
            base.fingerprint(),
            base.copy(usageScores = mapOf("a" to 0.7f, "b" to 0.6f)).fingerprint(),
        )
    }

    @Test
    fun `a change in the top apps does affect it`() {
        assertNotEquals(
            base.fingerprint(),
            base.copy(usageScores = mapOf("z" to 0.9f, "b" to 0.5f)).fingerprint(),
        )
    }

    @Test
    fun `foreground state alone does not affect it`() {
        // Nano eligibility is checked separately; folding it into the
        // fingerprint would re-run everything on every resume.
        assertEquals(
            base.fingerprint(),
            base.copy(launcherForeground = false).fingerprint(),
        )
    }

    @Test
    fun `fold posture affects it`() {
        assertNotEquals(
            base.fingerprint(),
            base.copy(layoutMode = LayoutMode.Expanded).fingerprint(),
        )
    }
}
