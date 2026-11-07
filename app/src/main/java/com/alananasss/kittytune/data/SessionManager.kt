package com.alananasss.kittytune.data

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import com.alananasss.kittytune.utils.Config
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

@SuppressLint("StaticFieldLeak")
object SessionManager {
    private const val TAG = "SessionManager"
    private const val SESSION_REFRESH_TIMEOUT_MS = 12_000L
    private const val AUTH_API_BASE_URL = "https://api-auth.soundcloud.com/"

    private val SESSION_COOKIE_URLS = listOf(
        "https://soundcloud.com",
        "https://m.soundcloud.com",
        "https://api-v2.soundcloud.com",
        "https://api-mobile.soundcloud.com",
        "https://api-auth.soundcloud.com",
        "https://secure.soundcloud.com"
    )

    private data class OAuthTokenResponse(
        @SerializedName("access_token") val accessToken: String?,
        @SerializedName("refresh_token") val refreshToken: String?,
        @SerializedName("expires_in") val expiresInSeconds: Long?,
        @SerializedName("scope") val scope: String?
    )

    private sealed interface TokenResult {
        data class Issued(val tokens: OAuthTokenResponse) : TokenResult
        data class Refused(val code: Int, val body: String) : TokenResult
        data object Unreachable : TokenResult
    }

    @Volatile private var appContext: Context? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val apiRefreshLock = Any()
    private var pendingApiRefresh: CompletableDeferred<String?>? = null

    private val baseAuthClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val authClient: OkHttpClient
        get() = com.alananasss.kittytune.data.network.ProxyManager.configureOkHttpClient(baseAuthClient.newBuilder()).build()

    private val gson = com.alananasss.kittytune.utils.AppUtils.gson

