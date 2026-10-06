package com.alananasss.kittytune.data.zapret

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.zapret.byedpi.ByeDpiProxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

enum class Reachability { REACHABLE, BLOCKED }

data class ServiceCheck(val service: ZapretService, val reachability: Reachability, val isCovered: Boolean)

data class ZapretState(
    val enabled: Boolean = false,
    val strategy: ZapretStrategy = ZapretStrategy.SPLIT2,
    val domains: Set<String> = emptySet(),
    val isPausedForVpn: Boolean = false,
)

/**
 * Android zapret / DPI circumvention powered by the native ByeDPI engine.
 *
 * Runs an embedded local SOCKS5 proxy on 127.0.0.1 in user-space without root or VPN.
 * Only connections to covered domains ([ZapretHostList]) are routed through the proxy by
 * [ProxyManager][com.alananasss.kittytune.data.network.ProxyManager], where TLS records
 * and TCP packets are desynced to defeat DPI boxes without breaking Conscrypt / BoringSSL.
 *
 * Automatically detects external active VPN connections (Telegram-style) and suspends
 * the local proxy to avoid port conflicts (e.g. port 10808) and unnecessary double proxying.
 */
object ZapretManager {
    private const val TAG = "ZapretManager"
    const val DEFAULT_PORT = 10898

    private val proxy = ByeDpiProxy()

    private val _state = MutableStateFlow(ZapretState())
    val state: StateFlow<ZapretState> = _state.asStateFlow()

    @Volatile
    private var prefs: PlayerPreferences? = null

    @Volatile
    private var vpnCallback: ConnectivityManager.NetworkCallback? = null

