package com.alananasss.kittytune.e2e.harness

import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-memory simulation of KittyTune Connect protocol (LAN / Relay WebSocket)
 * for desktop playback control verification.
 */
class MockConnectEnvironment {

    data class DispatchedCommand(
        val peerId: String,
        val action: String,
        val value: Long = 0,
        val timestampMs: Long = System.currentTimeMillis()
    )

    data class DesktopPlaybackState(
        var peerId: String = "desktop_peer_alpha",
        var isPlaying: Boolean = false,
        var currentTrackId: Long = 101L,
        var currentTitle: String = "Desktop Symphony",
        var currentArtist: String = "Classical Orchestra",
        var volume: Long = 700L, // 0..1000
        var positionMs: Long = 0L,
        var durationMs: Long = 240_000L
    )

    val desktopState = DesktopPlaybackState()
    private val _dispatchedCommands = CopyOnWriteArrayList<DispatchedCommand>()
    val dispatchedCommands: List<DispatchedCommand> get() = _dispatchedCommands

    var isOnline: Boolean = true

    fun dispatchCommand(peerId: String, action: String, value: Long = 0): Boolean {
        if (!isOnline) return false

        val cmd = DispatchedCommand(peerId, action, value)
        _dispatchedCommands.add(cmd)

        // Apply state mutations according to Connect protocol specification
        when (action) {
            E2EContracts.ACTION_PLAY -> desktopState.isPlaying = true
            E2EContracts.ACTION_PAUSE -> desktopState.isPlaying = false
            E2EContracts.ACTION_NEXT -> {
                desktopState.currentTrackId += 1
                desktopState.currentTitle = "Track #${desktopState.currentTrackId}"
                desktopState.positionMs = 0L
                desktopState.isPlaying = true
            }
            E2EContracts.ACTION_PREVIOUS -> {
                if (desktopState.positionMs > E2EContracts.SMART_PREVIOUS_THRESHOLD_MS) {
                    desktopState.positionMs = 0L
                } else if (desktopState.currentTrackId > 1L) {
                    desktopState.currentTrackId -= 1
                    desktopState.currentTitle = "Track #${desktopState.currentTrackId}"
                    desktopState.positionMs = 0L
                }
            }
            E2EContracts.ACTION_VOLUME -> {
                desktopState.volume = value.coerceIn(0L, E2EContracts.DESKTOP_VOLUME_MAX)
            }
        }
        return true
    }

    fun clear() {
        _dispatchedCommands.clear()
    }
}
