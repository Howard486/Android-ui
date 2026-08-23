package com.foldspace.launcher.ai

/**
 * The one thing a language model has to do for FoldSpace: take a prompt,
 * return text.
 *
 * Deliberately this narrow. Everything that makes model output safe to use —
 * label whitelisting, confidence floors, rejecting free text, batching,
 * caching, the foreground gate — lives above this interface and is testable
 * without a model. Swapping in AICore, ML Kit GenAI or a remote endpoint is a
 * change to one class.
 */
interface TextInference {

    /** Cheap and synchronous; must never block on a model download. */
    fun availability(): NanoUnavailableReason

    /** Null on any failure. The caller always has a fallback path. */
    suspend fun generate(prompt: String): String?

    companion object {
        /**
         * No model. Reports the device as unsupported, which is what every
         * build without an inference backend must say.
         */
        val None: TextInference = object : TextInference {
            override fun availability() = NanoUnavailableReason.DeviceNotSupported
            override suspend fun generate(prompt: String): String? = null
        }
    }
}
