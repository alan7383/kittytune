    package com.alananasss.kittytune.ui.profile
    
    import android.annotation.SuppressLint
    import android.graphics.Bitmap
    import android.view.View
    import android.view.ViewGroup
    import android.webkit.CookieManager
    import android.webkit.JsResult
    import android.webkit.WebChromeClient
    import android.webkit.WebView
    import android.webkit.WebViewClient
    import android.webkit.WebStorage
    import androidx.activity.compose.BackHandler
    import androidx.compose.foundation.layout.fillMaxSize
    import androidx.compose.ui.res.stringResource
    import androidx.compose.foundation.layout.padding
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.automirrored.rounded.ArrowBack
    import androidx.compose.material3.*
    import androidx.compose.runtime.Composable
    import androidx.compose.runtime.remember
    import androidx.compose.runtime.rememberCoroutineScope
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.platform.LocalContext
    import androidx.compose.ui.viewinterop.AndroidView
    import com.alananasss.kittytune.data.local.PlayerPreferences
    import com.alananasss.kittytune.R
    import kotlinx.coroutines.launch
    
    const val JS_SNIPPET = "javascript:(function()%7Bvar%20i%3Ddocument.createElement('iframe')%3Bdocument.body.appendChild(i)%3Balert(i.contentWindow.localStorage.token.slice(1,-1))%7D)()"
    
    @SuppressLint("SetJavaScriptEnabled")
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun DiscordLoginScreen(
        onBackClick: () -> Unit,
        onLoginSuccess: () -> Unit
    ) {
        val context = LocalContext.current
        val prefs = remember { PlayerPreferences(context) }
        val scope = rememberCoroutineScope()
        var webView: WebView? = null
    
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.discord_login_title)) },
                    navigationIcon = {
                        IconButton(
                            onClick = onBackClick,
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                        }
                    }
                )
            }
        ) { innerPadding ->
            AndroidView(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
    
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                        }
    
                        CookieManager.getInstance().apply {
                            removeAllCookies(null)
                            flush()
                        }
                        WebStorage.getInstance().deleteAllData()
    
                        webChromeClient = object : WebChromeClient() {
                            override fun onJsAlert(
                                view: WebView?,
                                url: String?,
                                message: String?,
                                result: JsResult?
                            ): Boolean {
                                if (!message.isNullOrEmpty() && message != "null") {
                                    scope.launch {
                                        val cleanToken = message.replace("\"", "")
                                        prefs.setDiscordToken(cleanToken)
                                        prefs.setDiscordRpcEnabled(true)
                                        onLoginSuccess()
                                    }
                                    result?.confirm()
                                    return true
                                }
                                return super.onJsAlert(view, url, message, result)
                            }
                        }
    
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                if (url?.contains("discord.com/app") == true || url?.contains("discord.com/channels/@me") == true) {
                                    view?.loadUrl(JS_SNIPPET)
                                }
                            }
    
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                if (url?.contains("discord.com/app") == true || url?.contains("discord.com/channels/@me") == true) {
                                    view?.loadUrl(JS_SNIPPET)
                                    visibility = View.GONE
                                }
                            }
                        }
    
                        webView = this
                        loadUrl("https://discord.com/login")
                    }
                }
            )
        }
    
        BackHandler(enabled = webView?.canGoBack() == true) {
            webView?.goBack()
        }
    }
    


