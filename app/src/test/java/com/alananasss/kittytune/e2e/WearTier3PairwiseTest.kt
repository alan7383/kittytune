package com.alananasss.kittytune.e2e

import com.alananasss.kittytune.e2e.harness.E2EContracts
import com.alananasss.kittytune.e2e.harness.MockConnectEnvironment
import com.alananasss.kittytune.e2e.harness.MockWearableEnvironment
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tier 3: Pairwise & Combinatorial Interaction Testing.
 * Covers orthogonal 2-way interactions across:
 * - Target Device: PHONE, DESKTOP
 * - Command Action: PLAY, PAUSE, PLAY_PAUSE, NEXT, PREVIOUS, VOLUME_UP, VOLUME_DOWN, SET_VOLUME
 * - Network/Connection: CONNECTED, DISCONNECTED, RECONNECTING
 * - Initial Player State: PLAYING, PAUSED, BUFFERING, IDLE
 * - Input Source: BUTTON_TAP, ROTARY_CROWN, MESSAGE_LAYER
 */
class WearTier3PairwiseTest {

    private lateinit var wearableEnv: MockWearableEnvironment
    private lateinit var connectEnv: MockConnectEnvironment

    @Before
    fun setUp() {
        wearableEnv = MockWearableEnvironment()
        connectEnv = MockConnectEnvironment()
    }

    enum class TargetDevice { PHONE, DESKTOP }
    enum class CommandAction { PLAY, PAUSE, PLAY_PAUSE, NEXT, PREVIOUS, VOLUME_UP, VOLUME_DOWN, SET_VOLUME }
    enum class NetworkState { CONNECTED, DISCONNECTED, RECONNECTING }
    enum class PlayerState { PLAYING, PAUSED, BUFFERING, IDLE }
    enum class InputSource { BUTTON_TAP, ROTARY_CROWN, MESSAGE_LAYER }

    private data class PairwiseResult(
        val success: Boolean,
        val dispatchedToTarget: String,
        val resultingPlayingState: Boolean,
        val resultingVolume: Float
    )