    /**
     * Direct connections only: the check probes whether the network lets a service through directly,
     * so it explicitly bypasses any proxy.
     */
    private val probeClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .followRedirects(false)
            .build()
    }

    private fun findAvailablePort(preferredPort: Int = DEFAULT_PORT): Int {
        try {
            ServerSocket(preferredPort).use { return it.localPort }
        } catch (_: Exception) {}
        try {
            ServerSocket(0).use { return it.localPort }
        } catch (_: Exception) {}
        return preferredPort
    }

    fun isVpnConnected(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val active = cm.activeNetwork
            if (active != null && cm.getNetworkCapabilities(active)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) {
                return true
            }
            cm.allNetworks.any { network ->
                cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to check VPN connectivity", e)
            false
        }
    }

    private fun registerVpnMonitor(context: Context) {
        if (vpnCallback != null) return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        try {
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.i(TAG, "VPN network available: $network")
                    onVpnStatusChanged(isVpn = true)
                }

                override fun onLost(network: Network) {
                    Log.i(TAG, "VPN network lost: $network")
                    val stillVpn = isVpnConnected(context)
                    if (!stillVpn) {
                        onVpnStatusChanged(isVpn = false)
                    }
                }
            }
            vpnCallback = callback
            cm.registerNetworkCallback(request, callback)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register VPN network callback", e)
        }
    }

    private fun onVpnStatusChanged(isVpn: Boolean) {
        val current = _state.value
        if (current.isPausedForVpn == isVpn) return
        Log.i(TAG, "VPN status changed: isVpn=$isVpn (zapretEnabled=${current.enabled})")
        _state.value = current.copy(isPausedForVpn = isVpn)
        CoroutineScope(Dispatchers.IO).launch {
            if (isVpn) {
                // VPN is active: stop ByeDPI proxy so port is freed and VPN tunnel handles everything
                proxy.stop()
            } else {
                // VPN was disconnected: resume ByeDPI proxy if Zapret was enabled
                if (_state.value.enabled) {
                    startProxyInternal(_state.value.strategy)
                }
            }
        }
    }

    fun init(context: Context) {
        val appContext = context.applicationContext
        val p = PlayerPreferences(appContext)
        prefs = p
        val isEnabled = p.getZapretEnabled()
        val strategy = ZapretStrategy.fromId(p.getZapretStrategy())
        val savedDomains = p.getZapretDomains()

        // If enabled, always merge ZapretServices.ALL_DOMAINS so updates and CDN domains
        // (like image covers) are never omitted even on existing installs.
        val domains = if (isEnabled) {
            val merged = (savedDomains + ZapretServices.ALL_DOMAINS).toSet()
            if (merged != savedDomains) {
                p.setZapretDomains(merged)
            }
            merged
        } else {
            savedDomains
        }

        val vpnActive = isVpnConnected(appContext)

        _state.value = ZapretState(
            enabled = isEnabled,
            strategy = strategy,
            domains = domains,
            isPausedForVpn = vpnActive,
        )

        registerVpnMonitor(appContext)

        if (isEnabled && !vpnActive) {
            CoroutineScope(Dispatchers.IO).launch {
                startProxyInternal(strategy)
            }
        }
        Log.i(TAG, "Initialized (enabled=$isEnabled, vpnActive=$vpnActive, domains=${domains.size})")
    }

    fun getLocalProxy(): Proxy? {
        val current = _state.value
        if (!current.enabled || current.isPausedForVpn) return null
        val port = proxy.boundPort.takeIf { it > 0 } ?: DEFAULT_PORT
        return Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("127.0.0.1", port))
    }

    fun getProxyForHost(host: String?): Proxy? {
        val current = _state.value
        if (!current.enabled || current.isPausedForVpn) return null
        val clean = host?.lowercase()?.trim()?.trimEnd('.') ?: return null
        if (clean.isBlank()) return null
        val effectiveDomains = current.domains + ZapretServices.ALL_DOMAINS
        return if (ZapretHostList.isCovered(clean, effectiveDomains)) {
            getLocalProxy()
        } else null
    }

    private suspend fun startProxyInternal(strategy: ZapretStrategy) {
        if (_state.value.isPausedForVpn) {
            Log.i(TAG, "Skipping proxy start: VPN is active")
            return
        }
        val port = findAvailablePort(DEFAULT_PORT)
        // Clean Linux desync without TLS record corruption (--tlsrec causes resets on CDNs like CloudFront/Cloudflare)
        val args = when (strategy) {
            ZapretStrategy.SPLIT2 -> arrayOf(
                "--ip", "127.0.0.1",
                "--port", port.toString(),
                "--disorder", "1",
                "--mod-http", "h,d"
            )
            ZapretStrategy.MULTI -> arrayOf(
                "--ip", "127.0.0.1",
                "--port", port.toString(),
                "--split", "1+s",
                "--disorder", "3+s",
                "--mod-http", "h,d"
            )
        }
        proxy.start(args, port)
    }

    /** A fresh OkHttp builder pre-configured with proxy routing and browser headers. */
    fun newBuilder(): OkHttpClient.Builder {
        val builder = OkHttpClient.Builder()
        com.alananasss.kittytune.data.network.ProxyManager.configureOkHttpClient(builder)
        return builder
    }

    /** A fresh OkHttp client. */
    fun newClient(): OkHttpClient = newBuilder().build()

    fun bypassSocketFactory(): SocketFactory = SocketFactory.getDefault()

    /** Retained for compatibility. Proxy routing is handled via ProxySelector. */
    fun applyTo(builder: OkHttpClient.Builder): OkHttpClient.Builder = builder

    fun isEnabled(): Boolean = _state.value.enabled

    fun isPausedForVpn(): Boolean = _state.value.isPausedForVpn

    fun setEnabled(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        val p = prefs ?: PlayerPreferences(appContext).also { prefs = it }
        p.setZapretEnabled(enabled)
        val curDomains = _state.value.domains
        val domains = if (enabled) {
            val merged = (curDomains + ZapretServices.ALL_DOMAINS).toSet()
            p.setZapretDomains(merged)
            merged
        } else {
            curDomains
        }
        _state.value = _state.value.copy(enabled = enabled, domains = domains)
        CoroutineScope(Dispatchers.IO).launch {
            if (enabled) {
                if (!_state.value.isPausedForVpn) {
                    startProxyInternal(_state.value.strategy)
                } else {
                    Log.i(TAG, "Zapret enabled, but paused because VPN is currently active.")
                }
            } else {
                proxy.stop()
            }
        }
    }

    fun setStrategy(context: Context, strategy: ZapretStrategy) {
        PlayerPreferences(context.applicationContext).setZapretStrategy(strategy.id)
        prefs?.setZapretStrategy(strategy.id)
        _state.value = _state.value.copy(strategy = strategy)
        if (_state.value.enabled && !_state.value.isPausedForVpn) {
            CoroutineScope(Dispatchers.IO).launch {
                proxy.stop()
                startProxyInternal(strategy)
            }
        }
    }

    fun coveredDomains(): Set<String> = _state.value.domains

    /** Whether [host] (or its parent domain) is in the bypass list — host lists match subdomains. */
    fun isCovered(host: String): Boolean {
        val covered = _state.value.domains + ZapretServices.ALL_DOMAINS
        if (covered.isEmpty()) return false
        return ZapretHostList.isCovered(host, covered)
    }

    /** Replaces the bypass domain list; returns the domains kept. */
    fun setDomains(context: Context, domains: Collection<String>): List<String> {
        val clean = domains.map { it.lowercase().trim().trimEnd('.') }.filter { it.isNotBlank() }.distinct().sorted()
        PlayerPreferences(context.applicationContext).setZapretDomains(clean.toSet())
        prefs?.setZapretDomains(clean.toSet())
        _state.value = _state.value.copy(domains = clean.toSet())
        return clean
    }

    /** Probes every service in parallel, with raw direct connections. */
    suspend fun check(): List<ServiceCheck> = coroutineScope {
        val covered = coveredDomains()
        ZapretServices.ALL.map { service ->
            async(Dispatchers.IO) {
                ServiceCheck(
                    service = service,
                    reachability = if (service.probeUrls.all { probe(it) == Reachability.REACHABLE }) Reachability.REACHABLE else Reachability.BLOCKED,
                    isCovered = service.domains.all { ZapretHostList.isCovered(it, covered) },
                )
            }
        }.awaitAll()
    }

    private fun probe(url: String): Reachability = runCatching {
        probeClient.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()).execute().use { response ->
            response.body.byteStream().use { input ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (total < PROBE_BYTES) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                }
            }
            Reachability.REACHABLE
        }
    }.getOrDefault(Reachability.BLOCKED)

    private const val PROBE_BYTES = 64L * 1024

    /** Adds the domains of [services] to the bypass list; returns the domains added. */
    suspend fun addDomains(context: Context, services: Collection<ZapretService>): List<String> = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val covered = coveredDomains().toMutableSet()
        val missing = services.flatMap { it.domains }
            .map { it.lowercase().trim().trimEnd('.') }.distinct()
            .filter { !ZapretHostList.isCovered(it, covered) }
        if (missing.isEmpty()) return@withContext emptyList()
        covered += missing
        setDomains(appContext, covered)
        missing
    }

    suspend fun removeOwnDomains(context: Context) = withContext(Dispatchers.IO) {
        setDomains(context.applicationContext, emptyList())
    }

    suspend fun autoConfigureOnce(context: Context): List<String> {
        val p = prefs ?: PlayerPreferences(context.applicationContext).also { prefs = it }
        if (p.getZapretAutoChecked() || !isEnabled()) return emptyList()
        val cur = coveredDomains()
        if (cur.isEmpty()) {
            val all = ZapretServices.ALL_DOMAINS
            setDomains(context, all)
            p.setZapretAutoChecked(true)
            return all.toList()
        }
        val results = check()
        val blocked = results.filter { it.reachability == Reachability.BLOCKED && !it.isCovered }.map { it.service }
        p.setZapretAutoChecked(true)
        return if (blocked.isEmpty()) emptyList() else addDomains(context, blocked)
    }

    fun exportAsHostList(): String =
        ZapretHostList.withOwnDomains("", _state.value.domains.sorted())
}
