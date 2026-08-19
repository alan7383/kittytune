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
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

@SuppressLint("StaticFieldLeak")
object SessionManager {
    private const val TAG = "SessionManager"
    private const val REFRESH_INTERVAL = 20 * 60 * 1000L
    private const val SESSION_REFRESH_TIMEOUT_MS = 12_000L
    private const val AUTH_API_BASE_URL = "https://api-auth.soundcloud.com/"
    private const val DATADOME_PREFS = "soundcloud_datadome"
    private const val KEY_DATADOME_COOKIE = "datadome_cookie"

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

    private val dataDomeLock = Any()
    private var pendingDataDomeChallenge: CompletableDeferred<Boolean>? = null
    @Volatile private var dataDomeRequestUrl: String? = null
    @Volatile private var dataDomeCaptchaUrl: String? = null

    private val authClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = com.alananasss.kittytune.utils.AppUtils.gson

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

    private class DataDomeBridge {
        @JavascriptInterface
        fun onCaptchaSuccess(cookie: String?) {
            scope.launch {
                completeDataDomeChallenge(cookie, solved = true)
            }
        }

        @JavascriptInterface
        fun onAdditionalChallengeRedirection(responseType: Int) {
            // DataDome uses this to toggle challenge visibility for some flows.
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
        webView.addJavascriptInterface(DataDomeBridge(), "android")

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

        var token: String? = null
        SESSION_COOKIE_URLS.forEach { url ->
            harvestDataDomeCookie(url, safeContext)
            if (token == null) {
                token = harvestCookie(url, safeContext, completePending)
            }
        }
        return token
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

    private fun harvestDataDomeCookie(url: String?, context: Context): String? {
        val cookie = readDataDomeCookie(url) ?: return null
        saveDataDomeCookie(context, cookie)
        return cookie
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

    suspend fun awaitDataDomeChallenge(
        context: Context,
        captchaUrl: String,
        requestUrl: String? = null,
        timeoutMs: Long = 120_000L
    ): Boolean {
        val safeContext = context.applicationContext
        appContext = safeContext

        val targetRequestUrl = requestUrl
            ?: extractDataDomeReferer(captchaUrl)
            ?: "https://api-mobile.soundcloud.com"

        dataDomeRequestUrl = targetRequestUrl
        dataDomeCaptchaUrl = captchaUrl

        val deferred = synchronized(dataDomeLock) {
            pendingDataDomeChallenge?.takeIf { !it.isCompleted }
                ?: CompletableDeferred<Boolean>().also { pendingDataDomeChallenge = it }
        }

        try {
            withContext(Dispatchers.Main.immediate) {
                val webView = ghostWebView
                if (webView == null) {
                    deferred.complete(false)
                    return@withContext
                }

                seedDataDomeCookieForChallenge(safeContext, captchaUrl, targetRequestUrl)
                _showCaptchaFlow.value = true
                webView.loadUrl(
                    captchaUrl,
                    mapOf("Accept-Language" to com.alananasss.kittytune.utils.LocaleUtils.getAcceptLanguage(safeContext))
                )
            }

            val solved = withTimeoutOrNull(timeoutMs) {
                deferred.await()
            } ?: false

            if (!solved) {
                synchronized(dataDomeLock) {
                    if (pendingDataDomeChallenge === deferred) {
                        pendingDataDomeChallenge = null
                    }
                }
            }

            return solved
        } catch (e: Exception) {
            e.printStackTrace()
            synchronized(dataDomeLock) {
                if (pendingDataDomeChallenge === deferred) {
                    pendingDataDomeChallenge = null
                }
            }
            return false
        }
    }

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

    fun getStoredDataDomeCookie(context: Context): String? {
        return context.applicationContext
            .getSharedPreferences(DATADOME_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DATADOME_COOKIE, null)
            ?.let(::normalizeDataDomeCookie)
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

    private fun extractDataDomeReferer(captchaUrl: String): String? {
        return runCatching {
            Uri.parse(captchaUrl).getQueryParameter("referer")
        }.getOrNull()?.takeIf { it.startsWith("http") }
    }

    private fun seedDataDomeCookieForChallenge(context: Context, captchaUrl: String, requestUrl: String) {
        val cookie = getStoredDataDomeCookie(context)
            ?: readDataDomeCookie(requestUrl)
            ?: readDataDomeCookie(captchaUrl)

        if (cookie != null) {
            setDataDomeCookie(context, cookie, listOf(captchaUrl, requestUrl))
        }
    }

    private fun completeDataDomeChallenge(cookie: String?, solved: Boolean) {
        val context = appContext

        if (context != null) {
            val resolvedCookie = normalizeDataDomeCookie(cookie)
                ?: readDataDomeCookie(dataDomeRequestUrl)
                ?: readDataDomeCookie(dataDomeCaptchaUrl)
                ?: getStoredDataDomeCookie(context)

            if (resolvedCookie != null) {
                setDataDomeCookie(
                    context = context,
                    cookie = cookie ?: resolvedCookie,
                    urls = listOf(
                        dataDomeRequestUrl,
                        dataDomeCaptchaUrl,
                        "https://api-mobile.soundcloud.com",
                        "https://api-v2.soundcloud.com",
                        "https://soundcloud.com",
                        "https://m.soundcloud.com"
                    )
                )
            }
        }

        _showCaptchaFlow.value = false

        val deferred = synchronized(dataDomeLock) {
            pendingDataDomeChallenge.also { pendingDataDomeChallenge = null }
        }
        deferred?.complete(solved)
    }

    private fun setDataDomeCookie(context: Context, cookie: String, urls: List<String?>) {
        val normalizedCookie = normalizeDataDomeCookie(cookie) ?: return
        saveDataDomeCookie(context, normalizedCookie)

        val cookieManager = CookieManager.getInstance()
        urls.filterNotNull().filter { it.startsWith("http") }.distinct().forEach { url ->
            cookieManager.setCookie(url, cookie)
            if (cookie != normalizedCookie) {
                cookieManager.setCookie(url, normalizedCookie)
            }
        }
        cookieManager.flush()
    }

    private fun saveDataDomeCookie(context: Context, cookie: String) {
        val normalizedCookie = normalizeDataDomeCookie(cookie) ?: return
        context.applicationContext
            .getSharedPreferences(DATADOME_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DATADOME_COOKIE, normalizedCookie)
            .apply()
    }

    private fun readDataDomeCookie(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val cookies = CookieManager.getInstance().getCookie(url) ?: return null
        return extractValue(cookies, "datadome")?.let { "datadome=$it" }
    }

    private fun normalizeDataDomeCookie(cookie: String?): String? {
        if (cookie.isNullOrBlank()) return null

        val directPart = cookie.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("datadome=") && it.length > "datadome=".length }

        return directPart?.takeIf { !it.equals("datadome=", ignoreCase = true) }
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
        completeDataDomeChallenge(null, solved = true)
        pendingLikeAction?.invoke()
    }

    fun cancelCaptcha() {
        completeDataDomeChallenge(null, solved = false)
        pendingLikeAction = null
        reloadSession(forceRefresh = true)
    }

    fun syncLikeState(trackId: Long, isLike: Boolean, token: String, userId: Long) {
        return
    }
}
