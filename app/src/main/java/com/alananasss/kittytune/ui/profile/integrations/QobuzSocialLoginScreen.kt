package com.alananasss.kittytune.ui.profile.integrations

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.AppWebView
import java.util.UUID

/**
 * The social sign-ins of the official Qobuz app.
 *
 * Qobuz does not take a password for those: it takes the token the provider
 * hands out — a Google identity token, a Facebook access token — and exchanges it
 * for its own `user_auth_token`. The provider's own page is opened here and the
 * token is read from the redirect it answers with.
 */
enum class QobuzSocialProvider {
    /** Facebook, whose login dialog works with the official client id as it is. */
    FACEBOOK,
}

/** What the provider's redirect carried back. */
data class QobuzSocialToken(
    val token: String,
    val userId: String? = null,
    val expiresAt: Long = 0L,
)

@Composable
fun QobuzSocialLoginScreen(
    provider: QobuzSocialProvider,
    onDismiss: () -> Unit,
    onToken: (QobuzSocialToken) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun handleUrl(url: String?): Boolean {
        val target = url ?: return false
        val fragment = target.substringAfter('#', "")
        val query = target.substringAfter('?', "")
        val params = (fragment + "&" + query)
            .split('&')
            .mapNotNull { part ->
                if (part.isBlank()) null
                else part.split('=', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            }
            .toMap()

        params["error"]?.let {
            val message = params["error_description"] ?: it
            error = message
            loading = false
            onError(message)
            return true
        }

        val accessToken = params["access_token"]
        if (!accessToken.isNullOrBlank()) {
            val expires = params["expires_in"]?.toLongOrNull() ?: 0L
            loading = false
            onToken(QobuzSocialToken(accessToken, expiresAt = expires))
            return true
        }
        return false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = "Close")
            }
            Text(
                text = "Sign in with Facebook",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (error != null) {
            Text(
                text = error!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        @SuppressLint("SetJavaScriptEnabled")
        AppWebView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            onCreated = { webView ->
                webView.settings.javaScriptEnabled = true
                webView.settings.domStorageEnabled = true
                // Some providers refuse embedded browsers; a desktop user agent is
                // what the official app ends up presenting too.
                webView.settings.userAgentString =
                    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                webView.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean = handleUrl(request?.url?.toString())

                    @Deprecated("Kept for API < 24")
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
                        handleUrl(url)
                }
                val (clientId, redirect) = when (provider) {
                    QobuzSocialProvider.FACEBOOK -> {
                        val configured = PlayerPreferences(context).getQobuzFacebookClientId()
                        val configuredRedirect = PlayerPreferences(context).getQobuzFacebookRedirect()
                        if (configured.isNotBlank() && configuredRedirect.isNotBlank()) {
                            configured to configuredRedirect
                        } else {
                            QobuzSocialIds.FACEBOOK_APP_ID to QobuzSocialIds.FACEBOOK_REDIRECT
                        }
                    }
                }
                webView.loadUrl(authUrl(provider, clientId, redirect))
            },
        )
    }
}

/**
 * The provider's own sign-in page.
 *
 * The client ids default to the ones the official Qobuz app ships with. Google
 * only accepts a custom-scheme redirect for a client of type "Android", which is
 * bound to the app that owns it — so a Google client registered for KittyTune
 * (type "Web" or "Desktop", with an https or loopback redirect) is the way to
 * make that button work. Facebook accepts the official client id as it is.
 */
private fun authUrl(provider: QobuzSocialProvider, clientId: String, redirect: String): String =
    when (provider) {
        QobuzSocialProvider.FACEBOOK -> {
            "https://www.facebook.com/v18.0/dialog/oauth" +
                "?client_id=$clientId" +
                "&redirect_uri=${Uri.encode(redirect)}" +
                "&response_type=token" +
                "&scope=email"
        }
    }
