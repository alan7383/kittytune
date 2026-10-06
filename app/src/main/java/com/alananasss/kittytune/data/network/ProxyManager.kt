package com.alananasss.kittytune.data.network

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.webkit.ProxyConfig as WebKitProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.zionhuang.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Authenticator as OkHttpAuthenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.TimeUnit

enum class ProxyProtocol {
    HTTP,
    SOCKS
}

data class ProxyConfig(
    val enabled: Boolean = false,
    val protocol: ProxyProtocol = ProxyProtocol.HTTP,
    val host: String = "",
    val port: Int = 8080,
    val authEnabled: Boolean = false,
    val username: String = "",
    val password: String = ""
) {
    fun isValid(): Boolean = host.isNotBlank() && port in 1..65535
}

data class ProxyProfile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val config: ProxyConfig
)

sealed class ProxyTestResult {
    data class Success(val pingMs: Long) : ProxyTestResult()
    data class Error(val message: String) : ProxyTestResult()
}

object ProxyManager {

    private const val TAG = "ProxyManager"

    private val initialDefaultProxySelector: ProxySelector? = ProxySelector.getDefault()

    private val _proxyConfigFlow = MutableStateFlow(ProxyConfig())
    val proxyConfigFlow: StateFlow<ProxyConfig> = _proxyConfigFlow.asStateFlow()

    @Volatile
    private var activeJavaProxy: Proxy? = null

    @Volatile
    private var activeProxyAuthenticator: OkHttpAuthenticator? = null

