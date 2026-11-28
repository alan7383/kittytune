package com.alananasss.kittytune.data.together

import com.alananasss.kittytune.data.sync.NetLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * The wire under Listen Together: sealed messages for the shared rooms this app follows, over public MQTT
 * brokers (issue #66). The brokers only ever see ciphertext; see [TogetherWire].
 *
 * ## Every broker at once
 *
 * The relay used to connect to one broker and fall back to the next only when the first refused. Two
 * people who landed on different brokers were then in different rooms with the same code, and neither
 * could tell. Now it stays connected to every broker it can reach, publishes each sealed message to all of
 * them and drops the copies on the way in (identical ciphertext, so identical bytes). Anyone who can reach
 * any one broker meets everyone else on it. The website does the same.
 *
 * ## Every route to each broker
 *
 * MQTT over TLS on port 8883 is blocked on plenty of networks: mobile carriers, schools, offices, some home
 * routers. That showed up as "stuck on Reconnecting" with nothing in the logs. Each broker is now tried over
 * TLS and then over secure WebSockets (the route a browser uses), and the route that worked is tried first
 * next time.
 *
 * ## Reconnecting is ours
 *
 * Paho's own automatic reconnect kept retrying one route forever and never tried another. Each broker has a
 * small loop instead: connect over the first route that answers, wait for the connection to drop, back off
 * (2 s up to a minute, with jitter) and try again. [follow] or a new message wakes the loops at once.
 *
 * Connect, subscribe and publish run on one worker, in the order asked, so callers never block on the
 * network; a broker that connects later takes every followed room before it is used.
 */
internal class TogetherRelay(private val clientId: String) {

    data class Incoming(val code: String, val message: TogetherMessage, val isSnapshot: Boolean = false)

    private val _incoming = MutableSharedFlow<Incoming>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val incoming: SharedFlow<Incoming> = _incoming

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val followedCodes = ConcurrentHashMap.newKeySet<String>()
    private val codesByTopic = ConcurrentHashMap<String, String>()
    private val codesBySnapPrefix = ConcurrentHashMap<String, String>()

    private val scopeJob = SupervisorJob()
    private val scope = CoroutineScope(scopeJob + Dispatchers.IO)
    private val workerDispatcher = Dispatchers.IO.limitedParallelism(1)

    /** Whether there is anything to be connected for. Nothing connects until a room is followed or sent to. */
    @Volatile private var wanted = false

    /** The queue of things to do on the wire. Bounded: presence and state are re-sent every few seconds anyway. */
    private val outbox = Channel<Pending>(capacity = OUTBOX_CAPACITY, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private sealed interface Pending {
        data class Publish(val topic: String, val code: String, val message: TogetherMessage, val isRetained: Boolean) : Pending
        data class ClearSnapshots(val topics: List<String>) : Pending
        data class Subscribe(val topics: Array<String>) : Pending
        data class Unsubscribe(val topics: Array<String>) : Pending
        data object Disconnect : Pending
    }

    /** Copies of one message arriving from several brokers: the first is kept, the rest dropped. */
    private val seen = object : LinkedHashMap<String, Boolean>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > DEDUPE_WINDOW
    }

    private val links: List<BrokerLink> = (System.getProperty("kittytune.together.testBroker")
        ?.let { listOf(Broker(it, listOf(it))) } ?: BROKERS)
        .mapIndexed { index, broker -> BrokerLink(index, broker) }

    init {
        links.forEach { link -> scope.launch { link.run() } }
    }

    @Suppress("unused")
    private val worker: Job = scope.launch(workerDispatcher) {
        for (work in outbox) {
            when (work) {
                Pending.Disconnect -> {
                    wanted = false
                    links.forEach { it.disconnect() }
                }
                is Pending.Subscribe -> live().forEach { it.subscribe(work.topics) }
                is Pending.Unsubscribe -> live().forEach { it.unsubscribe(work.topics) }
                is Pending.ClearSnapshots -> {
                    if (!awaitAnyConnection()) continue
                    val clear = ByteArray(0)
                    work.topics.forEach { topic -> live().forEach { it.publish(topic, clear, qos = 1, retained = true) } }
                }
                is Pending.Publish -> {
                    // Reactions are transient: dropped when nothing is connected or they waited too long.
                    if (work.message is TogetherMessage.Reaction) {
                        if (!_isConnected.value || System.currentTimeMillis() - work.message.sentAt > 3_000L) continue
                    } else if (!awaitAnyConnection()) continue
                    // Sealed once: every broker carries the same bytes, which is what lets receivers drop copies.
                    val payload = runCatching { TogetherWire.seal(work.code, work.message) }.getOrNull() ?: continue
                    live().forEach { it.publish(work.topic, payload, work.message.deliveryQos, work.isRetained) }
                }
            }
        }
    }

    private fun live() = links.filter { it.connected }

    private suspend fun awaitAnyConnection(): Boolean {
        wanted = true
        if (_isConnected.value) return true
        links.forEach { it.wake() }
        return withTimeoutOrNull(SEND_WAIT_MS) { _isConnected.first { it } } == true
    }

    private fun updateConnected() {
        _isConnected.value = links.any { it.connected }
    }

    private fun deliver(topic: String, message: MqttMessage) {
        val payload = message.payload
        if (payload.isEmpty()) return
        val key = topic + "|" + digest(payload)
        synchronized(seen) {
            if (seen.put(key, true) != null) return
        }
        val code = resolveCodeForTopic(topic) ?: return
        val opened = TogetherWire.open(code, payload) ?: return
        val isSnapshot = message.isRetained || topic.contains("/snap/")
        _incoming.tryEmit(Incoming(code, opened, isSnapshot = isSnapshot))
    }

    private fun followedTopics(): Array<String> = followedCodes.flatMap {
        listOf(TogetherWire.topicFor(it), TogetherWire.snapshotWildcardTopicFor(it))
    }.toTypedArray()

    /** One broker, reached over whichever of its routes works. */
    private inner class BrokerLink(private val index: Int, private val broker: Broker) {
        @Volatile private var client: MqttAsyncClient? = null
        @Volatile var connected = false
            private set
        private val wakeUp = Channel<Unit>(Channel.CONFLATED)
        @Volatile private var lost = CompletableDeferred<Unit>()

        fun wake() { wakeUp.trySend(Unit) }

        suspend fun run() {
            var backoff = MIN_BACKOFF_MS
            while (scope.isActive) {
                if (!wanted) { wakeUp.receive(); continue }
                if (connectAnyRoute()) {
                    backoff = MIN_BACKOFF_MS
                    lost.await()
                    // A drop is usually the network changing under us: retry soon, not after a full backoff.
                    withTimeoutOrNull(1_000L) { wakeUp.receive() }
                    continue
                }
                val wait = backoff + kotlin.random.Random.nextLong(0, backoff / 4 + 1)
                withTimeoutOrNull(wait) { wakeUp.receive() }
                backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }

        /** Tries the route that worked last time first, then the others. */
        private fun connectAnyRoute(): Boolean {
            val preferred = preferredRoute[broker.name] ?: 0
            val order = broker.routes.indices.sortedBy { if (it == preferred) -1 else it }
            for (route in order) {
                if (!wanted) return false
                val uri = broker.routes[route]
                val started = System.currentTimeMillis()
                val result = runCatching { open(uri) }
                if (result.isSuccess) {
                    preferredRoute[broker.name] = route
                    NetLog.i("together", "Connected to ${describe(uri)} in ${System.currentTimeMillis() - started} ms")
                    return true
                }
                NetLog.w("together", "Could not reach ${describe(uri)} after ${System.currentTimeMillis() - started} ms", result.exceptionOrNull())
            }
            return false
        }

        private fun open(uri: String) {
            val mqtt = MqttAsyncClient(uri, "$clientId-$index", MemoryPersistence())
            val dropped = CompletableDeferred<Unit>()
            mqtt.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    if (client === mqtt) {
                        connected = false
                        client = null
                        updateConnected()
                        NetLog.w("together", "Lost ${describe(uri)}", cause)
                    }
                    runCatching { mqtt.close() }
                    dropped.complete(Unit)
                }

                override fun messageArrived(topic: String, message: MqttMessage) = deliver(topic, message)

                override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
            })
            val options = MqttConnectOptions().apply {
                isAutomaticReconnect = false
                isCleanSession = true
                connectionTimeout = CONNECT_TIMEOUT_S
                keepAliveInterval = KEEP_ALIVE_S
                maxInflight = 64
            }
            try {
                mqtt.connect(options).waitForCompletion(CONNECT_TIMEOUT_S * 1_000L + 3_000L)
                val topics = followedTopics()
                if (topics.isNotEmpty()) {
                    mqtt.subscribe(topics, IntArray(topics.size) { 1 }).waitForCompletion(SUBSCRIBE_TIMEOUT_MS)
                }
            } catch (t: Throwable) {
                runCatching { mqtt.disconnectForcibly(0, 500) }
                runCatching { mqtt.close() }
                throw t
            }
            lost = dropped
            client = mqtt
            connected = true
            updateConnected()
            // Dropped between the CONNACK and here: connectionLost saw no client to clear, so clear it now.
            if (dropped.isCompleted && client === mqtt) {
                client = null
                connected = false
                updateConnected()
                error("Connection dropped while subscribing")
            }
        }

        fun subscribe(topics: Array<String>) {
            val mqtt = client ?: return
            runCatching { mqtt.subscribe(topics, IntArray(topics.size) { 1 }).waitForCompletion(SUBSCRIBE_TIMEOUT_MS) }
                .onFailure { NetLog.w("together", "Subscribe failed on ${broker.name}", it) }
        }

        fun unsubscribe(topics: Array<String>) {
            runCatching { client?.unsubscribe(topics) }
        }

        fun publish(topic: String, payload: ByteArray, qos: Int, retained: Boolean) {
            val mqtt = client ?: return
            runCatching { mqtt.publish(topic, MqttMessage(payload).apply { this.qos = qos; isRetained = retained }) }
                .onFailure { NetLog.w("together", "Send failed on ${broker.name}", it) }
        }

        fun disconnect() {
            val mqtt = client ?: return
            client = null
            connected = false
            updateConnected()
            runCatching { mqtt.disconnect().waitForCompletion(2_000L) }
            runCatching { mqtt.close() }
            lost.complete(Unit)
        }
    }

    internal fun resolveCodeForTopic(topic: String): String? {
        codesByTopic[topic]?.let { return it }
        for ((prefix, code) in codesBySnapPrefix) {
            if (topic.startsWith(prefix)) return code
        }
        return null
    }

    fun follow(code: String) {
        val normalized = TogetherWire.normalizeCode(code)
        if (normalized.isEmpty()) return
        val mainTopic = TogetherWire.topicFor(normalized)
        val snapWildcard = TogetherWire.snapshotWildcardTopicFor(normalized)
        followedCodes.add(normalized)
        codesByTopic[mainTopic] = normalized
        codesByTopic[TogetherWire.stateSnapshotTopicFor(normalized)] = normalized
        codesByTopic[TogetherWire.queueSnapshotTopicFor(normalized)] = normalized
        codesBySnapPrefix[snapWildcard.removeSuffix("#")] = normalized
        wanted = true
        links.forEach { it.wake() }
        outbox.trySend(Pending.Subscribe(arrayOf(mainTopic, snapWildcard)))
    }

    fun unfollow(code: String) {
        val normalized = TogetherWire.normalizeCode(code)
        if (normalized.isEmpty()) return
        val mainTopic = TogetherWire.topicFor(normalized)
        val snapWildcard = TogetherWire.snapshotWildcardTopicFor(normalized)
        followedCodes.remove(normalized)
        codesByTopic.remove(mainTopic)
        codesByTopic.remove(TogetherWire.stateSnapshotTopicFor(normalized))
        codesByTopic.remove(TogetherWire.queueSnapshotTopicFor(normalized))
        codesBySnapPrefix.remove(snapWildcard.removeSuffix("#"))
        outbox.trySend(Pending.Unsubscribe(arrayOf(mainTopic, snapWildcard)))
    }

    fun isFollowing(code: String): Boolean = followedCodes.contains(TogetherWire.normalizeCode(code))

    fun send(code: String, message: TogetherMessage) {
        val normalized = TogetherWire.normalizeCode(code)
        outbox.trySend(Pending.Publish(TogetherWire.topicFor(normalized), normalized, message, isRetained = false))
    }

    fun sendSnapshot(topic: String, code: String, message: TogetherMessage) {
        outbox.trySend(Pending.Publish(topic, TogetherWire.normalizeCode(code), message, isRetained = true))
    }

    fun clearSnapshots(code: String) {
        val normalized = TogetherWire.normalizeCode(code)
        outbox.trySend(
            Pending.ClearSnapshots(listOf(TogetherWire.stateSnapshotTopicFor(normalized), TogetherWire.queueSnapshotTopicFor(normalized)))
        )
    }

    /** Disconnects from every broker. The relay stays usable: following a room again reconnects. */
    fun close() {
        while (outbox.tryReceive().isSuccess) {
            // Stale work is dropped rather than sent on the way out.
        }
        wanted = false
        outbox.trySend(Pending.Disconnect)
    }

    /** A public broker and the routes to it, best first. */
    internal data class Broker(val name: String, val routes: List<String>)

    internal companion object {
        val BROKERS = listOf(
            Broker("broker.emqx.io", listOf("ssl://broker.emqx.io:8883", "wss://broker.emqx.io:8084/mqtt")),
            Broker("broker.hivemq.com", listOf("ssl://broker.hivemq.com:8883", "wss://broker.hivemq.com:8884/mqtt")),
        )

        /** The route that last worked for each broker, for as long as the app runs. */
        private val preferredRoute = ConcurrentHashMap<String, Int>()

        const val CONNECT_TIMEOUT_S = 10
        const val KEEP_ALIVE_S = 30
        const val SUBSCRIBE_TIMEOUT_MS = 5_000L
        const val SEND_WAIT_MS = 10_000L
        const val MIN_BACKOFF_MS = 2_000L
        const val MAX_BACKOFF_MS = 60_000L
        const val OUTBOX_CAPACITY = 256
        const val DEDUPE_WINDOW = 512

        /** "broker.emqx.io over TLS (8883)": the route, without anything about the room. */
        fun describe(uri: String): String = runCatching {
            val parsed = java.net.URI(uri)
            val how = when (parsed.scheme) { "ssl" -> "TLS"; "wss" -> "WebSocket"; else -> parsed.scheme }
            "${parsed.host} over $how (${parsed.port})"
        }.getOrDefault(uri)

        fun digest(payload: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(payload).take(12).joinToString("") { "%02x".format(it) }
    }
}