    private val _sessionEndedEvent = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)

    /** The server ended the session; the UI goes back to the welcome screen. */
    val sessionEndedEvent = _sessionEndedEvent.asSharedFlow()

    private val _isClientIdValid = MutableStateFlow(true)
    val isClientIdValid = _isClientIdValid.asStateFlow()

    private val _sessionReadyEvent = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val sessionReadyEvent = _sessionReadyEvent.asSharedFlow()

    /** Called once per process: moves a legacy session onto the expiring client. */
    fun start(context: Context) {
        val safeContext = context.applicationContext
        appContext = safeContext
        scope.launch(Dispatchers.IO) {
            singleFlight { migrateLegacySession(safeContext) }
        }
    }

    fun harvestStoredSession(context: Context): String? {
        val safeContext = context.applicationContext
        appContext = safeContext
        val tokenManager = TokenManager(safeContext)
        val token = tokenManager.getAccessToken()
        if (!token.isNullOrEmpty()) {
            return token
        }

        var cookieToken: String? = null
        SESSION_COOKIE_URLS.forEach { url ->
            if (cookieToken == null) {
                cookieToken = readCookieToken(url)
            }
        }

        if (!cookieToken.isNullOrEmpty()) {
            tokenManager.saveTokens(cookieToken)
            _sessionReadyEvent.tryEmit(Unit)
            return cookieToken
        }

        return null
    }

    private fun readCookieToken(url: String): String? {
        val cookies = CookieManager.getInstance().getCookie(url) ?: return null
        if (cookies.contains("oauth_token")) {
            return extractValue(cookies, "oauth_token")
        }
        return null
    }

    fun requestSessionRefresh(context: Context, force: Boolean = false) {
        val safeContext = context.applicationContext
        appContext = safeContext
        val tokenManager = TokenManager(safeContext)
        if (tokenManager.isGuestMode()) return

        val shouldRefreshToken = tokenManager.shouldRefreshAccessToken()
        if (force || shouldRefreshToken) {
            scope.launch {
                refreshAccessTokenFromApi(safeContext)
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun reloadSession(forceRefresh: Boolean = false) {
        // No-op: ghost WebView removed.
    }

    /**
     * [staleToken] is the token the server just refused, when there is one. The refresh runs in
     * [scope], so a caller's timeout only stops the wait: a rotated token still gets saved.
     */
    suspend fun awaitFreshAccessToken(
        context: Context,
        staleToken: String? = null,
        force: Boolean = false,
        timeoutMs: Long = SESSION_REFRESH_TIMEOUT_MS
    ): String? {
        val safeContext = context.applicationContext
        appContext = safeContext

        val tokenManager = TokenManager(safeContext)
        if (tokenManager.isGuestMode()) return null

        val currentToken = tokenManager.getAccessToken() ?: harvestStoredSession(safeContext)

        if (!force && !currentToken.isNullOrEmpty() && !tokenManager.shouldRefreshAccessToken()) {
            return currentToken
        }

        val refreshedByApi = withTimeoutOrNull(timeoutMs) {
            refreshAccessTokenFromApi(safeContext, staleToken)
        }

        // Read back rather than falling back to currentToken: the session may have just ended.
        return refreshedByApi ?: tokenManager.getAccessToken()
    }

    fun refreshSessionBlocking(
        context: Context,
        staleToken: String? = null,
        timeoutMs: Long = SESSION_REFRESH_TIMEOUT_MS
    ): String? = runBlocking {
        awaitFreshAccessToken(
            context = context,
            staleToken = staleToken,
            force = true,
            timeoutMs = timeoutMs
        )
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun awaitDataDomeChallenge(
        context: Context,
        captchaUrl: String,
        requestUrl: String? = null,
        timeoutMs: Long = 120_000L
    ): Boolean = false

    fun extractDataDomeCaptchaUrl(body: String?): String? {
        if (body.isNullOrBlank()) return null

        val jsonUrl = runCatching {
            JSONObject(body).optString("url")
        }.getOrNull()

        return jsonUrl
            ?.takeIf { it.contains("captcha-delivery.com") }
            ?: Regex("\"url\"\\s*:\\s*\"([^\"]+)\"")
                .find(body)
                ?.groupValues
                ?.getOrNull(1)
                ?.replace("\\/", "/")
                ?.takeIf { it.contains("captcha-delivery.com") }
    }



    private suspend fun refreshAccessTokenFromApi(context: Context, staleToken: String? = null): String? {
        val safeContext = context.applicationContext
        return singleFlight { refreshSession(safeContext, staleToken) }
    }

    /**
     * One token operation at a time: refresh tokens rotate, so a second request carrying the same
     * one would be refused. Whatever the operation needs is read inside [block], never before.
     */
    private suspend fun singleFlight(block: () -> String?): String? {
        var shouldStart = false
        val deferred = synchronized(apiRefreshLock) {
            pendingApiRefresh?.takeIf { !it.isCompleted } ?: CompletableDeferred<String?>().also {
                pendingApiRefresh = it
                shouldStart = true
            }
        }

        if (shouldStart) {
            scope.launch(Dispatchers.IO) {
                try {
                    deferred.complete(block())
                } finally {
                    deferred.complete(null)
                    synchronized(apiRefreshLock) {
                        if (pendingApiRefresh === deferred) pendingApiRefresh = null
                    }
                }
            }
        }

        return deferred.await()
    }

    private fun refreshSession(context: Context, staleToken: String?): String? {
        val tokenManager = TokenManager(context)
        val accessToken = tokenManager.getAccessToken()
        // A burst of 401s all carry the same old token: only the first one may refresh.
        if (staleToken != null && accessToken != null && accessToken != staleToken) return accessToken

        val refreshToken = tokenManager.getRefreshToken()
        if (refreshToken != null) return redeemRefreshToken(context, refreshToken)

        // Nothing to redeem. Only a token the server refused can end the session here, and one
        // endpoint refusing it proves little (a stream can 401 for reasons of its own), so ask /me.
        if (accessToken != null && accessToken == staleToken && isRejectedByServer(accessToken)) {
            endSession(context, "access token rejected, no refresh token")
        }
        return null
    }

    private fun redeemRefreshToken(context: Context, refreshToken: String): String? {
        val tokenManager = TokenManager(context)
        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", Config.OFFICIAL_CLIENT_ID)
            .add("refresh_token", refreshToken)
            .build()

        return when (val result = requestTokens(form)) {
            is TokenResult.Issued ->
                // A logout during the request must not bring the session back.
                if (tokenManager.getRefreshToken() == refreshToken) {
                    saveIssuedTokens(tokenManager, result.tokens, refreshToken)
                } else {
                    null
                }
            is TokenResult.Refused -> {
                Log.w(TAG, "Token refresh refused (HTTP ${result.code})")
                if (tokenManager.getRefreshToken() == refreshToken &&
                    OAuthTokens.isRefreshTokenDead(result.code, result.body)
                ) {
                    endSession(context, "refresh token invalidated")
                }
                null
            }
            TokenResult.Unreachable -> null
        }
    }

    /**
     * Moves a non-expiring token onto the expiring client, like the official app's
     * TokenExchangeCoordinator. Sessions logged in with the legacy client since the credentials
     * change of 2026-10-29 hold such a token and no refresh token. A failure keeps the token as is;
     * the next launch retries.
     */
    private fun migrateLegacySession(context: Context): String? {
        val tokenManager = TokenManager(context)
        val accessToken = tokenManager.getAccessToken() ?: return null
        if (tokenManager.isGuestMode() || tokenManager.hasExpiringSession()) return null

        val form = FormBody.Builder()
            .add("grant_type", "token_exchange")
            .add("token", accessToken)
            .add("client_id", Config.LEGACY_CLIENT_ID)
            .add("client_secret", Config.LEGACY_CLIENT_SECRET)
            .add("issue_for_client_id", Config.OFFICIAL_CLIENT_ID)
            .build()

        return when (val result = requestTokens(form)) {
            is TokenResult.Issued ->
                if (tokenManager.getAccessToken() == accessToken) {
                    saveIssuedTokens(tokenManager, result.tokens, null)
                } else {
                    null
                }
            is TokenResult.Refused -> {
                Log.w(TAG, "Token exchange refused (HTTP ${result.code})")
                null
            }
            TokenResult.Unreachable -> null
        }
    }

    /** Like the official app: no Authorization header, api-auth only. The other headers are ours. */
    private fun requestTokens(form: FormBody): TokenResult {
        val request = Request.Builder()
            .url("${AUTH_API_BASE_URL}oauth/token")
            .header("User-Agent", Config.USER_AGENT)
            .header("Accept", "application/json")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Origin", "https://soundcloud.com")
            .header("Referer", "https://soundcloud.com/")
            .post(form)
            .build()

        return try {
            authClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) return TokenResult.Refused(response.code, body)

                val tokens = gson.fromJson(body, OAuthTokenResponse::class.java)
                if (tokens?.accessToken.cleanOAuthValue() == null) TokenResult.Unreachable else TokenResult.Issued(tokens)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Token request failed: ${e.message}")
            TokenResult.Unreachable
        }
    }

    private fun saveIssuedTokens(
        tokenManager: TokenManager,
        tokens: OAuthTokenResponse,
        currentRefreshToken: String?
    ): String? {
        val accessToken = tokens.accessToken.cleanOAuthValue() ?: return null
        tokenManager.saveTokens(
            accessToken = accessToken,
            refreshToken = tokens.refreshToken.cleanOAuthValue() ?: currentRefreshToken,
            expiresInSeconds = tokens.expiresInSeconds,
            scope = tokens.scope
        )
        _sessionReadyEvent.tryEmit(Unit)
        return accessToken
    }

    private fun isRejectedByServer(accessToken: String): Boolean {
        val request = Request.Builder()
            .url("${Config.BASE_URL}me?client_id=${Config.OFFICIAL_CLIENT_ID}")
            .header("User-Agent", Config.USER_AGENT)
            .header("Accept", "application/json")
            .header("Authorization", "OAuth $accessToken")
            .build()

        return runCatching {
            authClient.newCall(request).execute().use { it.code == 401 }
        }.getOrDefault(false)
    }

    /** Sends the user back to the welcome screen instead of leaving a dead session looking alive. */
    private fun endSession(context: Context, reason: String) {
        Log.w(TAG, "SoundCloud session ended: $reason")
        // Right away: requests already queued must see the session gone, not end it again.
        TokenManager(context).clearTokens()
        // scope runs on Main: logout() goes through CookieManager and the repositories' UI state.
        scope.launch {
            TokenManager(context).logout()
            _sessionEndedEvent.tryEmit(Unit)
        }
    }

    private fun extractValue(cookies: String, key: String): String? {
        return cookies.split(";").map { it.trim() }.find { it.startsWith("$key=") }
            ?.substringAfter("$key=")?.replace("\"", "")?.trim()
    }

    private fun String?.cleanOAuthValue(): String? = this
        ?.trim()
        ?.trim('"')
        ?.takeIf { it.isNotBlank() && it != "null" }
}
