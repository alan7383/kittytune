package com.alananasss.kittytune.data.together

import android.util.Log
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

/**
 * One connection to the relay, carrying the sealed messages of the shared playlists this app follows (issue #66,
 * ported from desktop).
 *
 * MQTT over TLS to a public broker: the protocol is made for small messages to many listeners over flaky networks,
 * the client reconnects by itself, and nothing needs running on our side. The broker only ever relays ciphertext;
 * see [TogetherWire]. Brokers are tried in order, the next one when the first will not take us.
 *
 * The same public brokers and the same topic space as the desktop app, so the two platforms meet in the same room.
 *
 * Everything the relay is asked to do — connect, subscribe, publish — is queued on a single background worker, in
 * the order asked, so a call from the UI thread never waits on the network: an unreachable broker used to freeze
 * the caller for the whole connect timeout, which is what an ANR looks like from the outside. When the broker is
 * down, queued work is dropped rather than piled up without end, exactly as the old blocking calls came back with
 * nothing sent.
 */
internal class TogetherRelay(private val clientId: String) {

    data class Incoming(
        val code: String,
        val message: TogetherMessage,
        val isSnapshot: Boolean = false,
    )

    private val _incoming = MutableSharedFlow<Incoming>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val incoming: SharedFlow<Incoming> = _incoming

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val followedCodes = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val subscribedOnCurrentSession = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val codesByTopic = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val codesBySnapPrefix = java.util.concurrent.ConcurrentHashMap<String, String>()
    @Volatile
    private var client: MqttAsyncClient? = null

    /** One worker for connect/subscribe/publish, so messages leave in the order they were asked for. */
    private val workerDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val scopeJob = SupervisorJob()
    private val scope = CoroutineScope(scopeJob + Dispatchers.IO)

