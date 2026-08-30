package com.alananasss.kittytune.data.vk

import android.content.Context
import android.util.Log
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Persistent cookie jar for every VK host.
 *
 * MeridiusCore keeps a real `tough-cookie` jar backed by `cookies.json`, so every `Set-Cookie`
 * VK sends back (session rotation, `remixstid`, `remixsslsid`, anti-bot cookies, ...) is kept and
 * replayed on the next request. KittyTune previously rebuilt a three-cookie header by hand from
 * SharedPreferences, which is why sessions silently died a few minutes after login and why
 * restarting a VPN broke search: VK rotates the session cookie and the rotation was thrown away.
 *
 * VK serves the same session on the `vk.ru` and `vk.com` families, so the `remix*` session cookies
 * are mirrored between both families while every other cookie stays scoped to its own domain.
 */
class VkCookieJar(private val context: Context) : CookieJar {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** key = "domain|name|path" */
    private val store = ConcurrentHashMap<String, Cookie>()

    init {
        load()
        adoptLegacySession()
    }

    /**
     * Sessions created by builds that predate this jar live in `kittytune_vk_prefs` as three loose
     * values. They must be adopted here, at construction time, because nothing else guarantees a
     * call into [VkTokenManager] before the first request goes out — and a request without cookies
     * simply comes back logged out, which silently breaks search for everyone who upgrades.
     */
    private fun adoptLegacySession() {
        if (hasSession()) return
        val legacy = context.applicationContext
            .getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val remixsid = legacy.getString("vk_remixsid", null)
        val remixnsid = legacy.getString("vk_remixnsid", null)
        val remixdsid = legacy.getString("vk_remixdsid", null)
        if (remixsid.isNullOrBlank() && remixnsid.isNullOrBlank() && remixdsid.isNullOrBlank()) return

        Log.i(TAG, "Adopting a VK session stored by a previous version")
        seedSession(remixsid, remixnsid, remixdsid, legacy.getLong("vk_user_id", 0L))
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        var changed = false
        for (cookie in cookies) {
            // A single malformed cookie must never take the whole request down with it.
            try {
                if (persist(cookie)) changed = true
                if (isSessionCookie(cookie.name)) {
                    for (mirrored in mirrorsOf(cookie)) {
                        if (persist(mirrored)) changed = true
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Ignoring cookie ${cookie.name}: ${e.message}")
            }
        }
        if (changed) save()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val result = mutableListOf<Cookie>()
        var expired = false

        for (cookie in store.values) {
            if (cookie.expiresAt <= now) {
                expired = true
                continue
            }
            if (cookie.matches(url)) result.add(cookie)
        }

        if (expired) {
            store.entries.removeIf { it.value.expiresAt <= now }
            save()
        }

        // Longest path first, as required by RFC 6265.
        return result.sortedByDescending { it.path.length }
    }

    /**
     * Header value for the callers that build a request by hand (media players, ExoPlayer data
     * sources) instead of going through OkHttp.
     */
    fun cookieHeader(url: String): String {
        val parsed = url.toHttpUrlOrNull() ?: return ""
        return loadForRequest(parsed).joinToString("; ") { "${it.name}=${it.value}" }
    }

    fun value(name: String): String? = store.values
        .firstOrNull { it.name == name && it.expiresAt > System.currentTimeMillis() }
        ?.value

    fun hasSession(): Boolean =
        SESSION_COOKIES.any { !value(it).isNullOrBlank() }

    /**
     * Seeds the jar from a session that was captured before this jar existed (or from the manual
     * "paste your remixsid" screen), so upgrading users are not logged out.
     */
    fun seedSession(
        remixsid: String?,
        remixnsid: String? = null,
        remixdsid: String? = null,
        userId: Long = 0L
    ) {
        val seeds = buildMap {
            remixsid?.takeIf { it.isNotBlank() }?.let { put("remixsid", it) }
            remixnsid?.takeIf { it.isNotBlank() }?.let { put("remixnsid", it) }
            remixdsid?.takeIf { it.isNotBlank() }?.let { put("remixdsid", it) }
            if (userId > 0L) {
                put("remixmid", userId.toString())
                put("l", userId.toString())
            }
            put("remixlang", "0")
        }
        if (seeds.isEmpty()) return

        val expiry = System.currentTimeMillis() + SEED_LIFETIME_MS
        for (family in COOKIE_FAMILIES) {
            for ((name, value) in seeds) {
                try {
                    persist(
                        Cookie.Builder()
                            .domain(family)
                            .name(name)
                            .value(value)
                            .path("/")
                            .expiresAt(expiry)
                            .build()
                    )
                } catch (e: Exception) {
                    Log.d(TAG, "Could not seed $name for $family: ${e.message}")
                }
            }
        }
        save()
    }

    fun clear() {
        store.clear()
        prefs.edit().remove(KEY_COOKIES).apply()
    }

    private fun persist(cookie: Cookie): Boolean {
        val key = keyOf(cookie)
        if (cookie.value.isBlank() || cookie.expiresAt <= System.currentTimeMillis()) {
            // VK clears a cookie by sending an empty value with a past expiry. Auxiliary endpoints
            // do that when they refuse a request — `login.vk.ru/?act=web_token` and `api.vk.ru` both
            // answer that way for sessions that were not created through the QR/password flow — and
            // obeying it would throw away a web session that still serves the audio catalogue
            // perfectly. Only an explicit logout ([clear]) may drop the session cookies.
            if (isSessionCookie(cookie.name)) {
                Log.d(TAG, "Ignoring server-side clear of session cookie ${cookie.name}")
                return false
            }
            return store.remove(key) != null
        }
        val previous = store.put(key, cookie)
        return previous == null || previous.value != cookie.value || previous.expiresAt != cookie.expiresAt
    }

    /**
     * VK hands out the same session on `vk.ru` and `vk.com` (plus their `login.`/`id.` subdomains).
     * Copies a session cookie to the other family so a login performed on one domain also works on
     * the other, which is what makes the `vk.ru`-first / `vk.com`-fallback strategy usable.
     */
    private fun mirrorsOf(cookie: Cookie): List<Cookie> = COOKIE_FAMILIES
        .filter { it != cookie.domain }
        .map { family ->
            Cookie.Builder()
                .domain(family)
                .name(cookie.name)
                .value(cookie.value)
                .path("/")
                .expiresAt(cookie.expiresAt)
                .build()
        }

    private fun keyOf(cookie: Cookie) =
        "${cookie.domain}|${cookie.name}|${cookie.path}"

    private fun load() {
        val raw = prefs.getString(KEY_COOKIES, null) ?: return
        try {
            val array = JSONArray(raw)
            val now = System.currentTimeMillis()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val expiresAt = obj.optLong("expiresAt", 0L)
                if (expiresAt <= now) continue
                try {
                    val cookie = Cookie.Builder()
                        .domain(obj.optString("domain"))
                        .name(obj.optString("name"))
                        .value(obj.optString("value"))
                        .path(obj.optString("path", "/"))
                        .expiresAt(expiresAt)
                        .apply {
                            if (obj.optBoolean("secure")) secure()
                            if (obj.optBoolean("httpOnly")) httpOnly()
                            if (obj.optBoolean("hostOnly")) hostOnlyDomain(obj.optString("domain"))
                        }
                        .build()
                    store[keyOf(cookie)] = cookie
                } catch (e: Exception) {
                    // Skip just this entry rather than discarding the whole session.
                    Log.d(TAG, "Skipping stored cookie: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Dropping unreadable cookie store: ${e.message}")
            prefs.edit().remove(KEY_COOKIES).apply()
        }
    }

    private fun save() {
        try {
            val array = JSONArray()
            for (cookie in store.values) {
                array.put(
                    JSONObject().apply {
                        put("domain", cookie.domain)
                        put("name", cookie.name)
                        put("value", cookie.value)
                        put("path", cookie.path)
                        put("expiresAt", cookie.expiresAt)
                        put("secure", cookie.secure)
                        put("httpOnly", cookie.httpOnly)
                        put("hostOnly", cookie.hostOnly)
                    }
                )
            }
            prefs.edit().putString(KEY_COOKIES, array.toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Could not persist cookies: ${e.message}")
        }
    }

    private fun isSessionCookie(name: String) =
        name.startsWith("remix") || name == "l" || name == "p"

    companion object {
        private const val TAG = "VkCookieJar"
        private const val PREFS_NAME = "kittytune_vk_cookies"
        private const val LEGACY_PREFS = "kittytune_vk_prefs"
        private const val KEY_COOKIES = "cookies_v1"
        private const val SEED_LIFETIME_MS = 365L * 24 * 60 * 60 * 1000

        private val COOKIE_FAMILIES = listOf("vk.ru", "vk.com")
        private val SESSION_COOKIES = listOf("remixsid", "remixnsid", "remixdsid")
    }
}
