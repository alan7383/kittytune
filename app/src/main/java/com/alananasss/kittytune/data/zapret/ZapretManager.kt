package com.alananasss.kittytune.data.zapret

import android.content.Context
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
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

enum class Reachability { REACHABLE, BLOCKED }

data class ServiceCheck(val service: ZapretService, val reachability: Reachability, val isCovered: Boolean)

data class ZapretState(
    val enabled: Boolean = false,
    val strategy: ZapretStrategy = ZapretStrategy.SPLIT2,
    val domains: Set<String> = emptySet(),
)

/**
 * Android zapret / DPI circumvention powered by the native ByeDPI engine.
 *
 * Runs an embedded local SOCKS5 proxy on 127.0.0.1 in user-space without root or VPN.
 * Only connections to covered domains ([ZapretHostList]) are routed through the proxy by
 * [ProxyManager][com.alananasss.kittytune.data.network.ProxyManager], where TLS records
 * and TCP packets are desynced to defeat DPI boxes without breaking Conscrypt / BoringSSL.
 */
object ZapretManager {
    private const val TAG = "ZapretManager"
    const val DEFAULT_PORT = 10808

    private val proxy = ByeDpiProxy()

    private val _state = MutableStateFlow(ZapretState())
    val state: StateFlow<ZapretState> = _state.asStateFlow()

    @Volatile
    private var prefs: PlayerPreferences? = null

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

    fun init(context: Context) {
        val p = PlayerPreferences(context.applicationContext)
        prefs = p
        val isEnabled = p.getZapretEnabled()
        val strategy = ZapretStrategy.fromId(p.getZapretStrategy())
        val domains = p.getZapretDomains()

        _state.value = ZapretState(
            enabled = isEnabled,
            strategy = strategy,
            domains = domains,
        )

        if (isEnabled) {
            CoroutineScope(Dispatchers.IO).launch {
                startProxyInternal(strategy)
            }
        }
        Log.i(TAG, "Initialized (enabled=$isEnabled, domains=${domains.size})")
    }

    fun getLocalProxy(): Proxy? {
        val current = _state.value
        if (!current.enabled || !proxy.isRunning) return null
        val port = proxy.boundPort.takeIf { it > 0 } ?: DEFAULT_PORT
        return Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("127.0.0.1", port))
    }

    fun getProxyForHost(host: String?): Proxy? {
        val current = _state.value
        if (!current.enabled || !proxy.isRunning) return null
        val clean = host?.lowercase()?.trim()?.trimEnd('.') ?: return null
        if (clean.isBlank()) return null
        return if (ZapretHostList.isCovered(clean, current.domains)) {
            getLocalProxy()
        } else null
    }

    private suspend fun startProxyInternal(strategy: ZapretStrategy) {
        val port = DEFAULT_PORT
        val args = when (strategy) {
            ZapretStrategy.SPLIT2 -> arrayOf("--ip", "127.0.0.1", "--port", port.toString(), "--split", "2", "--tlsrec", "1+s")
            ZapretStrategy.MULTI -> arrayOf("--ip", "127.0.0.1", "--port", port.toString(), "--split", "1+s", "--disorder", "1", "--tlsrec", "1+s")
        }
        proxy.start(args, port)
    }

    /** A fresh OkHttp builder. */
    fun newBuilder(): OkHttpClient.Builder = OkHttpClient.Builder()

    /** A fresh OkHttp client. */
    fun newClient(): OkHttpClient = newBuilder().build()

    fun bypassSocketFactory(): SocketFactory = SocketFactory.getDefault()

    /** Retained for compatibility. Proxy routing is handled via ProxySelector. */
    fun applyTo(builder: OkHttpClient.Builder): OkHttpClient.Builder = builder

    fun isEnabled(): Boolean = _state.value.enabled

    fun setEnabled(context: Context, enabled: Boolean) {
        PlayerPreferences(context.applicationContext).setZapretEnabled(enabled)
        prefs?.setZapretEnabled(enabled)
        _state.value = _state.value.copy(enabled = enabled)
        CoroutineScope(Dispatchers.IO).launch {
            if (enabled) {
                startProxyInternal(_state.value.strategy)
            } else {
                proxy.stop()
            }
        }
    }

    fun setStrategy(context: Context, strategy: ZapretStrategy) {
        PlayerPreferences(context.applicationContext).setZapretStrategy(strategy.id)
        prefs?.setZapretStrategy(strategy.id)
        _state.value = _state.value.copy(strategy = strategy)
        if (_state.value.enabled) {
            CoroutineScope(Dispatchers.IO).launch {
                proxy.stop()
                startProxyInternal(strategy)
            }
        }
    }

    fun coveredDomains(): Set<String> = _state.value.domains

    /** Whether [host] (or its parent domain) is in the bypass list — host lists match subdomains. */
    fun isCovered(host: String): Boolean {
        val covered = _state.value.domains
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
            .map { it.lowercase().trim() }.distinct()
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
        val results = check()
        if (results.none { it.reachability == Reachability.REACHABLE }) return emptyList()
        val blocked = results.filter { it.reachability == Reachability.BLOCKED && !it.isCovered }.map { it.service }
        p.setZapretAutoChecked(true)
        return if (blocked.isEmpty()) emptyList() else addDomains(context, blocked)
    }

    fun exportAsHostList(): String =
        ZapretHostList.withOwnDomains("", _state.value.domains.sorted())
}
