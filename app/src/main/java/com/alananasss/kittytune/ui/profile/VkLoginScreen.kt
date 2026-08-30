package com.alananasss.kittytune.ui.profile

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.vk.VkTokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "VkLoginScreen"
// Kate Mobile client ID (2685278) allows audio scope without OAuth restrictions
private const val VK_OAUTH_URL =
    "https://oauth.vk.com/authorize?client_id=2685278&scope=audio,offline&redirect_uri=https://oauth.vk.com/blank.html&display=mobile&response_type=token&v=5.199"

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VkLoginScreen(
    onBackClick: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val tokenManager = remember { VkTokenManager(context) }
    val scope = rememberCoroutineScope()
    var webView: WebView? = null
    var authHandled = false

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.account_vk_details_title)) },
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
                        userAgentString =
                            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
                    }

                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    val handleAuthCheck: (String) -> Boolean = { targetUrl ->
                        if (!authHandled) {
                            checkAuthIntercept(
                                currentUrl = targetUrl,
                                scope = scope,
                                cookieManager = cookieManager,
                                onSuccess = { sid, nsid, dsid, tok, uId ->
                                    if (!authHandled && uId > 0L) {
                                        authHandled = true
                                        scope.launch(Dispatchers.IO) {
                                            var firstName = ""
                                            var lastName = ""
                                            var photoUrl: String? = null
                                            var screenName: String? = null

                                            if (!tok.isNullOrBlank()) {
                                                val urls = listOf(
                                                    "https://api.vk.ru/method/users.get?user_ids=$uId&fields=photo_max,screen_name,status&access_token=$tok&v=5.258",
                                                    "https://api.vk.com/method/users.get?user_ids=$uId&fields=photo_max,screen_name,status&access_token=$tok&v=5.258"
                                                )
                                                for (url in urls) {
                                                    try {
                                                        val req = okhttp3.Request.Builder()
                                                            .url(url)
                                                            .header("User-Agent", "VKAndroidApp/9.2.0-24200 (Android 11; SDK 30; arm64-v8a; Xiaomi M2003J15SC; ru; 2340x1080)")
                                                            .build()
                                                        val client = okhttp3.OkHttpClient()
                                                        client.newCall(req).execute().use { resp ->
                                                            if (resp.isSuccessful) {
                                                                val bodyStr = resp.body?.string() ?: ""
                                                                val json = org.json.JSONObject(bodyStr)
                                                                val respArr = json.optJSONArray("response")
                                                                val userObj = respArr?.optJSONObject(0)
                                                                if (userObj != null) {
                                                                    firstName = userObj.optString("first_name", "")
                                                                    lastName = userObj.optString("last_name", "")
                                                                    photoUrl = userObj.optString("photo_max", "").takeIf { it.isNotBlank() }
                                                                    screenName = userObj.optString("screen_name", "").takeIf { it.isNotBlank() }
                                                                }
                                                            }
                                                        }
                                                        if (firstName.isNotBlank() || lastName.isNotBlank()) break
                                                    } catch (e: Exception) {
                                                        Log.d(TAG, "users.get failed: ${e.message}")
                                                    }
                                                }
                                            }

                                            tokenManager.saveSession(
                                                remixsid = sid,
                                                remixnsid = nsid,
                                                remixdsid = dsid,
                                                userId = uId,
                                                accessToken = tok,
                                                firstName = firstName,
                                                lastName = lastName,
                                                photoUrl = photoUrl,
                                                screenName = screenName
                                            )

                                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.account_vk_login_success),
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                onLoginSuccess()
                                            }
                                        }
                                    }
                                }
                            )
                        } else false
                    }

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            if (isRedirectOrTokenUrl(url)) {
                                view?.stopLoading()
                                url?.let { handleAuthCheck(it) }
                            }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            if (isRedirectOrTokenUrl(url)) {
                                url?.let { handleAuthCheck(it) }
                            }
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val url = request?.url?.toString() ?: ""
                            if (isRedirectOrTokenUrl(url)) {
                                view?.stopLoading()
                                handleAuthCheck(url)
                                return true
                            }
                            return false
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: android.webkit.WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            val failingUrl = request?.url?.toString() ?: ""
                            if (isRedirectOrTokenUrl(failingUrl)) {
                                handleAuthCheck(failingUrl)
                            }
                        }
                    }

                    webView = this
                    loadUrl(VK_OAUTH_URL)
                }
            }
        )
    }

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }
}

private fun isRedirectOrTokenUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val uri = Uri.parse(url)
    val host = uri.host ?: ""
    val path = uri.path ?: ""
    val fragment = uri.fragment ?: ""

    // 1. blank.html redirect (must not be the initial authorize query string)
    if (path.endsWith("blank.html") && !url.contains("oauth.vk.com/authorize") && !url.contains("oauth.vk.ru/authorize")) {
        return true
    }

    // 2. Direct token in URL fragment
    if (fragment.contains("access_token=")) {
        return true
    }

    // 3. Reached feed or profile on vk.com / m.vk.com
    if ((host == "vk.com" || host == "m.vk.com" || host == "vk.ru" || host == "m.vk.ru") &&
        (path.startsWith("/feed") || (path.startsWith("/id") && path.removePrefix("/id").takeWhile { it.isDigit() }.isNotEmpty()))) {
        return true
    }

    return false
}

