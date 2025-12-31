package com.alananasss.kittytune.data

import android.content.Context
import android.content.SharedPreferences

class TokenManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("soundtune_auth", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_TIMESTAMP = "token_timestamp"
        private const val KEY_IS_GUEST_MODE = "is_guest_mode"
        // consider token stale after 30 mins
        private const val TOKEN_VALIDITY_MS = 30 * 60 * 1000L
    }

    fun saveTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putLong(KEY_TOKEN_TIMESTAMP, System.currentTimeMillis())
            .putBoolean(KEY_IS_GUEST_MODE, false) // not a guest anymore if we have tokens
            .apply()
    }

    fun setGuestMode(isGuest: Boolean) {
        prefs.edit()
            .putBoolean(KEY_IS_GUEST_MODE, isGuest)
            .apply()
    }

    fun isGuestMode(): Boolean = prefs.getBoolean(KEY_IS_GUEST_MODE, false)

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)
    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun isTokenExpired(): Boolean {
        val timestamp = prefs.getLong(KEY_TOKEN_TIMESTAMP, 0)
        val now = System.currentTimeMillis()
        // check if timestamp is missing or too old
        return timestamp == 0L || (now - timestamp) > TOKEN_VALIDITY_MS
    }

    fun clearTokens() {
        prefs.edit().clear().apply()
    }

    fun logout() {
        // clean slate
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_TOKEN_TIMESTAMP)
            .putBoolean(KEY_IS_GUEST_MODE, false) // reset guest flag to force a choice/login again
            .apply()
    }
}