package com.alananasss.kittytune.ui.login

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.TokenManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context) }
    var isLoading by remember { mutableStateOf(true) }

    var popupWebView by remember { mutableStateOf<WebView?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.login_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.btn_cancel))
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()) {

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        configureWebView(this)

                        settings.setSupportMultipleWindows(true)
                        settings.javaScriptCanOpenWindowsAutomatically = true

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                            }
                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                checkCookies(url, CookieManager.getInstance(), tokenManager, onLoginSuccess)
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onCreateWindow(
                                view: WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: Message?
                            ): Boolean {
                                val newWebView = WebView(ctx)
                                configureWebView(newWebView)

                                newWebView.webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        checkCookies(url, CookieManager.getInstance(), tokenManager, onLoginSuccess)
                                    }
                                }
                                newWebView.webChromeClient = object : WebChromeClient() {
                                    override fun onCloseWindow(window: WebView?) {
                                        popupWebView = null
                                    }
                                }

                                popupWebView = newWebView

                                val transport = resultMsg?.obj as? WebView.WebViewTransport
                                transport?.webView = newWebView
                                resultMsg?.sendToTarget()

                                return true
                            }
                        }

                        loadUrl("https://soundcloud.com/signin")
                    }
                }
            )

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            if (popupWebView != null) {
                Dialog(
                    onDismissRequest = { popupWebView = null },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text(stringResource(R.string.nav_login)) },
                                navigationIcon = {
                                    IconButton(onClick = { popupWebView = null }) {
                                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.btn_close))
                                    }
                                }
                            )
                        }
                    ) { popupPadding ->
                        Box(modifier = Modifier.padding(popupPadding).fillMaxSize()) {
                            AndroidView(
                                factory = { popupWebView!! },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun configureWebView(webView: WebView) {
    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    cookieManager.setAcceptThirdPartyCookies(webView, true)

    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        useWideViewPort = true
        loadWithOverviewMode = true
        userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }
}

private fun checkCookies(
    url: String?,
    cookieManager: CookieManager,
    tokenManager: TokenManager,
    onSuccess: () -> Unit
) {
    val cookies = cookieManager.getCookie(url)

    if (cookies != null && cookies.contains("oauth_token")) {
        val accessToken = extractValueFromCookie(cookies, "oauth_token")
        val refreshToken = extractValueFromCookie(cookies, "refresh_token") ?: ""

        if (!accessToken.isNullOrEmpty()) {
            tokenManager.saveTokens(accessToken, refreshToken)
            onSuccess()
        }
    }
}

private fun extractValueFromCookie(cookies: String, key: String): String? {
    return cookies.split(";")
        .map { it.trim() }
        .find { it.startsWith("$key=") }
        ?.substringAfter("$key=")
        ?.replace("\"", "")
        ?.trim()
}