    private val appProxySelector = object : ProxySelector() {
        override fun select(uri: URI?): List<Proxy> {
            val host = uri?.host?.lowercase()
            val zapretProxy = com.alananasss.kittytune.data.zapret.ZapretManager.getProxyForHost(host)
            if (zapretProxy != null) {
                return listOf(zapretProxy)
            }
            val userProxy = activeJavaProxy
            if (userProxy != null) {
                return listOf(userProxy)
            }
            return initialDefaultProxySelector?.select(uri) ?: listOf(Proxy.NO_PROXY)
        }

        override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
            Log.w(TAG, "Proxy connection failed for URI: $uri", ioe)
        }
    }

    fun init(context: Context) {
        ProxySelector.setDefault(appProxySelector)
        applyConfiguration(context.applicationContext)
    }

    fun getActiveProxy(): Proxy? = activeJavaProxy

    fun getActiveConfig(): ProxyConfig = _proxyConfigFlow.value

    fun applyConfiguration(context: Context) {
        val prefs = PlayerPreferences(context)
        val enabled = prefs.getProxyEnabled()
        val typeStr = prefs.getProxyType()
        val protocol = if (typeStr.equals("SOCKS", ignoreCase = true)) ProxyProtocol.SOCKS else ProxyProtocol.HTTP
        val host = prefs.getProxyHost().trim()
        val port = prefs.getProxyPort()
        val authEnabled = prefs.getProxyAuthEnabled()
        val username = prefs.getProxyUsername().trim()
        val password = prefs.getProxyPassword()

        val config = ProxyConfig(
            enabled = enabled,
            protocol = protocol,
            host = host,
            port = port,
            authEnabled = authEnabled,
            username = username,
            password = password
        )

        _proxyConfigFlow.value = config

        if (config.enabled && config.isValid()) {
            val proxyType = if (config.protocol == ProxyProtocol.SOCKS) Proxy.Type.SOCKS else Proxy.Type.HTTP
            val socketAddress = InetSocketAddress.createUnresolved(config.host, config.port)
            val javaProxy = Proxy(proxyType, socketAddress)
            activeJavaProxy = javaProxy

            if (config.authEnabled && config.username.isNotEmpty()) {
                activeProxyAuthenticator = OkHttpAuthenticator { _, response ->
                    val credential = Credentials.basic(config.username, config.password)
                    response.request.newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build()
                }

                Authenticator.setDefault(object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(config.username, config.password.toCharArray())
                    }
                })
            } else {
                activeProxyAuthenticator = null
                Authenticator.setDefault(null)
            }

            ProxySelector.setDefault(appProxySelector)

            // System properties for legacy/standard Java network libraries
            if (config.protocol == ProxyProtocol.HTTP) {
                System.setProperty("http.proxyHost", config.host)
                System.setProperty("http.proxyPort", config.port.toString())
                System.setProperty("https.proxyHost", config.host)
                System.setProperty("https.proxyPort", config.port.toString())
                System.clearProperty("socksProxyHost")
                System.clearProperty("socksProxyPort")
            } else {
                System.setProperty("socksProxyHost", config.host)
                System.setProperty("socksProxyPort", config.port.toString())
                System.clearProperty("http.proxyHost")
                System.clearProperty("http.proxyPort")
                System.clearProperty("https.proxyHost")
                System.clearProperty("https.proxyPort")
            }

            // Apply WebKit ProxyOverride for WebViews
            try {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                    val scheme = if (config.protocol == ProxyProtocol.SOCKS) "socks5://" else "http://"
                    val proxyUrl = "$scheme${config.host}:${config.port}"
                    val webKitConfig = WebKitProxyConfig.Builder()
                        .addProxyRule(proxyUrl)
                        .build()
                    ProxyController.getInstance().setProxyOverride(webKitConfig, { Log.d(TAG, "WebKit Proxy applied: $proxyUrl") }, {})
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to apply WebKit Proxy override", e)
            }

            // InnerTube proxy support
            try {
                YouTube.proxy = javaProxy
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set YouTube proxy", e)
            }

            Log.i(TAG, "Proxy applied successfully: ${config.protocol} ${config.host}:${config.port} (auth=${config.authEnabled})")
        } else {
            activeJavaProxy = null
            activeProxyAuthenticator = null

            // Keep appProxySelector active so Zapret continues to work
            ProxySelector.setDefault(appProxySelector)
            Authenticator.setDefault(null)

            System.clearProperty("http.proxyHost")
            System.clearProperty("http.proxyPort")
            System.clearProperty("https.proxyHost")
            System.clearProperty("https.proxyPort")
            System.clearProperty("socksProxyHost")
            System.clearProperty("socksProxyPort")

            try {
                if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                    ProxyController.getInstance().clearProxyOverride({ Log.d(TAG, "WebKit Proxy cleared") }, {})
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear WebKit Proxy override", e)
            }

            try {
                YouTube.proxy = null
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear YouTube proxy", e)
            }

            Log.i(TAG, "Proxy disabled / reverted to direct connection.")
        }

        // Reset RetrofitClient singleton to recreate with new proxy settings
        RetrofitClient.resetClient()
        // The shared client carries the old proxy, so it has to go with it.
        sharedClient = null
        try {
            YouTube.okHttpClient = getOkHttpClient(context)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update YouTube OkHttpClient", e)
        }
    }

    fun configureOkHttpClient(builder: OkHttpClient.Builder, context: Context? = null): OkHttpClient.Builder {
        // In-app DPI bypass (zapret): installed unconditionally, the policy inside is a dynamic
        // no-op until the user enables it, so toggling needs no client rebuild.
        com.alananasss.kittytune.data.zapret.ZapretManager.applyTo(builder)
        // Never bake `builder.proxy(...)` here. A baked proxy is a static snapshot: every client
        // built while the proxy was enabled — Retrofit's cached client, SocialProofRepository's
        // cached API, ViewModels' `api` fields, lyrics lazy clients — keeps dialing the dead proxy
        // after it is disabled (ECONNREFUSED on 127.0.0.1:1080) until the process dies. The app
        // selector below reads the @Volatile proxy state (zapret first, then the user proxy) on
        // every new connection, so toggling applies to already-built clients immediately. SOCKS
        // auth still works via the global `Authenticator`; HTTP 407 via the builder authenticator.
        builder.proxySelector(appProxySelector)
        activeProxyAuthenticator?.let { builder.proxyAuthenticator(it) }
        builder.addInterceptor { chain ->
            val request = chain.request()
            if (request.header("User-Agent").isNullOrBlank()) {
                chain.proceed(
                    request.newBuilder()
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                        .build()
                )
            } else {
                chain.proceed(request)
            }
        }
        return builder
    }

    /**
     * One client per proxy configuration, shared by every caller. This used to build a fresh
     * [OkHttpClient] per call, and its own connection pool and dispatcher with it: the stream
     * resolver reaches this several times per track resolution, and the lyrics translator once per
     * line, so every one of those requests paid a full TCP+TLS handshake instead of reusing a warm
     * socket and left a dispatcher thread behind.
     *
     * Safe to cache: [configureOkHttpClient] only reads the proxy fields, which are both
     * `@Volatile`, and its `context` parameter is unused. Cleared by [applyProxy] when the proxy
     * changes, so a stale client is never reused under a new configuration.
     */
    @Volatile
    private var sharedClient: OkHttpClient? = null

    fun getOkHttpClient(context: Context? = null): OkHttpClient {
        sharedClient?.let { return it }
        return synchronized(this) {
            sharedClient ?: run {
                val builder = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                configureOkHttpClient(builder, context).build().also { sharedClient = it }
            }
        }
    }

    suspend fun testProxyConnection(config: ProxyConfig): ProxyTestResult = withContext(Dispatchers.IO) {
        if (!config.isValid()) {
            return@withContext ProxyTestResult.Error("Invalid host or port")
        }

        try {
            val proxyType = if (config.protocol == ProxyProtocol.SOCKS) Proxy.Type.SOCKS else Proxy.Type.HTTP
            val socketAddress = InetSocketAddress.createUnresolved(config.host, config.port)
            val testProxy = Proxy(proxyType, socketAddress)

            val builder = OkHttpClient.Builder()
                .proxy(testProxy)
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .writeTimeout(8, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)

            if (config.authEnabled && config.username.isNotEmpty()) {
                builder.proxyAuthenticator { _, response ->
                    val credential = Credentials.basic(config.username, config.password)
                    response.request.newBuilder()
                        .header("Proxy-Authorization", credential)
                        .build()
                }
            }

            val testClient = builder.build()

            val testRequest = Request.Builder()
                .url("https://api-v2.soundcloud.com/")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .head()
                .build()

            val startTime = SystemClock.elapsedRealtime()
            val response = testClient.newCall(testRequest).execute()
            val latency = SystemClock.elapsedRealtime() - startTime
            response.close()

            ProxyTestResult.Success(latency)
        } catch (e: Exception) {
            Log.w(TAG, "Proxy test failed", e)
            ProxyTestResult.Error(e.localizedMessage ?: e.message ?: "Connection timed out")
        }
    }

    fun parseProxyUri(raw: String): ProxyConfig? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null

        try {
            // Telegram proxy scheme: tg://socks?server=...&port=...
            if (trimmed.startsWith("tg://socks", ignoreCase = true) || trimmed.startsWith("tg://proxy", ignoreCase = true)) {
                val uri = android.net.Uri.parse(trimmed)
                val server = uri.getQueryParameter("server") ?: uri.getQueryParameter("host") ?: ""
                val port = uri.getQueryParameter("port")?.toIntOrNull() ?: 1080
                val user = uri.getQueryParameter("user") ?: uri.getQueryParameter("username") ?: ""
                val pass = uri.getQueryParameter("pass") ?: uri.getQueryParameter("password") ?: ""
                if (server.isNotBlank()) {
                    return ProxyConfig(
                        enabled = true,
                        protocol = ProxyProtocol.SOCKS,
                        host = server,
                        port = port,
                        authEnabled = user.isNotBlank(),
                        username = user,
                        password = pass
                    )
                }
            }

            // Standard scheme URI: (socks5://, socks://, http://, https://)
            val schemeRegex = Regex("^(socks5|socks|http|https)://", RegexOption.IGNORE_CASE)
            val hasScheme = schemeRegex.containsMatchIn(trimmed)

            val uriString = if (hasScheme) trimmed else "http://$trimmed"
            val javaUri = URI(uriString)

            val scheme = javaUri.scheme?.lowercase() ?: "http"
            val protocol = if (scheme.startsWith("socks")) ProxyProtocol.SOCKS else ProxyProtocol.HTTP

            val host = javaUri.host ?: ""
            val port = if (javaUri.port != -1) javaUri.port else if (protocol == ProxyProtocol.SOCKS) 1080 else 8080

            var user = ""
            var pass = ""
            val userInfo = javaUri.userInfo
            if (!userInfo.isNullOrBlank()) {
                val parts = userInfo.split(":", limit = 2)
                user = parts.getOrNull(0) ?: ""
                pass = parts.getOrNull(1) ?: ""
            }

            if (host.isNotBlank() && port in 1..65535) {
                return ProxyConfig(
                    enabled = true,
                    protocol = protocol,
                    host = host,
                    port = port,
                    authEnabled = user.isNotBlank(),
                    username = user,
                    password = pass
                )
            }

            // Colon-separated fallback: host:port:user:pass or host:port
            val colonParts = trimmed.split(":")
            if (colonParts.size >= 2) {
                val hostPart = colonParts[0].trim()
                val portPart = colonParts[1].toIntOrNull()
                if (hostPart.isNotBlank() && portPart != null && portPart in 1..65535) {
                    val userPart = colonParts.getOrNull(2)?.trim() ?: ""
                    val passPart = colonParts.getOrNull(3)?.trim() ?: ""
                    return ProxyConfig(
                        enabled = true,
                        protocol = ProxyProtocol.HTTP,
                        host = hostPart,
                        port = portPart,
                        authEnabled = userPart.isNotBlank(),
                        username = userPart,
                        password = passPart
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed parsing proxy URI: $trimmed", e)
        }

        return null
    }
}
