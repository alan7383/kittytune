package com.alananasss.kittytune.ui.login

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.AuthFlowManager
import com.alananasss.kittytune.data.GuestDataTransferManager
import kotlinx.coroutines.launch
import com.alananasss.kittytune.data.PkceHelper
import com.alananasss.kittytune.data.SessionManager
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.utils.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val TAG = "LoginScreen"

/**
 * Auth URL base used by SoundCloud's official app.
 * Extracted from: zc0/i.smali -> AUTH_URL.
 */
private const val AUTH_BASE_URL = "https://secure.soundcloud.com/"
private const val OFFICIAL_APP_ID = 3152
private const val AUTH_PREFS_NAME = "soundcloud_auth_flow"
private const val KEY_AUTH_DEVICE_ID = "soundcloud_device_id"

/**
 * Custom scheme redirect URI supported by SoundCloud's official app.
 * Extracted from: zc0/i.smali -> AUTH_URL_REDIRECT_URI_TO_SPARE.
 */
private const val REDIRECT_URI = "sc://auth"

private const val AUTH_API_BASE = "https://api-auth.soundcloud.com"

private val authHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .writeTimeout(15, TimeUnit.SECONDS)
    .build()

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val tokenManager = remember { TokenManager(context) }
    val pkceVerifier = remember { PkceHelper.generateVerifier() }
    val pkceChallenge = remember(pkceVerifier) { PkceHelper.generateChallenge(pkceVerifier) }
    val deviceId = remember { getOrCreateSoundCloudDeviceId(context) }
    val authUrl = remember(pkceChallenge, deviceId) {
        buildAuthUrl(
            clientId = Config.OFFICIAL_CLIENT_ID,
            deviceId = deviceId,
            trackingAnonymousId = deviceId,
            codeChallenge = pkceChallenge,
            isSignup = false
        )
    }

    var isLoading by remember { mutableStateOf(false) }
    var lastHandledCode by remember { mutableStateOf<String?>(null) }
    val authCodeFromIntent by AuthFlowManager.authCode.collectAsState()

    var hasLaunchedBrowser by rememberSaveable { mutableStateOf(false) }
    var showTransferDialog by remember { mutableStateOf(false) }
    var isTransferring by remember { mutableStateOf(false) }
    var transferProgress by remember { mutableFloatStateOf(0f) }
    var guestSummary by remember { mutableStateOf<com.alananasss.kittytune.data.GuestDataSummary?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(authUrl) {
        if (!hasLaunchedBrowser) {
            hasLaunchedBrowser = true
            launchSoundCloudAuth(context, authUrl)
        }
    }

    LaunchedEffect(authCodeFromIntent) {
        val code = authCodeFromIntent
        if (code != null && code != lastHandledCode) {
            lastHandledCode = code
            isLoading = true

            val success = withContext(Dispatchers.IO) {
                exchangeCodeForTokens(
                    code = code,
                    codeVerifier = pkceVerifier,
                    clientId = Config.OFFICIAL_CLIENT_ID,
                    tokenManager = tokenManager,
                    context = context
                )
            }

            isLoading = false
            AuthFlowManager.clearAuthCode()

            if (success) {
                SessionManager.harvestStoredSession(context)
                SessionManager.requestSessionRefresh(context, force = true)

                val summary = GuestDataTransferManager.getGuestDataSummary(context)
                if (summary.hasData) {
                    guestSummary = summary
                    showTransferDialog = true
                } else {
                    onLoginSuccess()
                }
            } else {
                Log.w(TAG, "Token exchange failed after OAuth callback")
            }
        }
    }

    if (showTransferDialog && guestSummary != null) {
        TransferGuestDataDialog(
            summary = guestSummary!!,
            isTransferring = isTransferring,
            progress = transferProgress,
            onTransfer = { transferLikes, transferUserPlaylists, transferLikedPlaylists ->
                isTransferring = true
                coroutineScope.launch {
                    val ok = GuestDataTransferManager.transferData(
                        context = context,
                        transferLikes = transferLikes,
                        transferUserPlaylists = transferUserPlaylists,
                        transferLikedPlaylists = transferLikedPlaylists,
                        onProgress = { transferProgress = it }
                    )
                    isTransferring = false
                    showTransferDialog = false
                    val msgRes = if (ok) R.string.transfer_guest_success else R.string.transfer_guest_error
                    android.widget.Toast.makeText(context, context.getString(msgRes), android.widget.Toast.LENGTH_LONG).show()
                    onLoginSuccess()
                }
            },
            onDismiss = {
                showTransferDialog = false
                coroutineScope.launch {
                    GuestDataTransferManager.clearGuestData(context)
                    onLoginSuccess()
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.login_title)) },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBackClick,
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.btn_cancel)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                ContainedLoadingIndicator()
                Spacer(modifier = Modifier.height(24.dp))

                if (isLoading) {
                    Text(
                        text = stringResource(R.string.login_auth_in_progress),
                        style = MaterialTheme.typography.titleMedium
                    )
                } else {
                    Text(
                        text = stringResource(R.string.login_waiting_browser),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.login_no_browser_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(onClick = { launchSoundCloudAuth(context, authUrl) }) {
                        Text(stringResource(R.string.login_reopen_browser))
                    }
                }
            }
        }
    }
}

