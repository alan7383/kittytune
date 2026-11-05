package com.alananasss.kittytune.wear.data

import com.alananasss.kittytune.wear.common.TargetDeviceInfo
import com.alananasss.kittytune.wear.common.TargetSelectionPayload
import com.alananasss.kittytune.wear.common.WearPlaybackState
import com.alananasss.kittytune.wear.presentation.ConnectionStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * Clean controller interface abstracting transport-level playback control and state sync.
 * Implemented by:
 * - PhoneWearableController (Milestone 3: DataClient & MessageClient over BLE)
 * - DesktopConnectController (Milestone 4: Direct Ktor WebSocket or Relay WS)
 * - RoutingPlaybackController (Routes commands according to selected target)
 * - DefaultPlaybackController (In-app default/preview state for Milestone 2)
 * - FakeWearPlaybackController (Unit tests and Compose Previews)
 */
interface WearPlaybackController {
    /** Continuous stream of authoritative playback state. */
    val playbackState: StateFlow<WearPlaybackState>

    /** Current connection health status. */
    val connectionStatus: StateFlow<ConnectionStatus>

    /** List of all reachable playback targets. */
    val availableTargets: StateFlow<List<TargetDeviceInfo>>

    suspend fun play(): Result<Unit>
    suspend fun pause(): Result<Unit>
    suspend fun playPause(): Result<Unit>
    suspend fun next(): Result<Unit>
    suspend fun previous(): Result<Unit>
    suspend fun setVolume(volume: Float): Result<Unit>
    suspend fun adjustVolume(delta: Float): Result<Unit>
    suspend fun selectTarget(target: TargetSelectionPayload): Result<Unit>
    suspend fun requestSync(): Result<Unit>
}
