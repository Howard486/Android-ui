package com.foldspace.launcher.ai

import com.foldspace.launcher.context.ContextSnapshot
import com.foldspace.launcher.spaces.NotificationTier
import com.foldspace.launcher.spaces.SpaceId

/**
 * §8.4 — structured Nano output. The UI must never bind to a natural-language
 * string, so the adapter's contract is this object, not text.
 */
data class NanoContextResult(
    val space: SpaceId,
    val confidence: Float,
    val reasonCode: String,
)

data class NanoNotificationResult(
    val key: String,
    val tier: NotificationTier,
    val confidence: Float,
)

/** Why on-device inference is unavailable. Surfaced in settings so the user
 *  sees a reason rather than a silently missing feature (§21.1). */
enum class NanoUnavailableReason {
    Available,
    DeviceNotSupported,
    ModelNotDownloaded,
    NotForeground,
    QuotaExceeded,
    Busy,
    FeatureDisabled,
}

/**
 * §8 Gemini Nano boundary.
 *
 * Kept as an interface with a no-op default for a concrete reason: §22 lists
 * Nano availability as device-specific and its background use as prohibited,
 * so the launcher must be fully functional with no implementation behind this
 * at all. The ML Kit GenAI implementation lands in V0.5 (§20.2) and plugs in
 * here without touching the Context Engine.
 */
interface NanoAdapter {

    /** Cheap, synchronous capability probe — never blocks on a download. */
    fun availability(): NanoUnavailableReason

    /**
     * §8.1 context classification. Only ever called when the launcher is the
     * top foreground app and the rule engine declined to decide.
     */
    suspend fun classifyContext(snapshot: ContextSnapshot): NanoContextResult?

    /**
     * §10.3 — batch, never per-notification. A single notification arriving
     * must not trigger inference (§12.4).
     */
    suspend fun classifyNotifications(
        items: List<NanoNotificationInput>,
    ): List<NanoNotificationResult>

    companion object {
        /**
         * The V0.1 implementation. Reports unsupported and returns nothing, so
         * every caller exercises its fallback path in day-one testing rather
         * than the first time it meets an unsupported device.
         */
        val Unsupported: NanoAdapter = object : NanoAdapter {
            override fun availability() = NanoUnavailableReason.DeviceNotSupported
            override suspend fun classifyContext(snapshot: ContextSnapshot): NanoContextResult? = null
            override suspend fun classifyNotifications(
                items: List<NanoNotificationInput>,
            ): List<NanoNotificationResult> = emptyList()
        }
    }
}

/**
 * Notification text handed to inference. Carries only what classification
 * needs; the body is optional because §10.4 lets the user mark an app
 * "badge only, do not analyse content".
 */
data class NanoNotificationInput(
    val key: String,
    val packageName: String,
    val title: String?,
    val body: String?,
    val hasActions: Boolean,
    val isOngoing: Boolean,
)
