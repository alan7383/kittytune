package com.alananasss.kittytune.data.sync

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Connect away from home: a paired device reached through public MQTT brokers, for when the two are not
 * on the same network (4G, work, a friend's place). Off unless the user turns it on.
 *
 * It stands in for the self-hosted relay without changing anything above it: [open] hands ConnectManager
 * a [WebSocket] that behaves like a relay room, saying `ready` when the other device is there and
 * `peer_left` when it goes, and carrying the same sealed frames. Those frames are already encrypted and
 * authenticated with the pair's key, so a broker only ever sees ciphertext.
 *
 * ## Topics
 *
 * Nothing on the wire names a device or a pair. Topics are hashes of the pairing secrets, so only the two
 * devices can compute them:
 * - presence: `kittytune/connect/p/<hash(id, secret)>`, retained `here <nonce>` while this device is
 *   reachable and `gone` when it leaves (published by the broker too, as a last will, if it vanishes);
 * - inbox: `kittytune/connect/m/<hash(room)>/<hash(recipient)>`, the frames for one device of a pair.
 *
 * A device whose presence says `here` with a new nonce has (re)connected: that is the relay's `ready`.
 */
internal object ConnectCloud {
    private const val READY = "{\"type\":\"ready\"}"
    private const val PEER_LEFT = "{\"type\":\"peer_left\"}"
    private val GONE = "gone".toByteArray()

    /** Deliveries leave Paho's callback thread at once: publishing from it can deadlock the client. */
    private val inbound = Executors.newSingleThreadExecutor { Thread(it, "connect-cloud").apply { isDaemon = true } }

    @Volatile private var mesh: CloudMesh? = null
    private var nonce = ""
    private val sessions = ConcurrentHashMap<String, Session>()

    /** Last presence each broker reported for a watched device: its nonce, or null for gone. */
    private val presence = ConcurrentHashMap<String, ConcurrentHashMap<Int, String>>()

    private val seen = object : LinkedHashMap<String, Boolean>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > 512
    }

    private fun hash(label: String, vararg parts: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(("kitty-connect/cloud/$label\u0000" + parts.joinToString("\u0000")).toByteArray(Charsets.UTF_8))
            .take(16).joinToString("") { "%02x".format(it) }

    fun presenceTopic(deviceId: String, secret: String) = "kittytune/connect/p/${hash("presence", deviceId, secret)}"
    fun inboxTopic(room: String, recipient: String) = "kittytune/connect/m/${hash("room", room)}/${hash("to", recipient)}"
    private fun ownPresence() = presenceTopic(SyncLog.deviceId, SyncService.pairingSecret)

    /** Comes online on the brokers, announcing this device to its peers. Idempotent. */
    @Synchronized fun activate() {
        if (mesh != null) return
        nonce = UUID.randomUUID().toString().take(12)
        val here = "here $nonce".toByteArray()
        val own = ownPresence()
        NetLog.i("cloud", "Internet control on")
        mesh = CloudMesh(
            clientId = "ktc-" + hash("client", SyncLog.deviceId).take(10) + "-" + nonce.take(4),
            will = own to GONE,
            onConnected = { broker -> mesh?.publishTo(broker, own, here, qos = 1, retained = true) },
            onMessage = { topic, payload, broker -> inbound.execute { deliver(topic, payload, broker) } },
        ).also { created ->
            // Watched presences from sessions opened before this point.
            sessions.values.forEach { created.subscribe(it.peerPresence, it.inbox) }
        }
    }

    /** Says goodbye and goes offline; every open session ends as if its peer had left. */
    @Synchronized fun deactivate() {
        val running = mesh ?: return
        mesh = null
        NetLog.i("cloud", "Internet control off")
        sessions.values.toList().forEach { it.socket.cancel() }
        presence.clear()
        Thread { running.stop(farewell = ownPresence() to GONE) }.start()
    }

    val isActive: Boolean get() = mesh != null

    /** A relay-like socket to [peer]. [listener] gets onOpen at once and `ready` once the peer is there. */
    fun open(peer: KnownDevice, credentials: ConnectCredentials, listener: WebSocketListener): WebSocket {
        activate()
        val session = Session(
            peerId = peer.deviceId,
            peerPresence = presenceTopic(peer.deviceId, peer.secret),
            inbox = inboxTopic(credentials.room, SyncLog.deviceId),
            outbox = inboxTopic(credentials.room, peer.deviceId),
            listener = listener,
        )
        sessions.put(peer.deviceId, session)?.socket?.cancel()
        inbound.execute {
            session.listener.onOpen(session.socket, fakeResponse())
            mesh?.subscribe(session.peerPresence, session.inbox)
            // Presence already known from earlier: no need to wait for the retained copy.
            session.maybeReady()
        }
        return session.socket
    }

    private fun deliver(topic: String, payload: ByteArray, broker: Int) {
        val presenceOwners = sessions.values.filter { it.peerPresence == topic }
        if (presenceOwners.isNotEmpty()) {
            val text = String(payload, Charsets.UTF_8)
            val states = presence.getOrPut(topic) { ConcurrentHashMap() }
            if (text.startsWith("here ")) states[broker] = text.removePrefix("here ") else states.remove(broker)
            presenceOwners.forEach { it.maybeReady() }
            return
        }
        val session = sessions.values.firstOrNull { it.inbox == topic } ?: return
        val key = topic + "|" + MessageDigest.getInstance("SHA-256").digest(payload).take(12).joinToString("") { "%02x".format(it) }
        synchronized(seen) { if (seen.put(key, true) != null) return }
        session.listener.onMessage(session.socket, String(payload, Charsets.UTF_8))
    }

    private fun fakeResponse(): Response = Response.Builder()
        .request(Request.Builder().url("https://connect.cloud.invalid/").build())
        .protocol(Protocol.HTTP_1_1).code(101).message("Cloud").build()

    private class Session(
        val peerId: String,
        val peerPresence: String,
        val inbox: String,
        val outbox: String,
        val listener: WebSocketListener,
    ) {
        @Volatile private var readyNonce: String? = null
        @Volatile var closed = false

        /** `ready` on a new nonce (the peer arrived or came back), `peer_left` once no broker sees it. */
        fun maybeReady() {
            if (closed) return
            val current = presence[peerPresence]?.values?.firstOrNull()
            if (current != null && current != readyNonce) {
                readyNonce = current
                listener.onMessage(socket, READY)
            } else if (current == null && readyNonce != null) {
                readyNonce = null
                listener.onMessage(socket, PEER_LEFT)
            }
        }

        val socket: WebSocket = object : WebSocket {
            private val request = Request.Builder().url("https://connect.cloud.invalid/").build()
            override fun request(): Request = request
            override fun queueSize(): Long = 0
            override fun send(text: String): Boolean {
                if (closed) return false
                // The relay's join handshake has no meaning here: presence does that job.
                if (text.startsWith("{\"type\":\"join\"")) return true
                return (mesh?.publish(outbox, text.toByteArray(Charsets.UTF_8)) ?: 0) > 0
            }
            override fun send(bytes: ByteString): Boolean = false
            override fun close(code: Int, reason: String?): Boolean { end(code, reason.orEmpty()); return true }
            override fun cancel() { end(1001, "") }
        }

        private fun end(code: Int, reason: String) {
            if (closed) return
            closed = true
            sessions.remove(peerId, this)
            if (sessions.values.none { it.peerPresence == peerPresence }) {
                mesh?.unsubscribe(peerPresence)
                presence.remove(peerPresence)
            }
            if (sessions.values.none { it.inbox == inbox }) mesh?.unsubscribe(inbox)
            inbound.execute { listener.onClosed(socket, code, reason) }
        }
    }
}
