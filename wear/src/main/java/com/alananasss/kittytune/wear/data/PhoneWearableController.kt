package com.alananasss.kittytune.wear.data

import android.content.Context
import android.util.Log
import com.alananasss.kittytune.wear.common.TargetDeviceInfo
import com.alananasss.kittytune.wear.common.TargetSelectionPayload
import com.alananasss.kittytune.wear.common.WearCodecs
import com.alananasss.kittytune.wear.common.WearConstants
import com.alananasss.kittytune.wear.common.WearDataMapMapper
import com.alananasss.kittytune.wear.common.WearPlaybackState
import com.alananasss.kittytune.wear.presentation.ConnectionStatus
import com.alananasss.kittytune.wear.presentation.volume.VolumeSanitizer
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Production implementation of [WearPlaybackController] communicating with the
 * Android handheld phone app over Google Play Services Wearable Data Layer API.
 */
class PhoneWearableController(
    private val context: Context,
    private val scope: CoroutineScope
) : WearPlaybackController, DataClient.OnDataChangedListener {

    companion object {
        private const val TAG = "PhoneWearableController"
    }

    private val dataClient by lazy { Wearable.getDataClient(context) }
    private val messageClient by lazy { Wearable.getMessageClient(context) }
    private val nodeClient by lazy { Wearable.getNodeClient(context) }

    private val _playbackState = MutableStateFlow(WearPlaybackState.EMPTY)
    override val playbackState: StateFlow<WearPlaybackState> = _playbackState.asStateFlow()

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.CONNECTING)
    override val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _availableTargets = MutableStateFlow<List<TargetDeviceInfo>>(
        listOf(
            TargetDeviceInfo(
                id = WearConstants.TARGET_PHONE_ID,
                name = WearConstants.TARGET_PHONE_LABEL,
                type = WearConstants.TARGET_TYPE_PHONE,
                isConnected = true,
                isSelected = true
            )
        )
    )
    override val availableTargets: StateFlow<List<TargetDeviceInfo>> = _availableTargets.asStateFlow()

    init {
        dataClient.addListener(this)
        scope.launch(Dispatchers.IO) {
            checkConnectionAndSync()
        }
    }

    private suspend fun checkConnectionAndSync() {
        try {
            val nodes = Tasks.await(nodeClient.connectedNodes)
            if (nodes.isNotEmpty()) {
                _connectionStatus.value = ConnectionStatus.CONNECTED
                requestSync()
            } else {
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query connected nodes", e)
            _connectionStatus.value = ConnectionStatus.ERROR
        }
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        for (event in dataEvents) {
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == WearConstants.PATH_PLAYBACK_STATE) {
                try {
                    val dataMapItem = DataMapItem.fromDataItem(event.dataItem)
                    val state = WearDataMapMapper.fromDataMapItem(dataMapItem)
                    _playbackState.value = state
                    _connectionStatus.value = ConnectionStatus.CONNECTED
                    if (state.availableTargets.isNotEmpty()) {
                        _availableTargets.value = state.availableTargets
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error decoding playback state from data item", e)
                }
            }
        }
    }

    private suspend fun sendCommand(action: String, payload: ByteArray = ByteArray(0)): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val nodes = Tasks.await(nodeClient.connectedNodes)
            if (nodes.isEmpty()) {
                _connectionStatus.value = ConnectionStatus.DISCONNECTED
                return@withContext Result.failure(IllegalStateException("No phone connected"))
            }

            for (node in nodes) {
                Tasks.await(messageClient.sendMessage(node.id, action, payload))
            }
            _connectionStatus.value = ConnectionStatus.CONNECTED
            Result.success(Unit)
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
        _playbackState.update { it.copy(volume = safe) }
        return sendCommand(WearConstants.ACTION_SET_VOLUME, WearCodecs.encodeVolume(safe))
    }

    override suspend fun adjustVolume(delta: Float): Result<Unit> {
        val newVol = VolumeSanitizer.sanitize(_playbackState.value.volume + delta)
        _playbackState.update { it.copy(volume = newVol) }
        val action = if (delta >= 0f) WearConstants.ACTION_VOLUME_UP else WearConstants.ACTION_VOLUME_DOWN
        return sendCommand(action)
    }

    override suspend fun selectTarget(target: TargetSelectionPayload): Result<Unit> {
        val isPhone = target.targetType == WearConstants.TARGET_TYPE_PHONE
        _playbackState.update {
            it.copy(
                targetType = target.targetType,
                targetDevice = target.peerName ?: if (isPhone) WearConstants.TARGET_PHONE_LABEL else "Desktop",
                targetDeviceId = target.peerId
            )
        }
        val payload = WearCodecs.encodeTarget(target)
        return sendCommand(WearConstants.ACTION_TARGET_SELECT, payload)
    }

    override suspend fun requestSync(): Result<Unit> = sendCommand(WearConstants.ACTION_SYNC_REQUEST)

    fun cleanup() {
        dataClient.removeListener(this)
    }
}
