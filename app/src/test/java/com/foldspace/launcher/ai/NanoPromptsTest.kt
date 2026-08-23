package com.foldspace.launcher.ai

import com.foldspace.launcher.context.ContextSnapshot
import com.foldspace.launcher.spaces.NotificationTier
import com.foldspace.launcher.spaces.SpaceId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The layer that decides whether a model's output is allowed to mean
 * anything.
 *
 * §8.4 forbids the UI binding to model text. That is only true if the parser
 * refuses everything that is not exactly the expected shape — a prompt asking
 * politely for a format guarantees nothing.
 */
class NanoPromptsTest {

    private val snapshot = ContextSnapshot.initial()

    private fun input(index: Int) = NanoNotificationInput(
        key = "k$index",
        packageName = "com.app$index",
        title = "title $index",
        body = "body $index",
        hasActions = false,
        isOngoing = false,
    )

    @Test
    fun `a well-formed context reply parses`() {
        val result = NanoPrompts.parseContext("work|0.82")
        assertEquals(SpaceId.Work, result?.space)
        assertEquals(0.82f, result?.confidence)
    }

    @Test
    fun `the reason code is fixed, never the model's words`() {
        assertEquals("NANO_CLASSIFICATION", NanoPrompts.parseContext("work|0.9")?.reasonCode)
    }

    @Test
    fun `free text is refused rather than salvaged`() {
        assertNull(NanoPrompts.parseContext("I think the user is working right now."))
        assertNull(NanoPrompts.parseContext(""))
        assertNull(NanoPrompts.parseContext(null))
    }

    @Test
    fun `a label FoldSpace does not know is refused`() {
        assertNull(NanoPrompts.parseContext("gaming|0.99"))
    }

    @Test
    fun `the model may never nominate the simplified Space`() {
        assertNull(NanoPrompts.parseContext("simple|1.0"))
        assertTrue("simple" !in NanoPrompts.contextPrompt(snapshot))
    }

    @Test
    fun `a confidence outside zero to one is refused`() {
        assertNull(NanoPrompts.parseContext("work|1.4"))
        assertNull(NanoPrompts.parseContext("work|-0.2"))
        assertNull(NanoPrompts.parseContext("work|lots"))
    }

    @Test
    fun `notification verdicts parse by index`() {
        val items = List(3, ::input)
        val parsed = NanoPrompts.parseNotifications("0|Now|0.9\n2|Noise|0.7", items)
        assertEquals(setOf("k0", "k2"), parsed.mapTo(mutableSetOf()) { it.key })
        assertEquals(NotificationTier.Now, parsed.first { it.key == "k0" }.tier)
    }

    @Test
    fun `an invented index loses only its own line`() {
        val items = List(2, ::input)
        val parsed = NanoPrompts.parseNotifications("0|Now|0.9\n9|Now|0.9\nrubbish", items)
        assertEquals(listOf("k0"), parsed.map { it.key })
    }

    @Test
    fun `a repeated index keeps the first verdict`() {
        val items = List(1, ::input)
        val parsed = NanoPrompts.parseNotifications("0|Now|0.9\n0|Noise|0.9", items)
        assertEquals(1, parsed.size)
        assertEquals(NotificationTier.Now, parsed.first().tier)
    }

    @Test
    fun `a pipe in a notification title cannot fake an extra field`() {
        val nasty = input(0).copy(title = "budget | Q3 | urgent")
        val prompt = NanoPrompts.notificationPrompt(listOf(nasty))
        val inputLine = prompt.lines().last { it.startsWith("0|") }
        assertEquals(6, inputLine.split("|").size)
    }

    private class Fake(private val reply: String?) : TextInference {
        var calls = 0
        override fun availability() = NanoUnavailableReason.Available
        override suspend fun generate(prompt: String): String? {
            calls++
            return reply
        }
    }

    @Test
    fun `one notification never wakes the model`() = runTest {
        val fake = Fake("0|Now|0.9")
        val adapter = PromptNanoAdapter(fake)
        assertTrue(adapter.classifyNotifications(listOf(input(0))).isEmpty())
        assertEquals(0, fake.calls)
    }

    @Test
    fun `a weak verdict is treated as no verdict`() = runTest {
        val adapter = PromptNanoAdapter(Fake("work|0.2"))
        assertNull(adapter.classifyContext(snapshot))
    }

    @Test
    fun `an unsupported backend never calls generate`() = runTest {
        val adapter = PromptNanoAdapter(TextInference.None)
        assertNull(adapter.classifyContext(snapshot))
        assertTrue(adapter.classifyNotifications(List(5, ::input)).isEmpty())
    }

    @Test
    fun `an oversized batch is truncated rather than sent whole`() = runTest {
        val items = List(40, ::input)
        val fake = Fake((0 until 40).joinToString("\n") { "$it|Now|0.9" })
        val parsed = PromptNanoAdapter(fake).classifyNotifications(items)
        assertEquals(PromptNanoAdapter.MAX_BATCH, parsed.size)
    }
}
