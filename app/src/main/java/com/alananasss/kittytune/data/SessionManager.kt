package com.alananasss.kittytune.data

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.alananasss.kittytune.utils.Config
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@SuppressLint("StaticFieldLeak")
object SessionManager {
    private const val TAG = "SessionManager"
    private const val REFRESH_INTERVAL = 20 * 60 * 1000L
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

    private var ghostWebView: WebView? = null
    @Volatile private var appContext: Context? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var keepAliveJob: Job? = null

    private val refreshLock = Any()
    private var pendingRefresh: CompletableDeferred<String?>? = null

    private val apiRefreshLock = Any()
    private var pendingApiRefresh: CompletableDeferred<String?>? = null

    private val authClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private val _isClientIdValid = MutableStateFlow(false)
    val isClientIdValid = _isClientIdValid.asStateFlow()

    private val _sessionReadyEvent = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val sessionReadyEvent = _sessionReadyEvent.asSharedFlow()

    private val _showCaptchaFlow = MutableStateFlow(false)
    val showCaptchaFlow = _showCaptchaFlow.asStateFlow()

    private var pendingLikeAction: (() -> Unit)? = null

    private class AndroidBridge {
        @JavascriptInterface
        fun requestCaptcha() {
            if (_showCaptchaFlow.value) return

            scope.launch {
                _showCaptchaFlow.value = true
            }
        }

        @JavascriptInterface
        fun requestAuthRefresh() {
            scope.launch {
                val context = appContext ?: return@launch
                awaitFreshAccessToken(context, force = true)
                retryPendingAction()
            }
        }

        @JavascriptInterface
        fun onLikeSuccess() {
            scope.launch {
                _showCaptchaFlow.value = false
                pendingLikeAction = null
            }
        }
    }

    fun attachGhost(webView: WebView, context: Context) {
        val safeContext = context.applicationContext
        appContext = safeContext
        ghostWebView = webView
        setupWebView(webView, safeContext)
        CookieManager.getInstance().flush()
        harvestStoredSession(safeContext)
        if (!_showCaptchaFlow.value) {
            reloadSession(forceRefresh = true)
        }
        startKeepAliveCycle(safeContext)
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun setupWebView(webView: WebView, context: Context) {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.userAgentString = Config.USER_AGENT

        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url?.toString() ?: ""
                if (url.contains("client_id=")) {
                    try {
                        val uri = Uri.parse(url)
                        val capturedId = uri.getQueryParameter("client_id")
                        if (!capturedId.isNullOrEmpty()) {
                            if (capturedId != Config.CLIENT_ID) {
                                Config.updateClientId(context, capturedId)
                            }
                            _isClientIdValid.value = true
                        }
                    } catch (e: Exception) { }
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                val token = harvestCookie(url, context, completePending = true)
                    ?: harvestStoredSession(context, completePending = true)

                if (token == null) {
                    completePendingRefresh(TokenManager(context).getAccessToken())
                }

                if (_showCaptchaFlow.value && url != null && (url.contains("/discover") || url.contains("/stream"))) {
                    scope.launch {
                        retryPendingAction()
                    }
                }
            }
        }
    }

    fun harvestStoredSession(context: Context, completePending: Boolean = false): String? {
        val safeContext = context.applicationContext
        appContext = safeContext
        return SESSION_COOKIE_URLS.firstNotNullOfOrNull { url ->
            harvestCookie(url, safeContext, completePending)
        }
    }

    private fun harvestCookie(url: String?, context: Context, completePending: Boolean): String? {
        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie(url)

        if (cookies != null && cookies.contains("oauth_token")) {
            val newToken = extractValue(cookies, "oauth_token")
            val refreshToken = extractValue(cookies, "refresh_token")
            if (!newToken.isNullOrEmpty()) {
                val tokenManager = TokenManager(context)
                val previousToken = tokenManager.getAccessToken()
                val previousRefreshToken = tokenManager.getRefreshToken()
                val shouldAcceptCookieAccessToken = previousToken.isNullOrEmpty() ||
                    completePending ||
                    tokenManager.shouldRefreshAccessToken(bufferMs = 0L)

                if (newToken != previousToken && shouldAcceptCookieAccessToken) {
                    tokenManager.saveTokens(newToken, refreshToken ?: previousRefreshToken)
                } else if (!refreshToken.isNullOrEmpty() && refreshToken != previousRefreshToken && !previousToken.isNullOrEmpty()) {
                    tokenManager.saveTokens(previousToken, refreshToken)
                } else if (completePending) {
                    tokenManager.markAccessTokenFresh()
                }

                if (completePending || (newToken != previousToken && shouldAcceptCookieAccessToken)) {
                    completePendingRefresh(if (shouldAcceptCookieAccessToken) newToken else previousToken)
                }

                _sessionReadyEvent.tryEmit(Unit)
                cookieManager.flush()
                return if (shouldAcceptCookieAccessToken) newToken else previousToken
            }
        }
        return null
    }

    private fun startKeepAliveCycle(context: Context) {
        if (keepAliveJob?.isActive == true) return

        keepAliveJob = scope.launch {
            while (true) {
                delay(REFRESH_INTERVAL)
                if (!_showCaptchaFlow.value) {
                    requestSessionRefresh(context, force = false)
                }
            }
        }
    }

