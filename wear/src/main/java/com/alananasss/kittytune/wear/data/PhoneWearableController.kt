package com.alananasss.kittytune.wear.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.alananasss.kittytune.wear.common.TargetDeviceInfo
import com.alananasss.kittytune.wear.common.TargetSelectionPayload
import com.alananasss.kittytune.wear.common.WearCommandResult
import com.alananasss.kittytune.wear.common.WearCodecs
import com.alananasss.kittytune.wear.common.WearConstants
import com.alananasss.kittytune.wear.common.WearDataMapMapper
import com.alananasss.kittytune.wear.common.WearPlaybackState
import com.alananasss.kittytune.wear.presentation.ConnectionStatus
import com.alananasss.kittytune.wear.presentation.volume.VolumeSanitizer
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Production implementation of [WearPlaybackController] communicating with the
 * Android handheld phone app over Google Play Services Wearable Data Layer API.
 *
 * Responsibilities:
 * - Reads the current [WearPlaybackState] from the frozen Data Layer item at startup, so the screen
 *   shows the real track immediately instead of "Nothing playing" until the phone publishes again.
 * - Keeps [connectionStatus] honest: a periodic node poll plus a capability listener flip the status
 *   when the phone disappears or comes back, instead of leaving it stuck on its first answer.
 * - Ignores out-of-order frames using the publisher timestamp, so a late duplicate can never rewind
 *   the watch UI to an older track or volume.
 */