private fun checkAuthIntercept(
    currentUrl: String,
    scope: CoroutineScope,
    cookieManager: CookieManager,
    onSuccess: (remixsid: String?, remixnsid: String?, remixdsid: String?, token: String?, userId: Long) -> Unit
): Boolean {
    Log.d(TAG, "Checking URL for VK Auth: $currentUrl")
    cookieManager.flush()

    fun getAllVkCookies(): Map<String, String> {
        val domainList = listOf(
            "https://vk.com", "https://oauth.vk.com", "https://m.vk.com", "https://login.vk.com", "https://id.vk.com",
            "https://vk.ru", "https://oauth.vk.ru", "https://m.vk.ru", "https://login.vk.ru", "https://id.vk.ru"
        )
        val cookieMap = mutableMapOf<String, String>()
        for (domain in domainList) {
            val cookies = cookieManager.getCookie(domain) ?: continue
            cookies.split(";").forEach {
                val pair = it.trim().split("=", limit = 2)
                if (pair.size == 2 && pair[0].isNotBlank()) {
                    cookieMap[pair[0]] = pair[1]
                }
            }
        }
        val currentCookies = cookieManager.getCookie(currentUrl)
        if (!currentCookies.isNullOrBlank()) {
            currentCookies.split(";").forEach {
                val pair = it.trim().split("=", limit = 2)
                if (pair.size == 2 && pair[0].isNotBlank()) {
                    cookieMap[pair[0]] = pair[1]
                }
            }
        }
        return cookieMap
    }

    // 1. Check for OAuth Redirect callback (e.g. oauth.vk.com/blank.html#access_token=...&user_id=...)
    if ((currentUrl.contains("blank.html") || currentUrl.contains("access_token=")) &&
        !currentUrl.contains("oauth.vk.com/authorize") && !currentUrl.contains("oauth.vk.ru/authorize")) {
        try {
            val fragment = currentUrl.substringAfter("#", "")
            val queryParams = (fragment.ifBlank { currentUrl.substringAfter("?", "") })
                .split("&")
                .associate {
                    val pair = it.split("=", limit = 2)
                    if (pair.size == 2) pair[0] to pair[1] else pair[0] to ""
                }

            val token = queryParams["access_token"]
            var userId = queryParams["user_id"]?.toLongOrNull() ?: 0L

            if (userId == 0L && !token.isNullOrBlank()) {
                userId = extractUserIdFromJwt(token)
            }

            val cookieMap = getAllVkCookies()
            val remixsid = cookieMap["remixsid"]
            val remixnsid = cookieMap["remixnsid"]
            val remixdsid = cookieMap["remixdsid"]

            if (userId == 0L) {
                userId = cookieMap["remixmid"]?.toLongOrNull() ?: cookieMap["l"]?.toLongOrNull() ?: 0L
            }

            if (!token.isNullOrBlank() && userId > 0L) {
                Log.d(TAG, "OAuth Success: User ID $userId, token found")
                scope.launch(Dispatchers.Main) {
                    onSuccess(remixsid, remixnsid, remixdsid, token, userId)
                }
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing OAuth callback: ${e.message}", e)
        }
    }

    // 2. Check for Cookies & standard web feed navigation (vk.com/feed or vk.com/id123)
    val cookieMap = getAllVkCookies()
    val remixsid = cookieMap["remixsid"]
    val remixnsid = cookieMap["remixnsid"]
    val remixdsid = cookieMap["remixdsid"]

    if (!remixsid.isNullOrBlank() || !remixnsid.isNullOrBlank()) {
        var userId = cookieMap["remixmid"]?.toLongOrNull()
            ?: cookieMap["l"]?.toLongOrNull()
            ?: 0L

        if (userId == 0L) {
            val uri = Uri.parse(currentUrl)
            val host = uri.host ?: ""
            val path = uri.path ?: ""
            if (!host.contains("id.vk.") && path.startsWith("/id")) {
                userId = path.removePrefix("/id").takeWhile { it.isDigit() }.toLongOrNull() ?: 0L
            }
        }

        val isNotAuthPage = !currentUrl.contains("oauth.vk.com/authorize") && !currentUrl.contains("id.vk.com/auth")
        if (userId > 0L && isNotAuthPage && (currentUrl.contains("/feed") || currentUrl.contains("/id$userId") || currentUrl.contains("blank.html"))) {
            Log.d(TAG, "Cookie Login Success: userId=$userId")
            scope.launch(Dispatchers.Main) {
                onSuccess(remixsid, remixnsid, remixdsid, null, userId)
            }
            return true
        }
    }

    return false
}

private fun extractUserIdFromJwt(token: String): Long {
    return try {
        val parts = token.split(".")
        if (parts.size >= 2) {
            val payloadB64 = parts[1]
            val decoded = android.util.Base64.decode(payloadB64, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
            val json = org.json.JSONObject(String(decoded, Charsets.UTF_8))
            json.optLong("user_id", 0L).takeIf { it != 0L }
                ?: json.optLong("sub", 0L).takeIf { it != 0L }
                ?: json.optLong("id", 0L)
        } else 0L
    } catch (e: Exception) {
        0L
    }
}
