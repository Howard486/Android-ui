package com.foldspace.launcher.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickTileCodecTest {

    private val field = "\u001F"
    private val record = "\u001E"

    private val tiles = listOf(
        QuickTile(QuickTileKind.App, "com.foo/com.foo.Main", "Foo"),
        QuickTile(QuickTileKind.Shortcut, "com.mail/compose", ""),
        QuickTile(QuickTileKind.Url, "https://example.com/a?b=c#d", "\u5167\u7db2"),
    )

    @Test
    fun `a round trip keeps every tile`() {
        assertEquals(tiles, QuickTileCodec.decode(QuickTileCodec.encode(tiles)))
    }

    @Test
    fun `a url survives the punctuation it is made of`() {
        val url = "https://example.com/a?b=c&d=e#f,g(h)"
        val decoded = QuickTileCodec.decode(
            QuickTileCodec.encode(listOf(QuickTile(QuickTileKind.Url, url))),
        )
        assertEquals(url, decoded.single().target)
    }

    @Test
    fun `a separator typed into a label cannot forge a field or a record`() {
        val nasty = QuickTile(QuickTileKind.Url, "https://x.test", "a" + field + "b" + record + "c")
        val decoded = QuickTileCodec.decode(QuickTileCodec.encode(listOf(nasty)))
        assertEquals(1, decoded.size)
        assertEquals("a b c", decoded.single().label)
        assertEquals("https://x.test", decoded.single().target)
    }

    @Test
    fun `a damaged record costs only that record`() {
        val good = QuickTileCodec.encode(listOf(tiles[0]))
        val decoded = QuickTileCodec.decode(
            good + record + "garbage" + record + "a" + field + "b",
        )
        assertEquals(listOf(tiles[0]), decoded)
    }

    @Test
    fun `an unknown kind is dropped rather than guessed at`() {
        assertTrue(QuickTileCodec.decode("nonsense" + field + "target" + field + "label").isEmpty())
    }

    @Test
    fun `a tile with no target is refused`() {
        assertTrue(QuickTileCodec.decode("url" + field + field + "label").isEmpty())
    }

    @Test
    fun `nothing stored decodes to nothing`() {
        assertTrue(QuickTileCodec.decode(null).isEmpty())
        assertTrue(QuickTileCodec.decode("").isEmpty())
        assertEquals("", QuickTileCodec.encode(emptyList()))
    }

    @Test
    fun `a url with no label shows its host, not the whole address`() {
        val tile = QuickTile(QuickTileKind.Url, "https://www.mail.google.com/mail/u/0/#inbox")
        assertEquals("mail.google.com", tile.displayLabel())
    }

    @Test
    fun `a typed label always wins`() {
        val tile = QuickTile(QuickTileKind.Url, "https://example.com", "\u5167\u7db2")
        assertEquals("\u5167\u7db2", tile.displayLabel(resolved = "Example"))
    }

    @Test
    fun `a resolved name is used when there is no typed one`() {
        val tile = QuickTile(QuickTileKind.App, "com.foo/com.foo.Main")
        assertEquals("Foo App", tile.displayLabel(resolved = "Foo App"))
    }

    @Test
    fun `component and shortcut id are read from the right kinds only`() {
        assertEquals("com.foo/com.foo.Main", tiles[0].component)
        assertNull(tiles[1].component)
        assertEquals("compose", tiles[1].shortcutId)
        assertNull(tiles[0].shortcutId)
        assertEquals("com.mail", tiles[1].packageName)
    }
}

class QuickLaunchGridTest {

    private fun tiles(n: Int) = List(n) { QuickTile(QuickTileKind.Url, "https://x$it.test") }

    @Test
    fun `a one-by-one block holds four tiles`() {
        assertEquals(4, QuickLaunchGrid.capacity(1, 1))
    }

    @Test
    fun `a two-by-two block holds sixteen, which is the point of it`() {
        // Four icons' worth of area, sixteen actions.
        assertEquals(16, QuickLaunchGrid.capacity(2, 2))
    }

    @Test
    fun `overflow is reported rather than silently dropped`() {
        assertEquals(0, QuickLaunchGrid.hidden(tiles(4), 1, 1))
        assertEquals(3, QuickLaunchGrid.hidden(tiles(7), 1, 1))
        assertEquals(4, QuickLaunchGrid.visible(tiles(7), 1, 1).size)
    }

    @Test
    fun `a nonsense span is treated as one cell rather than crashing`() {
        assertEquals(4, QuickLaunchGrid.capacity(0, 0))
        assertEquals(4, QuickLaunchGrid.capacity(-3, -3))
    }

    @Test
    fun `visible keeps the order the user arranged`() {
        val list = tiles(6)
        assertEquals(list.take(4), QuickLaunchGrid.visible(list, 1, 1))
    }
}
