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
        _uiState.update { current ->
            // Reconcile selected target info
            val activeTarget = current.availableTargets.find { it.id == backend.targetDeviceId || (backend.isPhone && it.isPhone) }
                ?: TargetDeviceInfo(
                    id = backend.targetDeviceId ?: WearConstants.TARGET_PHONE_ID,
                    name = backend.targetDevice.ifBlank { if (backend.isPhone) WearConstants.TARGET_PHONE_LABEL else "Desktop" },
                    type = backend.targetType,
                    isConnected = true,
                    isSelected = true
                )

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
                volume = backend.volume,
                selectedTarget = activeTarget,
                timestamp = backend.timestamp,
                availableTargets = if (backend.availableTargets.isNotEmpty()) backend.availableTargets else current.availableTargets,
                errorMessage = null // Clear errors on fresh authoritative frame
            )
        }
    }

    // =========================================================================
    // User Intent Actions (Optimistic UI + Dispatch)
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
        viewModelScope.launch {
            val result = controller.next()
            if (result.isFailure) {
                _uiState.update { it.copy(errorMessage = "Next track failed") }
            }
        }
    }

    fun onPrevious() {
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

        // 1. Optimistic volume update
        _uiState.update { current ->
            current.copy(
                volume = newVol,
                volumeRevision = current.volumeRevision + 1
            )
        }

        // 2. Debounced dispatch
        dispatchDebouncedVolume(newVol)
    }

    fun onSetVolume(volume: Float) {
        val safe = VolumeSanitizer.sanitize(volume)
        _uiState.update { current ->
            current.copy(
                volume = safe,
                volumeRevision = current.volumeRevision + 1
            )
        }
        dispatchDebouncedVolume(safe)
    }

    private fun dispatchDebouncedVolume(volume: Float) {
        optimisticVolumeJob?.cancel()
        optimisticVolumeJob = viewModelScope.launch {
            delay(50L) // Debounce for rapid rotary crown ticks
            val result = controller.setVolume(volume)
            if (result.isFailure) {
                _uiState.update { it.copy(errorMessage = "Volume sync failed") }
            }
        }
    }

    /**
     * Toggles between Phone and Desktop if only 2 targets exist,
     * or opens the picker dialog if 3+ targets exist.
     */
    fun onToggleTarget() {
        val targets = _uiState.value.availableTargets
        if (targets.size <= 2) {
            val nextTarget = targets.firstOrNull { it.id != _uiState.value.selectedTarget.id }
            if (nextTarget != null) {
                onSelectTarget(nextTarget)
            } else {
                // Direct fallback toggle
                if (_uiState.value.isPhone) {
                    onSelectTarget(TargetDeviceInfo("desktop", "Desktop", WearConstants.TARGET_TYPE_DESKTOP))
                } else {
                    onSelectTarget(TargetDeviceInfo(WearConstants.TARGET_PHONE_ID, WearConstants.TARGET_PHONE_LABEL, WearConstants.TARGET_TYPE_PHONE))
                }
            }
        } else {
            onShowTargetPicker(true)
        }
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
