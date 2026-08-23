package com.foldspace.launcher.ai

import com.foldspace.launcher.context.ContextSnapshot

/**
 * §8 — the Nano adapter, built on any [TextInference].
 *
 * This is where the rules §8 states actually get enforced, and none of them
 * depend on which model is behind the seam:
 *
 *  - a batch smaller than [MIN_BATCH] is not worth waking a model for (§12.4:
 *    a single notification arriving must never trigger inference);
 *  - a batch larger than [MAX_BATCH] is truncated, because prompt length is
 *    the one cost that grows without bound;
 *  - anything the parser cannot read is dropped, not guessed at;
 *  - a verdict below [MIN_CONFIDENCE] is discarded — the decision ladder
 *    treats a weak model answer as no answer, and §7.1 puts every
 *    deterministic source above this one anyway.
 *
 * With [TextInference.None] behind it, every one of those paths still runs
 * and returns nothing, which is what makes the fallback the tested path
 * rather than the surprise.
 */
class PromptNanoAdapter(
    private val inference: TextInference,
) : NanoAdapter {

    override fun availability(): NanoUnavailableReason = inference.availability()

    override suspend fun classifyContext(snapshot: ContextSnapshot): NanoContextResult? {
        if (availability() != NanoUnavailableReason.Available) return null
        val reply = inference.generate(NanoPrompts.contextPrompt(snapshot)) ?: return null
        return NanoPrompts.parseContext(reply)?.takeIf { it.confidence >= MIN_CONFIDENCE }
    }

    override suspend fun classifyNotifications(
        items: List<NanoNotificationInput>,
    ): List<NanoNotificationResult> {
        if (availability() != NanoUnavailableReason.Available) return emptyList()
        if (items.size < MIN_BATCH) return emptyList()

        val batch = items.take(MAX_BATCH)
        val reply = inference.generate(NanoPrompts.notificationPrompt(batch)) ?: return emptyList()
        return NanoPrompts.parseNotifications(reply, batch)
            .filter { it.confidence >= MIN_CONFIDENCE }
    }

    companion object {
        /** §12.4 — one notification is never a reason to run a model. */
        const val MIN_BATCH = 3

        /** Prompt length is the unbounded cost; this is the bound. */
        const val MAX_BATCH = 20

        /** Below this the ladder treats the answer as no answer. */
        const val MIN_CONFIDENCE = 0.6f
    }
}
