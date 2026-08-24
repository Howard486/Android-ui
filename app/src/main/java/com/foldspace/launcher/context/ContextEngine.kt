package com.foldspace.launcher.context

import com.foldspace.launcher.ai.NanoAdapter
import com.foldspace.launcher.ai.NanoUnavailableReason
import com.foldspace.launcher.spaces.SpaceId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** A suggestion the UI may show. Null [ContextEngine.suggestion] means "say nothing". */
data class SpaceSuggestion(
    val space: SpaceId,
    val confidence: Float,
    val reasonCode: String,
)

/**
 * §19.1 — the Space suggestion pipeline, implemented exactly as the spec's
 * pseudocode orders it:
 *
 * ```
 * event -> snapshot -> fingerprint -> cache hit? -> rules -> (foreground? nano) -> suggest
 * ```
 *
 * Two invariants this class exists to enforce:
 *  - the same fingerprint never runs inference twice (§12.4, §21.2);
 *  - Nano is only consulted while the launcher is the top foreground app
 *    (§8.2, §22 — background inference is prohibited, not merely discouraged).
 */
class ContextEngine(
    private val scope: CoroutineScope,
    private val ruleEngine: RuleEngine,
    private val nano: NanoAdapter,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val _snapshot = MutableStateFlow(ContextSnapshot.initial())
    val snapshot: StateFlow<ContextSnapshot> = _snapshot.asStateFlow()

    private val _suggestion = MutableStateFlow<SpaceSuggestion?>(null)
    val suggestion: StateFlow<SpaceSuggestion?> = _suggestion.asStateFlow()

    /** Set by the UI when the user picks a Space by hand (§7.1 level 1). */
    private var userOverride: SpaceId? = null
    private var overrideFingerprint: ContextFingerprint? = null

    private var switchMode: SwitchMode = SwitchMode.SuggestFirst

    /**
     * The last suggestion the user turned down. See [SuggestionGate].
     *
     * The mechanism already existed for a hand-picked Space — [userOverride]
     * remembers the fingerprint it was chosen at — and refusing simply never
     * used it.
     */
    private var dismissed: DismissedSuggestion? = null

    private var lastFingerprint: ContextFingerprint? = null
    private var lastDecision: ContextDecision = ContextDecision.NoAction

    /**
     * The fingerprint Nano has already been offered. Foreground state is
     * deliberately *not* part of the fingerprint (§7.3 — otherwise every
     * resume invalidates the cache), so this is what lets a context that was
     * evaluated while backgrounded still get its one inference when the
     * launcher comes forward — and only one.
     */
    private var nanoAttemptedFor: ContextFingerprint? = null

    /**
     * Emitted when the engine decides a Space should actually be applied
     * (Automatic mode + an explicit user rule). Conflated: a stale pending
     * switch is worthless once a newer one exists.
     */
    private val _applySpace = MutableSharedFlow<SpaceId>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val applySpace = _applySpace

    fun setSwitchMode(mode: SwitchMode) {
        switchMode = mode
        if (mode == SwitchMode.ManualOnly) _suggestion.value = null
    }

    /**
     * §7.1 level 1. A manual pick outranks everything until the context
     * genuinely moves on — hence pinning it to the fingerprint rather than to
     * a timer, so "I chose Work" survives until the situation changes.
     */
    fun setUserOverride(space: SpaceId?) {
        userOverride = space
        overrideFingerprint = _snapshot.value.fingerprint()
        // Choosing by hand settles the question; a refusal of the old
        // suggestion should not then suppress a later, different one.
        dismissed = null
        _suggestion.value = null
    }

    fun dismissSuggestion() {
        _suggestion.value?.let { current ->
            dismissed = DismissedSuggestion(
                space = current.space,
                fingerprint = _snapshot.value.fingerprint(),
                atMillis = clock(),
            )
        }
        _suggestion.value = null
    }

    /** The single entry point. Every signal source calls this and nothing else. */
    fun onEvent(event: ContextEvent) {
        val updated = ContextReducer.reduce(_snapshot.value, event, clock())
        _snapshot.value = updated
        evaluate(updated)
    }

    private fun evaluate(snapshot: ContextSnapshot) {
        if (switchMode == SwitchMode.ManualOnly) return

        val fingerprint = snapshot.fingerprint()

        // §7.3 — unchanged fingerprint, cached decision, no work. The single
        // exception is the deferred inference described on [nanoAttemptedFor].
        if (fingerprint == lastFingerprint) {
            if (lastDecision.source == DecisionSource.None &&
                nanoAttemptedFor != fingerprint &&
                nanoEligible(snapshot)
            ) {
                nanoAttemptedFor = fingerprint
                scope.launch { runNano(snapshot, fingerprint) }
                return
            }
            publish(lastDecision)
            return
        }

        // The override survives only while the context that produced it holds.
        if (overrideFingerprint != null && fingerprint != overrideFingerprint) {
            userOverride = null
            overrideFingerprint = null
        }
        if (userOverride != null) {
            lastFingerprint = fingerprint
            lastDecision = ContextDecision.NoAction
            _suggestion.value = null
            return
        }

        val decision = ruleEngine.evaluate(snapshot, switchMode)
        lastFingerprint = fingerprint
        lastDecision = decision

        if (decision.source != DecisionSource.None) {
            publish(decision)
            return
        }

        // §7.1 level 5 — only now, and only if we are allowed to.
        if (nanoEligible(snapshot)) {
            nanoAttemptedFor = fingerprint
            scope.launch { runNano(snapshot, fingerprint) }
        } else {
            publish(ContextDecision.NoAction)
        }
    }

    /**
     * §8.2 / §12.1 rule 3. All three conditions must hold: foreground, model
     * actually available, and not in battery saver.
     */
    private fun nanoEligible(snapshot: ContextSnapshot): Boolean =
        snapshot.launcherForeground &&
            !snapshot.powerSave &&
            nano.availability() == NanoUnavailableReason.Available

    private suspend fun runNano(snapshot: ContextSnapshot, fingerprint: ContextFingerprint) {
        val result = nano.classifyContext(snapshot)
        // The context may have moved while inference ran; a result for a stale
        // fingerprint is not just useless, it would suggest the wrong Space.
        if (_snapshot.value.fingerprint() != fingerprint) return

        if (result == null || result.confidence < NANO_CONFIDENCE_THRESHOLD) {
            publish(ContextDecision.NoAction)
            return
        }

        // §7.1 — a model is not allowed to nominate 簡易 either, however
        // confident it is. Only the user puts themselves in simplified mode.
        if (result.space.isUserSelectableOnly) {
            publish(ContextDecision.NoAction)
            return
        }

        val decision = ContextDecision(
            space = result.space,
            source = DecisionSource.NanoClassification,
            confidence = result.confidence,
            reasonCode = result.reasonCode,
            // §1.2 rule 4 and §7.2: AI suggests, it never decides.
            apply = false,
        )
        lastDecision = decision
        publish(decision)
    }

    private fun publish(decision: ContextDecision) {
        val space = decision.space
        if (space == null || decision.source == DecisionSource.None) {
            _suggestion.value = null
            return
        }
        if (decision.apply) {
            _suggestion.value = null
            _applySpace.tryEmit(space)
            return
        }
        if (!SuggestionGate.allows(dismissed, space, _snapshot.value.fingerprint(), clock())) {
            // Turned down, and nothing has changed since. Saying nothing is
            // the whole point — asking again is what "先不要" was refusing.
            _suggestion.value = null
            return
        }

        _suggestion.value = SpaceSuggestion(
            space = space,
            confidence = decision.confidence,
            reasonCode = decision.reasonCode,
        )
    }

    private companion object {
        const val NANO_CONFIDENCE_THRESHOLD = 0.7f
    }
}
