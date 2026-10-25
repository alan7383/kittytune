package com.alananasss.kittytune.ui.profile.integrations

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.hapticAction
import com.alananasss.kittytune.ui.common.hapticClickable
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.network.ProxyManager
import com.alananasss.kittytune.ui.common.SafeWebView
import com.alananasss.kittytune.ui.common.createSafeWebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

private const val QOBUZ_LOGIN_URL =
    "https://www.qobuz.com/signin/oauth?ext_app_id=798273057&redirect_url=https%3A%2F%2Fplay.qobuz.com%2Fdiscover"

private const val QOBUZ_APP_ID = "798273057"
private const val QOBUZ_OAUTH_PRIVATE_KEY = "6lz8C03UDIC7"

private const val QOBUZ_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36"

private const val QOBUZ_INJECT_SCRIPT = """
(function() {
    function killOverlay() {
        try {
            var hideStyle = document.getElementById('kitty-hide-overlay');
            if (!hideStyle) {
                hideStyle = document.createElement('style');
                hideStyle.id = 'kitty-hide-overlay';
                hideStyle.innerHTML = '.mobileOverlay, [class*="mobileOverlay"] { display: none !important; visibility: hidden !important; opacity: 0 !important; pointer-events: none !important; height: 0 !important; max-height: 0 !important; overflow: hidden !important; position: absolute !important; left: -9999px !important; }';
                (document.head || document.documentElement).appendChild(hideStyle);
            }
            var els = document.querySelectorAll('.mobileOverlay, [class*="mobileOverlay"]');
            for (var i = 0; i < els.length; i++) {
                els[i].remove();
            }
        } catch(e) {}
    }
    killOverlay();
    setInterval(killOverlay, 600);

    try {
        Object.defineProperty(window, 'innerWidth', { get: function() { return 1280; }, configurable: true });
        Object.defineProperty(screen, 'width', { get: function() { return 1280; }, configurable: true });
    } catch(e) {}

    // When an input element is focused in the web view, notify native Android to bring up the keyboard
    document.addEventListener('focusin', function(e) {
        if (e.target && (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA' || e.target.isContentEditable)) {
            if (window.KittyQobuzAuth && window.KittyQobuzAuth.onInputFocused) {
                window.KittyQobuzAuth.onInputFocused();
            }
        }
    }, true);

    if (window.__kittyQobuzInstalled) return;
    window.__kittyQobuzInstalled = true;

    function extractTokenFromText(text) {
        if (!text) return null;
        try {
            var json = JSON.parse(text);
            if (json.user_auth_token) return json.user_auth_token;
            if (json.user && json.user.user_auth_token) return json.user.user_auth_token;
            if (json.token) return json.token;
            if (json.oauth2 && json.oauth2.access_token) return json.oauth2.access_token;
        } catch(e) {
            var m = text.match(/["']?user_auth_token["']?\s*:\s*["']([^"']+)["']/i);
            if (m && m[1]) return m[1];
        }
        return null;
    }

    function checkPayload(payload) {
        var token = extractTokenFromText(payload);
        if (token && window.KittyQobuzAuth) {
            window.KittyQobuzAuth.onTokenFound(token);
        }
    }

    // Intercept fetch
    var originalFetch = window.fetch;
    if (originalFetch) {
        window.fetch = async function() {
            var response = await originalFetch.apply(this, arguments);
            try {
                var clone = response.clone();
                clone.text().then(checkPayload);
            } catch(e) {}
            return response;
        };
    }

    // Intercept XMLHttpRequest
    var origOpen = XMLHttpRequest.prototype.open;
    var origSend = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.open = function() {
        return origOpen.apply(this, arguments);
    };
    XMLHttpRequest.prototype.send = function() {
        this.addEventListener('load', function() {
            checkPayload(this.responseText);
        });
        return origSend.apply(this, arguments);
    };

    // Periodically check localStorage and sessionStorage
    setInterval(function() {
        try {
            for (var i = 0; i < localStorage.length; i++) {
                var k = localStorage.key(i);
                var v = localStorage.getItem(k);
                if (v && (v.indexOf('user_auth_token') !== -1 || v.indexOf('user') !== -1)) {
                    checkPayload(v);
                }
            }
        } catch(e) {}
    }, 1500);
})();
"""

