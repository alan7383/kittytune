package com.alananasss.kittytune.ui.profile.integrations

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import android.os.Message
import android.webkit.WebChromeClient
import androidx.compose.foundation.background
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
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.alananasss.kittytune.R
import com.alananasss.kittytune.audio.providers.tidal.extractTidalRefreshToken
import com.alananasss.kittytune.audio.providers.tidal.mergeTidalCookieInputs
import com.alananasss.kittytune.data.local.PlayerPreferences

private const val TIDAL_LOGIN_URL = "https://tidal.com/"

private val TidalCookieUrls =
    listOf(
        "https://listen.tidal.com",
        "https://tidal.com",
        "https://www.tidal.com",
        "https://login.tidal.com",
        "https://auth.tidal.com",
        "https://api.tidal.com",
    )

private val TidalDocumentStartOrigins =
    setOf(
        "https://listen.tidal.com",
        "https://tidal.com",
        "https://www.tidal.com",
        "https://login.tidal.com",
        "https://auth.tidal.com",
        "https://api.tidal.com",
        "https://*.tidal.com",
    )

private val TidalAuthCaptureDelaysMs = listOf(0L, 250L, 750L, 1_500L, 3_000L, 5_000L, 8_000L)

private const val TIDAL_AUTH_CAPTURE_SCRIPT =
    """
    (function() {
        'use strict';
        if (window.__metroTidalAuthCaptureInstalled) return true;
        window.__metroTidalAuthCaptureInstalled = true;

        function looksUseful(value) {
            value = String(value || '');
            return value.indexOf('refresh_token') !== -1 ||
                value.indexOf('refreshToken') !== -1;
        }

        function rememberAuthPayload(payload) {
            try {
                if (!looksUseful(payload)) return;
                payload = String(payload);
                window.__metroTidalAuthPayload = payload;
                try { sessionStorage.setItem('__metroTidalAuthPayload', payload); } catch (error) {}
                try { localStorage.setItem('__metroTidalAuthPayload', payload); } catch (error) {}
                if (window.MetroTidalAuth && window.MetroTidalAuth.saveAuthPayload) {
                    window.MetroTidalAuth.saveAuthPayload(payload);
                }
            } catch (error) {}
        }

        const originalFetch = window.fetch;
        if (typeof originalFetch === 'function') {
            window.fetch = async (...args) => {
                const resource = args[0];
                const url = typeof resource === 'string' ? resource : (resource && resource.url) || '';
                const response = await originalFetch(...args);
                try {
                    const text = await response.clone().text();
                    if (String(url).indexOf('oauth2/token') !== -1 || looksUseful(text)) {
                        rememberAuthPayload(text);
                    }
                } catch (error) {}
                return response;
            };
        }

        const originalOpen = XMLHttpRequest.prototype.open;
        const originalSend = XMLHttpRequest.prototype.send;
        XMLHttpRequest.prototype.open = function(method, url) {
            this.__metroTidalUrl = url;
            return originalOpen.apply(this, arguments);
        };
        XMLHttpRequest.prototype.send = function() {
            this.addEventListener('load', function() {
                try {
                    rememberAuthPayload(this.responseText);
                } catch (error) {}
            });
            return originalSend.apply(this, arguments);
        };

        function dumpStorage(label, storage) {
            try {
                for (var i = 0; i < storage.length; i++) {
                    var key = storage.key(i);
                    var value = storage.getItem(key);
                    if (key && value && looksUseful(value)) {
                        rememberAuthPayload(label + ':' + key + '=' + value);
                    }
                }
            } catch (error) {}
        }

        async function dumpIndexedDb() {
            try {
                if (!window.indexedDB || !window.indexedDB.databases) return;
                const databases = await window.indexedDB.databases();
                for (const database of databases || []) {
                    if (!database || !database.name) continue;
                    await new Promise((resolve) => {
                        const open = window.indexedDB.open(database.name);
                        open.onerror = function() { resolve(); };
                        open.onsuccess = function() {
                            const db = open.result;
                            try {
                                const names = Array.from(db.objectStoreNames || []);
                                if (!names.length) {
                                    db.close();
                                    resolve();
                                    return;
                                }
                                let pending = names.length;
                                names.forEach((storeName) => {
                                    try {
                                        const request = db.transaction(storeName, 'readonly').objectStore(storeName).getAll();
                                        request.onsuccess = function() {
                                            try {
                                                rememberAuthPayload('indexedDB:' + database.name + ':' + storeName + '=' + JSON.stringify(request.result));
                                            } catch (error) {}
                                            if (--pending === 0) {
                                                db.close();
                                                resolve();
                                            }
                                        };
                                        request.onerror = function() {
                                            if (--pending === 0) {
                                                db.close();
                                                resolve();
                                            }
                                        };
                                    } catch (error) {
                                        if (--pending === 0) {
                                            db.close();
                                            resolve();
                                        }
                                    }
                                });
                            } catch (error) {
                                try { db.close(); } catch (closeError) {}
                                resolve();
                            }
                        };
                    });
                }
            } catch (error) {}
        }

        dumpStorage('localStorage', window.localStorage);
        dumpStorage('sessionStorage', window.sessionStorage);
        dumpIndexedDb();
        return true;
    })()
    """

