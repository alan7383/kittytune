package com.alananasss.kittytune.wear.data

import com.alananasss.kittytune.wear.common.TargetDeviceInfo
import com.alananasss.kittytune.wear.common.TargetSelectionPayload
import com.alananasss.kittytune.wear.common.WearConstants
import com.alananasss.kittytune.wear.common.WearPlaybackState
import com.alananasss.kittytune.wear.presentation.ConnectionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * Composite controller that routes user playback commands to either
 * [phoneController] or [desktopController] depending on the currently selected target.
 */
class RoutingPlaybackController(
    private val phoneController: WearPlaybackController,
    private val desktopController: WearPlaybackController,
    private val scope: CoroutineScope,
) : WearPlaybackController {

    private val _currentTargetType = MutableStateFlow(WearConstants.TARGET_TYPE_PHONE)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val playbackState: StateFlow<WearPlaybackState> = _currentTargetType.flatMapLatest { type ->
        if (type == WearConstants.TARGET_TYPE_PHONE) phoneController.playbackState
        else desktopController.playbackState
    }.stateIn(scope, SharingStarted.Eagerly, WearPlaybackState.EMPTY)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val connectionStatus: StateFlow<ConnectionStatus> = _currentTargetType.flatMapLatest { type ->
        if (type == WearConstants.TARGET_TYPE_PHONE) phoneController.connectionStatus
        else desktopController.connectionStatus
    }.stateIn(scope, SharingStarted.Eagerly, ConnectionStatus.CONNECTED)

    override val availableTargets: StateFlow<List<TargetDeviceInfo>> = combine(
        phoneController.availableTargets,
        desktopController.availableTargets
    ) { phoneTargets, desktopTargets ->
        (phoneTargets + desktopTargets).distinctBy { it.id }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private fun activeController(): WearPlaybackController {
        return if (_currentTargetType.value == WearConstants.TARGET_TYPE_PHONE) phoneController else desktopController
    }

    override suspend fun play(): Result<Unit> = activeController().play()
    override suspend fun pause(): Result<Unit> = activeController().pause()
    override suspend fun playPause(): Result<Unit> = activeController().playPause()
    override suspend fun next(): Result<Unit> = activeController().next()
    override suspend fun previous(): Result<Unit> = activeController().previous()
    override suspend fun setVolume(volume: Float): Result<Unit> = activeController().setVolume(volume)
    override suspend fun adjustVolume(delta: Float): Result<Unit> = activeController().adjustVolume(delta)

    override suspend fun selectTarget(target: TargetSelectionPayload): Result<Unit> {
        _currentTargetType.value = target.targetType
        phoneController.selectTarget(target)
        return desktopController.selectTarget(target)
    }

    override suspend fun requestSync(): Result<Unit> = activeController().requestSync()
}
