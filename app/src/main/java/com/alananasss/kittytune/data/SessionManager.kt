package com.alananasss.kittytune.data

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.alananasss.kittytune.utils.Config
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@SuppressLint("StaticFieldLeak")
object SessionManager {
    private const val TAG = "SESSION_GHOST"
    private const val REFRESH_INTERVAL = 20 * 60 * 1000L

    private var ghostWebView: WebView? = null

    // track if we actually have a valid id yet
    private val _isClientIdValid = MutableStateFlow(false)
    val isClientIdValid = _isClientIdValid.asStateFlow()

    fun attachGhost(webView: WebView, context: Context) {
        ghostWebView = webView
        setupWebView(webView, context)
        CookieManager.getInstance().flush()
        Log.d(TAG, "👻 Navigateur Fantôme attaché.")

        // check if we already have a good id at startup
        checkIdValidity()

        // start immediately
        reloadSession()
        startKeepAliveCycle()
    }

    private fun checkIdValidity() {
        // if id isn't the fallback one, assume it's good enough for now
        _isClientIdValid.value = Config.CLIENT_ID != Config.FALLBACK_ID
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(webView: WebView, context: Context) {
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.userAgentString = Config.USER_AGENT

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url?.toString() ?: ""

                if (url.contains("client_id=")) {
                    try {
                        val uri = Uri.parse(url)
                        val capturedId = uri.getQueryParameter("client_id")
                        if (!capturedId.isNullOrEmpty()) {
                            // if it's a new id, update it
                            if (capturedId != Config.CLIENT_ID) {
                                Config.updateClientId(context, capturedId)
                            }
                            // signal that we're good to go
                            _isClientIdValid.value = true
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
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
                val currentToken = tokenManager.getAccessToken()

                if (newToken != currentToken) {
                    Log.d(TAG, "⚡ SUPER-REFRESH: Nouveau token capturé !")
                    tokenManager.saveTokens(newToken, "ghost_refresh")
                }
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
        Log.d(TAG, "🔄 Refresh Session / Scraping ID...")
        ghostWebView?.loadUrl("https://m.soundcloud.com/discover")
    }

    private fun extractValue(cookies: String, key: String): String? {
        return cookies.split(";")
            .map { it.trim() }
            .find { it.startsWith("$key=") }
            ?.substringAfter("$key=")
            ?.replace("\"", "")
            ?.trim()
    }
}