package com.alananasss.kittytune.data

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.alananasss.kittytune.utils.Config
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

@SuppressLint("StaticFieldLeak")
object SessionManager {
    private const val TAG = "SessionManager"
    private const val REFRESH_INTERVAL = 20 * 60 * 1000L // 20 minutes

    private var ghostWebView: WebView? = null

    private val _isClientIdValid = MutableStateFlow(false)
    val isClientIdValid = _isClientIdValid.asStateFlow()

    private val _sessionReadyEvent = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val sessionReadyEvent = _sessionReadyEvent.asSharedFlow()

    // --- NOUVEAU : Flux pour afficher le Captcha et action en attente ---
    private val _showCaptchaFlow = MutableStateFlow(false)
    val showCaptchaFlow = _showCaptchaFlow.asStateFlow()
    private var pendingLikeAction: (() -> Unit)? = null

    // Pont de communication JS -> Kotlin
    private class AndroidBridge {
        @JavascriptInterface
        fun requestCaptcha() {
            MainScope().launch { _showCaptchaFlow.value = true }
        }

        @JavascriptInterface
        fun onLikeSuccess() {
            MainScope().launch {
                _showCaptchaFlow.value = false
                pendingLikeAction = null
            }
        }
    }

    fun attachGhost(webView: WebView, context: Context) {
        ghostWebView = webView
        setupWebView(webView, context)
        CookieManager.getInstance().flush()
        reloadSession()
        startKeepAliveCycle()
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun setupWebView(webView: WebView, context: Context) {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.userAgentString = Config.USER_AGENT

        // On connecte l'interface Javascript
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
                harvestCookie(url, context)
            }
        }
    }

    private fun harvestCookie(url: String?, context: Context) {
        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie(url)

        if (cookies != null && cookies.contains("oauth_token")) {
            val newToken = extractValue(cookies, "oauth_token")
            if (!newToken.isNullOrEmpty()) {
                val tokenManager = TokenManager(context)
                if (newToken != tokenManager.getAccessToken()) {
                    tokenManager.saveTokens(newToken, "ghost_refresh")
                }
                _sessionReadyEvent.tryEmit(Unit)
                cookieManager.flush()
            }
        }
    }

    private fun startKeepAliveCycle() {
        MainScope().launch {
            while (true) {
                delay(REFRESH_INTERVAL)
                reloadSession()
            }
        }
    }

    fun reloadSession() {
        ghostWebView?.loadUrl("https://soundcloud.com/discover")
    }

    private fun extractValue(cookies: String, key: String): String? {
        return cookies.split(";").map { it.trim() }.find { it.startsWith("$key=") }
            ?.substringAfter("$key=")?.replace("\"", "")?.trim()
    }

    // --- NOUVELLES FONCTIONS POUR LE CAPTCHA ---
    fun retryPendingAction() {
        _showCaptchaFlow.value = false
        pendingLikeAction?.invoke()
    }

    fun cancelCaptcha() {
        _showCaptchaFlow.value = false
        pendingLikeAction = null
        reloadSession()
    }

    fun syncLikeState(trackId: Long, isLike: Boolean, token: String, userId: Long) {
        val clientId = Config.CLIENT_ID
        val url = "https://api-v2.soundcloud.com/users/$userId/track_likes/$trackId?client_id=$clientId&app_version=1771407416&app_locale=en"

        val method = if (isLike) "PUT" else "DELETE"
        val contentType = if (isLike) "'Content-Type': 'application/json; charset=utf-8'," else ""
        val bodyStr = if (isLike) "body: '{}'," else ""

        // On sauvegarde l'action pour la retenter une fois le captcha validé
        pendingLikeAction = { syncLikeState(trackId, isLike, token, userId) }

        val js = """
            fetch('$url', {
                method: '$method',
                credentials: 'include',
                headers: {
                    'Authorization': 'OAuth $token',
                    'Accept': 'application/json',
                    $contentType
                },
                $bodyStr
            }).then(r => {
                if (r.status === 403 || r.status === 401) {
                    AndroidBridge.requestCaptcha();
                    window.location.href = 'https://m.soundcloud.com/discover';
                } else if (r.status === 200 || r.status === 201 || r.status === 204) {
                    AndroidBridge.onLikeSuccess();
                }
            }).catch(e => console.error('Fetch Error:', e));
        """.trimIndent()

        MainScope().launch {
            ghostWebView?.evaluateJavascript(js, null)
        }
    }
}