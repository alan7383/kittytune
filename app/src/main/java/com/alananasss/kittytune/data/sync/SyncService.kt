package com.alananasss.kittytune.data.sync

import com.alananasss.kittytune.KittyTuneApp
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.BufferedInputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.Base64

/**
 * This phone answering the computer, rather than only calling it (issue #33).
 *
 * The phone used to be call-only, and that made sync feel broken in a way no amount of reliability work
 * would have fixed: the desktop's "sync now" button could not fetch anything, because there was nothing to
 * fetch *from*. Whatever the phone had heard stayed on the phone until the phone itself decided to speak.
 * From the desktop it was indistinguishable from sync not working.
 *
 * So the phone listens too. The objection to that was always lifecycle — a phone's processes are killed
 * and its network changes, so a port it holds is often shut — and that objection is real but smaller than
 * it looks: KittyTune already runs a foreground service while it is playing, and a listener that is up
 * *whenever the app is* is up for exactly the periods anyone is waiting on it. When it is not, the phone
 * still calls out, as before. Nothing regresses; a whole direction is added.
 *
 * ## Access control
 *
 * Bound to every interface, because the point is to be reachable from the computer. That makes it
 * reachable from everything else on the network too, so **every request must carry the pairing secret** —
 * [PAIRING_HEADER] with the value from [pairingSecret] — and one that does not is refused before its body
 * is read. The secret is 160 random bits, generated once per install.
 *
 * Off unless something is paired. An install that never pairs never opens a port.
 *
 * ## Why a hand-written server
 *
 * `com.sun.net.httpserver`, which the desktop uses, does not exist on Android. The protocol here is one
 * POST and one GET with no chunking, no keep-alive and no TLS, so the twenty lines below are the whole of
 * it — considerably less than a dependency to do the same.
 */
object SyncService {

    private val gson = Gson()

    private val prefs by lazy {
        KittyTuneApp.instance.getSharedPreferences("sync_state", android.content.Context.MODE_PRIVATE)
    }

    /** Fixed so a paired device can find its way back without being told the port again. */
    const val DEFAULT_PORT = 47653

    /** Where the secret goes. Bearer-style, because that is what it is. */
    const val PAIRING_HEADER = "Authorization"

    const val PLATFORM = "android"

    private const val KEY_SECRET = "pairing_secret"
    private const val KEY_ENABLED = "listener_enabled"
    private const val KEY_PORT = "listener_port"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var acceptor: Job? = null
    private var server: ServerSocket? = null

    /** What a device has to know to reach this one. Never logged, never sent anywhere but to a peer. */
    val pairingSecret: String
        get() = prefs.getString(KEY_SECRET, null)?.takeIf { it.isNotBlank() } ?: newSecret()

    var port: Int
        get() = prefs.getInt(KEY_PORT, DEFAULT_PORT)
        set(value) {
            prefs.edit().putInt(KEY_PORT, value.coerceIn(1024, 65535)).apply()
        }

