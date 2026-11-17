package com.alananasss.kittytune.wear.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alananasss.kittytune.wear.common.TargetDeviceInfo
import com.alananasss.kittytune.wear.common.TargetSelectionPayload
import com.alananasss.kittytune.wear.common.WearConstants
import com.alananasss.kittytune.wear.common.WearPlaybackState
import com.alananasss.kittytune.wear.data.DefaultPlaybackController
import com.alananasss.kittytune.wear.data.WearPlaybackController
import com.alananasss.kittytune.wear.presentation.volume.VolumeSanitizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PlaybackViewModel(
    private val controller: WearPlaybackController = DefaultPlaybackController(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(WearPlaybackUiState.INITIAL)
    val uiState: StateFlow<WearPlaybackUiState> = _uiState.asStateFlow()

    // Tracks optimistic locks to prevent race conditions during rapid tapping
    private var optimisticPlayPauseJob: Job? = null
    private var optimisticVolumeJob: Job? = null

    // Tracks the latest authoritative backend volume to support rollback on transport failure
    private var lastAuthoritativeVolume: Float = controller.playbackState.value.volume

    init {
        // Collect authoritative playback state from controller
        viewModelScope.launch {
            controller.playbackState.collect { state ->
                reconcilePlaybackState(state)
            }
        }

        // Collect connection status
        viewModelScope.launch {
            controller.connectionStatus.collect { status ->
                _uiState.update { it.copy(connectionStatus = status) }
            }
        }

        // Collect available target devices
        viewModelScope.launch {
            controller.availableTargets.collect { targets ->
                _uiState.update { current ->
                    val defaultTargets = if (targets.isEmpty()) {
                        listOf(
                            TargetDeviceInfo(
                                id = WearConstants.TARGET_PHONE_ID,
                                name = WearConstants.TARGET_PHONE_LABEL,
                                type = WearConstants.TARGET_TYPE_PHONE,
                                isConnected = true,
                                isSelected = current.selectedTarget.isPhone
                            )
                        )
                    } else targets
                    current.copy(availableTargets = defaultTargets)
                }
            }
        }
    }

    private fun reconcilePlaybackState(backend: WearPlaybackState) {
        lastAuthoritativeVolume = backend.volume
        _uiState.update { current ->
            // Reconcile selected target info
            val activeTarget = (backend.availableTargets.ifEmpty { current.availableTargets }).find { it.id == backend.targetDeviceId || (backend.isPhone && it.isPhone) }
                ?: TargetDeviceInfo(
                    id = backend.targetDeviceId ?: WearConstants.TARGET_PHONE_ID,
                    name = backend.targetDevice.ifBlank { if (backend.isPhone) WearConstants.TARGET_PHONE_LABEL else "Desktop" },
                    type = backend.targetType,
                    isConnected = backend.isPhone,
                    isSelected = true
                )

            val effectiveVolume = if (optimisticVolumeJob?.isActive == true) current.volume else backend.volume

            current.copy(
                trackId = backend.trackId,
                title = backend.title,
                artist = backend.artist,
                album = backend.album,
                artworkUrl = backend.artworkUrl,
                isPlaying = backend.isPlaying,
                isLoading = backend.isLoading,
                positionMs = backend.positionMs,
                durationMs = backend.durationMs,
                volume = effectiveVolume,
                selectedTarget = activeTarget,
                timestamp = backend.timestamp,
                availableTargets = if (backend.availableTargets.isNotEmpty()) backend.availableTargets else current.availableTargets,
                errorMessage = null // Clear errors on a fresh authoritative frame
            )
        }
    }

    /**
     * True when the selected target cannot receive commands right now.
     *
     * Play/pause stays allowed: it is the one action a user wants to work as soon as the phone is
     * back, and a failed dispatch rolls back visibly. Skipping and volume changes are refused with a
     * message instead, because silently doing nothing is worse than saying why.
     */
    private fun isTargetReachable(): Boolean {
        val state = _uiState.value
        return state.connectionStatus == ConnectionStatus.CONNECTED &&
            (state.selectedTarget.isConnected || state.selectedTarget.isPhone)
    }

    private fun reportUnreachable(action: String) {
        _uiState.update { it.copy(errorMessage = "$action failed: device unreachable") }
    }

    // =========================================================================
    // User intent actions (optimistic UI + dispatch)
    // =========================================================================

    fun onPlayPause() {
        val previous = _uiState.value.isPlaying
        val optimistic = !previous

        // 1. Optimistic UI update
        _uiState.update { it.copy(isPlaying = optimistic) }

        // 2. Dispatch command asynchronously
        optimisticPlayPauseJob?.cancel()
        optimisticPlayPauseJob = viewModelScope.launch {
            val result = controller.playPause()
            if (result.isFailure) {
                // Rollback on communication failure
                _uiState.update {
                    it.copy(
                        isPlaying = previous,
                        errorMessage = "Failed to send command"
                    )
                }
            }
        }
    }

    fun onNext() {
        if (!isTargetReachable()) {
            reportUnreachable("Next track")
            return
        }
        viewModelScope.launch {
            val result = controller.next()
            if (result.isFailure) {
                _uiState.update { it.copy(errorMessage = "Next track failed") }
            }
        }
    }

    fun onPrevious() {
        if (!isTargetReachable()) {
            reportUnreachable("Previous track")
            return
        }
        viewModelScope.launch {
            val result = controller.previous()
            if (result.isFailure) {
                _uiState.update { it.copy(errorMessage = "Previous track failed") }
            }
        }
    }

    fun onVolumeChange(delta: Float) {
        val currentVol = _uiState.value.volume
        val newVol = if (delta.isNaN()) currentVol else VolumeSanitizer.sanitize(currentVol + delta)
        onSetVolume(newVol)
    }

    fun onSetVolume(volume: Float) {
        val safe = VolumeSanitizer.sanitize(volume)
        val current = _uiState.value
        // A no-op volume write would still round-trip to the phone and back; skip it entirely.
        if (safe == current.volume) return

        if (!isTargetReachable()) {
            reportUnreachable("Volume change")
            return
        }
        _uiState.update {
            it.copy(
                volume = safe,
                volumeRevision = it.volumeRevision + 1
            )
        }

        dispatchDebouncedVolume(safe)
    }

    private fun dispatchDebouncedVolume(volume: Float) {
        optimisticVolumeJob?.cancel()
        optimisticVolumeJob = viewModelScope.launch {
            delay(50L) // Debounce for rapid rotary crown ticks
            try {
                val result = controller.setVolume(volume)
                if (result.isSuccess) {
                    // The controller has merged the acknowledged player snapshot by now.
                    lastAuthoritativeVolume = controller.playbackState.value.volume
                    _uiState.update { it.copy(volume = lastAuthoritativeVolume) }
                } else {
                    _uiState.update { current ->
                        current.copy(
                            volume = lastAuthoritativeVolume,
                            volumeRevision = current.volumeRevision + 1,
                            errorMessage = "Volume sync failed"
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update { current ->
                    current.copy(
                        volume = lastAuthoritativeVolume,
                        volumeRevision = current.volumeRevision + 1,
                        errorMessage = "Volume sync failed"
                    )
                }
            }
        }
    }

    /**
     * Toggles between Phone and Desktop if only 2 targets exist,
     * or opens the picker dialog if 3+ targets exist.
     */
    fun onToggleTarget() {
        onShowTargetPicker(true)
    }

    fun onSelectTarget(target: TargetDeviceInfo) {
        val previousTarget = _uiState.value.selectedTarget

        // 1. Optimistic target update
        _uiState.update {
            it.copy(
                selectedTarget = target,
                connectionStatus = ConnectionStatus.CONNECTING,
                isTargetPickerVisible = false
            )
        }

        // 2. Dispatch target switch payload
        viewModelScope.launch {
            val payload = if (target.isPhone) {
                TargetSelectionPayload.phone()
            } else {
                TargetSelectionPayload.desktop(target.id, target.name)
            }
            val result = controller.selectTarget(payload)
            if (result.isFailure) {
                _uiState.update {
                    it.copy(
                        selectedTarget = previousTarget,
                        connectionStatus = ConnectionStatus.ERROR,
                        errorMessage = "Failed to switch to ${target.name}"
                    )
                }
            } else {
                // A successful dispatch proves the phone answered, so leave the CONNECTING state
                // even if the controller publishes nothing else for a while.
                _uiState.update { it.copy(connectionStatus = ConnectionStatus.CONNECTED) }
            }
        }
    }

    fun onShowTargetPicker(visible: Boolean) {
        _uiState.update { it.copy(isTargetPickerVisible = visible) }
    }

    fun onDismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun onSyncRequest() {
        viewModelScope.launch {
            controller.requestSync()
        }
    }

    companion object {
        fun provideFactory(controller: WearPlaybackController): androidx.lifecycle.ViewModelProvider.Factory =
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PlaybackViewModel(controller) as T
                }
            }
    }
}
