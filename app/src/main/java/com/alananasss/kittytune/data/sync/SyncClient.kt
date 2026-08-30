package com.alananasss.kittytune.data.sync

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * The calling side of an exchange (issue #33).
 *
 * Whichever device starts it, the exchange is symmetric: send what the peer lacks along with our
 * marks, receive the same in return, merge it. One round trip, so there is no state to get stuck in
 * halfway.
 *
 * Deliberately not routed through [com.alananasss.kittytune.data.network.ProxyManager]: this is a
 * call to a machine on the same network, and sending it through a remote proxy would either fail or
 * hand the listening history to the proxy.
 */
object SyncClient {

    private val gson = Gson()
    private val jsonType = "application/json".toMediaType()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // Pinned direct. A client with no proxy still consults the JVM's default ProxySelector,
            // which ProxyManager replaces globally with one that proxies every URI including private
            // addresses — so a proxied setup sent LAN sync traffic to a remote proxy that cannot reach
            // it, and the failure looked exactly like the peer not listening (issue #33).
            .proxy(java.net.Proxy.NO_PROXY)
            // Six seconds, not four: a LAN handshake is milliseconds, but a phone's Wi-Fi power-saving
            // can stall the first packet long enough to trip a tight timeout, and a spurious failure here
            // reads as "pairing is broken" (issue #33).
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** What came of an exchange, in a form the settings screen can show without interpreting. */
    sealed interface Result {
        data class Success(val peerName: String, val received: Int, val sent: Int) : Result

        /** Reached, and it refused us: the secret is wrong, or it was regenerated on that device. */
        data object Unauthorized : Result

        data class Failed(val reason: String) : Result
    }

    /**
     * Exchanges with the device described by [peer].
     *
     * @return what happened, never a thrown exception: this runs from a button and every failure
     *   here is a normal outcome of two devices being on different networks.
     */
    suspend fun exchange(peer: PairingPayload): Result = withContext(Dispatchers.IO) {
        val outgoing = SyncExchange(
            deviceId = SyncLog.deviceId,
            deviceName = SyncLog.deviceName,
            marks = SyncLog.marks(),
            events = SyncMerge.eventsToSend(
                SyncLog.all(),
                SyncLog.peerMarks(peer.deviceId),
                peer.deviceId,
            ),
            // Only when we are actually reachable. Handing over an address that answers nothing would
            // have the peer retrying it for ever instead of waiting to be called.
            callback = SyncService.selfPairing().takeIf { SyncService.isRunning },
        )

        val request = Request.Builder()
            .url("http://${peer.host}:${peer.port}/sync")
            .header(SyncService.PAIRING_HEADER, "Bearer ${peer.secret}")
            .post(gson.toJson(outgoing).toByteArray().toRequestBody(jsonType))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.code == 401) return@withContext Result.Unauthorized
                if (!response.isSuccessful) {
                    return@withContext Result.Failed("HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                val reply = runCatching { gson.fromJson(body, SyncExchange::class.java) }.getOrNull()
                    ?: return@withContext Result.Failed("malformed reply")

                val applied = SyncLog.merge(reply.events)
                SyncApply.applyNow(applied)
                // What it reported already accounts for what we just sent, so the next exchange
                // starts from there instead of from the beginning of the log.
                SyncLog.setPeerMarks(reply.deviceId, reply.marks)

                // The address that just worked, and the secret we used, are now what we know about it.
                SyncPeers.remember(
                    KnownDevice(
                        deviceId = reply.deviceId.ifBlank { peer.deviceId },
                        deviceName = reply.deviceName.ifBlank { peer.deviceName },
                        host = peer.host,
                        port = peer.port,
                        secret = peer.secret,
                        platform = reply.callback?.platform.orEmpty().ifBlank { peer.platform },
                        lastSyncedAtMs = System.currentTimeMillis(),
                    )
                )

                Result.Success(
                    peerName = reply.deviceName.ifBlank { peer.deviceName },
                    received = applied.size,
                    sent = outgoing.events.size,
                )
            }
        } catch (t: Throwable) {
            Result.Failed(describe(t, peer))
        }
    }

    /**
     * Exchanges with a device we know, finding it again if it has moved.
     *
     * The saved address goes stale whenever the router hands out a different one, which is the one thing
     * that would otherwise force a second pairing. The device is then found by id — one UDP broadcast,
     * not a sweep of the subnet — and the entry is corrected in place, so the code is never needed again.
     *
     * A refused secret is not retried: that is the device saying no, not a wrong address (issue #33).
     */
    suspend fun exchangeWith(device: KnownDevice): Result {
        val secret = device.secret
        if (secret.isBlank()) return Result.Failed("not dialable")

        if (device.host.isNotBlank()) {
            val direct = exchange(device.toPairing() ?: return Result.Failed("not dialable"))
            if (direct !is Result.Failed) return direct
        }

        val found = SyncDiscovery.locate(device.deviceId)
            .firstOrNull { it.deviceId == device.deviceId }
            ?: return Result.Failed("not on this network")

        return exchange(
            PairingPayload(
                host = found.host,
                port = found.port,
                secret = secret,
                deviceId = found.deviceId,
                deviceName = found.deviceName.ifBlank { device.deviceName },
                platform = found.platform,
            )
        )
    }

    /**
     * Turns a connection failure into something worth reading.
     *
     * "failed to connect to /192.168.1.121 (port 47653) after 6000ms" is the truth and tells nobody what
     * to do about it. A timeout to an address on the same subnet has two causes and only two: the other
     * device is not accepting connections, or a firewall is dropping the port. Both are one switch away,
     * so the message names them instead of quoting the exception (issue #33).
     */
    private fun describe(t: Throwable, peer: PairingPayload): String {
        // The formatted message says what to do about it; the log says what actually happened. Without
        // this the only evidence of a failed pairing was a sentence with the exception thrown away, which
        // is not enough to tell a firewall from a bug (issue #33).
        android.util.Log.w("SyncClient", "Exchange with ${peer.host}:${peer.port} failed", t)
        return describeFor(t, peer)
    }

    private fun describeFor(t: Throwable, peer: PairingPayload): String = when (t) {
        is java.net.SocketTimeoutException, is java.net.ConnectException ->
            "No answer from ${peer.host}:${peer.port}. Check that the other device is running " +
                "KittyTune with connections accepted, and that port ${peer.port} is allowed."

        is java.net.UnknownHostException, is java.net.NoRouteToHostException ->
            "${peer.host} is not on this network."

        else -> "${t::class.simpleName}: ${t.message ?: "no detail"}"
    }
}
