package com.foldspace.launcher

import com.foldspace.launcher.context.AutomationRule
import com.foldspace.launcher.context.AutomationRuleCodec
import com.foldspace.launcher.context.BluetoothClass
import com.foldspace.launcher.context.RuleMatcher
import com.foldspace.launcher.context.TimeBucket
import com.foldspace.launcher.home.BackupItem
import com.foldspace.launcher.home.BackupPage
import com.foldspace.launcher.home.LayoutBackup
import com.foldspace.launcher.home.LayoutBackupCodec
import com.foldspace.launcher.spaces.SpaceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two hand-rolled text formats.
 *
 * Both make the same claim: a damaged line costs only that line. These tests
 * are what make that claim true rather than aspirational.
 */
class CodecTest {

    // ---- automation rules ----

    private val rule = AutomationRule(
        id = "r1",
        target = SpaceId.Work,
        matcher = RuleMatcher(
            timeBuckets = setOf(TimeBucket.Morning, TimeBucket.Afternoon),
            weekdayOnly = true,
            charging = false,
            bluetoothClass = BluetoothClass.Car,
            calendarCategory = "meeting",
        ),
    )

    @Test
    fun `a rule survives a round trip`() {
        assertEquals(rule, AutomationRuleCodec.decode(AutomationRuleCodec.encode(rule)))
    }

    @Test
    fun `an unset tri-state stays unset rather than becoming false`() {
        val open = rule.copy(matcher = rule.matcher.copy(charging = null))
        val decoded = AutomationRuleCodec.decode(AutomationRuleCodec.encode(open))
        assertNull(decoded!!.matcher.charging)
    }

    @Test
    fun `a rule with no real condition is refused`() {
        // weekdayOnly alone would fire all day, every weekday.
        val inert = rule.copy(matcher = RuleMatcher(weekdayOnly = true))
        assertNull(AutomationRuleCodec.decode(AutomationRuleCodec.encode(inert)))
    }

    @Test
    fun `an unknown Space is refused rather than defaulted`() {
        val encoded = AutomationRuleCodec.encode(rule).replace("work", "atlantis")
        assertNull(AutomationRuleCodec.decode(encoded))
    }

    @Test
    fun `one bad rule does not take the others with it`() {
        val good = AutomationRuleCodec.encode(rule)
        val other = AutomationRuleCodec.encode(rule.copy(id = "r2", target = SpaceId.General))
        val decoded = AutomationRuleCodec.decodeAll(setOf(good, other, "garbage", ""))
        assertEquals(listOf("r1", "r2"), decoded.map { it.id })
    }

    // ---- layout backup ----

    private val backup = LayoutBackup(
        items = listOf(
            BackupItem("desktop", "folded", 0, 1, 2, 1, 1, "app", "com.a", "com.a.Main"),
            BackupItem("desktop", "folded", 0, 3, 0, 2, 2, "folder", folderTitle = "Media"),
            BackupItem(
                "desktop", "folded", -99, 0, 0, 1, 1, "app", "com.b", "com.b.Main",
                inFolder = "Media",
            ),
        ),
        pages = listOf(BackupPage("desktop", "folded", 2, "widgets", "work")),
    )

    @Test
    fun `a backup survives a round trip`() {
        val decoded = LayoutBackupCodec.decode(LayoutBackupCodec.encode(backup))!!
        assertEquals(backup.items, decoded.items)
        assertEquals(backup.pages, decoded.pages)
        assertEquals(LayoutBackup.FORMAT_VERSION, decoded.version)
    }

    @Test
    fun `a file that is not ours is rejected outright`() {
        assertNull(LayoutBackupCodec.decode("{}"))
        assertNull(LayoutBackupCodec.decode(""))
    }

    @Test
    fun `a damaged line costs only that line`() {
        val encoded = LayoutBackupCodec.encode(backup)
        val damaged = encoded.lines().toMutableList().apply { add(2, "I\u001Fnonsense") }
        val decoded = LayoutBackupCodec.decode(damaged.joinToString("\n"))!!
        assertEquals(backup.items.size, decoded.items.size)
    }

    @Test
    fun `a folder title carrying the separator cannot corrupt the line`() {
        val nasty = LayoutBackup(
            items = listOf(
                BackupItem(
                    "desktop", "folded", 0, 0, 0, 1, 1, "folder",
                    folderTitle = "Med\u001Fia",
                ),
            ),
            pages = emptyList(),
        )
        val decoded = LayoutBackupCodec.decode(LayoutBackupCodec.encode(nasty))!!
        assertEquals(1, decoded.items.size)
        assertEquals("Media", decoded.items[0].folderTitle)
    }

    @Test
    fun `widgets are absent from a backup by construction`() {
        // Nothing in the format carries an appWidgetId. Pinned down here so a
        // later change to add one has to be deliberate.
        assertTrue("appWidgetId" !in LayoutBackupCodec.encode(backup))
    }
}