private const val TIDAL_STORAGE_READ_SCRIPT =
    """
    (function() {
        var rows = [];
        function dumpStorage(label, storage) {
            try {
                for (var i = 0; i < storage.length; i++) {
                    var key = storage.key(i);
                    var value = storage.getItem(key);
                    if (key && value) {
                        rows.push(label + ':' + key + '=' + value);
                    }
                }
            } catch (error) {}
        }
        dumpStorage('localStorage', window.localStorage);
        dumpStorage('sessionStorage', window.sessionStorage);
        return rows.join('\n');
    })()
    """

private fun isTidalWebPlayerUrl(url: String?): Boolean =
    url?.startsWith("https://listen.tidal.com", ignoreCase = true) == true ||
        url?.startsWith("https://tidal.com", ignoreCase = true) == true ||
        url?.startsWith("https://www.tidal.com", ignoreCase = true) == true ||
        url?.startsWith("https://login.tidal.com", ignoreCase = true) == true

private fun decodeJavascriptString(result: String?): String? =
    result
        ?.trim()
        ?.takeIf { it.isNotEmpty() && it != "null" }
        ?.trim('"')
        ?.replace("\\\"", "\"")
        ?.replace("\\\\", "\\")
        ?.replace("\\n", "\n")
        ?.replace("\\r", "\r")
        ?.replace("\\u003d", "=")
        ?.replace("\\u003D", "=")
        ?.replace("\\u003b", ";")
        ?.replace("\\u003B", ";")
        ?.takeIf { it.isNotBlank() }

private fun WebView.installTidalAuthCapture() {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
        runCatching {
            WebViewCompat.addDocumentStartJavaScript(
                this,
                TIDAL_AUTH_CAPTURE_SCRIPT,
                TidalDocumentStartOrigins,
            )
        }.onFailure {
            evaluateJavascript(TIDAL_AUTH_CAPTURE_SCRIPT, null)
        }
    } else {
        evaluateJavascript(TIDAL_AUTH_CAPTURE_SCRIPT, null)
    }
}

