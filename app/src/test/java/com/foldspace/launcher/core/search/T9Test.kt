package com.foldspace.launcher.core.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class T9Test {

    @Test
    fun `the keypad is the one every phone shipped`() {
        assertEquals("2", T9.signature("a"))
        assertEquals("222", T9.signature("abc"))
        assertEquals("7777", T9.signature("pqrs"))
        assertEquals("9999", T9.signature("wxyz"))
        assertEquals("8", T9.signature("t"))
    }

    @Test
    fun `gmail is 46245`() {
        assertEquals("46245", T9.signature("Gmail"))
        assertEquals(T9.FULL_PREFIX, T9.score("Gmail", "4624"))
    }

    @Test
    fun `case does not matter`() {
        assertEquals(T9.signature("CHROME"), T9.signature("chrome"))
    }

    @Test
    fun `spaces and punctuation are dropped, not mapped`() {
        // Nobody types the space in "Google Maps", so the signature cannot
        // contain one.
        assertEquals(T9.signature("GoogleMaps"), T9.signature("Google Maps"))
        assertEquals(T9.signature("WhatsApp"), T9.signature("What's App!"))
    }

    @Test
    fun `ascii digits map to themselves so a literal number still matches`() {
        assertTrue(T9.signature("Office 365").endsWith("365"))
    }

    @Test
    fun `a word can be matched from its own start`() {
        // 6277 is "maps". It is not where the label begins, so this only works
        // if each word carries its own signature.
        assertEquals(T9.WORD_PREFIX, T9.score("Google Maps", "6277"))
    }

    @Test
    fun `ranks are ordered whole-label, then word, then anywhere`() {
        assertTrue(T9.FULL_PREFIX < T9.WORD_PREFIX)
        assertTrue(T9.WORD_PREFIX < T9.CONTAINS)
        assertEquals(T9.FULL_PREFIX, T9.score("Slack", "75"))
        assertEquals(T9.WORD_PREFIX, T9.score("Adobe Reader", "7323"))
        assertEquals(T9.CONTAINS, T9.score("Instagram", "4726"))
    }

    @Test
    fun `a label a keypad cannot spell never matches`() {
        // The documented limit: 漢字 have no letters, so there is nothing to
        // map. These labels stay reachable through the text search instead.
        assertEquals("", T9.signature("設定"))
        assertEquals(T9.NO_MATCH, T9.score("設定", "738"))
        assertEquals(T9.NO_MATCH, T9.score("相機", "22"))
    }

    @Test
    fun `a mixed label is matched on its latin part`() {
        assertEquals(T9.FULL_PREFIX, T9.score("LINE 貼圖", "5463"))
    }

    @Test
    fun `an empty query is not a match`() {
        assertEquals(T9.NO_MATCH, T9.score("Gmail", ""))
    }

    @Test
    fun `only a run of at least two digits is treated as a keypad query`() {
        assertFalse(T9.isNumericQuery(""))
        assertFalse(T9.isNumericQuery("4"))
        assertTrue(T9.isNumericQuery("46"))
        assertFalse(T9.isNumericQuery("4a"))
        assertFalse(T9.isNumericQuery("gm"))
    }

    @Test
    fun `an index is the same answer as scoring the label directly`() {
        val index = T9.index("Google Maps")
        assertEquals(T9.score("Google Maps", "6277"), T9.score(index, "6277"))
        assertEquals(listOf("466453", "6277"), index.words)
    }
}
