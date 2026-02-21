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
    import kotlinx.coroutines.flow.MutableSharedFlow
    import kotlinx.coroutines.flow.asSharedFlow
    import kotlinx.coroutines.launch
    
    /**
     * Manages a background WebView ("Ghost Browser") to maintain the session active
     * and scrape dynamic Client IDs or Tokens required by the internal API.
     */
    @SuppressLint("StaticFieldLeak")
    object SessionManager {
        private const val TAG = "SessionManager"
        private const val REFRESH_INTERVAL = 20 * 60 * 1000L // 20 minutes
    
        private var ghostWebView: WebView? = null
    
        // Tracks if we have obtained a valid Client ID from the web session
        private val _isClientIdValid = MutableStateFlow(false)
        val isClientIdValid = _isClientIdValid.asStateFlow()
    
        private val _sessionReadyEvent = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
        val sessionReadyEvent = _sessionReadyEvent.asSharedFlow()
    
        fun attachGhost(webView: WebView, context: Context) {
            ghostWebView = webView
            setupWebView(webView, context)
            CookieManager.getInstance().flush()
            Log.d(TAG, "Headless browser attached for session management.")
    
            // Start the lifecycle immediately
            reloadSession()
            startKeepAliveCycle()
        }
    
        private fun checkIdValidity() {
            // If the ID differs from the fallback, we assume it is valid
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
    
                    // Intercept Client ID from API requests made by the web client
                    if (url.contains("client_id=")) {
                        try {
                            val uri = Uri.parse(url)
                            val capturedId = uri.getQueryParameter("client_id")
                            if (!capturedId.isNullOrEmpty()) {
                                // Update config if a new ID is detected
                                if (capturedId != Config.CLIENT_ID) {
                                    Config.updateClientId(context, capturedId)
                                }
                                // Signal valid state
                                _isClientIdValid.value = true
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing client_id: ${e.message}")
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
    
                    // If the token has changed, update it locally
                    if (newToken != currentToken) {
                        Log.d(TAG, "Session refresh: New access token captured via cookie interception.")
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
            Log.d(TAG, "Reloading session to refresh Client ID...")
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


