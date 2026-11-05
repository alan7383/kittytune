package com.alananasss.kittytune.wear.presentation

import com.alananasss.kittytune.wear.common.TargetDeviceInfo
import com.alananasss.kittytune.wear.common.WearConstants

enum class ConnectionStatus {
    CONNECTED,
    CONNECTING,
    DISCONNECTED,
    ERROR
}

data class WearPlaybackUiState(
    val trackId: Long = 0L,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkUrl: String? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 0.5f,
    val volumeRevision: Long = 0L,
    val selectedTarget: TargetDeviceInfo = TargetDeviceInfo(
        id = WearConstants.TARGET_PHONE_ID,
        name = WearConstants.TARGET_PHONE_LABEL,
        type = WearConstants.TARGET_TYPE_PHONE,
        isConnected = true,
        isSelected = true
    ),
    val availableTargets: List<TargetDeviceInfo> = emptyList(),
    val connectionStatus: ConnectionStatus = ConnectionStatus.CONNECTED,
    val errorMessage: String? = null,
    val isTargetPickerVisible: Boolean = false,
    val timestamp: Long = 0L,
) {
    val targetDevice: String get() = selectedTarget.name
    val targetType: String get() = selectedTarget.type
    val isConnected: Boolean get() = connectionStatus == ConnectionStatus.CONNECTED
    val isPhone: Boolean get() = selectedTarget.isPhone
    val isDesktop: Boolean get() = selectedTarget.isDesktop
    val hasTrack: Boolean get() = trackId != 0L || title.isNotBlank()

    fun progressFraction(nowMs: Long = System.currentTimeMillis()): Float {
        if (durationMs <= 0L) return 0.0f
        val elapsed = if (isPlaying && timestamp > 0L) (nowMs - timestamp).coerceAtLeast(0L) else 0L
        val current = (positionMs + elapsed).coerceIn(0L, durationMs)
        return (current.toFloat() / durationMs.toFloat()).coerceIn(0.0f, 1.0f)
    }

    val progressFraction: Float get() = progressFraction()

    constructor(
        trackId: Long = 0L,
        title: String = "",
        artist: String = "",
        album: String = "",
        artworkUrl: String? = null,
        isPlaying: Boolean = false,
        isLoading: Boolean = false,
        positionMs: Long = 0L,
        durationMs: Long = 0L,
        volume: Float = 0.5f,
        targetDevice: String,
        targetType: String = if (targetDevice == WearConstants.TARGET_PHONE_LABEL) WearConstants.TARGET_TYPE_PHONE else WearConstants.TARGET_TYPE_DESKTOP,
        isConnected: Boolean = true
    ) : this(
        trackId = trackId,
        title = title,
        artist = artist,
        album = album,
        artworkUrl = artworkUrl,
        isPlaying = isPlaying,
        isLoading = isLoading,
        positionMs = positionMs,
        durationMs = durationMs,
        volume = volume,
        volumeRevision = 0L,
        selectedTarget = TargetDeviceInfo(
            id = if (targetType == WearConstants.TARGET_TYPE_PHONE) WearConstants.TARGET_PHONE_ID else "desktop",
            name = targetDevice,
            type = targetType,
            isConnected = isConnected,
            isSelected = true
        ),
        availableTargets = emptyList(),
        connectionStatus = if (isConnected) ConnectionStatus.CONNECTED else ConnectionStatus.DISCONNECTED,
        errorMessage = null,
        isTargetPickerVisible = false,
        timestamp = 0L
    )

    companion object {
        val INITIAL = WearPlaybackUiState()
    }
}

typealias PlaybackUiState = WearPlaybackUiState
