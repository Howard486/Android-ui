package com.foldspace.launcher.context

import com.foldspace.launcher.spaces.SpaceId

/** What was turned down, and when. */
data class DismissedSuggestion(
    val space: SpaceId,
    val fingerprint: ContextFingerprint?,
    val atMillis: Long,
)

/**
 * Whether a suggestion may be shown again after being turned down.
 *
 * "先不要" used to mean nothing at all: dismissing set the flow to null and
 * recorded no reason, so the next signal to arrive — a battery tick, a
 * notification, a fold, a usage refresh — re-ran the same ladder, reached the
 * same conclusion and put the banner straight back. Refusing had the lifespan
 * of one event.
 *
 * Two conditions release it, and both have to be right:
 *
 *  - **the situation actually changed.** The fingerprint is the engine's own
 *    answer to "is this the same context", so it is the right thing to
 *    compare. A different Space is always allowed through: turning down 工作
 *    says nothing about 簡易.
 *  - **enough time passed.** A fingerprint buckets continuous signals, but it
 *    can still flicker — a headphone reconnecting, a battery crossing a
 *    bucket edge. Without a floor, the flicker alone brings the banner back
 *    and the user is asked again about the situation they just refused.
 */
object SuggestionGate {

    /**
     * How long a refusal holds even if the fingerprint changes.
     *
     * Twenty minutes: long enough that a stray signal cannot re-ask, short
     * enough that genuinely arriving somewhere new is still noticed within
     * the span of the thing you went there to do.
     */
    const val COOLDOWN_MS = 20 * 60 * 1000L

    fun allows(
        dismissed: DismissedSuggestion?,
        space: SpaceId,
        fingerprint: ContextFingerprint?,
        nowMillis: Long,
    ): Boolean {
        if (dismissed == null) return true
        // A refusal is about one Space, not about being suggested anything.
        if (dismissed.space != space) return true
        if (nowMillis - dismissed.atMillis < COOLDOWN_MS) return false
        // Past the cooldown, only a genuinely different situation reopens it.
        return fingerprint != dismissed.fingerprint
    }
}
