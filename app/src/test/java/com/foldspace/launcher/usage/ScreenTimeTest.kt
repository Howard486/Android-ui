package com.foldspace.launcher.usage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTimeTest {

    private fun minutes(n: Long) = n * 60_000L

    @Test
    fun `a package appearing twice is summed, not sampled`() {
        // A day's stats hold one row per interval bucket, so the same app
        // shows up repeatedly. Taking the first would report a fraction.
        val summary = ScreenTime.summarise(
            listOf(
                AppUsage("com.a", minutes(10)),
                AppUsage("com.a", minutes(20)),
                AppUsage("com.b", minutes(5)),
            ),
            unlocks = 4,
        )
        assertEquals(minutes(35), summary.totalMillis)
        assertEquals(AppUsage("com.a", minutes(30)), summary.top.first())
    }

    @Test
    fun `the top list is capped and ordered by time`() {
        val summary = ScreenTime.summarise(
            (1..10).map { AppUsage("com.app$it", minutes(it.toLong())) },
            unlocks = 0,
        )
        assertEquals(3, summary.top.size)
        assertEquals("com.app10", summary.top[0].packageName)
        assertEquals("com.app9", summary.top[1].packageName)
        assertEquals("com.app8", summary.top[2].packageName)
    }

    @Test
    fun `ties break on package name so the card does not reshuffle itself`() {
        val first = ScreenTime.summarise(
            listOf(AppUsage("com.b", minutes(9)), AppUsage("com.a", minutes(9))),
            unlocks = 0,
        )
        val second = ScreenTime.summarise(
            listOf(AppUsage("com.a", minutes(9)), AppUsage("com.b", minutes(9))),
            unlocks = 0,
        )
        assertEquals(first.top, second.top)
        assertEquals("com.a", first.top.first().packageName)
    }

    @Test
    fun `zero and negative durations are dropped rather than counted`() {
        val summary = ScreenTime.summarise(
            listOf(AppUsage("com.a", 0), AppUsage("com.b", -5), AppUsage("com.c", minutes(3))),
            unlocks = 1,
        )
        assertEquals(minutes(3), summary.totalMillis)
        assertEquals(1, summary.top.size)
    }

    @Test
    fun `an empty day is empty rather than a row of zeroes`() {
        val summary = ScreenTime.summarise(emptyList(), unlocks = 0)
        assertTrue(summary.isEmpty)
        assertEquals(0L, summary.totalMillis)
    }

    @Test
    fun `a negative unlock count cannot be displayed`() {
        assertEquals(0, ScreenTime.summarise(emptyList(), unlocks = -3).unlocks)
    }

    @Test
    fun `durations never render minutes above fifty-nine`() {
        assertEquals("2 小時 14 分", ScreenTime.formatDuration(minutes(134)))
        assertEquals("1 小時", ScreenTime.formatDuration(minutes(60)))
        assertEquals("48 分", ScreenTime.formatDuration(minutes(48)))
        assertEquals("3 小時 1 分", ScreenTime.formatDuration(minutes(181)))
    }

    @Test
    fun `a short but real session never reads as zero`() {
        assertEquals("不到 1 分", ScreenTime.formatDuration(40_000L))
        assertEquals("不到 1 分", ScreenTime.formatDuration(1L))
        assertEquals("0 分", ScreenTime.formatDuration(0L))
        assertEquals("0 分", ScreenTime.formatDuration(-1L))
    }

    @Test
    fun `asking for no top apps is allowed and returns none`() {
        val summary = ScreenTime.summarise(listOf(AppUsage("com.a", minutes(5))), 0, topN = 0)
        assertTrue(summary.top.isEmpty())
        assertEquals(minutes(5), summary.totalMillis)
    }
}