/**
 * Builds the auth URL like SoundCloud's official app.
 * Reverse-engineered from: zc0/i.smali -> createAuthUrl().
 */
private fun buildAuthUrl(
    clientId: String,
    deviceId: String,
    trackingAnonymousId: String,
    codeChallenge: String,
    isSignup: Boolean
): String {
    val startView = if (isSignup) "create_account" else "sign_in"
    val locale = Locale.getDefault().language

    return buildString {
        append(AUTH_BASE_URL).append("web-auth?")
        append("client_id=").append(Uri.encode(clientId))
        append("&app_id=").append(OFFICIAL_APP_ID)
        append("&device_id=").append(Uri.encode(deviceId))
        append("&start_view=").append(Uri.encode(startView))
        append("&redirect_uri=").append(Uri.encode(REDIRECT_URI))
        append("&response_type=code")
        append("&code_challenge=").append(Uri.encode(codeChallenge))
        append("&code_challenge_method=S256")
        append("&ui_evo=true")
        append("&stand_alone=true")
        append("&tracking=local")
        append("&show_confirmation=true")
        append("&theme=dark")
        append("&locale=").append(Uri.encode(locale))
        append("&sc_tracking_anonymous_id=").append(Uri.encode(trackingAnonymousId))
    }
}

private fun launchSoundCloudAuth(context: Context, authUrl: String) {
    val uri = Uri.parse(authUrl)

    try {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .build()
            .launchUrl(context, uri)
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No Custom Tabs provider, falling back to ACTION_VIEW", e)
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
        )
    }
}

private fun getOrCreateSoundCloudDeviceId(context: Context): String {
    return Config.getOrCreateSoundCloudDeviceId(context)
}

/**
 * Exchanges an authorization code for access and refresh tokens.
 * Reverse-engineered from: yc0/d.smali and zc0/i.smali -> postRequest().
 */
private fun exchangeCodeForTokens(
    code: String,
    codeVerifier: String,
    clientId: String,
    tokenManager: TokenManager,
    context: Context
): Boolean {
    val tokenUrls = listOf(
        "$AUTH_API_BASE/oauth/token",
        "${Config.BASE_URL.trimEnd('/')}/oauth/token"
    ).distinct()

    for (tokenUrl in tokenUrls) {
        try {
            val formBody = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("client_id", clientId)
                .add("code", code)
                .add("redirect_uri", REDIRECT_URI)
                .add("code_verifier", codeVerifier)
                .build()

            val requestBuilder = Request.Builder()
                .url(tokenUrl)
                .header("User-Agent", Config.USER_AGENT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Origin", "https://soundcloud.com")
                .header("Referer", "https://soundcloud.com/")

            if (clientId == Config.OFFICIAL_CLIENT_ID) {
                requestBuilder.header("Authorization", Config.OFFICIAL_CLIENT_SIGNATURE)
            }

            val request = requestBuilder.post(formBody).build()
            Log.d(TAG, "Exchanging code at: $tokenUrl")

            val response = authHttpClient.newCall(request).execute()
            try {
                val bodyStr = response.body.string()
                Log.d(TAG, "Token response code: ${response.code}")

                if (!response.isSuccessful) {
                    Log.w(TAG, "Token exchange failed at $tokenUrl: ${response.code} - $bodyStr")
                    continue
                }

                val json = JSONObject(bodyStr)
                val accessToken = json.optString("access_token", "").cleanOAuthValue()
                val refreshToken = json.optString("refresh_token", "").cleanOAuthValue()
                val expiresIn = json.optLong("expires_in", 0L)
                val scope = json.optString("scope", "").cleanOAuthValue()

                if (accessToken.isNullOrEmpty()) {
                    Log.w(TAG, "Token exchange returned empty access_token")
                    continue
                }

                Log.d(
                    TAG,
                    "Got access_token (${accessToken.take(8)}...), " +
                        "refresh_token: ${!refreshToken.isNullOrEmpty()}, " +
                        "expires_in: $expiresIn, scope: $scope"
                )

                tokenManager.saveTokens(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    expiresInSeconds = if (expiresIn > 0) expiresIn else null,
                    scope = scope
                )

                setCookiesForToken(accessToken, refreshToken)
                return true
            } finally {
                response.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token exchange error at $tokenUrl", e)
        }
    }

    return false
}

private fun setCookiesForToken(accessToken: String, refreshToken: String?) {
    val cookieManager = CookieManager.getInstance()
    val domains = listOf(
        "https://soundcloud.com",
        "https://m.soundcloud.com",
        "https://api-v2.soundcloud.com",
        "https://api-auth.soundcloud.com",
        "https://secure.soundcloud.com"
    )

    for (domain in domains) {
        cookieManager.setCookie(domain, "oauth_token=$accessToken; Path=/; Secure")
        if (!refreshToken.isNullOrEmpty()) {
            cookieManager.setCookie(domain, "refresh_token=$refreshToken; Path=/; Secure")
        }
    }
    cookieManager.flush()
}

private fun String?.cleanOAuthValue(): String? = this
    ?.trim()
    ?.trim('"')
    ?.takeIf { it.isNotBlank() && it != "null" }
