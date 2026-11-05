package com.alananasss.kittytune.wear

import com.alananasss.kittytune.wear.common.TargetDeviceInfo
import com.alananasss.kittytune.wear.common.TargetSelectionPayload
import com.alananasss.kittytune.wear.common.WearPlaybackState
import com.alananasss.kittytune.wear.data.WearPlaybackController
import com.alananasss.kittytune.wear.presentation.ConnectionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeWearPlaybackController : WearPlaybackController {
    val _playbackState = MutableStateFlow(WearPlaybackState.EMPTY)
    override val playbackState: StateFlow<WearPlaybackState> = _playbackState.asStateFlow()

    val _connectionStatus = MutableStateFlow(ConnectionStatus.CONNECTED)
    override val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    val _availableTargets = MutableStateFlow<List<TargetDeviceInfo>>(emptyList())
    override val availableTargets: StateFlow<List<TargetDeviceInfo>> = _availableTargets.asStateFlow()

    val dispatchedCommands = mutableListOf<String>()
    var shouldFailNextCommand: Boolean = false

    private fun record(command: String): Result<Unit> {
        dispatchedCommands.add(command)
        return if (shouldFailNextCommand) {
            shouldFailNextCommand = false
            Result.failure(RuntimeException("Simulated transport failure"))
        } else {
            Result.success(Unit)
        }
    }

    override suspend fun play() = record("play")
    override suspend fun pause() = record("pause")
    override suspend fun playPause() = record("playPause")
    override suspend fun next() = record("next")
    override suspend fun previous() = record("previous")
    override suspend fun setVolume(volume: Float) = record("setVolume:$volume")
    override suspend fun adjustVolume(delta: Float) = record("adjustVolume:$delta")
    override suspend fun selectTarget(target: TargetSelectionPayload) = record("selectTarget:${target.targetType}:${target.peerId}")
    override suspend fun requestSync() = record("requestSync")
}