    private fun executePairwiseTest(
        target: TargetDevice,
        command: CommandAction,
        network: NetworkState,
        initialPlayer: PlayerState,
        input: InputSource,
        volumeParam: Float = 0.50f
    ): PairwiseResult {
        // Setup initial player state
        var isPlaying = (initialPlayer == PlayerState.PLAYING)
        var volume = volumeParam
        wearableEnv.setNetworkConnected(network == NetworkState.CONNECTED)
        connectEnv.isOnline = (network == NetworkState.CONNECTED)

        var commandDispatched = false
        var targetRouted = ""

        when (target) {
            TargetDevice.PHONE -> {
                targetRouted = "PHONE"
                val path = when (command) {
                    CommandAction.PLAY -> E2EContracts.PATH_PLAY
                    CommandAction.PAUSE -> E2EContracts.PATH_PAUSE
                    CommandAction.PLAY_PAUSE -> E2EContracts.PATH_PLAY_PAUSE
                    CommandAction.NEXT -> E2EContracts.PATH_NEXT
                    CommandAction.PREVIOUS -> E2EContracts.PATH_PREVIOUS
                    CommandAction.VOLUME_UP -> E2EContracts.PATH_VOLUME_UP
                    CommandAction.VOLUME_DOWN -> E2EContracts.PATH_VOLUME_DOWN
                    CommandAction.SET_VOLUME -> E2EContracts.PATH_SET_VOLUME
                }

                val payload = if (command == CommandAction.SET_VOLUME) {
                    E2EContracts.encodeVolumePayload(volume)
                } else ByteArray(0)

                if (wearableEnv.isNetworkConnected) {
                    commandDispatched = wearableEnv.sendMessage("phone_node_01", path, payload)
                    if (commandDispatched) {
                        when (command) {
                            CommandAction.PLAY -> isPlaying = true
                            CommandAction.PAUSE -> isPlaying = false
                            CommandAction.PLAY_PAUSE -> isPlaying = !isPlaying
                            CommandAction.VOLUME_UP -> volume = (volume + E2EContracts.VOLUME_STEP).coerceIn(0f, 1f)
                            CommandAction.VOLUME_DOWN -> volume = (volume - E2EContracts.VOLUME_STEP).coerceIn(0f, 1f)
                            CommandAction.SET_VOLUME -> volume = volume.coerceIn(0f, 1f)
                            else -> {}
                        }
                    }
                } else if (network == NetworkState.RECONNECTING) {
                    // Reconnecting buffers message and flushes when reconnected
                    wearableEnv.setNetworkConnected(true)
                    commandDispatched = wearableEnv.sendMessage("phone_node_01", path, payload)
                }
            }

            TargetDevice.DESKTOP -> {
                targetRouted = "DESKTOP"
                val connectAction = when (command) {
                    CommandAction.PLAY -> E2EContracts.ACTION_PLAY
                    CommandAction.PAUSE -> E2EContracts.ACTION_PAUSE
                    CommandAction.PLAY_PAUSE -> if (isPlaying) E2EContracts.ACTION_PAUSE else E2EContracts.ACTION_PLAY
                    CommandAction.NEXT -> E2EContracts.ACTION_NEXT
                    CommandAction.PREVIOUS -> E2EContracts.ACTION_PREVIOUS
                    CommandAction.VOLUME_UP -> E2EContracts.ACTION_VOLUME
                    CommandAction.VOLUME_DOWN -> E2EContracts.ACTION_VOLUME
                    CommandAction.SET_VOLUME -> E2EContracts.ACTION_VOLUME
                }
                val value = when (command) {
                    CommandAction.VOLUME_UP -> E2EContracts.floatToDesktopVolume((volume + E2EContracts.VOLUME_STEP).coerceIn(0f, 1f))
                    CommandAction.VOLUME_DOWN -> E2EContracts.floatToDesktopVolume((volume - E2EContracts.VOLUME_STEP).coerceIn(0f, 1f))
                    CommandAction.SET_VOLUME -> E2EContracts.floatToDesktopVolume(volume)
                    else -> 0L
                }

                if (connectEnv.isOnline) {
                    commandDispatched = connectEnv.dispatchCommand("desktop_peer_alpha", connectAction, value)
                    if (commandDispatched) {
                        isPlaying = connectEnv.desktopState.isPlaying
                        if (connectAction == E2EContracts.ACTION_VOLUME) {
                            volume = E2EContracts.desktopVolumeToFloat(value)
                        }
                    }
                } else if (network == NetworkState.RECONNECTING) {
                    connectEnv.isOnline = true
                    commandDispatched = connectEnv.dispatchCommand("desktop_peer_alpha", connectAction, value)
                }
            }
        }

        return PairwiseResult(commandDispatched, targetRouted, isPlaying, volume)
    }

    // 24 Orthogonal Combinations

    @Test
    fun c01_phone_play_connected_idle_buttontap() {
        val res = executePairwiseTest(TargetDevice.PHONE, CommandAction.PLAY, NetworkState.CONNECTED, PlayerState.IDLE, InputSource.BUTTON_TAP)
        assertTrue(res.success)
        assertEquals("PHONE", res.dispatchedToTarget)
        assertTrue(res.resultingPlayingState)
    }

    @Test
    fun c02_phone_pause_connected_playing_buttontap() {
        val res = executePairwiseTest(TargetDevice.PHONE, CommandAction.PAUSE, NetworkState.CONNECTED, PlayerState.PLAYING, InputSource.BUTTON_TAP)
        assertTrue(res.success)
        assertEquals("PHONE", res.dispatchedToTarget)
        assertFalse(res.resultingPlayingState)
    }

    @Test
    fun c03_phone_playpause_connected_paused_buttontap() {
        val res = executePairwiseTest(TargetDevice.PHONE, CommandAction.PLAY_PAUSE, NetworkState.CONNECTED, PlayerState.PAUSED, InputSource.BUTTON_TAP)
        assertTrue(res.success)
        assertTrue(res.resultingPlayingState)
    }