private class TidalAuthBridge(
    private val handler: Handler,
    private val onAuthPayload: (String) -> Unit,
) {
    @JavascriptInterface
    fun saveAuthPayload(payload: String?) {
        val normalized = payload?.let { mergeTidalCookieInputs(listOf(it)) } ?: return
        if (extractTidalRefreshToken(normalized).isNullOrBlank()) return
        handler.post {
            onAuthPayload(normalized)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TidalLoginScreen(
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var popupWebView by remember { mutableStateOf<WebView?>(null) }
    var cookieCaptured by remember { mutableStateOf(false) }
    var isRedirecting by remember { mutableStateOf(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    fun saveAuth(
        normalizedAuth: String,
        showConfirmation: Boolean,
    ) {
        if (extractTidalRefreshToken(normalizedAuth).isNullOrBlank()) return
        prefs.setTidalCookie(normalizedAuth)
        if (showConfirmation && !isRedirecting) {
            isRedirecting = true
            cookieCaptured = true
            popupWebView = null
            mainHandler.postDelayed({
                onBackClick()
            }, 800L)
        }
    }

    fun captureAuth(
        showConfirmation: Boolean,
        afterCapture: (() -> Unit)? = null,
    ) {
        val currentWebView = webView
        currentWebView?.installTidalAuthCapture()

        val cookieManager = CookieManager.getInstance()
        cookieManager.flush()

        val cookieInputs =
            TidalCookieUrls
                .mapNotNull { cookieManager.getCookie(it) }

        fun finishCapture(storageDump: String?) {
            val normalizedAuth =
                mergeTidalCookieInputs(
                    cookieInputs + listOfNotNull(storageDump),
                )

            if (normalizedAuth != null) {
                saveAuth(normalizedAuth, showConfirmation)
            }

            afterCapture?.invoke()
        }

        currentWebView?.evaluateJavascript(TIDAL_STORAGE_READ_SCRIPT) { result ->
            finishCapture(decodeJavascriptString(result))
        } ?: finishCapture(storageDump = null)
    }

    fun scheduleAuthCapture(showConfirmation: Boolean) {
        TidalAuthCaptureDelaysMs.forEach { delayMs ->
            if (delayMs == 0L) {
                webView?.post {
                    captureAuth(showConfirmation)
                }
            } else {
                webView?.postDelayed(
                    {
                        captureAuth(showConfirmation)
                    },
                    delayMs,
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mainHandler.removeCallbacksAndMessages(null)
            runCatching {
                webView?.stopLoading()
                webView?.destroy()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tidal_web_login)) },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = {
                            captureAuth(showConfirmation = false) {
                                onBackClick()
                            }
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
                        onClick = {
                            captureAuth(showConfirmation = true) {
                                onBackClick()
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
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)

                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            setSupportMultipleWindows(true)
                            javaScriptCanOpenWindowsAutomatically = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                        }

                        addJavascriptInterface(
                            TidalAuthBridge(mainHandler) { normalizedAuth ->
                                saveAuth(normalizedAuth, showConfirmation = true)
                            },
                            "MetroTidalAuth",
                        )

                        webViewClient =
                            object : WebViewClient() {
                                override fun onPageStarted(
                                    view: WebView,
                                    url: String?,
                                    favicon: android.graphics.Bitmap?,
                                ) {
                                    view.installTidalAuthCapture()
                                    if (isTidalWebPlayerUrl(url)) {
                                        scheduleAuthCapture(showConfirmation = true)
                                    }
                                }

                                override fun onPageFinished(
                                    view: WebView,
                                    url: String?,
                                ) {
                                    view.installTidalAuthCapture()
                                    scheduleAuthCapture(showConfirmation = isTidalWebPlayerUrl(url))
                                }

                                override fun onLoadResource(
                                    view: WebView,
                                    url: String?,
                                ) {
                                    if (isTidalWebPlayerUrl(url)) {
                                        scheduleAuthCapture(showConfirmation = true)
                                    }
                                }
                            }

                        webChromeClient = object : WebChromeClient() {
                            override fun onCreateWindow(
                                view: WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: Message?
                            ): Boolean {
                                if (resultMsg == null) return false
                                val popup = WebView(view?.context ?: ctx).apply {
                                    layoutParams = ViewGroup.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                                    setSupportMultipleWindows(true)
                                        javaScriptCanOpenWindowsAutomatically = true
                                    }
                                    CookieManager.getInstance().setAcceptCookie(true)
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                    webViewClient = object : WebViewClient() {
                                        override fun onPageFinished(v: WebView?, url: String?) {
                                            super.onPageFinished(v, url)
                                            CookieManager.getInstance().flush()
                                            captureAuth(showConfirmation = true)
                                        }
                                    }
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onCloseWindow(window: WebView?) {
                                            super.onCloseWindow(window)
                                            popupWebView = null
                                            captureAuth(showConfirmation = true)
                                        }
                                    }
                                }
                                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                                transport.webView = popup
                                resultMsg.sendToTarget()
                                popupWebView = popup
                                return true
                            }
                        }

                        webView = this
                        installTidalAuthCapture()
                        loadUrl(TIDAL_LOGIN_URL)
                    }
                }
            )

            popupWebView?.let { popup ->
                key(popup) {
                    DisposableEffect(popup) {
                        onDispose {
                            runCatching {
                                (popup.parent as? ViewGroup)?.removeView(popup)
                                popup.stopLoading()
                                popup.destroy()
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { popup }
                        )
                        FilledTonalIconButton(
                            onClick = {
                                popupWebView = null
                                captureAuth(showConfirmation = true)
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(16.dp),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(android.R.string.cancel))
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = cookieCaptured,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 6.dp,
                ) {
                    Text(
                        text = stringResource(R.string.tidal_cookie_saved),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }

    BackHandler {
        val popup = popupWebView
        if (popup != null) {
            if (popup.canGoBack()) {
                popup.goBack()
            } else {
                popupWebView = null
                captureAuth(showConfirmation = true)
            }
        } else {
            val currentWebView = webView
            if (currentWebView?.canGoBack() == true) {
                currentWebView.goBack()
            } else {
                captureAuth(showConfirmation = false) {
                    onBackClick()
                }
            }
        }
    }
}
