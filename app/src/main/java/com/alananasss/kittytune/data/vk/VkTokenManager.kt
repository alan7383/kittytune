package com.alananasss.kittytune.data.vk

import android.content.Context
import android.content.SharedPreferences

class VkTokenManager(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val cookieJar: VkCookieJar get() = VkHttp.cookieJar(appContext)

    var remixsid: String?
        get() = prefs.getString(KEY_REMIXSID, null)
        set(value) = prefs.edit().putString(KEY_REMIXSID, value).apply()

    var remixnsid: String?
        get() = prefs.getString(KEY_REMIXNSID, null)
        set(value) = prefs.edit().putString(KEY_REMIXNSID, value).apply()

    var remixdsid: String?
        get() = prefs.getString(KEY_REMIXDSID, null)
        set(value) = prefs.edit().putString(KEY_REMIXDSID, value).apply()

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) = prefs.edit()
            .putString(KEY_ACCESS_TOKEN, value)
            .putLong(KEY_TOKEN_ISSUED_AT, if (value.isNullOrBlank()) 0L else System.currentTimeMillis())
            .apply()

    /** Stores a freshly minted token together with its issue time. */
    fun saveToken(token: String) {
        accessToken = token
    }

    /**
     * VK web tokens are bound to the IP they were minted for and expire quickly, so anything older
     * than [TOKEN_TTL_MS] is re-requested before use. This is what keeps search alive after the
     * session has been open for a while or after a VPN reconnect changes the exit IP.
     */
    fun isTokenStale(): Boolean {
        val issuedAt = prefs.getLong(KEY_TOKEN_ISSUED_AT, 0L)
        if (issuedAt == 0L) return true
        return System.currentTimeMillis() - issuedAt > TOKEN_TTL_MS
    }

    fun invalidateToken() {
        prefs.edit().putLong(KEY_TOKEN_ISSUED_AT, 0L).apply()
    }

    var userId: Long
        get() = prefs.getLong(KEY_USER_ID, 0L)
        set(value) = prefs.edit().putLong(KEY_USER_ID, value).apply()

    var userFirstName: String
        get() = prefs.getString(KEY_USER_FIRST_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_FIRST_NAME, value).apply()

    var userLastName: String
        get() = prefs.getString(KEY_USER_LAST_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_LAST_NAME, value).apply()

    var userPhoto: String?
        get() = prefs.getString(KEY_USER_PHOTO, null)
        set(value) = prefs.edit().putString(KEY_USER_PHOTO, value).apply()

    var userScreenName: String?
        get() = prefs.getString(KEY_USER_SCREEN_NAME, null)
        set(value) = prefs.edit().putString(KEY_USER_SCREEN_NAME, value).apply()

    var includeInSearch: Boolean
        get() = prefs.getBoolean(KEY_INCLUDE_SEARCH, true)
        set(value) = prefs.edit().putBoolean(KEY_INCLUDE_SEARCH, value).apply()

    var includeInRecommendations: Boolean
        get() = prefs.getBoolean(KEY_INCLUDE_RECOMMENDATIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_INCLUDE_RECOMMENDATIONS, value).apply()

    fun isLoggedIn(): Boolean {
        val hasCookie = cookieJar.hasSession() || !remixsid.isNullOrBlank() || !remixnsid.isNullOrBlank()
        val hasToken = !accessToken.isNullOrBlank()
        return (hasCookie || hasToken) && userId != 0L
    }

    /**
     * Cookie header for consumers that bypass OkHttp (ExoPlayer data sources, WebViews).
     *
     * The values come from the shared jar so every cookie VK rotated in is included; the legacy
     * SharedPreferences copies are only used to seed the jar once after an app update.
     */
    fun getCookieHeader(url: String = "https://vk.ru/"): String {
        if (!cookieJar.hasSession()) migrateLegacyCookies()
        val header = cookieJar.cookieHeader(url)
        if (header.isNotBlank()) return header

        // Nothing in the jar at all: fall back to the flat values so a manually pasted remixsid works.
        return buildList {
            if (userId > 0L) {
                add("remixmid=$userId")
                add("l=$userId")
            }
            remixsid?.takeIf { it.isNotBlank() }?.let { add("remixsid=$it") }
            remixnsid?.takeIf { it.isNotBlank() }?.let { add("remixnsid=$it") }
            remixdsid?.takeIf { it.isNotBlank() }?.let { add("remixdsid=$it") }
            add("remixlang=0")
        }.joinToString("; ")
    }

    /** Pushes a session captured before [VkCookieJar] existed into the jar. */
    fun migrateLegacyCookies() {
        if (remixsid.isNullOrBlank() && remixnsid.isNullOrBlank() && remixdsid.isNullOrBlank()) return
        cookieJar.seedSession(remixsid, remixnsid, remixdsid, userId)
    }

    fun saveSession(
        remixsid: String?,
        remixnsid: String? = null,
        remixdsid: String? = null,
        userId: Long,
        firstName: String = "",
        lastName: String = "",
        photoUrl: String? = null,
        accessToken: String? = null,
        screenName: String? = null
    ) {
        val editor = prefs.edit()
            .putString(KEY_REMIXSID, remixsid)
            .putString(KEY_REMIXNSID, remixnsid)
            .putString(KEY_REMIXDSID, remixdsid)
            .putLong(KEY_USER_ID, userId)

        if (firstName.isNotBlank() && isValidDisplayName(firstName)) {
            editor.putString(KEY_USER_FIRST_NAME, firstName)
        }
        if (lastName.isNotBlank() && isValidDisplayName(lastName)) {
            editor.putString(KEY_USER_LAST_NAME, lastName)
        }
        if (!photoUrl.isNullOrBlank()) {
            editor.putString(KEY_USER_PHOTO, photoUrl)
        }
        if (!accessToken.isNullOrBlank()) {
            editor.putString(KEY_ACCESS_TOKEN, accessToken)
            // Stamp the issue time here too, otherwise every later call treats the token as stale
            // and wastes a round trip trying to re-mint it.
            editor.putLong(KEY_TOKEN_ISSUED_AT, System.currentTimeMillis())
        }
        if (!screenName.isNullOrBlank() && isValidDisplayName(screenName)) {
            editor.putString(KEY_USER_SCREEN_NAME, screenName)
        }
        editor.apply()
        // Keep the shared jar authoritative: seeding it here means search, artist pages and stream
        // resolution all reuse the exact session the login flow just established.
        cookieJar.seedSession(remixsid, remixnsid, remixdsid, userId)
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_REMIXSID)
            .remove(KEY_REMIXNSID)
            .remove(KEY_REMIXDSID)
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_TOKEN_ISSUED_AT)
            .remove(KEY_USER_ID)
            .remove(KEY_USER_FIRST_NAME)
            .remove(KEY_USER_LAST_NAME)
            .remove(KEY_USER_PHOTO)
            .remove(KEY_USER_SCREEN_NAME)
            .apply()
        cookieJar.clear()
    }

    fun getUser(): VkUser? {
        if (!isLoggedIn()) return null
        val fName = userFirstName.takeIf { isValidDisplayName(it) } ?: ""
        val lName = userLastName.takeIf { isValidDisplayName(it) } ?: ""
        val sName = userScreenName?.takeIf { isValidDisplayName(it) }
        val fallbackName = when {
            fName.isNotBlank() || lName.isNotBlank() -> fName
            !sName.isNullOrBlank() -> sName
            userId > 0L -> "id$userId"
            else -> "VK User"
        }

        return VkUser(
            id = userId,
            firstName = if (fName.isNotBlank() || lName.isNotBlank()) fName else fallbackName,
            lastName = lName,
            photoMax = userPhoto,
            screenName = sName
        )
    }

    companion object {
        private const val PREFS_NAME = "kittytune_vk_prefs"
        private const val KEY_REMIXSID = "vk_remixsid"
        private const val KEY_REMIXNSID = "vk_remixnsid"
        private const val KEY_REMIXDSID = "vk_remixdsid"
        private const val KEY_ACCESS_TOKEN = "vk_access_token"
        private const val KEY_TOKEN_ISSUED_AT = "vk_token_issued_at"
        private const val KEY_USER_ID = "vk_user_id"
        private const val KEY_USER_FIRST_NAME = "vk_user_first_name"
        private const val KEY_USER_LAST_NAME = "vk_user_last_name"
        private const val KEY_USER_PHOTO = "vk_user_photo"
        private const val KEY_USER_SCREEN_NAME = "vk_user_screen_name"
        private const val KEY_INCLUDE_SEARCH = "vk_include_search"
        private const val KEY_INCLUDE_RECOMMENDATIONS = "vk_include_recommendations"

        /** VK web tokens are re-minted well before their real expiry. */
        private const val TOKEN_TTL_MS = 10L * 60 * 1000

        fun isValidDisplayName(name: String?): Boolean {
            if (name.isNullOrBlank()) return false
            val lower = name.lowercase().trim()
            val blocked = listOf(
                "vk", "vk.com", "вконтакте", "у вас слишком много запросов",
                "too many requests", "captcha", "ошибка", "error", "security check",
                "403", "404", "500", "forbidden", "unauthorized", "login", "войти", "вход",
                "vkontakte user"
            )
            return blocked.none { lower == it || lower.startsWith("$it ") || lower.endsWith(" $it") }
        }
    }
}