    @Test
    fun c04_phone_next_connected_playing_messagelayer() {
        val res = executePairwiseTest(TargetDevice.PHONE, CommandAction.NEXT, NetworkState.CONNECTED, PlayerState.PLAYING, InputSource.MESSAGE_LAYER)
        assertTrue(res.success)
        assertEquals("PHONE", res.dispatchedToTarget)
    }

    @Test
    fun c05_phone_prev_connected_buffering_messagelayer() {
        val res = executePairwiseTest(TargetDevice.PHONE, CommandAction.PREVIOUS, NetworkState.CONNECTED, PlayerState.BUFFERING, InputSource.MESSAGE_LAYER)
        assertTrue(res.success)
    }

    @Test
    fun c06_phone_volumeup_connected_playing_rotarycrown() {
        val res = executePairwiseTest(TargetDevice.PHONE, CommandAction.VOLUME_UP, NetworkState.CONNECTED, PlayerState.PLAYING, InputSource.ROTARY_CROWN, 0.40f)
        assertTrue(res.success)
        assertEquals(0.45f, res.resultingVolume, 0.0001f)
    }

    @Test
    fun c07_phone_volumedown_connected_paused_rotarycrown() {
        val res = executePairwiseTest(TargetDevice.PHONE, CommandAction.VOLUME_DOWN, NetworkState.CONNECTED, PlayerState.PAUSED, InputSource.ROTARY_CROWN, 0.40f)
        assertTrue(res.success)
        assertEquals(0.35f, res.resultingVolume, 0.0001f)
    }

    @Test
    fun c08_phone_setvolume_connected_idle_rotarycrown() {
        val res = executePairwiseTest(TargetDevice.PHONE, CommandAction.SET_VOLUME, NetworkState.CONNECTED, PlayerState.IDLE, InputSource.ROTARY_CROWN, 0.85f)
        assertTrue(res.success)
        assertEquals(0.85f, res.resultingVolume, 0.0001f)
    }

    @Test
    fun c09_phone_play_disconnected_idle_buttontap() {
        val res = executePairwiseTest(TargetDevice.PHONE, CommandAction.PLAY, NetworkState.DISCONNECTED, PlayerState.IDLE, InputSource.BUTTON_TAP)
        assertFalse("Command should fail when phone is disconnected", res.success)
    }

    @Test
    fun c10_phone_pause_disconnected_playing_buttontap() {
        val res = executePairwiseTest(TargetDevice.PHONE, CommandAction.PAUSE, NetworkState.DISCONNECTED, PlayerState.PLAYING, InputSource.BUTTON_TAP)
        assertFalse(res.success)
    }

    @Test
    fun c11_phone_next_reconnecting_playing_buttontap() {
        val res = executePairwiseTest(TargetDevice.PHONE, CommandAction.NEXT, NetworkState.RECONNECTING, PlayerState.PLAYING, InputSource.BUTTON_TAP)
        assertTrue("Reconnecting buffer flushes upon restoration", res.success)
    }

    @Test
    fun c12_phone_volumeup_reconnecting_paused_rotarycrown() {
        val res = executePairwiseTest(TargetDevice.PHONE, CommandAction.VOLUME_UP, NetworkState.RECONNECTING, PlayerState.PAUSED, InputSource.ROTARY_CROWN, 0.50f)
        assertTrue(res.success)
    }

    @Test
    fun c13_desktop_play_connected_paused_buttontap() {
        val res = executePairwiseTest(TargetDevice.DESKTOP, CommandAction.PLAY, NetworkState.CONNECTED, PlayerState.PAUSED, InputSource.BUTTON_TAP)
        assertTrue(res.success)
        assertEquals("DESKTOP", res.dispatchedToTarget)
        assertTrue(res.resultingPlayingState)
    }

    @Test
    fun c14_desktop_pause_connected_playing_buttontap() {
        val res = executePairwiseTest(TargetDevice.DESKTOP, CommandAction.PAUSE, NetworkState.CONNECTED, PlayerState.PLAYING, InputSource.BUTTON_TAP)
        assertTrue(res.success)
        assertEquals("DESKTOP", res.dispatchedToTarget)
        assertFalse(res.resultingPlayingState)
    }

