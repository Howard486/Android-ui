package com.foldspace.launcher.microsoft

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** What a successful token exchange yields. */
data class MicrosoftTokens(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtMillis: Long,
) {
    /**
     * Treated as expired a minute early, so a request never leaves here with a
     * token that expires while it is in flight.
     */
    fun isExpired(now: Long): Boolean = now >= expiresAtMillis - EXPIRY_MARGIN_MS

    private companion object {
        const val EXPIRY_MARGIN_MS = 60_000L
    }
}

/** One sign-in attempt: the challenge sent, and the state to match on return. */
data class AuthRequest(
    val authorizeUrl: String,
    val codeVerifier: String,
    val state: String,
)

/**
 * OAuth 2.0 authorization code with PKCE, by hand.
 *
 * **Why not MSAL.** MSAL's Android redirect URI is
 * `msauth://<package>/<base64 SHA-1 of the signing certificate>`. This project
 * signs its release build with the debug config and commits no keystore, so CI
 * generates a fresh `debug.keystore` on every runner — the signature hash, and
 * therefore the registered redirect URI, would change on nearly every build.
 * A custom scheme registered under Azure's *Mobile and desktop applications*
 * platform does not depend on the signature at all. It also costs no
 * dependency, and everything except the browser hop is testable off-device,
 * which in this environment is the difference between verified and hoped for.
 *
 * **What the user must do.** An Azure app registration needs their own
 * Microsoft account and the Azure portal, and no code here can do it for them.
 * FoldSpace therefore ships with no client id and says so.
 */
object MicrosoftAuth {

    const val AUTHORIZE_ENDPOINT =
        "https://login.microsoftonline.com/common/oauth2/v2.0/authorize"
    const val TOKEN_ENDPOINT =
        "https://login.microsoftonline.com/common/oauth2/v2.0/token"

    /** Must match the redirect URI registered in the Azure app. */
    const val REDIRECT_URI = "foldspace://auth/microsoft"

    /**
     * Read-only, and no more than the two pages need.
     *
     * `offline_access` is what makes a refresh token available at all; without
     * it the user would be sent back to a browser every hour.
     */
    val SCOPES = listOf("offline_access", "User.Read", "Calendars.Read", "Tasks.Read")

    fun scopeParameter(): String = SCOPES.joinToString(" ")

    /** RFC 7636: 43–128 characters from the unreserved set. 64 bytes gives 86. */
    fun createVerifier(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(VERIFIER_BYTES)
        random.nextBytes(bytes)
        return base64Url(bytes)
    }

    /** S256: the challenge is the base64url of the SHA-256 of the verifier. */
    fun challengeFor(verifier: String): String =
        base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    fun createState(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(STATE_BYTES)
        random.nextBytes(bytes)
        return base64Url(bytes)
    }

    fun buildRequest(
        clientId: String,
        verifier: String = createVerifier(),
        state: String = createState(),
    ): AuthRequest {
        val query = listOf(
            "client_id" to clientId,
            "response_type" to "code",
            "redirect_uri" to REDIRECT_URI,
            "response_mode" to "query",
            "scope" to scopeParameter(),
            "state" to state,
            "code_challenge" to challengeFor(verifier),
            "code_challenge_method" to "S256",
        ).joinToString("&") { (key, value) -> "$key=" + encode(value) }

        return AuthRequest("$AUTHORIZE_ENDPOINT?$query", verifier, state)
    }

    /**
     * The authorization code from a redirect, or null.
     *
     * A redirect whose `state` does not match the one issued is rejected
     * outright rather than merely logged: that check is the only thing standing
     * between this and someone else's authorization code being exchanged.
     */
    fun codeFromRedirect(redirect: String, expectedState: String): String? {
        if (!redirect.startsWith(REDIRECT_URI)) return null
        val query = redirect.substringAfter('?', "").takeIf { it.isNotBlank() } ?: return null
        val params = query.split('&').mapNotNull { pair ->
            val key = pair.substringBefore('=', "")
            val value = pair.substringAfter('=', "")
            if (key.isBlank()) null else key to decode(value)
        }.toMap()

        if (params["state"] != expectedState) return null
        return params["code"]?.takeIf { it.isNotBlank() }
    }

    /** The error a redirect carried, if it carried one instead of a code. */
    fun errorFromRedirect(redirect: String): String? {
        val query = redirect.substringAfter('?', "")
        val error = query.split('&')
            .firstOrNull { it.startsWith("error=") }
            ?.substringAfter('=')
            ?: return null
        return decode(error).takeIf { it.isNotBlank() }
    }

    fun tokenRequestBody(clientId: String, code: String, verifier: String): String = listOf(
        "client_id" to clientId,
        "grant_type" to "authorization_code",
        "code" to code,
        "redirect_uri" to REDIRECT_URI,
        "code_verifier" to verifier,
        "scope" to scopeParameter(),
    ).joinToString("&") { (key, value) -> "$key=" + encode(value) }

    fun refreshRequestBody(clientId: String, refreshToken: String): String = listOf(
        "client_id" to clientId,
        "grant_type" to "refresh_token",
        "refresh_token" to refreshToken,
        "redirect_uri" to REDIRECT_URI,
        "scope" to scopeParameter(),
    ).joinToString("&") { (key, value) -> "$key=" + encode(value) }

    /**
     * Turns a token response into an expiry instant.
     *
     * Kept separate from the JSON so the arithmetic can be tested: an
     * `expires_in` misread as an absolute time would make every token look
     * expired for the next fifty-five years.
     */
    fun tokensFrom(
        accessToken: String?,
        refreshToken: String?,
        expiresInSeconds: Long?,
        now: Long,
    ): MicrosoftTokens? {
        if (accessToken.isNullOrBlank()) return null
        val lifetime = (expiresInSeconds ?: DEFAULT_LIFETIME_SECONDS).coerceAtLeast(0L)
        return MicrosoftTokens(
            accessToken = accessToken,
            refreshToken = refreshToken?.takeIf { it.isNotBlank() },
            expiresAtMillis = now + lifetime * 1000L,
        )
    }

    private fun base64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    private fun decode(value: String): String =
        runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

    private const val VERIFIER_BYTES = 64
    private const val STATE_BYTES = 16
    private const val DEFAULT_LIFETIME_SECONDS = 3600L
}