private const val QOBUZ_SCAN_STORAGE_SCRIPT = """
(function() {
    var found = '';
    try {
        for (var i = 0; i < localStorage.length; i++) {
            var k = localStorage.key(i);
            var v = localStorage.getItem(k);
            if (v && v.indexOf('user_auth_token') !== -1) {
                return v;
            }
        }
    } catch(e) {}
    return '';
})()
"""

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun QobuzLoginScreen(
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }
    var mainWebView by remember { mutableStateOf<WebView?>(null) }
    var popupWebView by remember { mutableStateOf<WebView?>(null) }
    var isRedirecting by remember { mutableStateOf(false) }
    var isExchangingCode by remember { mutableStateOf(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    fun saveToken(token: String) {
        val clean = token.trim()
        if (clean.isBlank()) return
        if (isRedirecting && !isExchangingCode) return
        isRedirecting = true
        isExchangingCode = false
        prefs.setQobuzUserAuthToken(clean)
        Toast.makeText(context, R.string.qobuz_account_login_success, Toast.LENGTH_SHORT).show()
        mainHandler.postDelayed({
            onBackClick()
        }, 600L)
    }

    fun parseTokenFromRaw(raw: String?): String? {
        if (raw.isNullOrBlank() || raw == "null" || raw == "\"\"") return null
        return runCatching {
            val json = JSONObject(raw.trim('"').replace("\\\"", "\""))
            json.optString("user_auth_token").takeIf { it.isNotBlank() }
                ?: json.optJSONObject("user")?.optString("user_auth_token")?.takeIf { it.isNotBlank() }
                ?: json.optString("token").takeIf { it.isNotBlank() }
                ?: json.optJSONObject("oauth2")?.optString("access_token")?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: run {
            val regex = Regex("""["']?user_auth_token["']?\s*:\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
            regex.find(raw)?.groupValues?.getOrNull(1)
        }
    }

    fun scanStorage() {
        mainWebView?.evaluateJavascript(QOBUZ_SCAN_STORAGE_SCRIPT) { result ->
            val token = parseTokenFromRaw(result)
            if (token != null) {
                saveToken(token)
            }
        }
    }

    fun exchangeAuthCode(code: String) {
        if (isRedirecting) return
        isRedirecting = true
        isExchangingCode = true

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val callbackUrl = "https://www.qobuz.com/api.json/0.2/oauth/callback?code=${Uri.encode(code)}&private_key=$QOBUZ_OAUTH_PRIVATE_KEY&app_id=$QOBUZ_APP_ID"
                val request = Request.Builder()
                    .url(callbackUrl)
                    .header("X-App-Id", QOBUZ_APP_ID)
                    .header("User-Agent", QOBUZ_USER_AGENT)
                    .get()
                    .build()

                val client = ProxyManager.getOkHttpClient(context)
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    android.util.Log.d("QobuzLogin", "oauth/callback response: $body")
                    val json = JSONObject(body)
                    val token = json.optString("token").takeIf { it.isNotBlank() }
                        ?: json.optString("user_auth_token").takeIf { it.isNotBlank() }
                        ?: json.optJSONObject("user")?.optString("user_auth_token")

                    if (!token.isNullOrBlank()) {
                        withContext(Dispatchers.Main) {
                            saveToken(token)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            isRedirecting = false
                            isExchangingCode = false
                            val errorMsg = json.optString("message").takeIf { it.isNotBlank() }
                                ?: context.getString(R.string.qobuz_token_not_found)
                            Toast.makeText(context, errorMsg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("QobuzLogin", "Error exchanging auth code", e)
                withContext(Dispatchers.Main) {
                    isRedirecting = false
                    isExchangingCode = false
                    Toast.makeText(context, "Erreur: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun checkUrlForCode(view: WebView?, url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            return false
        }
        return runCatching {
            val uri = Uri.parse(url)
            if (!uri.isHierarchical) return@runCatching false
            val code = uri.getQueryParameter("code_autorisation")
                ?: uri.getQueryParameter("code")
            if (!code.isNullOrBlank() && (uri.host?.contains("qobuz.com") == true)) {
                view?.stopLoading()
                exchangeAuthCode(code)
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    BackHandler {
        val popup = popupWebView
        if (popup != null) {
            if (popup.canGoBack()) {
                popup.goBack()
            } else {
                popupWebView = null
                scanStorage()
            }
        } else {
            scanStorage()
            onBackClick()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mainHandler.removeCallbacksAndMessages(null)
            runCatching {
                mainWebView?.stopLoading()
                mainWebView?.destroy()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.qobuz_web_login)) },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = hapticAction(HapticKind.UI) {
                            scanStorage()
                            onBackClick()
                        },
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    FilledTonalIconButton(
                        onClick = hapticAction(HapticKind.UI) {
                            mainWebView?.evaluateJavascript(QOBUZ_SCAN_STORAGE_SCRIPT) { result ->
                                val token = parseTokenFromRaw(result)
                                if (token != null) {
                                    saveToken(token)
                                } else {
                                    Toast.makeText(context, R.string.qobuz_token_not_found, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = stringResource(android.R.string.ok))
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .focusable(),
                factory = { ctx ->
                    SafeWebView(ctx).apply {
                        mainWebView = this
                        isFocusable = true
                        isFocusableInTouchMode = true
                        requestFocus(View.FOCUS_DOWN)

                        setOnTouchListener { v, event ->
                            when (event.action) {
                                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP -> {
                                    if (!v.hasFocus()) {
                                        v.requestFocus()
                                    }
                                }
                            }
                            false
                        }

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            setSupportMultipleWindows(true)
                            javaScriptCanOpenWindowsAutomatically = true
                            userAgentString = QOBUZ_USER_AGENT
                        }
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
                        }
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        addJavascriptInterface(
                            object {
                                @JavascriptInterface
                                fun onTokenFound(token: String) {
                                    mainHandler.post { saveToken(token) }
                                }

                                @JavascriptInterface
                                fun onInputFocused() {
                                    mainHandler.post {
                                        if (!hasFocus()) {
                                            requestFocus()
                                        }
                                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                                        imm?.showSoftInput(this@apply, InputMethodManager.SHOW_IMPLICIT)
                                    }
                                }
                            },
                            "KittyQobuzAuth"
                        )

                        webChromeClient = object : WebChromeClient() {
                            override fun onCreateWindow(
                                view: WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: Message?
                            ): Boolean {
                                val popup = createSafeWebView(ctx).apply {
                                    isFocusable = true
                                    isFocusableInTouchMode = true
                                    requestFocus(View.FOCUS_DOWN)

                                    setOnTouchListener { v, event ->
                                        when (event.action) {
                                            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP -> {
                                                if (!v.hasFocus()) {
                                                    v.requestFocus()
                                                }
                                            }
                                        }
                                        false
                                    }

                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        databaseEnabled = true
                                        useWideViewPort = true
                                        loadWithOverviewMode = true
                                        setSupportMultipleWindows(true)
                                        javaScriptCanOpenWindowsAutomatically = true
                                        userAgentString = QOBUZ_USER_AGENT
                                    }
                                    if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                                        WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
                                    }
                                    CookieManager.getInstance().setAcceptCookie(true)
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                                    webChromeClient = object : WebChromeClient() {
                                        override fun onCloseWindow(window: WebView?) {
                                            popupWebView = null
                                            scanStorage()
                                        }
                                    }

                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): Boolean {
                                            val url = request?.url?.toString() ?: return false
                                            if (checkUrlForCode(view, url)) return true
                                            if (url.startsWith("http://") || url.startsWith("https://")) {
                                                return false
                                            }
                                            return try {
                                                val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                                                ctx.startActivity(intent)
                                                true
                                            } catch (e: Exception) {
                                                true
                                            }
                                        }

                                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                            super.onPageStarted(view, url, favicon)
                                            checkUrlForCode(view, url)
                                        }

                                        override fun onPageFinished(view: WebView, url: String?) {
                                            super.onPageFinished(view, url)
                                            CookieManager.getInstance().flush()
                                            checkUrlForCode(view, url)
                                            scanStorage()
                                        }
                                    }
                                }

                                popupWebView = popup
                                val transport = resultMsg?.obj as? WebView.WebViewTransport
                                transport?.webView = popup
                                resultMsg?.sendToTarget()
                                return true
                            }

                            override fun onCloseWindow(window: WebView?) {
                                popupWebView = null
                                scanStorage()
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val url = request?.url?.toString() ?: return false
                                if (checkUrlForCode(view, url)) return true
                                if (url.startsWith("http://") || url.startsWith("https://")) {
                                    return false
                                }
                                return try {
                                    val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                                    ctx.startActivity(intent)
                                    true
                                } catch (e: Exception) {
                                    true
                                }
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                if (checkUrlForCode(view, url)) return
                                view?.evaluateJavascript(QOBUZ_INJECT_SCRIPT, null)
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val headers = request?.requestHeaders
                                val authHeader = headers?.get("X-User-Auth-Token")
                                    ?: headers?.get("x-user-auth-token")
                                    ?: headers?.get("Authorization")?.removePrefix("Bearer ")?.trim()

                                if (!authHeader.isNullOrBlank() && authHeader != "null") {
                                    mainHandler.post { saveToken(authHeader) }
                                }

                                val url = request?.url?.toString().orEmpty()
                                if (url.contains("user_auth_token=")) {
                                    val token = url.substringAfter("user_auth_token=").substringBefore("&")
                                    if (token.isNotBlank()) {
                                        mainHandler.post { saveToken(token) }
                                    }
                                }

                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                super.onPageFinished(view, url)
                                CookieManager.getInstance().flush()
                                view.evaluateJavascript(QOBUZ_INJECT_SCRIPT, null)
                                scanStorage()
                                checkUrlForCode(view, url)

                                if (!isRedirecting && url != null && url.startsWith("https://www.qobuz.com") && !url.contains("/signin")) {
                                    val cookies = CookieManager.getInstance().getCookie("https://www.qobuz.com").orEmpty()
                                    if (cookies.contains("qobuz-session")) {
                                        view.loadUrl(QOBUZ_LOGIN_URL)
                                    }
                                }
                            }
                        }

                        loadUrl(QOBUZ_LOGIN_URL)
                    }
                }
            )

            // Popup window overlay (for Google / social OAuth dialogs)
            AnimatedVisibility(
                visible = popupWebView != null,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                val popup = popupWebView
                if (popup != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
                            .padding(16.dp)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = hapticAction(HapticKind.UI) {
                                        popupWebView = null
                                        scanStorage()
                                    }) {
                                        Icon(Icons.Rounded.Close, contentDescription = stringResource(android.R.string.cancel))
                                    }
                                }
                                AndroidView(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .focusable(),
                                    factory = {
                                        (popup.parent as? ViewGroup)?.removeView(popup)
                                        popup
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Exchanging code / Logging in spinner overlay
            AnimatedVisibility(
                visible = isExchangingCode,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = stringResource(R.string.qobuz_logging_in),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
