package com.alananasss.kittytune.ui.profile.integrations

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
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
import com.alananasss.kittytune.R
import com.alananasss.kittytune.audio.providers.deezer.isDeezerCookieConfigured
import com.alananasss.kittytune.audio.providers.deezer.mergeDeezerCookieInputs
import com.alananasss.kittytune.data.local.PlayerPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val DEEZER_LOGIN_URL = "https://www.deezer.com/login"
private const val DEEZER_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36"

val DeezerCookieUrls =
    listOf(
        "https://www.deezer.com",
        "https://deezer.com",
        "https://account.deezer.com",
        "https://auth.deezer.com",
        "https://connect.deezer.com",
        "https://api.deezer.com",
    )

private val DeezerCookieCaptureDelaysMs = listOf(0L, 250L, 750L, 1_500L, 3_000L, 5_000L)

fun captureDeezerCookieString(): String? {
    val cm = CookieManager.getInstance()
    cm.flush()
    val inputs = DeezerCookieUrls.mapNotNull { cm.getCookie(it) }
    return mergeDeezerCookieInputs(inputs)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DeezerLoginScreen(
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }
    val initialCookie = remember { prefs.getDeezerCookie() }
    var mainWebView by remember { mutableStateOf<WebView?>(null) }
    var popupWebView by remember { mutableStateOf<WebView?>(null) }
    var savedVisible by remember { mutableStateOf(false) }
    var isRedirecting by remember { mutableStateOf(false) }
    val handler = remember { Handler(Looper.getMainLooper()) }

    fun checkAndSaveCookies(forceRedirect: Boolean = false) {
        val captured = captureDeezerCookieString() ?: return
        if (!isDeezerCookieConfigured(captured)) return

        prefs.setDeezerCookie(captured)
        savedVisible = true

        val shouldRedirect = forceRedirect || initialCookie.isBlank() || captured != initialCookie
        if (shouldRedirect && !isRedirecting) {
            isRedirecting = true
            popupWebView = null
            Toast.makeText(context, R.string.deezer_cookie_saved, Toast.LENGTH_SHORT).show()
            handler.postDelayed({
                onBackClick()
            }, 800L)
        }
    }

    fun finish() {
        checkAndSaveCookies()
        onBackClick()
    }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(1000)
            checkAndSaveCookies()
        }
    }

    BackHandler {
        val popup = popupWebView
        if (popup != null) {
            if (popup.canGoBack()) {
                popup.goBack()
            } else {
                popupWebView = null
                checkAndSaveCookies(forceRedirect = true)
            }
        } else {
            finish()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            handler.removeCallbacksAndMessages(null)
            runCatching {
                mainWebView?.stopLoading()
                mainWebView?.destroy()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.deezer_web_login)) },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = ::finish,
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
                            val captured = captureDeezerCookieString()
                            if (captured != null && isDeezerCookieConfigured(captured)) {
                                prefs.setDeezerCookie(captured)
                                Toast.makeText(context, R.string.deezer_cookie_saved, Toast.LENGTH_SHORT).show()
                                onBackClick()
                            } else {
                                Toast.makeText(context, R.string.deezer_cookie_not_configured, Toast.LENGTH_SHORT).show()
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
                        mainWebView = this
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            setSupportMultipleWindows(true)
                            javaScriptCanOpenWindowsAutomatically = true
                            userAgentString = DEEZER_USER_AGENT
                        }
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val url = request?.url?.toString() ?: return false
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

                            override fun onPageFinished(view: WebView, url: String?) {
                                super.onPageFinished(view, url)
                                CookieManager.getInstance().flush()
                                val isNavigatedAwayFromLogin = url != null &&
                                    !url.contains("/login", ignoreCase = true) &&
                                    !url.contains("/register", ignoreCase = true) &&
                                    !url.contains("account.deezer.com", ignoreCase = true)

                                DeezerCookieCaptureDelaysMs.forEach { delay ->
                                    handler.postDelayed(
                                        {
                                            checkAndSaveCookies(forceRedirect = isNavigatedAwayFromLogin)
                                        },
                                        delay
                                    )
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
                                        userAgentString = DEEZER_USER_AGENT
                                    }
                                    val cm = CookieManager.getInstance()
                                    cm.setAcceptCookie(true)
                                    cm.setAcceptThirdPartyCookies(this, true)

                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(v: WebView?, req: WebResourceRequest?): Boolean {
                                            val url = req?.url?.toString() ?: return false
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

                                        override fun onPageFinished(v: WebView?, url: String?) {
                                            super.onPageFinished(v, url)
                                            CookieManager.getInstance().flush()
                                            checkAndSaveCookies()
                                        }
                                    }

                                    webChromeClient = object : WebChromeClient() {
                                        override fun onCloseWindow(window: WebView?) {
                                            super.onCloseWindow(window)
                                            popupWebView = null
                                            checkAndSaveCookies(forceRedirect = true)
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

                        loadUrl(DEEZER_LOGIN_URL)
                    }
                },
                update = {}
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
                                checkAndSaveCookies(forceRedirect = true)
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
                visible = savedVisible,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = 6.dp,
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = stringResource(R.string.deezer_cookie_saved),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
