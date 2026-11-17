package com.alananasss.kittytune.data.spotify

import android.util.Log
import com.alananasss.kittytune.data.network.ProxyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object SpotifyTokenManager {

    private const val TAG = "SpotifyTokenManager"
    private const val DEFAULT_BOOTSTRAP_URL = "https://open.spotify.com/embed/track/4uLU6hMCjMI75M1A2tKUQC"
    private const val FALLBACK_TOKEN_URL = "https://open.spotify.com/get_access_token?reason=transport&productType=web_player"
    private const val EXPIRY_SKEW_MS = 60_000L

    private val baseClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val client: OkHttpClient
        get() = ProxyManager.configureOkHttpClient(baseClient.newBuilder()).build()

    private val nextDataRegex = Pattern.compile(
        """<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""",
        Pattern.DOTALL
    )

    private val mutex = Mutex()
    private var cachedToken: String? = null
    private var tokenExpiryMs: Long = 0L

    suspend fun getValidAccessToken(): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            if (cachedToken != null && now < (tokenExpiryMs - EXPIRY_SKEW_MS)) {
                return@withLock cachedToken
            }

            // Bootstrap token from embed page
            val bootstrapped = bootstrapFromEmbed()
            if (bootstrapped != null) {
                cachedToken = bootstrapped.first
                tokenExpiryMs = bootstrapped.second
                Log.d(TAG, "Successfully bootstrapped anonymous Spotify access token (expires in ${(tokenExpiryMs - now) / 1000}s)")
                return@withLock cachedToken
            }

            // Try fallback token endpoint
            val fallback = bootstrapFromFallback()
            if (fallback != null) {
                cachedToken = fallback.first
                tokenExpiryMs = fallback.second
                Log.d(TAG, "Successfully acquired fallback Spotify access token (expires in ${(tokenExpiryMs - now) / 1000}s)")
                return@withLock cachedToken
            }

            Log.e(TAG, "Failed to obtain Spotify access token")
            return@withLock null
        }
    }

    fun invalidateToken() {
        cachedToken = null
        tokenExpiryMs = 0L
    }

    private fun bootstrapFromEmbed(): Pair<String, Long>? {
        try {
            val request = Request.Builder()
                .url(DEFAULT_BOOTSTRAP_URL)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Embed bootstrap HTTP error: ${response.code}")
                    return null
                }
                val html = response.body?.string() ?: return null
                val matcher = nextDataRegex.matcher(html)
                if (!matcher.find()) {
                    Log.w(TAG, "No __NEXT_DATA__ script found in embed HTML")
                    return null
                }
                val jsonStr = matcher.group(1) ?: return null
                val root = JSONObject(jsonStr)

                val props = root.optJSONObject("props") ?: return null
                val pageProps = props.optJSONObject("pageProps") ?: return null
                val state = pageProps.optJSONObject("state") ?: return null

                // Search for session inside state.settings.session or state.data.settings.session
                val settings = state.optJSONObject("settings")
                    ?: state.optJSONObject("data")?.optJSONObject("settings")
                    ?: return null

                val session = settings.optJSONObject("session") ?: return null
                val token = session.optString("accessToken")
                val expires = session.optLong("accessTokenExpirationTimestampMs")

                if (token.isNotBlank() && expires > 0L) {
                    return Pair(token, expires)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exception during embed bootstrap: ${e.message}")
        }
        return null
    }

    private fun bootstrapFromFallback(): Pair<String, Long>? {
        try {
            val request = Request.Builder()
                .url(FALLBACK_TOKEN_URL)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val token = json.optString("accessToken")
                val expires = json.optLong("accessTokenExpirationTimestampMs", System.currentTimeMillis() + 3600_000L)
                if (token.isNotBlank()) {
                    return Pair(token, expires)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exception during fallback token bootstrap: ${e.message}")
        }
        return null
    }
}
