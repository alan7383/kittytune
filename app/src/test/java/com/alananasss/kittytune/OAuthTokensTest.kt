package com.alananasss.kittytune

import com.alananasss.kittytune.data.OAuthTokens
import org.junit.Test
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The rules that decide whether a SoundCloud session renews, stays or ends. Getting one wrong either
 * leaves a dead session looking alive or logs people out for nothing.
 */
class OAuthTokensTest {

    private fun jwt(payload: String): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        return "${encoder.encodeToString("{}".toByteArray())}.${encoder.encodeToString(payload.toByteArray())}.sig"
    }

    @Test
    fun `a JWT carrying exp is an expiring token`() {
        assertEquals(1_800_000_000_000L, OAuthTokens.jwtExpiryMillis(jwt("""{"exp":1800000000}""")))
    }

    @Test
    fun `a legacy opaque token has no expiry`() {
        assertNull(OAuthTokens.jwtExpiryMillis("2-301234-123456789-AbCdEfGhIjKlM"))
        assertNull(OAuthTokens.jwtExpiryMillis(null))
    }

    @Test
    fun `a JWT without exp or with a broken payload has no expiry`() {
        assertNull(OAuthTokens.jwtExpiryMillis(jwt("""{"sub":"me"}""")))
        assertNull(OAuthTokens.jwtExpiryMillis("a.%%%.c"))
    }

    @Test
    fun `invalid_grant ends the session`() {
        assertTrue(OAuthTokens.isRefreshTokenDead(400, """{"error":"invalid_grant"}"""))
    }

    @Test
    fun `an unreadable 400 ends the session like the official app`() {
        assertTrue(OAuthTokens.isRefreshTokenDead(400, "<html>"))
    }

    @Test
    fun `a request we built wrong never ends the session`() {
        assertFalse(OAuthTokens.isRefreshTokenDead(400, """{"error":"invalid_client"}"""))
        assertFalse(OAuthTokens.isRefreshTokenDead(400, """{"error":"unauthorized_client"}"""))
    }

    @Test
    fun `server trouble and rate limits never end the session`() {
        for (code in listOf(401, 403, 429, 500, 503)) {
            assertFalse(OAuthTokens.isRefreshTokenDead(code, """{"error":"invalid_grant"}"""))
        }
    }
}
