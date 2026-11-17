package com.alananasss.kittytune.data

import com.google.gson.JsonParser
import java.util.Base64

/**
 * What the SoundCloud token endpoint's answers mean, kept pure so the rules are testable without a
 * session. Mirrors TokenKt and TokenRefreshCoordinator from the official Android app (2026.11.02).
 */
internal object OAuthTokens {

    /**
     * Errors saying our request is wrong, not that the session is over. They must never log anyone
     * out: a bad client id would otherwise end every user's session at once.
     */
    private val CLIENT_SIDE_ERRORS = setOf(
        "invalid_client",
        "invalid_request",
        "unauthorized_client",
        "unsupported_grant_type",
    )

    /**
     * Expiry of a JWT access token, or null for anything else. The official app tells the two token
     * kinds apart this way: an expiring token is a JWT carrying `exp`, a non-expiring one is opaque.
     * The `scope` can't be used for that: it is a list ("* upload non-expiring"), not a single value.
     */
    fun jwtExpiryMillis(accessToken: String?): Long? {
        val parts = accessToken?.split('.')?.takeIf { it.size == 3 } ?: return null
        return runCatching {
            val payload = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
            JsonParser.parseString(payload).asJsonObject.get("exp")?.asLong?.takeIf { it > 0L }?.times(1000L)
        }.getOrNull()
    }

    /** True when the server says this refresh token is dead, so only a new login can recover. */
    fun isRefreshTokenDead(httpCode: Int, responseBody: String?): Boolean {
        if (httpCode != 400) return false
        val error = runCatching {
            JsonParser.parseString(responseBody.orEmpty()).asJsonObject.get("error")?.asString
        }.getOrNull()
        return error !in CLIENT_SIDE_ERRORS
    }
}
