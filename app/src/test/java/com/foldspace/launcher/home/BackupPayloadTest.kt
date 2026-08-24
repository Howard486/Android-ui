package com.foldspace.launcher.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The quick-launch payload uses the same control characters this backup format
 * uses to separate its own fields, so it can only travel encoded. These tests
 * exist because writing it raw would not fail loudly — it would silently split
 * one item into several malformed lines.
 */
class BackupPayloadTest {

    private val field = "\u001F"

    private fun blockWith(tiles: List<QuickTile>) = BackupItem(
        surface = "desktop",
        posture = "folded",
        pageIndex = 0,
        cellX = 1,
        cellY = 2,
        spanX = 2,
        spanY = 2,
        type = HomeItemType.QuickLaunch.key,
        payload = QuickTileCodec.encode(tiles),
    )

    private val tiles = listOf(
        QuickTile(QuickTileKind.App, "com.foo/com.foo.Main#0", "Foo"),
        QuickTile(QuickTileKind.Shortcut, "com.foo/new_message", "\u65b0\u8a0a\u606f"),
        QuickTile(QuickTileKind.Url, "https://example.com/a?b=c", ""),
    )

    @Test
    fun `a block survives encode and decode with every tile intact`() {
        val backup = LayoutBackup(items = listOf(blockWith(tiles)), pages = emptyList())
        val restored = LayoutBackupCodec.decode(LayoutBackupCodec.encode(backup))

        assertEquals(1, restored?.items?.size)
        val payload = restored?.items?.first()?.payload
        assertEquals(tiles, QuickTileCodec.decode(payload))
    }

    @Test
    fun `the payload never puts a separator into the line`() {
        val encoded = LayoutBackupCodec.encode(
            LayoutBackup(items = listOf(blockWith(tiles)), pages = emptyList()),
        )
        val itemLines = encoded.lineSequence().filter { it.startsWith("I") }.toList()
        assertEquals(1, itemLines.size)
        // The tag, twelve fields, and the payload.
        assertEquals(14, itemLines.first().split(field).size)
    }

    @Test
    fun `an item written before blocks existed still restores`() {
        val old = BackupItem(
            surface = "desktop",
            posture = "folded",
            pageIndex = 0,
            cellX = 0,
            cellY = 0,
            spanX = 1,
            spanY = 1,
            type = HomeItemType.App.key,
            packageName = "com.foo",
            className = "com.foo.Main",
        )
        // Re-encoded without the trailing field, as an older writer would.
        val line = LayoutBackupCodec.encode(
            LayoutBackup(items = listOf(old), pages = emptyList()),
        ).lineSequence().first { it.startsWith("I") }
        val truncated = line.split(field).dropLast(1).joinToString(field)

        val restored = LayoutBackupCodec.decode(
            "foldspace-layout" + field + "2\n" + truncated + "\n",
        )
        assertEquals(1, restored?.items?.size)
        assertNull(restored?.items?.first()?.payload)
        assertEquals("com.foo", restored?.items?.first()?.packageName)
    }

    @Test
    fun `a corrupt payload costs one block, not the file`() {
        val line = listOf(
            "I", "desktop", "folded", "0", "0", "0", "2", "2",
            HomeItemType.QuickLaunch.key, "", "", "", "", "!!!not base64!!!",
        ).joinToString(field)
        val restored = LayoutBackupCodec.decode(
            "foldspace-layout" + field + "2\n" + line + "\n",
        )
        assertEquals(1, restored?.items?.size)
        assertNull(restored?.items?.first()?.payload)
        assertTrue(QuickTileCodec.decode(restored?.items?.first()?.payload).isEmpty())
    }
}
