package com.foldspace.launcher.ui.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IconOverrideCodecTest {

    private val sep = "\u001F"

    @Test
    fun `a round trip keeps both fields`() {
        val override = IconOverride("com.a/Main#0", "訊息", "content://media/1")
        assertEquals(override, IconOverrideCodec.decode(IconOverrideCodec.encode(override)))
    }

    @Test
    fun `a rename with no picture round trips`() {
        val override = IconOverride("com.a/Main#0", label = "訊息")
        val decoded = IconOverrideCodec.decode(IconOverrideCodec.encode(override))
        assertEquals("訊息", decoded?.label)
        assertNull(decoded?.iconUri)
    }

    @Test
    fun `a picture with no rename round trips`() {
        val override = IconOverride("com.a/Main#0", iconUri = "content://media/9")
        val decoded = IconOverrideCodec.decode(IconOverrideCodec.encode(override))
        assertNull(decoded?.label)
        assertEquals("content://media/9", decoded?.iconUri)
    }

    @Test
    fun `a separator typed into a name cannot forge a field`() {
        // The name is the one field a person types, so it is the one that has
        // to be unable to corrupt the row it sits in.
        val override = IconOverride("com.a/Main#0", "before" + sep + "after", null)
        val decoded = IconOverrideCodec.decode(IconOverrideCodec.encode(override))
        assertEquals("com.a/Main#0", decoded?.appKey)
        assertEquals("before after", decoded?.label)
        assertNull(decoded?.iconUri)
    }

    @Test
    fun `an app key survives the slash and hash it always contains`() {
        val override = IconOverride("com.foo.bar/com.foo.Main#0", "X", null)
        assertEquals("com.foo.bar/com.foo.Main#0", IconOverrideCodec.decode(IconOverrideCodec.encode(override))?.appKey)
    }

    @Test
    fun `an empty override is never stored`() {
        assertTrue(IconOverride("com.a/Main#0").isEmpty)
        assertTrue(IconOverrideCodec.encodeAll(listOf(IconOverride("com.a/Main#0"))).isEmpty())
        assertNull(IconOverrideCodec.decode("com.a/Main#0" + sep + sep))
    }

    @Test
    fun `a damaged line costs only that line`() {
        val good = IconOverrideCodec.encode(IconOverride("com.a/Main#0", "A", null))
        val decoded = IconOverrideCodec.decodeAll(setOf(good, "garbage", "", "a" + sep + "b"))
        assertEquals(1, decoded.size)
        assertEquals("A", decoded["com.a/Main#0"]?.label)
    }

    @Test
    fun `a row with no key is refused`() {
        assertNull(IconOverrideCodec.decode(sep + "name" + sep + "uri"))
    }

    @Test
    fun `decodeAll keys by app so the last write for one app wins cleanly`() {
        val map = IconOverrideCodec.decodeAll(
            IconOverrideCodec.encodeAll(
                listOf(
                    IconOverride("com.a/Main#0", "A", null),
                    IconOverride("com.b/Main#0", "B", null),
                ),
            ),
        )
        assertEquals(setOf("com.a/Main#0", "com.b/Main#0"), map.keys)
    }
}
