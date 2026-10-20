package com.alananasss.kittytune.data

import android.annotation.SuppressLint
import android.content.Context
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

    private val _isClientIdValid = MutableStateFlow(true)
    val isClientIdValid = _isClientIdValid.asStateFlow()

    private val _sessionReadyEvent = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val sessionReadyEvent = _sessionReadyEvent.asSharedFlow()

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

        if (force && !staleToken.isNullOrEmpty() && !currentToken.isNullOrEmpty() && currentToken != staleToken) {
            return currentToken
        }

        val refreshedByApi = withTimeoutOrNull(timeoutMs) {
            refreshAccessTokenFromApi(safeContext)
        }

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



    private suspend fun refreshAccessTokenFromApi(context: Context): String? {
        val safeContext = context.applicationContext
        val tokenManager = TokenManager(safeContext)
        val refreshToken = tokenManager.getRefreshToken() ?: return null

        var shouldStartRefresh = false
        val refreshDeferred = synchronized(apiRefreshLock) {
            pendingApiRefresh?.takeIf { !it.isCompleted } ?: CompletableDeferred<String?>().also {
                pendingApiRefresh = it
                shouldStartRefresh = true
            }
        }

        if (shouldStartRefresh) {
            scope.launch(Dispatchers.IO) {
                val refreshedToken = executeRefreshTokenRequest(safeContext, refreshToken)
                refreshDeferred.complete(refreshedToken)
                synchronized(apiRefreshLock) {
                    if (pendingApiRefresh === refreshDeferred) {
                        pendingApiRefresh = null
                    }
                }
            }
        }

        return refreshDeferred.await()
    }

    private fun executeRefreshTokenRequest(context: Context, refreshToken: String): String? {
        return oauthTokenUrls().firstNotNullOfOrNull { tokenUrl ->
            val request = Request.Builder()
                .url(tokenUrl)
                .header("User-Agent", Config.USER_AGENT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Origin", "https://soundcloud.com")
                .header("Referer", "https://soundcloud.com/")
                .header("Authorization", Config.OFFICIAL_CLIENT_SIGNATURE)
                .post(
                    FormBody.Builder()
                        .add("grant_type", "refresh_token")
                        .add("client_id", Config.OFFICIAL_CLIENT_ID)
                        .add("refresh_token", refreshToken)
                        .build()
                )
                .build()

            runCatching {
                authClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null

                    val body = response.body?.string().orEmpty()
                    val tokenResponse = gson.fromJson(body, OAuthTokenResponse::class.java)
                    val accessToken = tokenResponse.accessToken.cleanOAuthValue() ?: return@use null
                    val nextRefreshToken = tokenResponse.refreshToken.cleanOAuthValue() ?: refreshToken

                    TokenManager(context).saveTokens(
                        accessToken = accessToken,
                        refreshToken = nextRefreshToken,
                        expiresInSeconds = tokenResponse.expiresInSeconds,
                        scope = tokenResponse.scope
                    )

                    _sessionReadyEvent.tryEmit(Unit)
                    accessToken
                }
            }.getOrNull()
        }
    }

    private fun oauthTokenUrls(): List<String> = listOf(
        "${AUTH_API_BASE_URL.trimEnd('/')}/oauth/token",
        "${Config.BASE_URL.trimEnd('/')}/oauth/token"
    ).distinct()



    private fun extractValue(cookies: String, key: String): String? {
        return cookies.split(";").map { it.trim() }.find { it.startsWith("$key=") }
            ?.substringAfter("$key=")?.replace("\"", "")?.trim()
    }

    private fun String?.cleanOAuthValue(): String? = this
        ?.trim()
        ?.trim('"')
        ?.takeIf { it.isNotBlank() && it != "null" }
}
