package com.alananasss.kittytune.data.sync

import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS).pingInterval(60, TimeUnit.SECONDS).build()
    private val lanClient = client.newBuilder().connectTimeout(3, TimeUnit.SECONDS).proxy(java.net.Proxy.NO_PROXY).build()
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
    private val transferLock = kotlinx.coroutines.sync.Mutex()
    private val _transferring = MutableStateFlow(false)
    val transferring: StateFlow<Boolean> = _transferring
    private val _independent = MutableStateFlow(false)
    val independent: StateFlow<Boolean> = _independent
    private val _autoHeadphones = MutableStateFlow(ConnectPlatform.autoHeadphones)
    val autoHeadphones: StateFlow<Boolean> = _autoHeadphones
    private var headphonesConnected = false
    private var suppressAutomatic = false
    private var automaticJob: Job? = null
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

    fun setForeground(value: Boolean) {
        foreground = value; _visible.value = value; refresh()
        if (!value) { automaticJob?.cancel() } else if (headphonesConnected) scheduleHeadphoneTransfer()
    }
    fun networkChanged() { if (active()) restart() }
    @Synchronized fun selectDevice(id: String?) { selection.choose(id); _selectedDevice.value = selection.selectedDevice }
    /** A remote command addresses our renderer; it is not a manual choice to pin this UI locally. */
    @Synchronized fun activateLocalRenderer() { selection.activateLocalRenderer(); _selectedDevice.value = null }
    fun setEnabled(value: Boolean) {
        enabled = value; _independent.value = !value
        if (!value) { automaticJob?.cancel(); selectDevice(null); SyncPlayback.claimLocal() }
        refresh()
    }
    fun setIndependent(value: Boolean) { suppressAutomatic = true; SyncPlayback.enabled = !value }
    fun setAutoHeadphones(value: Boolean) {
        ConnectPlatform.autoHeadphones = value; _autoHeadphones.value = value
        automaticJob?.cancel(); suppressAutomatic = false; refresh()
        if (value && headphonesConnected) scheduleHeadphoneTransfer()
    }
    fun headphonesChanged(connected: Boolean) {
        if (headphonesConnected == connected) return
        headphonesConnected = connected
        automaticJob?.cancel()
        if (!connected) {
            suppressAutomatic = false
            // Only pause our renderer; never pause a PC selected by the user.
            if (ConnectPlatform.autoHeadphones && selectedDevice.value == null && playing) scope.launch {
                runCatching { executeLocal("pause") }
            }
        } else scheduleHeadphoneTransfer()
    }
    private fun scheduleHeadphoneTransfer() {
        if (!ConnectPlatform.mobile || !foreground || !enabled || !SyncPlayback.enabled ||
            !ConnectPlatform.autoHeadphones || !ConnectPlatform.canAutoTransfer() || suppressAutomatic || playing || automaticJob?.isActive == true) return
        automaticJob = scope.launch {
            val remote = withTimeoutOrNull(20_000) {
                peers.first { states -> states.values.any { it.connected && it.snapshot?.isPlaying == true && SyncPeers.find(it.deviceId)?.platform != "android" } }
            }?.values?.filter { it.connected && it.snapshot?.isPlaying == true && SyncPeers.find(it.deviceId)?.platform != "android" }
                ?.maxByOrNull { it.snapshot?.updatedAtMs ?: 0L } ?: return@launch
            if (headphonesConnected && foreground && !suppressAutomatic && !playing && SyncPlayback.enabled) {
                switchOutput(null, sourceOverride = remote.deviceId, automatic = true)
            }
        }
    }
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
        _independent.value = !SyncPlayback.enabled
        ConnectPlatform.observeHeadphones(enabled && SyncPlayback.enabled && handler != null && (foreground || playing))
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
                if (links[peerId]?.transport == "LAN") {
                    _peers.first { links[peerId]?.transport != "LAN" }
                    continue
                }
                var established = false
                if (ConnectPlatform.mobile && ConnectPlatform.canUseLan() && peer.platform != "android" && peer.host.isNotBlank()) {
                    established = connect(peer, "ws://${peer.host}:${ConnectLanPort}/v1/connect", "LAN")
                    if (!established && relayUrl.isBlank()) {
                        // One bounded discovery attempt per backoff, never a continuous LAN scan.
                        val found = runCatching { SyncDiscovery.locate(peerId).firstOrNull() }.getOrNull()
                        if (found != null) SyncPeers.remember(peer.copy(host = found.host))
                    }
                }
                if (!active()) break
                if (!established && relayUrl.isNotBlank()) {
                    established = connect(peer, ConnectPlatform.relayEndpoint() + "/v1/connect", "Internet")
                }
                if (established) backoff = 2_000L
                delay(backoff + kotlin.random.Random.nextLong(0, 500))
                backoff = (backoff * 2).coerceAtMost(120_000L)
            }
        } finally { /* connect() owns its socket; an obsolete job must not cancel a replacement. */ }
    }

    private suspend fun connect(peer: KnownDevice, url: String, transport: String): Boolean {
        ConnectPlatform.trace("connect $transport")
        val credentials = runCatching { credentials(peer) }.getOrNull() ?: return false
        var link: Link? = null
        lateinit var listener: ConnectSocketListener
        listener = ConnectSocketListener(opened = { webSocket ->
                webSocket.send(gson.toJson(mapOf("type" to "join", "room" to credentials.room,
                    "token" to credentials.token, "device" to SyncLog.deviceId)))
            }, received = received@ { webSocket, text ->
                if (text == "{\"type\":\"ready\"}") {
                    ConnectPlatform.trace("ready $transport")
                    if (transport != "LAN" && links[peer.deviceId]?.transport == "LAN") {
                        webSocket.close(1000, "LAN preferred")
                        listener.finish("")
                        return@received
                    }
                    if (link == null) link = attach(peer, transport, webSocket::send) { webSocket.close(1000, "idle") }
                    else { link?.sendMessage("hello"); link?.sendState(localState(), force = true) }
                    listener.ready.complete(true)
                } else if (text == "{\"type\":\"peer_left\"}") {
                    updatePeer(peer.deviceId) { (it ?: ConnectPeerState(peer.deviceId, peer.label)).offline() }
                } else link?.receive(text)
            }, departed = { error ->
                ConnectPlatform.trace("departed $transport $error")
                link?.detach()
                if (error.isNotBlank() && links[peer.deviceId] == null) updatePeer(peer.deviceId) {
                    (it ?: ConnectPeerState(peer.deviceId, peer.label)).offline().copy(error = error)
                }
            })
        val socket = (if (transport == "LAN") lanClient else client).newWebSocket(Request.Builder().url(url).build(), listener)
        sockets[peer.deviceId] = socket
        return try {
            val connected = withTimeoutOrNull(if (transport == "LAN") 5_000L else 15_000L) { listener.ready.await() } == true
            if (!connected) return false
            listener.ended.await()
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
        private var peerCompression = false
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
                trackId = trackId, queueVersion = queueVersion, ok = ok, error = error, compression = "gzip")
            runCatching { send(ConnectWire.seal(credentials, message, compress = peerCompression)) }
                .onFailure { ConnectPlatform.trace("send failed $transport ${it.javaClass.simpleName}"); close() }
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
            ConnectPlatform.trace("received $transport ${message.kind}")
            if (message.kind != "hello" && message.replyTo != challenge) return
            if (message.sender != peer.deviceId || !replay.accept(message)) return
            when (message.kind) {
                "hello", "welcome" -> {
                    if (message.challenge.length !in 16..64) return
                    peerChallenge = message.challenge
                    peerCompression = message.compression == "gzip"
                    if (message.kind == "hello") sendMessage("welcome")
                    updatePeer(peer.deviceId) { (it ?: ConnectPeerState(peer.deviceId, peer.label)).copy(connected = true, transport = transport, error = "", snapshot = null, queueVersion = "") }
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
                    // Ordered websocket delivery makes the new queue visible before a transfer ACK.
                    // Subsequent guarded seeks must address the prepared track, not the old one.
                    withContext(Dispatchers.Main) { local = snapshotProvider?.invoke(); sendState(localState(), force = message.action == "transfer" && result.isSuccess) }
                    sendMessage("ack", id = message.id, ok = result.isSuccess,
                        error = result.exceptionOrNull()?.message?.take(100).orEmpty())
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
            val ack = withTimeout(if (action == "transfer") 30_000L else 8_000L) { deferred.await() }
            check(ack.ok) { ack.error.ifBlank { "Command rejected" } }
        } finally { pending.remove(id) }
    }
    private suspend fun executeLocal(action: String, state: PlaybackSnapshot? = null, value: Long = 0) {
        withContext(Dispatchers.Main) {
            check(SyncPlayback.enabled) { "Independent playback is enabled" }
            requireNotNull(handler) { "Player is starting" }.invoke(ConnectMessage(kind = "command", sender = SyncLog.deviceId,
                session = UUID.randomUUID().toString(), sequence = 1, action = action, state = state, value = value))
            publish(snapshotProvider?.invoke())
        }
    }
    private suspend fun sourceState(id: String?): PlaybackSnapshot? = if (id == null) {
        withContext(Dispatchers.Main) { snapshotProvider?.invoke() }
    } else _peers.value[id]?.takeIf { it.connected }?.let { peer ->
        peer.snapshot?.copy(positionMs = peer.position(), updatedAtMs = System.currentTimeMillis())
    }
    /** A device click changes the output, including remote-to-remote transfers. */
    fun switchOutput(target: String?, sourceOverride: String? = null, automatic: Boolean = false) {
        if (!automatic) { suppressAutomatic = true; automaticJob?.cancel() }
        if (!transferLock.tryLock()) return
        _transferring.value = true; _feedback.value = ""
        val source = sourceOverride ?: selectedDevice.value
        scope.launch {
            try {
                check(SyncPlayback.enabled) { "Turn off independent playback first" }
                if (source != target) {
                    if (target != null) check(_peers.value[target]?.connected == true) { "Device is offline" }
                    val state = sourceState(source)
                    if (state != null) ConnectHandoff.move(source, target, state, { sourceState(source) }) { device, action, snapshot, value ->
                        check(SyncPlayback.enabled) { "Independent playback is enabled" }
                        if (automatic && (device == source && action == "pause" || device == target && action == "play")) {
                            check(headphonesConnected && foreground && !suppressAutomatic && ConnectPlatform.canAutoTransfer()) { "Automatic transfer cancelled" }
                        }
                        if (device == null) executeLocal(action, snapshot, value)
                        else executeRemote(device, action, value = value, state = snapshot)
                    }
                    selectDevice(target)
                }
            } catch (failure: Exception) {
                _feedback.value = failure.message ?: "Could not switch device"
                selectDevice(source)
            } finally { _transferring.value = false; transferLock.unlock() }
        }
    }
    fun transferHere(peerId: String) = switchOutput(null, sourceOverride = peerId)
    fun transferThere(peerId: String) = switchOutput(peerId)
}

const val ConnectLanPort = 47654
