package com.foldspace.launcher.microsoft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "invalid_request" flashed over the dock for two seconds is a status line, not
 * a message. Every branch here has to name something the user can go and
 * change, or the card is no better than the toast was.
 */
class MicrosoftErrorTest {

    @Test
    fun `the commonest failure names the redirect URI to register`() {
        val text = MicrosoftAuth.explain("invalid_request")
        assertTrue(text.contains(MicrosoftAuth.REDIRECT_URI))
    }

    @Test
    fun `a public-client rejection names the setting that fixes it`() {
        assertTrue(MicrosoftAuth.explain("unauthorized_client").contains("公用用戶端"))
    }

    @Test
    fun `case and whitespace from the query string do not matter`() {
        assertEquals(
            MicrosoftAuth.explain("invalid_request"),
            MicrosoftAuth.explain("  Invalid_Request "),
        )
    }

    @Test
    fun `an unknown code is passed through rather than swallowed`() {
        assertTrue(MicrosoftAuth.explain("some_new_code").contains("some_new_code"))
    }

    @Test
    fun `no code at all still says something`() {
        assertTrue(MicrosoftAuth.explain(null).isNotBlank())
        assertTrue(MicrosoftAuth.explain("").isNotBlank())
    }

    @Test
    fun `an error in the redirect is explained, not relayed raw`() {
        val redirect = MicrosoftAuth.REDIRECT_URI + "?error=invalid_request&state=abc"
        val raw = MicrosoftAuth.errorFromRedirect(redirect)
        assertEquals("invalid_request", raw)
        assertTrue(MicrosoftAuth.explain(raw).length > raw!!.length)
    }
}
