package com.alananasss.kittytune.data.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.concurrent.ConcurrentHashMap

/**
 * A connection to every public MQTT broker at once, over whichever route reaches each (TLS 8883, then
 * secure WebSockets), reconnecting by itself. What Connect uses to reach a paired device away from home;
 * the same brokers and routes as Listen Together.
 *
 * Everything published goes to every connected broker, so two devices meet as long as they share one.
 * Copies arriving from several brokers are the caller's to drop ([onMessage] says which broker).
 *
 * @param will a retained message the brokers publish if this device vanishes without saying goodbye.
 * @param onConnected called on each broker connection, after the subscriptions are back.
 */
internal class CloudMesh(
    private val clientId: String,
    private val will: Pair<String, ByteArray>?,
    private val onConnected: (broker: Int) -> Unit,
    private val onMessage: (topic: String, payload: ByteArray, broker: Int) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val topics = ConcurrentHashMap.newKeySet<String>()
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected
    @Volatile private var running = true
    private val links = BROKERS.mapIndexed { index, broker -> Link(index, broker) }
    private val jobs: List<Job> = links.map { link -> scope.launch { link.run() } }

    fun subscribe(vararg wanted: String) {
        val fresh = wanted.filter { topics.add(it) }.toTypedArray()
        if (fresh.isNotEmpty()) links.forEach { it.subscribe(fresh) }
    }

    fun unsubscribe(vararg gone: String) {
        val removed = gone.filter { topics.remove(it) }.toTypedArray()
        if (removed.isNotEmpty()) links.forEach { it.unsubscribe(removed) }
    }

    /** Publishes to every connected broker. Returns how many took it. */
    fun publish(topic: String, payload: ByteArray, qos: Int = 1, retained: Boolean = false): Int =
        links.count { it.publish(topic, payload, qos, retained) }

    fun publishTo(broker: Int, topic: String, payload: ByteArray, qos: Int = 1, retained: Boolean = false): Boolean =
        links.getOrNull(broker)?.publish(topic, payload, qos, retained) == true

    /** Says goodbye on every broker with [farewell] (if any), then disconnects for good. */
    fun stop(farewell: Pair<String, ByteArray>? = null) {
        running = false
        links.forEach { it.close(farewell) }
        jobs.forEach { it.cancel() }
        _connected.value = false
    }

    private fun update() { _connected.value = links.any { it.connected } }

    private inner class Link(private val index: Int, private val broker: Broker) {
        @Volatile private var client: MqttAsyncClient? = null
        @Volatile var connected = false
            private set
        @Volatile private var lost = CompletableDeferred<Unit>()
        private val wake = Channel<Unit>(Channel.CONFLATED)

        suspend fun run() {
            var backoff = 2_000L
            while (scope.isActive && running) {
                if (connectAny()) {
                    backoff = 2_000L
                    lost.await()
                    withTimeoutOrNull(1_000L) { wake.receive() }
                    continue
                }
                withTimeoutOrNull(backoff + kotlin.random.Random.nextLong(0, backoff / 4 + 1)) { wake.receive() }
                backoff = (backoff * 2).coerceAtMost(60_000L)
            }
        }

        private fun connectAny(): Boolean {
            val preferred = preferredRoute[broker.name] ?: 0
            for (route in broker.routes.indices.sortedBy { if (it == preferred) -1 else it }) {
                if (!running) return false
                val uri = broker.routes[route]
                val started = System.currentTimeMillis()
                val result = runCatching { open(uri) }
                if (result.isSuccess) {
                    preferredRoute[broker.name] = route
                    NetLog.i("cloud", "Connected to ${describe(uri)} in ${System.currentTimeMillis() - started} ms")
                    runCatching { onConnected(index) }
                    return true
                }
                NetLog.w("cloud", "Could not reach ${describe(uri)} after ${System.currentTimeMillis() - started} ms", result.exceptionOrNull())
            }
            return false
        }

        private fun open(uri: String) {
            val mqtt = MqttAsyncClient(uri, "$clientId-$index", MemoryPersistence())
            val dropped = CompletableDeferred<Unit>()
            mqtt.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    if (client === mqtt) {
                        client = null
                        connected = false
                        update()
                        NetLog.w("cloud", "Lost ${describe(uri)}", cause)
                    }
                    runCatching { mqtt.close() }
                    dropped.complete(Unit)
                }

                override fun messageArrived(topic: String, message: MqttMessage) {
                    runCatching { onMessage(topic, message.payload, index) }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
            })
            val options = MqttConnectOptions().apply {
                isAutomaticReconnect = false
                isCleanSession = true
                connectionTimeout = 10
                keepAliveInterval = 30
                maxInflight = 64
                will?.let { (topic, payload) -> setWill(topic, payload, 1, true) }
            }
            try {
                mqtt.connect(options).waitForCompletion(13_000L)
                val current = topics.toTypedArray()
                if (current.isNotEmpty()) mqtt.subscribe(current, IntArray(current.size) { 1 }).waitForCompletion(5_000L)
            } catch (t: Throwable) {
                runCatching { mqtt.disconnectForcibly(0, 500) }
                runCatching { mqtt.close() }
                throw t
            }
            lost = dropped
            client = mqtt
            connected = true
            update()
            if (dropped.isCompleted && client === mqtt) {
                client = null; connected = false; update()
                error("Connection dropped while subscribing")
            }
        }

        fun subscribe(wanted: Array<String>) {
            runCatching { client?.subscribe(wanted, IntArray(wanted.size) { 1 }) }
        }

        fun unsubscribe(gone: Array<String>) {
            runCatching { client?.unsubscribe(gone) }
        }

        fun publish(topic: String, payload: ByteArray, qos: Int, retained: Boolean): Boolean {
            val mqtt = client ?: return false
            return runCatching { mqtt.publish(topic, MqttMessage(payload).apply { this.qos = qos; isRetained = retained }) }
                .onFailure { NetLog.w("cloud", "Send failed on ${broker.name}", it) }.isSuccess
        }

        fun close(farewell: Pair<String, ByteArray>?) {
            val mqtt = client ?: return
            client = null
            connected = false
            farewell?.let { (topic, payload) ->
                runCatching { mqtt.publish(topic, MqttMessage(payload).apply { qos = 1; isRetained = true }).waitForCompletion(1_500L) }
            }
            runCatching { mqtt.disconnect().waitForCompletion(1_500L) }
            runCatching { mqtt.close() }
            lost.complete(Unit)
        }
    }

    /** A public broker and the routes to it, best first. Kept in step with Listen Together's. */
    internal data class Broker(val name: String, val routes: List<String>)

    internal companion object {
        val BROKERS = listOf(
            Broker("broker.emqx.io", listOf("ssl://broker.emqx.io:8883", "wss://broker.emqx.io:8084/mqtt")),
            Broker("broker.hivemq.com", listOf("ssl://broker.hivemq.com:8883", "wss://broker.hivemq.com:8884/mqtt")),
        )
        private val preferredRoute = ConcurrentHashMap<String, Int>()

        fun describe(uri: String): String = runCatching {
            val parsed = java.net.URI(uri)
            val how = when (parsed.scheme) { "ssl" -> "TLS"; "wss" -> "WebSocket"; else -> parsed.scheme }
            "${parsed.host} over $how (${parsed.port})"
        }.getOrDefault(uri)
    }
}
