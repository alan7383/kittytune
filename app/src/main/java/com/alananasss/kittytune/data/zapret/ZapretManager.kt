package com.alananasss.kittytune.data.zapret

import android.content.Context
import android.util.Log
import com.alananasss.kittytune.data.local.PlayerPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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
 * Android zapret: DPI bypass built into the app, no external folder, no VPN, no proxy.
 *
 * Desktop finds a zapret installation and edits its host lists; here the same service catalogue
 * ([ZapretServices]) and host matching ([ZapretHostList]) drive [DpiBypass], which fragments the
 * TLS ClientHello of the app's own connections to the listed domains. Every OkHttp client routed
 * through [newBuilder]/[applyTo] (including [ProxyManager][com.alananasss.kittytune.data.network.ProxyManager]
 * choke point) and every `HttpURLConnection` (ExoPlayer streaming) is covered.
 *
 * The gating is dynamic — [DpiBypass.policy] reads the current state on each connection — so
 * toggling takes effect immediately without rebuilding any client.
 */
object ZapretManager {
    private const val TAG = "ZapretManager"

    private val _state = MutableStateFlow(ZapretState())
    val state: StateFlow<ZapretState> = _state.asStateFlow()

    @Volatile
    private var prefs: PlayerPreferences? = null

    /**
     * Direct connections only: the check is about whether the network lets a service through, so a proxy
     * configured in the app would hide the answer. Raw socket factory on purpose — no bypass either.
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
        _state.value = ZapretState(
            enabled = p.getZapretEnabled(),
            strategy = ZapretStrategy.fromId(p.getZapretStrategy()),
            domains = p.getZapretDomains(),
        )
        DpiBypass.policy = { host ->
            val current = _state.value
            if (!current.enabled || current.domains.isEmpty()) null
            else {
                val covered = current.domains
                // Unknown host: fragment — TCP segmentation is transparent to the server.
                // Known host: only fragment when it (or its parent) is in the bypass list.
                val clean = host?.lowercase()?.trim()?.trimEnd('.')
                if (clean.isNullOrBlank() || ZapretHostList.isCovered(clean, covered)) {
                    FragmentSpec.forStrategy(current.strategy)
                } else null
            }
        }
        DpiBypass.installDefaultHttpsFactory()
        Log.i(TAG, "Initialized (enabled=${_state.value.enabled}, domains=${_state.value.domains.size})")
    }

    /** A fresh OkHttp builder with the bypass socket factory installed. */
    fun newBuilder(): OkHttpClient.Builder =
        OkHttpClient.Builder().socketFactory(bypassSocketFactory())

    /** A fresh OkHttp client with the bypass socket factory installed. */
    fun newClient(): OkHttpClient = newBuilder().build()

    fun bypassSocketFactory(): SocketFactory = DpiBypass.socketFactory

    /** Installs the bypass socket factory on any OkHttp builder (no-op until enabled). */
    fun applyTo(builder: OkHttpClient.Builder): OkHttpClient.Builder =
        builder.socketFactory(DpiBypass.socketFactory)

    fun isEnabled(): Boolean = _state.value.enabled

    fun setEnabled(context: Context, enabled: Boolean) {
        PlayerPreferences(context.applicationContext).setZapretEnabled(enabled)
        prefs?.setZapretEnabled(enabled)
        _state.value = _state.value.copy(enabled = enabled)
    }

    fun setStrategy(context: Context, strategy: ZapretStrategy) {
        PlayerPreferences(context.applicationContext).setZapretStrategy(strategy.id)
        prefs?.setZapretStrategy(strategy.id)
        _state.value = _state.value.copy(strategy = strategy)
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

    /** Probes every service in parallel, with raw connections (no proxy, no bypass). */
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

    /** Any answer at all — a 403 included — means the connection got through; a timeout or reset does not. */
    /**
     * A real request, with its body read: blocking by traffic inspection often lets the handshake and a few
     * kilobytes through and then stalls the connection, so a HEAD with an empty answer said "works" for services
     * whose searches never returned. Any status counts — a 401 from an API is it answering.
     */
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

    /**
     * The first time the bypass is enabled: probe everything and add only what is blocked. Runs once;
     * after that the settings page's buttons are the way to change the list.
     */
    suspend fun autoConfigureOnce(context: Context): List<String> {
        val p = prefs ?: PlayerPreferences(context.applicationContext).also { prefs = it }
        if (p.getZapretAutoChecked() || !isEnabled()) return emptyList()
        val results = check()
        // Nothing reachable at all is no network, not a blocklist: try again next launch.
        if (results.none { it.reachability == Reachability.REACHABLE }) return emptyList()
        val blocked = results.filter { it.reachability == Reachability.BLOCKED && !it.isCovered }.map { it.service }
        p.setZapretAutoChecked(true)
        return if (blocked.isEmpty()) emptyList() else addDomains(context, blocked)
    }

    /**
     * The bypass list in zapret host-list format, with KittyTune's marker block — paste it into
     * `lists/list-general-user.txt` (Windows) or `ipset/zapret-hosts-user.txt` (Linux) to reuse the
     * same domains with zapret on a PC.
     */
    fun exportAsHostList(): String =
        ZapretHostList.withOwnDomains("", _state.value.domains.sorted())
}
