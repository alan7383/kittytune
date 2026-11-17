package com.alananasss.kittytune.wear.data

import com.alananasss.kittytune.wear.common.TargetDeviceInfo
import com.alananasss.kittytune.wear.common.TargetSelectionPayload
import com.alananasss.kittytune.wear.common.WearConstants
import com.alananasss.kittytune.wear.common.WearPlaybackState
import com.alananasss.kittytune.wear.presentation.ConnectionStatus
import com.alananasss.kittytune.wear.presentation.volume.VolumeSanitizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Default in-app controller implementation providing fallback/standalone state
 * until Milestone 3 (Phone Data Layer) and Milestone 4 (Desktop bridge) are connected.
 */
class DefaultPlaybackController(
    initialState: WearPlaybackState = WearPlaybackState(
        trackId = 1L,
        title = "KittyTune",
        artist = "Companion Ready",
        isPlaying = false,
        volume = 0.5f,
        targetDevice = WearConstants.TARGET_PHONE_LABEL,
        targetType = WearConstants.TARGET_TYPE_PHONE,
        availableTargets = listOf(
            TargetDeviceInfo(
                id = WearConstants.TARGET_PHONE_ID,
                name = WearConstants.TARGET_PHONE_LABEL,
                type = WearConstants.TARGET_TYPE_PHONE,
                isConnected = true,
                isSelected = true
            ),
            TargetDeviceInfo(
                id = "desktop-default",
                name = "Desktop",
                type = WearConstants.TARGET_TYPE_DESKTOP,
                isConnected = true,
                isSelected = false
            )
        ),
        timestamp = System.currentTimeMillis()
    )
) : WearPlaybackController {

    private val _playbackState = MutableStateFlow(initialState)
    override val playbackState: StateFlow<WearPlaybackState> = _playbackState.asStateFlow()

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.CONNECTED)
    override val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _availableTargets = MutableStateFlow(initialState.availableTargets)
    override val availableTargets: StateFlow<List<TargetDeviceInfo>> = _availableTargets.asStateFlow()

    override suspend fun play(): Result<Unit> {
        _playbackState.update { it.copy(isPlaying = true, timestamp = System.currentTimeMillis()) }
        return Result.success(Unit)
    }

    override suspend fun pause(): Result<Unit> {
        _playbackState.update { it.copy(isPlaying = false, timestamp = System.currentTimeMillis()) }
        return Result.success(Unit)
    }

    override suspend fun playPause(): Result<Unit> {
        _playbackState.update { it.copy(isPlaying = !it.isPlaying, timestamp = System.currentTimeMillis()) }
        return Result.success(Unit)
    }

    override suspend fun next(): Result<Unit> {
        _playbackState.update {
            it.copy(
                trackId = if (it.trackId <= 0L) 1L else it.trackId + 1,
                positionMs = 0L,
                timestamp = System.currentTimeMillis()
            )
        }
        return Result.success(Unit)
    }

    override suspend fun previous(): Result<Unit> {
        _playbackState.update {
            it.copy(
                positionMs = 0L,
                timestamp = System.currentTimeMillis()
            )
        }
        return Result.success(Unit)
    }

    override suspend fun setVolume(volume: Float): Result<Unit> {
        val safe = VolumeSanitizer.sanitize(volume)
        _playbackState.update { it.copy(volume = safe) }
        return Result.success(Unit)
    }

    override suspend fun adjustVolume(delta: Float): Result<Unit> {
        _playbackState.update {
            val safe = VolumeSanitizer.sanitize(it.volume + delta)
            it.copy(volume = safe)
        }
        return Result.success(Unit)
    }

    override suspend fun selectTarget(target: TargetSelectionPayload): Result<Unit> {
        _playbackState.update { current ->
            val isPhone = target.targetType == WearConstants.TARGET_TYPE_PHONE
            val deviceName = target.peerName ?: if (isPhone) WearConstants.TARGET_PHONE_LABEL else "Desktop"
            current.copy(
                targetType = target.targetType,
                targetDevice = deviceName,
                targetDeviceId = target.peerId
            )
        }
        _availableTargets.update { targets ->
            targets.map {
                it.copy(isSelected = (it.id == target.peerId) || (target.targetType == WearConstants.TARGET_TYPE_PHONE && it.isPhone))
            }
        }
        return Result.success(Unit)
    }

    override suspend fun requestSync(): Result<Unit> {
        _playbackState.update { it.copy(timestamp = System.currentTimeMillis()) }
        return Result.success(Unit)
    }
}