    /** The queue of things to do on the wire. Bounded: presence is re-sent every few seconds anyway. */
    private val outbox = Channel<Pending>(capacity = OUTBOX_CAPACITY, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private sealed interface Pending {
        data class Publish(val code: String, val message: TogetherMessage) : Pending
        data class PublishSnapshot(val topic: String, val code: String, val message: TogetherMessage) : Pending
        data class ClearSnapshots(val code: String) : Pending
        data class Subscribe(val code: String, val topics: Array<String>) : Pending
        data class Unsubscribe(val code: String, val topics: Array<String>) : Pending
    }

    private var lastConnectAttemptAt = 0L
    private val worker: Job = scope.launch(workerDispatcher) {
        for (work in outbox) {
            // Reactions are transient. Drop them when disconnected or delayed in the queue.
            if (work is Pending.Publish && work.message is TogetherMessage.Reaction) {
                if (client?.isConnected != true || System.currentTimeMillis() - work.message.sentAt > 3_000L) continue
            } else if (!ensureConnected()) continue
            val missingCodes = followedCodes.filter {
                it !in subscribedOnCurrentSession && (work as? Pending.Subscribe)?.code != it
            }
            if (missingCodes.isNotEmpty()) {
                val missingTopics = missingCodes.flatMap {
                    listOf(TogetherWire.topicFor(it), TogetherWire.snapshotWildcardTopicFor(it))
                }.toTypedArray()
                val missingQos = IntArray(missingTopics.size) { 1 }
                runCatching {
                    client?.subscribe(missingTopics, missingQos)?.waitForCompletion(SUBSCRIBE_TIMEOUT_MS)
                    subscribedOnCurrentSession.addAll(missingCodes)
                }.onFailure { logWarn("Restore subscribe failed: ${it.message}") }
            }
            when (work) {
                is Pending.Publish -> runCatching {
                    client?.publish(
                        TogetherWire.topicFor(work.code),
                        MqttMessage(TogetherWire.seal(work.code, work.message)).apply { qos = work.message.deliveryQos }
                    )
                }.onFailure { logWarn("Send failed: ${it.message}") }
                is Pending.PublishSnapshot -> runCatching {
                    client?.publish(
                        work.topic,
                        MqttMessage(TogetherWire.seal(work.code, work.message)).apply {
                            qos = work.message.deliveryQos
                            isRetained = true
                        }
                    )
                }.onFailure { logWarn("Snapshot send failed: ${it.message}") }
                is Pending.ClearSnapshots -> {
                    runCatching {
                        client?.publish(
                            TogetherWire.stateSnapshotTopicFor(work.code),
                            MqttMessage(ByteArray(0)).apply { qos = 1; isRetained = true }
                        )
                    }.onFailure { logWarn("Clear state snapshot failed: ${it.message}") }
                    runCatching {
                        client?.publish(
                            TogetherWire.queueSnapshotTopicFor(work.code),
                            MqttMessage(ByteArray(0)).apply { qos = 1; isRetained = true }
                        )
                    }.onFailure { logWarn("Clear queue snapshot failed: ${it.message}") }
                }
                is Pending.Subscribe -> runCatching {
                    val qos = IntArray(work.topics.size) { 1 }
                    client?.subscribe(work.topics, qos)?.waitForCompletion(SUBSCRIBE_TIMEOUT_MS)
                    subscribedOnCurrentSession.add(work.code)
                }.onFailure { logWarn("Subscribe failed: ${it.message}") }
                is Pending.Unsubscribe -> {
                    subscribedOnCurrentSession.remove(work.code)
                    runCatching { client?.unsubscribe(work.topics) }
                }
            }
        }
    }

    /**
     * Makes sure a client exists and is connected, or that the one we have is reconnecting itself. Returns whether
     * the wire is usable right now.
     *
     * The client is created at most once: Paho's automatic reconnect keeps retrying the one it has, and building a
     * second one next to it used to leave a zombie whose callbacks kept firing into both.
     */
    private suspend fun ensureConnected(): Boolean {
        val existing = client
        if (existing != null) {
            if (existing.isConnected) return true
            // Its own automatic reconnect is on it: wait for it, bounded, instead of starting a second client.
            return withTimeoutOrNull(RECONNECT_WAIT_MS) {
                while (isActive && !existing.isConnected) delay(RECONNECT_POLL_MS)
                existing.isConnected
            } == true
        }
        val now = System.currentTimeMillis()
        if (now - lastConnectAttemptAt < RETRY_COOLDOWN_MS) return false
        lastConnectAttemptAt = now
        val brokers = System.getProperty("kittytune.together.testBroker")?.let(::listOf) ?: BROKERS
        for (broker in brokers) {
            val attempt = runCatching { connect(broker) }
            if (attempt.isSuccess && client?.isConnected == true) return true
            logWarn("Could not reach $broker: ${attempt.exceptionOrNull()?.message}")
        }
        return false
    }

    private fun connect(broker: String) {
        val mqtt = MqttAsyncClient(broker, clientId, MemoryPersistence())
        mqtt.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                _isConnected.value = true
                if (reconnect) {
                    // A reconnect starts with no subscriptions: take both live and snapshot topics again.
                    subscribedOnCurrentSession.clear()
                    followedCodes.forEach { code ->
                        val topics = arrayOf(TogetherWire.topicFor(code), TogetherWire.snapshotWildcardTopicFor(code))
                        runCatching {
                            mqtt.subscribe(topics, intArrayOf(1, 1))
                            subscribedOnCurrentSession.add(code)
                        }
                    }
                }
            }

            override fun connectionLost(cause: Throwable?) {
                subscribedOnCurrentSession.clear()
                _isConnected.value = false
            }

            override fun messageArrived(topic: String, message: MqttMessage) {
                if (message.payload.isEmpty()) return
                val code = resolveCodeForTopic(topic) ?: return
                val opened = TogetherWire.open(code, message.payload) ?: return
                val isSnapshot = message.isRetained || topic.contains("/snap/")
                _incoming.tryEmit(Incoming(code, opened, isSnapshot))
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
        })
        val options = MqttConnectOptions().apply {
            isAutomaticReconnect = true
            isCleanSession = true
            connectionTimeout = 5
            keepAliveInterval = 30
        }
        mqtt.connect(options).waitForCompletion(CONNECT_TIMEOUT_MS)
        client = mqtt
        _isConnected.value = true
    }

    internal fun resolveCodeForTopic(topic: String): String? {
        codesByTopic[topic]?.let { return it }
        if (topic.startsWith(SNAP_TOPIC_ROOT)) {
            val rest = topic.substring(SNAP_TOPIC_ROOT.length)
            val slash = rest.indexOf('/')
            if (slash > 0) {
                val snapPrefix = SNAP_TOPIC_ROOT + rest.substring(0, slash + 1)
                return codesBySnapPrefix[snapPrefix]
            }
        }
        return null
    }

    fun follow(code: String) {
        val normalized = TogetherWire.normalizeCode(code)
        if (normalized.isEmpty()) return
        val mainTopic = TogetherWire.topicFor(normalized)
        val snapWildcard = TogetherWire.snapshotWildcardTopicFor(normalized)
        val snapPrefix = snapWildcard.removeSuffix("#")
        followedCodes.add(normalized)
        codesByTopic[mainTopic] = normalized
        codesBySnapPrefix[snapPrefix] = normalized
        outbox.trySend(Pending.Subscribe(normalized, arrayOf(mainTopic, snapWildcard)))
    }

    fun unfollow(code: String) {
        val normalized = TogetherWire.normalizeCode(code)
        if (normalized.isEmpty()) return
        val mainTopic = TogetherWire.topicFor(normalized)
        val snapWildcard = TogetherWire.snapshotWildcardTopicFor(normalized)
        val snapPrefix = snapWildcard.removeSuffix("#")
        followedCodes.remove(normalized)
        codesByTopic.remove(mainTopic)
        codesBySnapPrefix.remove(snapPrefix)
        outbox.trySend(Pending.Unsubscribe(normalized, arrayOf(mainTopic, snapWildcard)))
    }

    fun isFollowing(code: String): Boolean = followedCodes.contains(TogetherWire.normalizeCode(code))

    fun send(code: String, message: TogetherMessage) {
        outbox.trySend(Pending.Publish(TogetherWire.normalizeCode(code), message))
    }

    fun sendSnapshot(topic: String, code: String, message: TogetherMessage) {
        outbox.trySend(Pending.PublishSnapshot(topic, TogetherWire.normalizeCode(code), message))
    }

    fun clearSnapshots(code: String) {
        outbox.trySend(Pending.ClearSnapshots(TogetherWire.normalizeCode(code)))
    }

    fun close() {
        worker.cancel()
        outbox.close()
        scopeJob.cancel()
        runCatching { client?.disconnect()?.waitForCompletion(2_000) }
        runCatching { client?.close() }
        client = null
        subscribedOnCurrentSession.clear()
        _isConnected.value = false
    }

    private fun logWarn(message: String) {
        runCatching { Log.w("TogetherRelay", message) }
    }

    private companion object {
        const val SNAP_TOPIC_ROOT = "kittytune/together/snap/"
        val BROKERS = listOf("ssl://broker.emqx.io:8883", "ssl://broker.hivemq.com:8883")
        const val CONNECT_TIMEOUT_MS = 5_000L
        const val SUBSCRIBE_TIMEOUT_MS = 1_500L

        /** Long enough that a full failed round of brokers is not retried on every queued presence message. */
        const val RETRY_COOLDOWN_MS = 30_000L

        /** How long Paho's own reconnect is given before the wire is reported unusable for this message. */
        const val RECONNECT_WAIT_MS = 8_000L
        const val RECONNECT_POLL_MS = 250L

        /** A few minutes of state and presence; the newest is what matters, the oldest is dropped. */
        const val OUTBOX_CAPACITY = 256
    }
}
