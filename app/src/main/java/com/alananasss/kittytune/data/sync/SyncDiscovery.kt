package com.alananasss.kittytune.data.sync

import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface

/**
 * Finding a paired device on whatever network it is on now (issue #33).
 *
 * This replaces sweeping the local `/24` — 254 TCP connections, several seconds, and wrong the moment
 * the network is not a `/24` or the peer is on a different subnet. A device that has moved is now found
 * by asking the network out loud: one UDP broadcast, and the device that recognises its own id answers
 * with where it is. It takes about as long as a single ping.
 *
 * That is what makes "paired once" true rather than aspirational. The address in a pairing code is
 * correct for as long as the router feels like it; the device id is forever. Everything else in sync
 * keys on the id, and this is the piece that turns an id back into an address.
 *
 * ## What is on the wire, and what is not
 *
 * A query names the device it wants. A device answers only when the name is its own, so a stranger
 * sweeping the network learns nothing it did not already know — it has to guess a UUID to get a reply
 * at all. The one exception is pairing: while [isAdvertising] is on, because a pairing screen is open,
 * a wildcard query is answered so a phone can find a computer it has never met. That window is short
 * and deliberate.
 *
 * No secret is ever broadcast. An answer says "device X is at this address"; reaching it still needs
 * the pairing secret, and [SyncService] refuses anything without it.
 */
object SyncDiscovery {

    private val gson = Gson()

    /** Next to [SyncService.DEFAULT_PORT], and fixed for the same reason: it has to be guessable. */
    const val DISCOVERY_PORT = 47654

    private const val QUERY = "kt-sync-query"
    private const val ANSWER = "kt-sync-here"

    /** Answered by any device, so a pairing screen can be found without knowing its id yet. */
    private const val WILDCARD = "*"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var responder: Job? = null
    private var socket: DatagramSocket? = null

    /**
     * Whether to answer wildcard queries.
     *
     * On only while a pairing screen is open. Off the rest of the time, so this device does not announce
     * itself to every network it joins.
     */
    @Volatile
    var isAdvertising: Boolean = false

    /** A query, and an answer. Same envelope both ways so one parse handles either. */
    private data class Beacon(
        val kt: String,
        val want: String? = null,
        val deviceId: String? = null,
        val deviceName: String? = null,
        val port: Int? = null,
        val platform: String? = null,
    )

    /** Where a device was found. */
    data class Found(
        val deviceId: String,
        val deviceName: String,
        val host: String,
        val port: Int,
        val platform: String,
    )

    /**
     * Starts answering queries. Idempotent, and a port that cannot be bound is not fatal — this device
     * simply will not be findable, and a saved address may still reach it.
     */
    @Synchronized
    fun startResponder() {
        if (responder != null) return
        responder = scope.launch {
            val bound = runCatching {
                DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(DISCOVERY_PORT))
                }
            }.getOrNull() ?: return@launch
            socket = bound

            val buffer = ByteArray(2048)
            while (!bound.isClosed) {
                val packet = DatagramPacket(buffer, buffer.size)
                val received = runCatching { bound.receive(packet); true }.getOrDefault(false)
                if (!received) break
                runCatching { answer(bound, packet) }
            }
        }
    }

    @Synchronized
    fun stopResponder() {
        runCatching { socket?.close() }
        socket = null
        responder?.cancel()
        responder = null
    }

    private fun answer(bound: DatagramSocket, packet: DatagramPacket) {
        val text = String(packet.data, packet.offset, packet.length)
        val beacon = runCatching { gson.fromJson(text, Beacon::class.java) }.getOrNull() ?: return
        if (beacon.kt != QUERY) return

        val want = beacon.want.orEmpty()
        val mine = SyncLog.deviceId
        val shouldAnswer = want == mine || (want == WILDCARD && isAdvertising)
        if (!shouldAnswer) return

        val reply = gson.toJson(
            Beacon(
                kt = ANSWER,
                deviceId = mine,
                deviceName = SyncLog.deviceName,
                port = SyncService.port,
                platform = PLATFORM,
            )
        ).toByteArray()

        // Straight back to whoever asked, not broadcast: only the asker needs it.
        bound.send(DatagramPacket(reply, reply.size, packet.address, packet.port))
    }

    /**
     * Asks the network where [deviceId] is.
     *
     * @param deviceId the device to look for, or null to find anything with a pairing screen open.
     * @param timeoutMs how long to wait for answers. A LAN round trip is single-digit milliseconds; the
     *   rest of the budget is for a phone whose Wi-Fi radio was asleep.
     * @return every device that answered, which for a specific id is at most one.
     */
    suspend fun locate(deviceId: String?, timeoutMs: Int = 1_200): List<Found> =
        withContext(Dispatchers.IO) {
            val query = gson.toJson(Beacon(kt = QUERY, want = deviceId ?: WILDCARD)).toByteArray()
            val found = LinkedHashMap<String, Found>()

            runCatching {
                // An ephemeral socket, not the responder's: replies come back to the port the query
                // left from, and mixing the two would have the responder consuming its own answers.
                DatagramSocket().use { asking ->
                    asking.broadcast = true
                    asking.soTimeout = 200

                    for (target in broadcastAddresses()) {
                        runCatching {
                            asking.send(DatagramPacket(query, query.size, target, DISCOVERY_PORT))
                        }
                    }

                    val deadline = System.currentTimeMillis() + timeoutMs
                    val buffer = ByteArray(2048)
                    while (System.currentTimeMillis() < deadline) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        val got = runCatching { asking.receive(packet); true }.getOrDefault(false)
                        if (!got) continue
                        val text = String(packet.data, packet.offset, packet.length)
                        val beacon = runCatching { gson.fromJson(text, Beacon::class.java) }.getOrNull()
                            ?: continue
                        if (beacon.kt != ANSWER) continue
                        val id = beacon.deviceId?.takeIf { it.isNotBlank() } ?: continue
                        if (id == SyncLog.deviceId) continue
                        if (deviceId != null && id != deviceId) continue
                        // The address the packet came from, not one it claimed: a device behind a NAT
                        // or reporting a stale interface would otherwise send us somewhere that does not
                        // answer. An answer we cannot attribute to an address is skipped, not fatal — the
                        // others already collected are still worth having.
                        val host = packet.address.hostAddress ?: continue
                        found[id] = Found(
                            deviceId = id,
                            deviceName = beacon.deviceName.orEmpty(),
                            host = host,
                            port = beacon.port?.takeIf { it in 1..65535 } ?: SyncService.DEFAULT_PORT,
                            platform = beacon.platform.orEmpty(),
                        )
                        if (deviceId != null) break
                    }
                }
            }
            found.values.toList()
        }

    /**
     * Everywhere a query should go.
     *
     * `255.255.255.255` alone is not enough: some stacks and some access points drop it, while a
     * subnet-directed broadcast gets through. Sending to both costs two datagrams.
     */
    private fun broadcastAddresses(): List<InetAddress> {
        val addresses = mutableListOf<InetAddress>()
        runCatching { addresses.add(InetAddress.getByName("255.255.255.255")) }
        runCatching {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.interfaceAddresses.asSequence() }
                .mapNotNull { it.broadcast }
                .forEach { addresses.add(it) }
        }
        return addresses.distinct()
    }

    private const val PLATFORM = SyncService.PLATFORM
}
