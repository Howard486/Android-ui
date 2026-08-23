package com.foldspace.launcher.quick

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickActionsTest {

    private fun kind(action: QuickAction, sdk: Int = 34, torch: Boolean = true, write: Boolean = false) =
        QuickActions.kindOf(action, sdk, torch, write)

    @Test
    fun `volume is the one control that always simply works`() {
        assertEquals(QuickKind.Direct, kind(QuickAction.MediaVolume))
        assertEquals(QuickKind.Direct, kind(QuickAction.RingVolume))
        assertEquals(QuickKind.Direct, kind(QuickAction.MediaVolume, sdk = 30, torch = false))
    }

    @Test
    fun `wifi is never something this app switches`() {
        // setWifiEnabled has returned false and done nothing since API 29.
        // Drawing it as a switch would be a lie at every API level.
        for (sdk in 30..36) {
            assertEquals(QuickKind.SystemPanel, kind(QuickAction.Wifi, sdk = sdk))
        }
    }

    @Test
    fun `the internet panel needs API 29 and says so below it`() {
        assertEquals(QuickKind.SystemPanel, kind(QuickAction.Internet, sdk = 29))
        assertEquals(QuickKind.SystemPanel, kind(QuickAction.Internet, sdk = 35))
        assertEquals(QuickKind.Unavailable, kind(QuickAction.Internet, sdk = 28))
    }

    @Test
    fun `bluetooth has no panel, only a settings screen`() {
        assertEquals(QuickKind.FullSettings, kind(QuickAction.Bluetooth))
    }

    @Test
    fun `brightness and rotation become direct only once write settings is granted`() {
        assertEquals(QuickKind.FullSettings, kind(QuickAction.Brightness, write = false))
        assertEquals(QuickKind.Direct, kind(QuickAction.Brightness, write = true))
        assertEquals(QuickKind.FullSettings, kind(QuickAction.Rotation, write = false))
        assertEquals(QuickKind.Direct, kind(QuickAction.Rotation, write = true))
    }

    @Test
    fun `a device with no flash does not get a torch button`() {
        assertEquals(QuickKind.Unavailable, kind(QuickAction.Torch, torch = false))
        assertEquals(QuickKind.Direct, kind(QuickAction.Torch, torch = true))
        assertFalse(QuickAction.Torch in QuickActions.visible(34, hasTorch = false, canWriteSettings = false))
        assertTrue(QuickAction.Torch in QuickActions.visible(34, hasTorch = true, canWriteSettings = false))
    }

    @Test
    fun `nothing unavailable is ever offered`() {
        for (sdk in listOf(28, 29, 30, 34, 36)) {
            for (torch in listOf(true, false)) {
                for (write in listOf(true, false)) {
                    val shown = QuickActions.visible(sdk, torch, write)
                    assertTrue(
                        "sdk=$sdk torch=$torch write=$write",
                        shown.none { QuickActions.kindOf(it, sdk, torch, write) == QuickKind.Unavailable },
                    )
                }
            }
        }
    }

    @Test
    fun `every kind has an explanation, including the one nothing uses`() {
        QuickKind.entries.forEach { assertTrue(QuickActions.explain(it).isNotBlank()) }
    }

    @Test
    fun `at minSdk the panel is still worth drawing`() {
        // minSdk is 30. If everything were unavailable there, the whole panel
        // would be an empty box on the oldest device this app supports.
        val shown = QuickActions.visible(30, hasTorch = true, canWriteSettings = false)
        assertTrue(shown.size >= 5)
    }
}
