package com.alananasss.kittytune.e2e.harness

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-memory simulation of Google Play Services Wearable Data Layer
 * (MessageClient, DataClient, NodeClient) for deterministic opaque-box E2E testing.
 */
class MockWearableEnvironment {

    data class SentMessage(
        val nodeId: String,
        val path: String,
        val payload: ByteArray,
        val timestampMs: Long = System.currentTimeMillis()
    )

    data class DataItemRecord(
        val path: String,
        val values: MutableMap<String, Any> = ConcurrentHashMap(),
        var lastUpdatedMs: Long = System.currentTimeMillis()
    )

    // Connected Nodes
    private val _connectedNodes = CopyOnWriteArrayList<String>()
    val connectedNodes: List<String> get() = _connectedNodes

    // Sent Messages log
    private val _sentMessages = CopyOnWriteArrayList<SentMessage>()
    val sentMessages: List<SentMessage> get() = _sentMessages

    // DataItems by path
    private val _dataItems = ConcurrentHashMap<String, DataItemRecord>()
    val dataItems: Map<String, DataItemRecord> get() = _dataItems

    // Registered Listeners
    private val messageListeners = CopyOnWriteArrayList<(SentMessage) -> Unit>()
    private val dataChangeListeners = CopyOnWriteArrayList<(DataItemRecord) -> Unit>()

    var isNetworkConnected: Boolean = true
        private set

    init {
        // By default, watch and phone nodes are paired and connected
        _connectedNodes.add("phone_node_01")
        _connectedNodes.add("watch_node_01")
    }

    fun setNetworkConnected(connected: Boolean) {
        this.isNetworkConnected = connected
    }

    fun sendMessage(nodeId: String, path: String, payload: ByteArray = ByteArray(0)): Boolean {
        if (!isNetworkConnected) {
            return false
        }
        val msg = SentMessage(nodeId, path, payload)
        _sentMessages.add(msg)
        messageListeners.forEach { listener ->
            try {
                listener(msg)
            } catch (e: Exception) {
                // ignore in mock or log
            }
        }
        return true
    }

    fun putDataItem(path: String, values: Map<String, Any>): DataItemRecord {
        val record = _dataItems.computeIfAbsent(path) { DataItemRecord(path) }
        synchronized(record) {
            record.values.putAll(values)
            record.lastUpdatedMs = System.currentTimeMillis()
        }
        if (isNetworkConnected) {
            dataChangeListeners.forEach { listener ->
                try {
                    listener(record)
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
        return record
    }

    fun getDataItem(path: String): DataItemRecord? {
        return _dataItems[path]
    }

    fun addMessageListener(listener: (SentMessage) -> Unit) {
        messageListeners.add(listener)
    }

    fun removeMessageListener(listener: (SentMessage) -> Unit) {
        messageListeners.remove(listener)
    }

    fun addDataListener(listener: (DataItemRecord) -> Unit) {
        dataChangeListeners.add(listener)
    }

    fun removeDataListener(listener: (DataItemRecord) -> Unit) {
        dataChangeListeners.remove(listener)
    }

    fun clearLog() {
        _sentMessages.clear()
    }
}