    /**
     * Whether the listener should come up with the app.
     *
     * Defaults to on once something is paired, and is irrelevant before that. A switch the user has to
     * find in order for the feature to work is not a safety measure, it is a trap — the safety measure is
     * that nothing is open until a device has been deliberately paired.
     */
    var isListenerEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
            if (value) start() else stop()
        }

    val isRunning: Boolean get() = server != null

    /** Replaces the secret. Every previously paired device stops being able to connect. */
    fun regeneratePairingSecret(): String = newSecret()

    private fun newSecret(): String {
        val bytes = ByteArray(20)
        SecureRandom().nextBytes(bytes)
        val secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        prefs.edit().putString(KEY_SECRET, secret).apply()
        return secret
    }

    /**
     * What another device needs to reach this one.
     *
     * Handed to a peer mid-exchange so it can call us back, which is what makes the pairing mutual after
     * one round in either direction.
     */
    fun selfPairing(): PairingPayload = PairingPayload(
        host = localAddress(),
        port = port,
        secret = pairingSecret,
        deviceId = SyncLog.deviceId,
        deviceName = SyncLog.deviceName,
        platform = PLATFORM,
    )

    fun pairingCode(): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(gson.toJson(selfPairing()).toByteArray())

    /** @return the pairing details in [code], or null when it is not one of ours. */
    fun parsePairingCode(code: String): PairingPayload? = runCatching {
        val json = String(Base64.getUrlDecoder().decode(code.trim()))
        gson.fromJson(json, PairingPayload::class.java)
            ?.takeIf { it.host.isNotBlank() && it.secret.isNotBlank() && it.port in 1..65535 }
    }.getOrNull()

    /**
     * Starts listening. Idempotent, and a port that cannot be bound is not fatal — the phone falls back to
     * being call-only, which is what it always was.
     */
    @Synchronized
    fun start() {
        if (server != null) return
        acceptor = scope.launch {
            val bound = runCatching {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(port))
                }
            }.getOrElse {
                android.util.Log.w("SyncService", "Listener failed to bind port $port", it)
                return@launch
            }
            server = bound
            // Findable for exactly as long as it is reachable. Announcing an address that nothing answers
            // on is the failure mode the beacon exists to remove.
            SyncDiscovery.startResponder()

            while (!bound.isClosed) {
                val socket = runCatching { bound.accept() }.getOrNull() ?: break
                // One coroutine per connection. There is at most one peer and one request in flight, so
                // this never has more than a couple alive.
                scope.launch { runCatching { serve(socket) } }
            }
        }
    }

    @Synchronized
    fun stop() {
        runCatching { server?.close() }
        server = null
        acceptor?.cancel()
        acceptor = null
        SyncDiscovery.stopResponder()
    }

    /** Brings the listener up if it should be, which is "something is paired and it is not switched off". */
    fun startIfWanted() {
        if (isListenerEnabled && !SyncPeers.isEmpty()) start()
    }

    /**
     * One request, one response, connection closed.
     *
     * The body is read only after the secret has been checked, so an unauthenticated caller cannot make us
     * parse anything it chose.
     */
    private fun serve(socket: Socket) {
        socket.use {
            socket.soTimeout = 15_000
            val input = BufferedInputStream(socket.getInputStream())
            val output = socket.getOutputStream()

            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2) return respond(output, 400)
            val method = parts[0]
            val path = parts[1].substringBefore('?')

            var contentLength = 0
            var presented: String? = null
            while (true) {
                val header = readLine(input) ?: return respond(output, 400)
                if (header.isEmpty()) break
                val name = header.substringBefore(':').trim().lowercase()
                val value = header.substringAfter(':', "").trim()
                when (name) {
                    "content-length" -> contentLength = value.toIntOrNull() ?: 0
                    "authorization" -> presented = value.removePrefix("Bearer ").trim()
                }
            }

            if (!secretMatches(presented)) {
                // Fingerprints, never the secrets themselves. A refused pairing is almost always a caller
                // holding a code that was replaced since it was shown, and without this the only evidence
                // was a 401 with nothing to compare it against.
                android.util.Log.w(
                    "SyncService",
                    "Refused a caller presenting ${fingerprint(presented)}, " +
                        "this device expects ${fingerprint(pairingSecret)}"
                )
                return respond(output, 401)
            }

            when {
                method == "POST" && path == "/sync" -> {
                    // Capped, so a caller that claims a gigabyte does not get to allocate one.
                    if (contentLength !in 0..MAX_BODY_BYTES) return respond(output, 413)
                    val body = ByteArray(contentLength)
                    var read = 0
                    while (read < contentLength) {
                        val got = input.read(body, read, contentLength - read)
                        if (got < 0) break
                        read += got
                    }
                    if (read < contentLength) return respond(output, 400)

                    val request = runCatching {
                        gson.fromJson(body.decodeToString(), SyncExchange::class.java)
                    }.getOrNull() ?: return respond(output, 400)

                    val reply = runBlocking {
                        respondTo(request, socket.inetAddress?.hostAddress)
                    }
                    respond(output, 200, gson.toJson(reply))
                }

                else -> respond(output, 404)
            }
        }
    }

    /**
     * The exchange itself, either side of the wire.
     *
     * Merging first and answering second is deliberate: the marks we report already account for what the
     * peer just told us, so it does not send the same batch again on the next round.
     *
     * @param observedHost the address the request actually arrived from, preferred over the one the caller
     *   claims. A device reporting a stale interface would otherwise have us saving an address that
     *   answers nothing.
     */
    suspend fun respondTo(request: SyncExchange, observedHost: String? = null): SyncExchange {
        val applied = SyncLog.merge(request.events)
        // Awaited, so the marks and the count we report describe work that has actually happened.
        SyncApply.applyNow(applied)
        SyncLog.setPeerMarks(request.deviceId, request.marks)

        val callback = request.callback
        SyncPeers.remember(
            KnownDevice(
                deviceId = request.deviceId,
                deviceName = request.deviceName,
                host = observedHost?.takeIf { it.isNotBlank() } ?: callback?.host.orEmpty(),
                port = callback?.port?.takeIf { it in 1..65535 } ?: DEFAULT_PORT,
                secret = callback?.secret.orEmpty(),
                platform = callback?.platform.orEmpty(),
                lastSyncedAtMs = System.currentTimeMillis(),
            )
        )

        return SyncExchange(
            deviceId = SyncLog.deviceId,
            deviceName = SyncLog.deviceName,
            marks = SyncLog.marks(),
            events = SyncMerge.eventsToSend(SyncLog.all(), request.marks, request.deviceId),
            callback = selfPairing().takeIf { isRunning },
        )
    }

    /**
     * This device's address on the local network.
     *
     * The loopback address is useless to another machine, so an interface with a real address is preferred
     * and loopback is only the last resort — which at least fails visibly rather than silently handing out
     * an address that cannot work.
     */
    fun localAddress(): String = runCatching {
        java.net.NetworkInterface.getNetworkInterfaces().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.asSequence() }
            .filterIsInstance<java.net.Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
            ?: "127.0.0.1"
    }.getOrDefault("127.0.0.1")

    /** Constant-time, so a wrong secret cannot be found one character at a time. */
    private fun secretMatches(presented: String?): Boolean {
        val expected = pairingSecret
        if (presented == null || presented.length != expected.length) return false
        var diff = 0
        for (i in expected.indices) diff = diff or (presented[i].code xor expected[i].code)
        return diff == 0
    }

    /** First bytes of a SHA-256, enough to tell two secrets apart in a log without revealing either. */
    private fun fingerprint(secret: String?): String {
        if (secret.isNullOrEmpty()) return "no secret"
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(secret.toByteArray())
        return digest.take(6).joinToString("") { "%02x".format(it) }
    }

    /** A line of the request head, without its CRLF. Null at end of stream. */
    private fun readLine(input: BufferedInputStream): String? {
        val buffer = StringBuilder()
        while (true) {
            val byte = input.read()
            if (byte < 0) return if (buffer.isEmpty()) null else buffer.toString()
            if (byte == '\n'.code) return buffer.toString().removeSuffix("\r")
            if (buffer.length > MAX_LINE_CHARS) return null
            buffer.append(byte.toChar())
        }
    }

    private fun respond(output: OutputStream, code: Int, body: String? = null) {
        val bytes = body?.toByteArray() ?: ByteArray(0)
        val head = buildString {
            append("HTTP/1.1 $code ${reason(code)}\r\n")
            append("Content-Type: application/json\r\n")
            append("Content-Length: ${bytes.size}\r\n")
            append("Connection: close\r\n\r\n")
        }
        output.write(head.toByteArray())
        if (bytes.isNotEmpty()) output.write(bytes)
        output.flush()
    }

    private fun reason(code: Int): String = when (code) {
        200 -> "OK"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        404 -> "Not Found"
        413 -> "Payload Too Large"
        else -> "Error"
    }

    /**
     * Enough for a first pairing's worth of history, and far short of anything that would matter to a
     * phone's heap. [SyncMerge.MAX_EVENTS_PER_EXCHANGE] keeps a real exchange well under this.
     */
    private const val MAX_BODY_BYTES = 8 * 1024 * 1024

    private const val MAX_LINE_CHARS = 8192
}

/**
 * What a device needs to reach another one.
 *
 * Doubles as the QR payload and as the connect-back details a caller hands over mid-exchange, so a pairing
 * is symmetric after one round in either direction. Byte-identical to the desktop's copy: these travel
 * between the two verbatim.
 */
data class PairingPayload(
    val host: String,
    val port: Int,
    val secret: String,
    val deviceId: String,
    val deviceName: String,
    /** "desktop" or "android", for a screen that wants to draw the right icon. Absent in old codes. */
    val platform: String = "",
)

/**
 * One side of an exchange: who I am, how far I have got, and what I think you are missing.
 *
 * The same shape both ways, so one type describes the request and the response.
 */
data class SyncExchange(
    val deviceId: String,
    val deviceName: String,
    val marks: Map<String, Long>,
    val events: List<SyncEvent>,
    /**
     * How to call the sender back, when it can be called.
     *
     * Null from a device with no listener, and absent entirely from a version that predates mutual
     * pairing — both of which leave the pairing one-directional rather than breaking it.
     */
    val callback: PairingPayload? = null,
)

/** Where the secret goes. Must match the desktop, which refuses anything else. */
const val PAIRING_HEADER = SyncService.PAIRING_HEADER