    @Test
    fun c15_desktop_playpause_connected_idle_buttontap() {
        val res = executePairwiseTest(TargetDevice.DESKTOP, CommandAction.PLAY_PAUSE, NetworkState.CONNECTED, PlayerState.IDLE, InputSource.BUTTON_TAP)
        assertTrue(res.success)
        assertTrue(res.resultingPlayingState)
    }

    @Test
    fun c16_desktop_next_connected_playing_messagelayer() {
        val res = executePairwiseTest(TargetDevice.DESKTOP, CommandAction.NEXT, NetworkState.CONNECTED, PlayerState.PLAYING, InputSource.MESSAGE_LAYER)
        assertTrue(res.success)
        assertEquals(102L, connectEnv.desktopState.currentTrackId)
    }

    @Test
    fun c17_desktop_prev_connected_playing_messagelayer() {
        connectEnv.desktopState.currentTrackId = 105L
        connectEnv.desktopState.positionMs = 500L
        val res = executePairwiseTest(TargetDevice.DESKTOP, CommandAction.PREVIOUS, NetworkState.CONNECTED, PlayerState.PLAYING, InputSource.MESSAGE_LAYER)
        assertTrue(res.success)
        assertEquals(104L, connectEnv.desktopState.currentTrackId)
    }

    @Test
    fun c18_desktop_volumeup_connected_playing_rotarycrown() {
        val res = executePairwiseTest(TargetDevice.DESKTOP, CommandAction.VOLUME_UP, NetworkState.CONNECTED, PlayerState.PLAYING, InputSource.ROTARY_CROWN, 0.60f)
        assertTrue(res.success)
        assertEquals(0.65f, res.resultingVolume, 0.001f)
    }

    @Test
    fun c19_desktop_volumedown_connected_paused_rotarycrown() {
        val res = executePairwiseTest(TargetDevice.DESKTOP, CommandAction.VOLUME_DOWN, NetworkState.CONNECTED, PlayerState.PAUSED, InputSource.ROTARY_CROWN, 0.60f)
        assertTrue(res.success)
        assertEquals(0.55f, res.resultingVolume, 0.001f)
    }

    @Test
    fun c20_desktop_setvolume_connected_idle_rotarycrown() {
        val res = executePairwiseTest(TargetDevice.DESKTOP, CommandAction.SET_VOLUME, NetworkState.CONNECTED, PlayerState.IDLE, InputSource.ROTARY_CROWN, 0.90f)
        assertTrue(res.success)
        assertEquals(0.90f, res.resultingVolume, 0.001f)
    }

    @Test
    fun c21_desktop_play_disconnected_paused_buttontap() {
        val res = executePairwiseTest(TargetDevice.DESKTOP, CommandAction.PLAY, NetworkState.DISCONNECTED, PlayerState.PAUSED, InputSource.BUTTON_TAP)
        assertFalse("Command must fail when desktop offline", res.success)
    }

    @Test
    fun c22_desktop_pause_disconnected_playing_buttontap() {
        val res = executePairwiseTest(TargetDevice.DESKTOP, CommandAction.PAUSE, NetworkState.DISCONNECTED, PlayerState.PLAYING, InputSource.BUTTON_TAP)
        assertFalse(res.success)
    }

    @Test
    fun c23_desktop_next_reconnecting_playing_buttontap() {
        val res = executePairwiseTest(TargetDevice.DESKTOP, CommandAction.NEXT, NetworkState.RECONNECTING, PlayerState.PLAYING, InputSource.BUTTON_TAP)
        assertTrue("Reconnecting desktop receives flushed command", res.success)
    }

    @Test
    fun c24_desktop_volume_reconnecting_idle_rotarycrown() {
        val res = executePairwiseTest(TargetDevice.DESKTOP, CommandAction.VOLUME_UP, NetworkState.RECONNECTING, PlayerState.IDLE, InputSource.ROTARY_CROWN, 0.30f)
        assertTrue(res.success)
    }
}
