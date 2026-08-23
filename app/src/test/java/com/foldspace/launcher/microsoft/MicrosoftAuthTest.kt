package com.foldspace.launcher.microsoft

import java.security.MessageDigest
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MicrosoftAuthTest {

    @Test
    fun `a verifier is within the length RFC 7636 allows`() {
        repeat(20) {
            val verifier = MicrosoftAuth.createVerifier()
            assertTrue(verifier.length in 43..128)
            assertTrue(verifier.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        }
    }

    @Test
    fun `two verifiers are not the same`() {
        assertNotEquals(MicrosoftAuth.createVerifier(), MicrosoftAuth.createVerifier())
    }

    @Test
    fun `the challenge is the base64url sha256 of the verifier, unpadded`() {
        val verifier = "abc123"
        val expected = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
        )
        assertEquals(expected, MicrosoftAuth.challengeFor(verifier))
        assertFalse(MicrosoftAuth.challengeFor(verifier).contains("="))
    }

    @Test
    fun `the authorize url carries everything the endpoint requires`() {
        val request = MicrosoftAuth.buildRequest("client-abc")
        val url = request.authorizeUrl
        assertTrue(url.startsWith(MicrosoftAuth.AUTHORIZE_ENDPOINT + "?"))
        listOf(
            "client_id=client-abc",
            "response_type=code",
            "code_challenge_method=S256",
            "state=" + urlEncode(request.state),
            "code_challenge=" + urlEncode(MicrosoftAuth.challengeFor(request.codeVerifier)),
        ).forEach { assertTrue(it, url.contains(it)) }
    }

    @Test
    fun `the redirect uri is url encoded rather than pasted raw`() {
        val url = MicrosoftAuth.buildRequest("c").authorizeUrl
        assertFalse(url.contains("redirect_uri=foldspace://"))
        assertTrue(url.contains("redirect_uri=" + urlEncode(MicrosoftAuth.REDIRECT_URI)))
    }

    @Test
    fun `offline_access is requested or there is no refresh token to have`() {
        assertTrue("offline_access" in MicrosoftAuth.SCOPES)
        assertEquals("offline_access User.Read Calendars.Read Tasks.Read", MicrosoftAuth.scopeParameter())
    }

    @Test
    fun `only read scopes are asked for`() {
        assertTrue(MicrosoftAuth.SCOPES.none { it.endsWith("ReadWrite") || it.contains("Send") })
    }

    @Test
    fun `a matching redirect yields its code`() {
        val redirect = MicrosoftAuth.REDIRECT_URI + "?code=THE_CODE&state=xyz"
        assertEquals("THE_CODE", MicrosoftAuth.codeFromRedirect(redirect, "xyz"))
    }

    @Test
    fun `a mismatched state is refused, not merely noted`() {
        val redirect = MicrosoftAuth.REDIRECT_URI + "?code=THE_CODE&state=attacker"
        assertNull(MicrosoftAuth.codeFromRedirect(redirect, "xyz"))
    }

    @Test
    fun `a redirect with no state at all is refused`() {
        assertNull(MicrosoftAuth.codeFromRedirect(MicrosoftAuth.REDIRECT_URI + "?code=C", "xyz"))
    }

    @Test
    fun `a redirect to somewhere else is refused`() {
        assertNull(MicrosoftAuth.codeFromRedirect("https://evil.example/?code=C&state=xyz", "xyz"))
    }

    @Test
    fun `a denied consent reports its error rather than an empty code`() {
        val redirect = MicrosoftAuth.REDIRECT_URI + "?error=access_denied&state=xyz"
        assertNull(MicrosoftAuth.codeFromRedirect(redirect, "xyz"))
        assertEquals("access_denied", MicrosoftAuth.errorFromRedirect(redirect))
    }

    @Test
    fun `expires_in is a lifetime, not an instant`() {
        val tokens = MicrosoftAuth.tokensFrom("at", "rt", 3600, now = 1_000_000L)
        assertEquals(1_000_000L + 3_600_000L, tokens?.expiresAtMillis)
    }

    @Test
    fun `a token is treated as expired a minute early`() {
        val tokens = MicrosoftAuth.tokensFrom("at", "rt", 3600, now = 0L)!!
        assertFalse(tokens.isExpired(3_500_000L))
        assertTrue(tokens.isExpired(3_550_000L))
        assertTrue(tokens.isExpired(3_600_000L))
    }

    @Test
    fun `a response with no access token is not a success`() {
        assertNull(MicrosoftAuth.tokensFrom(null, "rt", 3600, 0L))
        assertNull(MicrosoftAuth.tokensFrom("", "rt", 3600, 0L))
    }

    @Test
    fun `a refresh response that omits a new refresh token is still valid`() {
        val tokens = MicrosoftAuth.tokensFrom("at", null, 3600, 0L)
        assertEquals("at", tokens?.accessToken)
        assertNull(tokens?.refreshToken)
    }

    @Test
    fun `both request bodies carry the client id and the right grant`() {
        val exchange = MicrosoftAuth.tokenRequestBody("cid", "code", "verifier")
        assertTrue(exchange.contains("grant_type=authorization_code"))
        assertTrue(exchange.contains("code_verifier=verifier"))
        assertTrue(exchange.contains("client_id=cid"))

        val refresh = MicrosoftAuth.refreshRequestBody("cid", "rt")
        assertTrue(refresh.contains("grant_type=refresh_token"))
        assertTrue(refresh.contains("refresh_token=rt"))
        // A public client has no secret to send, and sending an empty one is
        // rejected by the endpoint rather than ignored.
        assertFalse(refresh.contains("client_secret"))
    }

    private fun urlEncode(value: String) = java.net.URLEncoder.encode(value, "UTF-8")
}
