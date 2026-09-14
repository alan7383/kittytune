package com.alananasss.kittytune.ui.profile

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.PlaybackService
import com.alananasss.kittytune.data.discord.DiscordRemoteAuthManager
import com.alananasss.kittytune.data.discord.RemoteAuthState
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.my.kizzy.rpc.KizzyRPC
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val JS_SNIPPET = "javascript:(function()%7Bvar%20i%3Ddocument.createElement('iframe')%3Bdocument.body.appendChild(i)%3Balert(i.contentWindow.localStorage.token.slice(1,-1))%7D)()"

private fun launchDiscordDeepLink(context: Context, url: String) {
    val uri = Uri.parse(url)
    try {
        val directIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.discord")
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        context.startActivity(directIntent)
    } catch (_: Exception) {
        try {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                .build()
                .launchUrl(context, uri)
        } catch (_: Exception) {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }
}

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

    val authManager = remember { DiscordRemoteAuthManager() }
    val authState by authManager.state.collectAsState()

    var useWebView by rememberSaveable { mutableStateOf(false) }
    var showManualTokenDialog by remember { mutableStateOf(false) }
    var hasLaunchedDeepLink by rememberSaveable { mutableStateOf(false) }
    var manualTokenInput by remember { mutableStateOf("") }
    var manualTokenLoading by remember { mutableStateOf(false) }
    var manualTokenError by remember { mutableStateOf<String?>(null) }

    var webView: WebView? by remember { mutableStateOf(null) }

    DisposableEffect(Unit) {
        authManager.start()
        onDispose {
            authManager.cancel()
        }
    }

    // Auto-launch deep link when ready for the first time
    LaunchedEffect(authState) {
        if (authState is RemoteAuthState.Ready && !hasLaunchedDeepLink && !useWebView) {
            hasLaunchedDeepLink = true
            val ready = authState as RemoteAuthState.Ready
            launchDiscordDeepLink(context, ready.deepLinkUrl)
        } else if (authState is RemoteAuthState.Success) {
            val success = authState as RemoteAuthState.Success
            prefs.setDiscordToken(success.token)
            success.username?.let { prefs.setDiscordUsername(it) }
            prefs.setDiscordRpcEnabled(true)
            context.startService(Intent(context, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_FORCE_UPDATE
            })
            Toast.makeText(context, context.getString(R.string.discord_login_success), Toast.LENGTH_SHORT).show()
            onLoginSuccess()
        }
    }

    if (showManualTokenDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!manualTokenLoading) showManualTokenDialog = false
            },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text(stringResource(R.string.discord_manual_token_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.discord_manual_token_desc),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = manualTokenInput,
                        onValueChange = {
                            manualTokenInput = it
                            manualTokenError = null
                        },
                        placeholder = { Text(stringResource(R.string.discord_manual_token_placeholder)) },
                        singleLine = true,
                        isError = manualTokenError != null,
                        supportingText = manualTokenError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = manualTokenInput.trim().replace("\"", "")
                        if (clean.isBlank()) {
                            manualTokenError = context.getString(R.string.discord_manual_token_invalid)
                            return@Button
                        }
                        manualTokenLoading = true
                        scope.launch {
                            val userInfoResult = withContext(Dispatchers.IO) {
                                KizzyRPC.getUserInfo(clean)
                            }
                            manualTokenLoading = false
                            if (userInfoResult.isSuccess) {
                                val info = userInfoResult.getOrNull()
                                val uname = info?.name?.ifBlank { null } ?: info?.username
                                prefs.setDiscordToken(clean)
                                uname?.let { prefs.setDiscordUsername(it) }
                                prefs.setDiscordRpcEnabled(true)
                                context.startService(Intent(context, PlaybackService::class.java).apply {
                                    action = PlaybackService.ACTION_FORCE_UPDATE
                                })
                                showManualTokenDialog = false
                                Toast.makeText(context, context.getString(R.string.discord_login_success), Toast.LENGTH_SHORT).show()
                                onLoginSuccess()
                            } else {
                                manualTokenError = context.getString(R.string.discord_manual_token_invalid)
                            }
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                    enabled = !manualTokenLoading && manualTokenInput.isNotBlank()
                ) {
                    if (manualTokenLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(stringResource(R.string.btn_ok))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showManualTokenDialog = false },
                    shapes = ButtonDefaults.shapes(),
                    enabled = !manualTokenLoading
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.discord_login_title)) },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBackClick,
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
                    IconButton(
                        onClick = { showManualTokenDialog = true },
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Key,
                            contentDescription = stringResource(R.string.discord_manual_token_title)
                        )
                    }
                    IconButton(
                        onClick = { useWebView = !useWebView },
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(
                            imageVector = if (useWebView) Icons.Rounded.QrCode2 else Icons.Rounded.Language,
                            contentDescription = if (useWebView) stringResource(R.string.discord_login_use_remote) else stringResource(R.string.discord_login_use_webview)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = useWebView,
            label = "DiscordLoginModeTransition",
            modifier = Modifier.padding(innerPadding)
        ) { isWeb ->
            if (isWeb) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                tint = MaterialTheme.colorScheme.primary,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.discord_login_2fa_tip),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    AndroidView(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
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

                                if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_AUTHENTICATION)) {
                                    WebSettingsCompat.setWebAuthenticationSupport(
                                        settings,
                                        WebSettingsCompat.WEB_AUTHENTICATION_SUPPORT_FOR_APP
                                    )
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
                                                try {
                                                    val info = KizzyRPC.getUserInfo(cleanToken).getOrNull()
                                                    val uname = info?.name?.ifBlank { null } ?: info?.username
                                                    uname?.let { prefs.setDiscordUsername(it) }
                                                } catch (_: Exception) {}
                                                context.startService(Intent(context, PlaybackService::class.java).apply {
                                                    action = PlaybackService.ACTION_FORCE_UPDATE
                                                })
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
            } else {
                // Remote Auth Flow (Deep Link & QR Code)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(vertical = 32.dp)
                    ) {
                        when (val state = authState) {
                            is RemoteAuthState.Idle, is RemoteAuthState.Connecting -> {
                                ContainedLoadingIndicator()
                                Spacer(Modifier.height(24.dp))
                                Text(
                                    text = stringResource(R.string.discord_login_remote_waiting),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.discord_login_remote_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }

                            is RemoteAuthState.Ready -> {
                                if (state.qrBitmap != null) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = Color.White,
                                        shadowElevation = 4.dp,
                                        modifier = Modifier.padding(8.dp)
                                    ) {
                                        Image(
                                            bitmap = state.qrBitmap.asImageBitmap(),
                                            contentDescription = "Discord Login QR",
                                            modifier = Modifier
                                                .size(220.dp)
                                                .padding(12.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        )
                                    }
                                    Spacer(Modifier.height(20.dp))
                                }

                                Text(
                                    text = stringResource(R.string.discord_login_remote_waiting),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.discord_login_remote_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(24.dp))

                                Button(
                                    onClick = { launchDiscordDeepLink(context, state.deepLinkUrl) },
                                    shapes = ButtonDefaults.shapes(),
                                    modifier = Modifier.fillMaxWidth(0.85f)
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (hasLaunchedDeepLink) stringResource(R.string.discord_login_reopen_app) else stringResource(R.string.discord_login_open_app)
                                    )
                                }

                                Spacer(Modifier.height(12.dp))

                                OutlinedButton(
                                    onClick = { useWebView = true },
                                    shapes = ButtonDefaults.shapes(),
                                    modifier = Modifier.fillMaxWidth(0.85f)
                                ) {
                                    Icon(Icons.Rounded.Language, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.discord_login_use_webview))
                                }
                            }

                            is RemoteAuthState.UserScanned -> {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(24.dp))
                                Text(
                                    text = stringResource(R.string.discord_login_scanned, state.username),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }

                            is RemoteAuthState.Success -> {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.discord_login_success),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            is RemoteAuthState.Error -> {
                                Icon(
                                    imageVector = Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = { authManager.start() },
                                    shapes = ButtonDefaults.shapes()
                                ) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.account_action_refresh))
                                }
                                Spacer(Modifier.height(12.dp))
                                TextButton(
                                    onClick = { useWebView = true },
                                    shapes = ButtonDefaults.shapes()
                                ) {
                                    Text(stringResource(R.string.discord_login_use_webview))
                                }
                            }

                            is RemoteAuthState.Canceled -> {
                                Text(
                                    text = stringResource(R.string.discord_login_canceled),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = { authManager.start() },
                                    shapes = ButtonDefaults.shapes()
                                ) {
                                    Text(stringResource(R.string.account_action_refresh))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    BackHandler(enabled = useWebView && webView?.canGoBack() == true) {
        webView?.goBack()
    }
}
