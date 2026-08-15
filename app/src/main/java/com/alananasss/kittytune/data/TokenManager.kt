package com.alananasss.kittytune.data

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager

class TokenManager(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "soundtune_auth"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_TIMESTAMP = "token_timestamp"
        private const val KEY_TOKEN_EXPIRES_AT = "token_expires_at"
        private const val KEY_TOKEN_SCOPE = "token_scope"
        private const val KEY_LAST_VALIDATED_AT = "last_validated_at"
        private const val KEY_IS_GUEST_MODE = "is_guest_mode"

        private const val DEFAULT_TOKEN_VALIDITY_MS = 25 * 60 * 1000L
        private const val REFRESH_SKEW_MS = 5 * 60 * 1000L
        private const val SCOPE_NON_EXPIRING = "non-expiring"
    }

    fun saveTokens(
        accessToken: String,
        refreshToken: String? = getRefreshToken(),
        expiresInSeconds: Long? = null,
        scope: String? = getTokenScope()
    ) {
        val cleanAccessToken = cleanToken(accessToken) ?: return
        val cleanRefreshToken = cleanToken(refreshToken)
        val cleanScope = cleanToken(scope)
        val now = System.currentTimeMillis()
        val expiresAt = resolveExpiresAt(now, expiresInSeconds, cleanScope)

        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, cleanAccessToken)
            if (!cleanRefreshToken.isNullOrEmpty()) {
                putString(KEY_REFRESH_TOKEN, cleanRefreshToken)
            } else if (refreshToken != null) {
                remove(KEY_REFRESH_TOKEN)
            }
            if (!cleanScope.isNullOrEmpty()) {
                putString(KEY_TOKEN_SCOPE, cleanScope)
            }
            putLong(KEY_TOKEN_TIMESTAMP, now)
            putLong(KEY_LAST_VALIDATED_AT, now)
            putLong(KEY_TOKEN_EXPIRES_AT, expiresAt)
            putBoolean(KEY_IS_GUEST_MODE, false)
        }.apply()
    }

    fun markAccessTokenFresh() {
        if (hasAccessToken()) {
            val now = System.currentTimeMillis()
            val expiresAt = prefs.getLong(KEY_TOKEN_EXPIRES_AT, 0L)
            val editor = prefs.edit()
                .putLong(KEY_TOKEN_TIMESTAMP, now)
                .putLong(KEY_LAST_VALIDATED_AT, now)
                .putBoolean(KEY_IS_GUEST_MODE, false)

            if (expiresAt == 0L || expiresAt <= now) {
                editor.putLong(KEY_TOKEN_EXPIRES_AT, now + DEFAULT_TOKEN_VALIDITY_MS)
            }

            editor.apply()
        }
    }

    fun setGuestMode(isGuest: Boolean) {
        prefs.edit()
            .putBoolean(KEY_IS_GUEST_MODE, isGuest)
            .apply()
    }

    fun isGuestMode(): Boolean = prefs.getBoolean(KEY_IS_GUEST_MODE, false)

    fun getAccessToken(): String? = cleanToken(prefs.getString(KEY_ACCESS_TOKEN, null))

    fun getRefreshToken(): String? = cleanToken(prefs.getString(KEY_REFRESH_TOKEN, null))

    fun getTokenScope(): String? = cleanToken(prefs.getString(KEY_TOKEN_SCOPE, null))

    fun getAccessTokenExpiresAt(): Long = prefs.getLong(KEY_TOKEN_EXPIRES_AT, 0L)

    fun getLastValidatedAt(): Long = prefs.getLong(KEY_LAST_VALIDATED_AT, 0L)

    fun hasAccessToken(): Boolean = !getAccessToken().isNullOrEmpty()

    fun shouldRefreshAccessToken(bufferMs: Long = REFRESH_SKEW_MS): Boolean {
        if (!hasAccessToken()) return true
        if (getTokenScope() == SCOPE_NON_EXPIRING) return false

        val now = System.currentTimeMillis()
        val expiresAt = getAccessTokenExpiresAt()
        if (expiresAt > 0L) {
            return now + bufferMs >= expiresAt
        }

        val timestamp = prefs.getLong(KEY_TOKEN_TIMESTAMP, 0L)
        val effectiveValidity = (DEFAULT_TOKEN_VALIDITY_MS - bufferMs).coerceAtLeast(0L)
        return timestamp == 0L || (now - timestamp) > effectiveValidity
    }

    fun isTokenExpired(): Boolean = shouldRefreshAccessToken(bufferMs = 0L)

    fun clearTokens() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_TOKEN_TIMESTAMP)
            .remove(KEY_TOKEN_EXPIRES_AT)
            .remove(KEY_TOKEN_SCOPE)
            .remove(KEY_LAST_VALIDATED_AT)
            .apply()
    }

    fun logout() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()

        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_TOKEN_TIMESTAMP)
            .remove(KEY_TOKEN_EXPIRES_AT)
            .remove(KEY_TOKEN_SCOPE)
            .remove(KEY_LAST_VALIDATED_AT)
            .putBoolean(KEY_IS_GUEST_MODE, false)
            .apply()
    }

    private fun resolveExpiresAt(now: Long, expiresInSeconds: Long?, scope: String?): Long {
        if (scope == SCOPE_NON_EXPIRING) return Long.MAX_VALUE

        val explicitExpiry = expiresInSeconds
            ?.takeIf { it > 0L }
            ?.let { seconds ->
                val safeSeconds = seconds.coerceAtMost((Long.MAX_VALUE - now) / 1000L)
                now + safeSeconds * 1000L
            }

        if (explicitExpiry != null) return explicitExpiry

        val existingExpiry = prefs.getLong(KEY_TOKEN_EXPIRES_AT, 0L)
        if (existingExpiry > now) return existingExpiry

        return now + DEFAULT_TOKEN_VALIDITY_MS
    }

    private fun cleanToken(value: String?): String? = value
        ?.trim()
        ?.trim('"')
        ?.takeIf { it.isNotBlank() && it != "null" }
}

