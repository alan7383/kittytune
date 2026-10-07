package com.alananasss.kittytune.data.sync

import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class ConnectPeerState(
    val deviceId: String, val name: String, val connected: Boolean = false,
    val transport: String = "", val snapshot: PlaybackSnapshot? = null,
    val queueVersion: String = "", val receivedAtNanos: Long = System.nanoTime(),
    val error: String = "",
) {
    fun offline(): ConnectPeerState = copy(connected = false,
        snapshot = snapshot?.copy(positionMs = position()), receivedAtNanos = System.nanoTime())
    fun position(): Long {
        val s = snapshot ?: return 0L
        val elapsed = if (connected && s.isPlaying) (System.nanoTime() - receivedAtNanos) / 1_000_000 else 0L
        val p = s.positionMs + elapsed.coerceAtLeast(0L)
        return s.queue.getOrNull(s.currentIndex)?.durationMs?.takeIf { it > 0 }?.let { p.coerceAtMost(it) } ?: p
    }
}

/** One duplex socket per peer. No polling, wake locks, background service or audio forwarding. */
object ConnectManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS).pingInterval(60, TimeUnit.SECONDS).build()
    private val lanClient = client.newBuilder().proxy(java.net.Proxy.NO_PROXY).build()
    private val gson = Gson()
    private val jobs = ConcurrentHashMap<String, Job>()
    private val sockets = ConcurrentHashMap<String, WebSocket>()
    private val links = ConcurrentHashMap<String, Link>()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<ConnectMessage>>()
    private val _peers = MutableStateFlow<Map<String, ConnectPeerState>>(emptyMap())
    val peers: StateFlow<Map<String, ConnectPeerState>> = _peers
    private val _feedback = MutableStateFlow("")
    val feedback: StateFlow<String> = _feedback
    private val _selectedDevice = MutableStateFlow<String?>(null)
    val selectedDevice: StateFlow<String?> = _selectedDevice
    private val _visible = MutableStateFlow(!ConnectPlatform.mobile)
    val visible: StateFlow<Boolean> = _visible
    private val selection = ConnectDeviceSelection()
    private var foreground = !ConnectPlatform.mobile
    private var playing = false
    @Volatile private var enabled = true
    @Volatile private var local: PlaybackSnapshot? = null
    private var handler: (suspend (ConnectMessage) -> Unit)? = null
    private var snapshotProvider: (() -> PlaybackSnapshot?)? = null
    private val handledCommands = LinkedHashMap<String, Boolean>()
    var relayUrl: String
        get() = ConnectPlatform.relayUrl
        set(value) {
            val url = value.trim().trimEnd('/').replaceFirst("https://", "wss://")
            require(url.isEmpty() || validRelayUrl(url)) { "Use a wss:// address (ws:// only for localhost)." }
            ConnectPlatform.relayUrl = url
            restart()
        }

    private fun validRelayUrl(url: String): Boolean = runCatching {
        val uri = java.net.URI(url)
        uri.userInfo == null && uri.query == null && uri.fragment == null && uri.host != null &&
            (uri.scheme == "wss" || uri.scheme == "ws" && uri.host in listOf("localhost", "127.0.0.1", "::1"))
    }.getOrDefault(false)

    fun importRelayUrl(url: String?) {
        if (!url.isNullOrBlank() && relayUrl.isBlank()) runCatching { relayUrl = url }
    }

    fun configure(provider: () -> PlaybackSnapshot?, execute: suspend (ConnectMessage) -> Unit) {
        snapshotProvider = provider
        handler = execute
        local = provider()
        refresh()
    }

    fun setForeground(value: Boolean) { foreground = value; _visible.value = value; refresh() }
    @Synchronized fun selectDevice(id: String?) { selection.choose(id); _selectedDevice.value = selection.selectedDevice }
    /** A remote command addresses our renderer; it is not a manual choice to pin this UI locally. */
    @Synchronized fun activateLocalRenderer() { selection.activateLocalRenderer(); _selectedDevice.value = null }
    fun setEnabled(value: Boolean) { enabled = value; refresh() }
    fun publish(snapshot: PlaybackSnapshot?) {
        local = snapshot
        val wasPlaying = playing
        playing = snapshot?.isPlaying == true
        if (wasPlaying != playing) refresh()
        links.values.forEach { it.sendState(snapshot) }
        if (!playing) followActivePeer()
    }
    fun localState(): PlaybackSnapshot? = local
    fun hasLivePeer(): Boolean = _peers.value.values.any { it.connected }
    private fun active() = ConnectPolicy.shouldConnect(enabled, SyncPlayback.enabled, ConnectPlatform.mobile, foreground, playing)

    @Synchronized fun refresh() {
        ConnectPlatform.setActive(!ConnectPlatform.mobile || foreground || playing)
        if (!active()) { closeConnections(); return }
        ConnectPlatform.startListener()
        val devices = SyncPeers.all().filter { it.secret.length >= 20 }
        val ids = devices.map { it.deviceId }.toSet()
        jobs.keys.filter { it !in ids }.forEach { jobs.remove(it)?.cancel(); sockets.remove(it)?.cancel() }
        links.keys.filter { it !in ids }.forEach { links.remove(it)?.close?.invoke() }
        for (peer in devices) {
            // Android dials the desktop on LAN. The desktop only makes outbound relay connections.
            if (links[peer.deviceId]?.transport == "LAN") continue
            if (!ConnectPlatform.mobile && relayUrl.isBlank()) continue
            if (jobs[peer.deviceId]?.isActive == true) continue
            jobs[peer.deviceId] = scope.launch { connectionLoop(peer.deviceId) }
        }
    }

    private fun restart() { closeConnections(); refresh() }
    @Synchronized private fun closeConnections() {
        jobs.values.forEach { it.cancel() }; jobs.clear()
        sockets.values.forEach { it.cancel() }; sockets.clear()
        links.values.toList().forEach { it.close() }; links.clear()
        _peers.value = _peers.value.mapValues { (_, v) -> v.offline() }
    }

    fun credentials(peer: KnownDevice) = ConnectCredentials.derive(SyncLog.deviceId,
        SyncService.pairingSecret, peer.deviceId, peer.secret)

    private suspend fun connectionLoop(peerId: String) {
        var backoff = 2_000L
        try {
            while (currentCoroutineContext().isActive && active()) {
                val peer = SyncPeers.find(peerId) ?: break
                if (links[peerId]?.transport == "LAN") { delay(120_000); continue }
                var established = false
                if (ConnectPlatform.mobile && peer.platform != "android" && peer.host.isNotBlank()) {
                    established = connect(peer, "ws://${peer.host}:${ConnectLanPort}/v1/connect", "LAN")
                    if (!established && relayUrl.isBlank()) {
                        // One bounded discovery attempt per backoff, never a continuous LAN scan.
                        val found = runCatching { SyncDiscovery.locate(peerId).firstOrNull() }.getOrNull()
                        if (found != null) SyncPeers.remember(peer.copy(host = found.host))
                    }
                }
                if (!active()) break
                if (!established && relayUrl.isNotBlank()) {
                    established = connect(peer, relayUrl + "/v1/connect", "Internet")
                }
                if (established) backoff = 2_000L
                delay(backoff + kotlin.random.Random.nextLong(0, 500))
                backoff = (backoff * 2).coerceAtMost(120_000L)
            }
        } finally { sockets.remove(peerId)?.cancel() }
    }

    private suspend fun connect(peer: KnownDevice, url: String, transport: String): Boolean {
        val ended = CompletableDeferred<Unit>()
        val ready = CompletableDeferred<Boolean>()
        val credentials = runCatching { credentials(peer) }.getOrNull() ?: return false
        var link: Link? = null
        val socket = (if (transport == "LAN") lanClient else client).newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(gson.toJson(mapOf("type" to "join", "room" to credentials.room,
                    "token" to credentials.token, "device" to SyncLog.deviceId)))
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text == "{\"type\":\"ready\"}") {
                    if (link == null) link = attach(peer, transport, webSocket::send) { webSocket.close(1000, "idle") }
                    else { link?.sendMessage("hello"); link?.sendState(localState(), force = true) }
                    ready.complete(true)
                } else if (text == "{\"type\":\"peer_left\"}") {
                    updatePeer(peer.deviceId) { (it ?: ConnectPeerState(peer.deviceId, peer.label)).offline() }
                } else link?.receive(text)
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                link?.detach(); ready.complete(false); ended.complete(Unit)
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                link?.detach(); ready.complete(false); ended.complete(Unit)
            }
        })
        sockets[peer.deviceId] = socket
        return try {
            val connected = withTimeoutOrNull(5_000L) { ready.await() } == true
            if (!connected) return false
            ended.await()
            true
        } finally {
            socket.cancel(); sockets.remove(peer.deviceId, socket); link?.detach()
        }
    }

    @Synchronized fun attach(peer: KnownDevice, transport: String, send: (String) -> Boolean, close: () -> Unit): Link {
        val old = links[peer.deviceId]
        val link = Link(peer, transport, send, close)
        links[peer.deviceId] = link
        old?.close?.invoke()
        updatePeer(peer.deviceId) { (it ?: ConnectPeerState(peer.deviceId, peer.label)).offline().copy(transport = transport) }
        link.sendMessage("hello")
        link.sendState(localState(), force = true)
        return link
    }

    @Synchronized private fun updatePeer(id: String, update: (ConnectPeerState?) -> ConnectPeerState) {
        val peer = update(_peers.value[id])
        _peers.value = _peers.value + (id to peer)
        followActivePeer()
    }

    @Synchronized private fun followActivePeer() {
        val id = _peers.value.values.filter { it.connected && it.snapshot?.isPlaying == true }
            .maxByOrNull { it.snapshot?.updatedAtMs ?: 0L }?.deviceId
        selection.followActivePeer(id, local?.isPlaying == true)
        _selectedDevice.value = selection.selectedDevice
    }

    class Link internal constructor(val peer: KnownDevice, val transport: String,
        private val send: (String) -> Boolean, val close: () -> Unit) {
        private val credentials = credentials(peer)
        private val session = UUID.randomUUID().toString()
        private val challenge = UUID.randomUUID().toString()
        private var peerChallenge = ""
        private var sequence = 0L
        private val replay = ConnectReplayGuard()
        private val commands = kotlinx.coroutines.sync.Mutex()
        private var lastSent: PlaybackSnapshot? = null
        private var lastQueue = ""
        @Synchronized fun sendMessage(kind: String, id: String = "", action: String = "", value: Long = 0, value2: Long = 0,
            state: PlaybackSnapshot? = null, trackId: Long? = null, queueVersion: String = "",
            ok: Boolean = false, error: String = "") {
            val message = ConnectMessage(kind = kind, sender = SyncLog.deviceId, session = session,
                sequence = ++sequence, id = id, action = action, value = value, value2 = value2, state = state,
                challenge = challenge, replyTo = peerChallenge,
                trackId = trackId, queueVersion = queueVersion, ok = ok, error = error)
            runCatching { send(ConnectWire.seal(credentials, message)) }.onFailure { close() }
        }
        @Synchronized fun sendState(state: PlaybackSnapshot?, force: Boolean = false) {
            if (peerChallenge.isEmpty()) return
            if (state == null) { if (force) sendMessage("idle"); return }
            val version = ConnectWire.queueVersion(state)
            val old = lastSent
            val changed = old == null || old.currentIndex != state.currentIndex ||
                old.isPlaying != state.isPlaying || old.shuffleEnabled != state.shuffleEnabled ||
                old.repeatMode != state.repeatMode || old.volume != state.volume || version != lastQueue ||
                kotlin.math.abs(state.positionMs - old.projectedPosition(state.updatedAtMs)) > 1_000L
            if (!force && !changed && state.updatedAtMs - old!!.updatedAtMs < 60_000L) return
            val payload = if (force || lastQueue != version) state else state.copy(queue = emptyList())
            sendMessage("state", state = payload, queueVersion = version)
            lastSent = state; lastQueue = version
        }
        fun receive(text: String) {
            if (links[peer.deviceId] !== this || SyncPeers.find(peer.deviceId)?.secret != peer.secret) { close(); return }
            val message = ConnectWire.open(credentials, text) ?: return
            if (message.kind != "hello" && message.replyTo != challenge) return
            if (message.sender != peer.deviceId || !replay.accept(message)) return
            when (message.kind) {
                "hello", "welcome" -> {
                    if (message.challenge.length !in 16..64) return
                    peerChallenge = message.challenge
                    if (message.kind == "hello") sendMessage("welcome")
                    updatePeer(peer.deviceId) { (it ?: ConnectPeerState(peer.deviceId, peer.label)).copy(connected = true, transport = transport) }
                    sendState(localState(), force = true)
                }
                "resync" -> sendState(localState(), force = true)
                "state" -> {
                    val incoming = message.state ?: return
                    val old = _peers.value[peer.deviceId]?.snapshot
                    val queue = if (incoming.queue.isEmpty()) {
                        if (_peers.value[peer.deviceId]?.queueVersion != message.queueVersion) { sendMessage("resync"); return }
                        old?.queue ?: return
                    } else incoming.queue
                    if (incoming.deviceId != peer.deviceId || queue.size !in 1..500 ||
                        incoming.currentIndex !in queue.indices || incoming.positionMs < 0 ||
                        incoming.repeatMode !in listOf("NONE", "ALL", "ONE") ||
                        incoming.volume?.let { !it.isFinite() || it !in 0f..1f } == true) return
                    val state = incoming.copy(queue = queue, updatedAtMs = System.currentTimeMillis())
                    if (ConnectWire.queueVersion(state) != message.queueVersion) return
                    updatePeer(peer.deviceId) { ConnectPeerState(peer.deviceId, peer.label, true, transport,
                        state, message.queueVersion) }
                }
                "ack" -> pending.remove(message.id)?.complete(message)
                "command" -> scope.launch {
                    commands.lock()
                    try {
                    val key = "${peer.deviceId}:${message.id}"
                    val duplicate = synchronized(handledCommands) { handledCommands.containsKey(key) }
                    if (duplicate || message.id.length !in 16..64) { sendMessage("ack", id = message.id, error = "Duplicate command"); return@launch }
                    synchronized(handledCommands) {
                        handledCommands[key] = true
                        while (handledCommands.size > 256) handledCommands.remove(handledCommands.keys.first())
                    }
                    val result = runCatching {
                        withContext(Dispatchers.Main) {
                            val current = snapshotProvider?.invoke()
                            if (message.trackId != null) require(current?.queue?.getOrNull(current.currentIndex)?.id == message.trackId) { "Track changed; try again" }
                            if (message.queueVersion.isNotBlank()) require(current != null && ConnectWire.queueVersion(current) == message.queueVersion) { "Queue changed; try again" }
                            requireNotNull(handler) { "Player is starting" }.invoke(message)
                        }
                    }
                    sendMessage("ack", id = message.id, ok = result.isSuccess,
                        error = result.exceptionOrNull()?.message?.take(100).orEmpty())
                    withContext(Dispatchers.Main) { local = snapshotProvider?.invoke(); sendState(localState()) }
                    } finally { commands.unlock() }
                }
            }
        }
        fun detach() {
            if (links.remove(peer.deviceId, this)) updatePeer(peer.deviceId) {
                (it ?: ConnectPeerState(peer.deviceId, peer.label)).offline()
            }
        }
    }

    fun command(peerId: String, action: String, value: Long = 0, state: PlaybackSnapshot? = null, value2: Long = 0) {
        scope.launch {
            _feedback.value = ""
            val result = runCatching { executeRemote(peerId, action, value, state, value2) }
            _feedback.value = result.exceptionOrNull()?.message ?: "✓"
        }
    }
    private suspend fun executeRemote(peerId: String, action: String, value: Long = 0, state: PlaybackSnapshot? = null, value2: Long = 0) {
        val link = links[peerId]?.takeIf { _peers.value[peerId]?.connected == true } ?: error("Device is offline")
        val remote = _peers.value[peerId]
        val id = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<ConnectMessage>()
        pending[id] = deferred
        try {
            link.sendMessage("command", id = id, action = action, value = value, value2 = value2, state = state,
                trackId = remote?.snapshot?.queue?.getOrNull(remote.snapshot.currentIndex)?.id.takeIf { action == "seek" },
                queueVersion = remote?.queueVersion.orEmpty().takeIf { action in listOf("queue", "remove", "move") }.orEmpty())
            val ack = withTimeout(8_000) { deferred.await() }
            check(ack.ok) { ack.error.ifBlank { "Command rejected" } }
        } finally { pending.remove(id) }
    }
    fun transferHere(peerId: String) {
        scope.launch {
            val remote = _peers.value[peerId] ?: return@launch
            val state = remote.snapshot?.copy(positionMs = remote.position(), updatedAtMs = System.currentTimeMillis()) ?: return@launch
            val result = runCatching {
                executeRemote(peerId, "pause")
                selectDevice(null)
                withContext(Dispatchers.Main) {
                    handler?.invoke(ConnectMessage(kind = "command", sender = peerId, session = UUID.randomUUID().toString(),
                        sequence = 1, action = "transfer", state = state))
                }
            }
            _feedback.value = result.exceptionOrNull()?.message ?: "✓"
        }
    }
    fun transferThere(peerId: String) {
        scope.launch {
            val state = withContext(Dispatchers.Main) { snapshotProvider?.invoke() } ?: return@launch
            val result = runCatching {
                executeRemote(peerId, "transfer", state = state)
                withContext(Dispatchers.Main) {
                    handler?.invoke(ConnectMessage(kind = "command", sender = SyncLog.deviceId,
                        session = UUID.randomUUID().toString(), sequence = 1, action = "pause"))
                }
                selectDevice(peerId)
            }
            _feedback.value = result.exceptionOrNull()?.message ?: "✓"
        }
    }
}

const val ConnectLanPort = 47654