    fun requestSessionRefresh(context: Context, force: Boolean = false) {
        val safeContext = context.applicationContext
        appContext = safeContext
        val tokenManager = TokenManager(safeContext)
        harvestStoredSession(safeContext)

        val shouldRefreshToken = tokenManager.shouldRefreshAccessToken()
        if (!_showCaptchaFlow.value && (force || shouldRefreshToken || !_isClientIdValid.value)) {
            scope.launch {
                val refreshedByApi = if (shouldRefreshToken) {
                    withTimeoutOrNull(SESSION_REFRESH_TIMEOUT_MS) {
                        refreshAccessTokenFromApi(safeContext)
                    }
                } else {
                    null
                }

                if (refreshedByApi == null || force || !_isClientIdValid.value) {
                    reloadSession(forceRefresh = force || shouldRefreshToken)
                }
            }
        }
    }

    fun reloadSession(forceRefresh: Boolean = false) {
        val targetUrl = buildSessionUrl(forceRefresh)
        val webView = ghostWebView

        if (webView == null) {
            appContext?.let { harvestStoredSession(it) }
            return
        }

        scope.launch {
            webView.loadUrl(targetUrl)
        }
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

        val cookieToken = harvestStoredSession(safeContext)
        val currentToken = cookieToken ?: tokenManager.getAccessToken()

        if (!force && !currentToken.isNullOrEmpty() && !tokenManager.shouldRefreshAccessToken()) {
            return currentToken
        }

        if (force && !staleToken.isNullOrEmpty() && !cookieToken.isNullOrEmpty() && cookieToken != staleToken) {
            return cookieToken
        }

        val refreshedByApi = withTimeoutOrNull(timeoutMs) {
            refreshAccessTokenFromApi(safeContext)
        }

        if (!refreshedByApi.isNullOrEmpty()) {
            return refreshedByApi
        }

        val webView = ghostWebView ?: return currentToken
        if (_showCaptchaFlow.value) return currentToken

        val refreshDeferred = getOrCreatePendingRefresh()
        withContext(Dispatchers.Main.immediate) {
            webView.loadUrl(buildSessionUrl(forceRefresh = true))
        }

        val refreshedToken = withTimeoutOrNull(timeoutMs) {
            refreshDeferred.await()
        }

        if (refreshedToken == null) {
            clearPendingRefresh(refreshDeferred)
        }

        return harvestStoredSession(safeContext) ?: refreshedToken ?: tokenManager.getAccessToken()
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

    private suspend fun refreshAccessTokenFromApi(context: Context): String? {
        val safeContext = context.applicationContext
        val tokenManager = TokenManager(safeContext)
        val refreshToken = tokenManager.getRefreshToken() ?: return null
        if (refreshToken == "ghost_refresh") return null

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

                    completePendingRefresh(accessToken)
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

    private fun buildSessionUrl(forceRefresh: Boolean): String {
        val baseUrl = "https://m.soundcloud.com/discover"
        return if (forceRefresh) {
            "$baseUrl?kittytune_refresh=${System.currentTimeMillis()}"
        } else {
            baseUrl
        }
    }

    private fun getOrCreatePendingRefresh(): CompletableDeferred<String?> = synchronized(refreshLock) {
        pendingRefresh?.takeIf { !it.isCompleted } ?: CompletableDeferred<String?>().also {
            pendingRefresh = it
        }
    }

    private fun completePendingRefresh(token: String?) {
        val deferred = synchronized(refreshLock) {
            pendingRefresh.also { pendingRefresh = null }
        }
        deferred?.complete(token)
    }

    private fun clearPendingRefresh(deferred: CompletableDeferred<String?>) {
        synchronized(refreshLock) {
            if (pendingRefresh === deferred) {
                pendingRefresh = null
            }
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

    fun retryPendingAction() {
        _showCaptchaFlow.value = false
        pendingLikeAction?.invoke()
    }

    fun cancelCaptcha() {
        _showCaptchaFlow.value = false
        pendingLikeAction = null
        reloadSession(forceRefresh = true)
    }

    fun syncLikeState(trackId: Long, isLike: Boolean, token: String, userId: Long) {
        val activeToken = appContext
            ?.let { TokenManager(it).getAccessToken() }
            ?.takeIf { it.isNotEmpty() }
            ?: token

        val clientId = Config.CLIENT_ID
        val url = "https://api-v2.soundcloud.com/users/$userId/track_likes/$trackId?client_id=$clientId&app_version=1771407416&app_locale=en"

        val method = if (isLike) "PUT" else "DELETE"
        val contentType = if (isLike) "'Content-Type': 'application/json; charset=utf-8'," else ""
        val bodyStr = if (isLike) "body: '{}'," else ""

        pendingLikeAction = { syncLikeState(trackId, isLike, activeToken, userId) }

        val js = """
            if (window.location.hostname.includes('captcha')) {
                AndroidBridge.requestCaptcha();
            } else {
                fetch('$url', {
                    method: '$method',
                    credentials: 'include',
                    headers: {
                        'Authorization': 'OAuth $activeToken',
                        'Accept': 'application/json',
                        $contentType
                    },
                    $bodyStr
                }).then(r => {
                    console.log('Like Sync Status: ' + r.status);
                    if (r.status === 401) {
                        AndroidBridge.requestAuthRefresh();
                    } else if (r.status === 403) {
                        AndroidBridge.requestCaptcha();
                    } else if (r.status === 200 || r.status === 201 || r.status === 204) {
                        AndroidBridge.onLikeSuccess();
                    }
                }).catch(e => {
                    console.error('Fetch Error:', e);
                    AndroidBridge.requestCaptcha();
                });
            }
        """.trimIndent()

        scope.launch {
            ghostWebView?.evaluateJavascript(js, null)
        }
    }
}