class PhoneWearableController(
    private val context: Context,
    private val scope: CoroutineScope
) : WearPlaybackController,
    DataClient.OnDataChangedListener,
    CapabilityClient.OnCapabilityChangedListener {

    companion object {
        private const val TAG = "PhoneWearableController"

        /** How often the connected node list is re-read while the watch UI is alive. */
        private const val CONNECTION_POLL_MS = 15_000L
    }

    private val dataClient by lazy { Wearable.getDataClient(context) }
    private val messageClient by lazy { Wearable.getMessageClient(context) }
    private val nodeClient by lazy { Wearable.getNodeClient(context) }
    private val capabilityClient by lazy { Wearable.getCapabilityClient(context) }

    private val _playbackState = MutableStateFlow(WearPlaybackState.EMPTY)
    override val playbackState: StateFlow<WearPlaybackState> = _playbackState.asStateFlow()

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.CONNECTING)
    override val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _availableTargets = MutableStateFlow(
        listOf(
            TargetDeviceInfo(
                id = WearConstants.TARGET_PHONE_ID,
                name = WearConstants.TARGET_PHONE_LABEL,
                type = WearConstants.TARGET_TYPE_PHONE,
                isConnected = false,
                isSelected = true
            )
        )
    )
    override val availableTargets: StateFlow<List<TargetDeviceInfo>> = _availableTargets.asStateFlow()

    /** Timestamp of the newest frame already merged, so stale duplicates are dropped. */
    @Volatile
    private var lastAppliedTimestamp: Long = 0L

    private var connectionPollJob: Job? = null
    private var listenerRegistered = false

    init {
        registerListeners()
        scope.launch(Dispatchers.IO) {
            loadCachedState()
            refreshConnection()
        }
        startConnectionPolling()
    }

    private fun registerListeners() {
        if (listenerRegistered) return
        listenerRegistered = true
        dataClient.addListener(this)
        runCatching {
            capabilityClient.addListener(this, WearConstants.CAPABILITY_PHONE_APP)
        }.onFailure { Log.w(TAG, "Capability listener unavailable", it) }
    }

    fun setForeground(visible: Boolean) {
        if (visible) startConnectionPolling()
        else {
            connectionPollJob?.cancel()
            connectionPollJob = null
        }
    }

    private fun startConnectionPolling() {
        if (connectionPollJob?.isActive == true) return
        connectionPollJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(CONNECTION_POLL_MS)
                refreshConnection()
            }
        }
    }

    /**
     * Reads the Data Layer item the phone last wrote. DataClient listeners only fire on change, so a
     * cold start on a paired watch would otherwise show an empty state until the next track change.
     */
    private suspend fun loadCachedState() {
        try {
            val uri = Uri.parse("wear://*${WearConstants.PATH_PLAYBACK_STATE}")
            val items = Tasks.await(dataClient.getDataItems(uri, DataClient.FILTER_LITERAL), 8, TimeUnit.SECONDS)
            try {
                for (item in items) {
                    if (item.uri.path == WearConstants.PATH_PLAYBACK_STATE) {
                        applyState(WearDataMapMapper.fromDataMapItem(DataMapItem.fromDataItem(item)))
                    }
                }
            } finally {
                items.release()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read cached playback state", e)
        }
    }

    private suspend fun refreshConnection() {
        try {
            val nodes = Tasks.await(capabilityClient.getCapability(WearConstants.CAPABILITY_PHONE_APP, CapabilityClient.FILTER_REACHABLE), 8, TimeUnit.SECONDS).nodes
            _connectionStatus.value = if (nodes.isNotEmpty()) {
                ConnectionStatus.CONNECTED
            } else {
                ConnectionStatus.DISCONNECTED
            }
            if (nodes.isNotEmpty()) requestSync()
            else markTargetsOffline()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query connected nodes", e)
            _connectionStatus.value = ConnectionStatus.ERROR
            markTargetsOffline()
        }
    }

    private fun markTargetsOffline() {
        _availableTargets.update { targets -> targets.map { it.copy(isConnected = false) } }
    }

    /**
     * Merges a frame from the phone. Frames older than the last applied one are dropped, which keeps
     * the UI monotonic when two DataItem updates arrive out of order.
     */
    private fun applyState(state: WearPlaybackState) {
        val timestamp = state.timestamp
        if (timestamp > 0L && timestamp < lastAppliedTimestamp) return
        if (timestamp > 0L) lastAppliedTimestamp = timestamp

        _playbackState.value = state
        _availableTargets.value = state.availableTargets
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == WearConstants.PATH_PLAYBACK_STATE) {
                try {
                    applyState(WearDataMapMapper.fromDataMapItem(DataMapItem.fromDataItem(event.dataItem)))
                } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
                    Log.e(TAG, "Error decoding playback state from data item", e)
                }
            }
        }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        val nodes = capabilityInfo.nodes
        _connectionStatus.value = when {
            nodes.any { it.isNearby } -> ConnectionStatus.CONNECTED
            nodes.isNotEmpty() -> ConnectionStatus.CONNECTING
            else -> ConnectionStatus.DISCONNECTED
        }
        if (nodes.isNotEmpty()) {
            scope.launch(Dispatchers.IO) { requestSync() }
        } else markTargetsOffline()
    }

    /**
     * Sends a command to one compatible phone and awaits application acknowledgement.
     *
     * The watch talks to the handheld only; routing to a desktop target is the phone's job, which
     * keeps working when the desktop is reachable from the phone's network but not from the watch.
     */
    private suspend fun sendCommand(action: String, payload: ByteArray = ByteArray(0)): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val nodes = Tasks.await(capabilityClient.getCapability(WearConstants.CAPABILITY_PHONE_APP, CapabilityClient.FILTER_REACHABLE), 8, TimeUnit.SECONDS).nodes
                if (nodes.isEmpty()) {
                    _connectionStatus.value = ConnectionStatus.DISCONNECTED
                    markTargetsOffline()
                    return@withContext Result.failure(IllegalStateException("No phone connected"))
                }

                // A command has exactly one recipient. Retrying a toggle on another phone could
                // execute it twice, so a timeout is reported to the UI without an automatic retry.
                val node = nodes.sortedWith(compareByDescending<com.google.android.gms.wearable.Node> { it.isNearby }.thenBy { it.id }).first()
                val bytes = Tasks.await(messageClient.sendRequest(node.id, action, payload), 15, TimeUnit.SECONDS)
                val reply = WearCommandResult.decode(bytes)
                check(reply.error == null) { reply.error ?: "Command failed" }
                applyState(requireNotNull(reply.state) { "Phone returned no playback state" })
                _connectionStatus.value = ConnectionStatus.CONNECTED
                Result.success(Unit)
            } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
                Log.e(TAG, "Error sending command $action to phone", e)
                _connectionStatus.value = ConnectionStatus.ERROR
                Result.failure(e)
            }
        }

    override suspend fun play(): Result<Unit> = sendCommand(WearConstants.ACTION_PLAY)

    override suspend fun pause(): Result<Unit> = sendCommand(WearConstants.ACTION_PAUSE)

    override suspend fun playPause(): Result<Unit> = sendCommand(WearConstants.ACTION_PLAY_PAUSE)

    override suspend fun next(): Result<Unit> = sendCommand(WearConstants.ACTION_NEXT)

    override suspend fun previous(): Result<Unit> = sendCommand(WearConstants.ACTION_PREVIOUS)

    override suspend fun setVolume(volume: Float): Result<Unit> {
        val safe = VolumeSanitizer.sanitize(volume)
        return sendCommand(WearConstants.ACTION_SET_VOLUME, WearCodecs.encodeVolume(safe))
    }

    override suspend fun adjustVolume(delta: Float): Result<Unit> {
        val newVol = VolumeSanitizer.sanitize(_playbackState.value.volume + delta)
        if (!delta.isFinite() || delta == 0f) return Result.success(Unit)
        return setVolume(newVol)
    }

    override suspend fun selectTarget(target: TargetSelectionPayload): Result<Unit> =
        sendCommand(WearConstants.ACTION_TARGET_SELECT, WearCodecs.encodeTarget(target))

    override suspend fun requestSync(): Result<Unit> = sendCommand(WearConstants.ACTION_SYNC_REQUEST)

    /** Releases Data Layer registrations; the connection poll dies with the owning scope. */
    fun cleanup() {
        runCatching { dataClient.removeListener(this) }
        runCatching { capabilityClient.removeListener(this) }
        listenerRegistered = false
        connectionPollJob?.cancel()
        connectionPollJob = null
    }
}
