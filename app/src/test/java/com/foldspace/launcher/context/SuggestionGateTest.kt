package com.foldspace.launcher.context

import com.foldspace.launcher.spaces.SpaceId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionGateTest {

    private val here = ContextSnapshot.initial().fingerprint()
    private val elsewhere = ContextSnapshot.initial().copy(charging = true).fingerprint()

    private fun dismissal(
        space: SpaceId = SpaceId.Work,
        fingerprint: ContextFingerprint? = here,
        at: Long = 0L,
    ) = DismissedSuggestion(space, fingerprint, at)

    @Test
    fun `nothing dismissed means anything may be suggested`() {
        assertTrue(SuggestionGate.allows(null, SpaceId.Work, here, 0L))
    }

    @Test
    fun `the same suggestion is not repeated a moment later`() {
        // This is the whole bug: every battery tick used to re-ask.
        assertFalse(SuggestionGate.allows(dismissal(), SpaceId.Work, here, 1_000L))
    }

    @Test
    fun `not even a changed situation reopens it inside the cooldown`() {
        // A fingerprint can flicker — a headphone reconnecting, a battery
        // crossing a bucket edge — and flicker alone must not re-ask.
        assertFalse(
            SuggestionGate.allows(dismissal(), SpaceId.Work, elsewhere, SuggestionGate.COOLDOWN_MS - 1),
        )
    }

    @Test
    fun `a genuinely different situation reopens it once the cooldown passes`() {
        assertTrue(
            SuggestionGate.allows(dismissal(), SpaceId.Work, elsewhere, SuggestionGate.COOLDOWN_MS + 1),
        )
    }

    @Test
    fun `the same situation stays shut even long afterwards`() {
        // Sitting at the same desk all afternoon is not a reason to ask again.
        assertFalse(
            SuggestionGate.allows(dismissal(), SpaceId.Work, here, SuggestionGate.COOLDOWN_MS * 20),
        )
    }

    @Test
    fun `refusing one Space says nothing about another`() {
        assertTrue(SuggestionGate.allows(dismissal(space = SpaceId.Work), SpaceId.Simple, here, 1_000L))
        assertTrue(SuggestionGate.allows(dismissal(space = SpaceId.Work), SpaceId.General, here, 1_000L))
    }

    @Test
    fun `a dismissal recorded with no fingerprint still blocks the repeat`() {
        assertFalse(SuggestionGate.allows(dismissal(fingerprint = null), SpaceId.Work, null, 1_000L))
        assertTrue(
            SuggestionGate.allows(
                dismissal(fingerprint = null),
                SpaceId.Work,
                here,
                SuggestionGate.COOLDOWN_MS + 1,
            ),
        )
    }

    @Test
    fun `the boundary is inclusive of the cooldown having elapsed`() {
        assertFalse(SuggestionGate.allows(dismissal(), SpaceId.Work, elsewhere, SuggestionGate.COOLDOWN_MS - 1))
        assertTrue(SuggestionGate.allows(dismissal(), SpaceId.Work, elsewhere, SuggestionGate.COOLDOWN_MS))
    }
}
