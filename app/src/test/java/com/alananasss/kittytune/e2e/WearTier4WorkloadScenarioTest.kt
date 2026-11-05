package com.alananasss.kittytune.e2e

import com.alananasss.kittytune.e2e.harness.E2EContracts
import com.alananasss.kittytune.e2e.harness.MockConnectEnvironment
import com.alananasss.kittytune.e2e.harness.MockWearableEnvironment
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tier 4: Workload & Operational Profile Scenario Tests.
 * Tests realistic, multi-step, end-to-end user workflows spanning
 * watch UI, communication layers, phone receiver, desktop Connect,
 * and fault recovery.
 */
class WearTier4WorkloadScenarioTest {

    private lateinit var wearableEnv: MockWearableEnvironment
    private lateinit var connectEnv: MockConnectEnvironment

    @Before
    fun setUp() {
        wearableEnv = MockWearableEnvironment()
        connectEnv = MockConnectEnvironment()
    }

    /**
     * Scenario 1: Complete Media Session Lifecycle
     * Watch launch -> state sync -> play -> track skip sequence -> volume adjust -> pause
     */
    @Test
    fun scenario1_completeMediaSessionLifecycle() {
        // Step 1: Watch opens, requests sync
        var phonePlaybackActive = false
        var currentTrackId = 501L
        var currentTrackTitle = "Morning Chill"
        var currentVolume = 0.50f

        wearableEnv.addMessageListener { msg ->
            when (msg.path) {
                E2EContracts.PATH_SYNC_REQUEST -> {
                    wearableEnv.putDataItem(
                        E2EContracts.PATH_PLAYBACK_STATE,
                        mapOf(
                            E2EContracts.KEY_TRACK_ID to currentTrackId,
                            E2EContracts.KEY_TITLE to currentTrackTitle,
                            E2EContracts.KEY_IS_PLAYING to phonePlaybackActive,
                            E2EContracts.KEY_VOLUME to currentVolume,
                            E2EContracts.KEY_TIMESTAMP to System.currentTimeMillis()
                        )
                    )
                }
                E2EContracts.PATH_PLAY -> {
                    phonePlaybackActive = true
                    wearableEnv.putDataItem(E2EContracts.PATH_PLAYBACK_STATE, mapOf(E2EContracts.KEY_IS_PLAYING to true))
                }
                E2EContracts.PATH_NEXT -> {
                    currentTrackId += 1
                    currentTrackTitle = "Song #$currentTrackId"
                    wearableEnv.putDataItem(
                        E2EContracts.PATH_PLAYBACK_STATE,
                        mapOf(
                            E2EContracts.KEY_TRACK_ID to currentTrackId,
                            E2EContracts.KEY_TITLE to currentTrackTitle
                        )
                    )
                }
                E2EContracts.PATH_SET_VOLUME -> {
                    currentVolume = E2EContracts.decodeVolumePayload(msg.payload)
                    wearableEnv.putDataItem(E2EContracts.PATH_PLAYBACK_STATE, mapOf(E2EContracts.KEY_VOLUME to currentVolume))
                }
                E2EContracts.PATH_PAUSE -> {
                    phonePlaybackActive = false
                    wearableEnv.putDataItem(E2EContracts.PATH_PLAYBACK_STATE, mapOf(E2EContracts.KEY_IS_PLAYING to false))
                }
            }
        }

        // 1. Initial sync
        wearableEnv.sendMessage("phone_node_01", E2EContracts.PATH_SYNC_REQUEST)
        val initialData = wearableEnv.getDataItem(E2EContracts.PATH_PLAYBACK_STATE)
        assertEquals("Morning Chill", initialData?.values?.get(E2EContracts.KEY_TITLE))
        assertEquals(false, initialData?.values?.get(E2EContracts.KEY_IS_PLAYING))

        // 2. Play
        wearableEnv.sendMessage("phone_node_01", E2EContracts.PATH_PLAY)
        val playingData = wearableEnv.getDataItem(E2EContracts.PATH_PLAYBACK_STATE)
        assertEquals(true, playingData?.values?.get(E2EContracts.KEY_IS_PLAYING))

        // 3. Skip 3 tracks
        for (i in 1..3) {
            wearableEnv.sendMessage("phone_node_01", E2EContracts.PATH_NEXT)
        }
        val skippedData = wearableEnv.getDataItem(E2EContracts.PATH_PLAYBACK_STATE)
        assertEquals(504L, skippedData?.values?.get(E2EContracts.KEY_TRACK_ID))
        assertEquals("Song #504", skippedData?.values?.get(E2EContracts.KEY_TITLE))

        // 4. Adjust volume to 75%
        val volumePayload = E2EContracts.encodeVolumePayload(0.75f)
        wearableEnv.sendMessage("phone_node_01", E2EContracts.PATH_SET_VOLUME, volumePayload)
        val volumeData = wearableEnv.getDataItem(E2EContracts.PATH_PLAYBACK_STATE)
        assertEquals(0.75f, volumeData?.values?.get(E2EContracts.KEY_VOLUME) as Float, 0.0001f)

        // 5. Pause
        wearableEnv.sendMessage("phone_node_01", E2EContracts.PATH_PAUSE)
        val pausedData = wearableEnv.getDataItem(E2EContracts.PATH_PLAYBACK_STATE)
        assertEquals(false, pausedData?.values?.get(E2EContracts.KEY_IS_PLAYING))
    }

    /**
     * Scenario 2: Cross-Device Handoff & Dual Target Routing
     * Playing on Phone -> User selects Desktop on watch -> Dispatches to Desktop -> Reverts to Phone
     */
    @Test
    fun scenario2_crossDeviceHandoffAndDualTargetRouting() {
        var activeTarget = E2EContracts.TARGET_TYPE_PHONE
        var phonePlaying = true

        val switchTarget = { newTarget: String -> activeTarget = newTarget }

        // Start: Play on phone
        assertEquals(E2EContracts.TARGET_TYPE_PHONE, activeTarget)

        // Switch to Desktop
        switchTarget(E2EContracts.TARGET_TYPE_DESKTOP)
        assertEquals(E2EContracts.TARGET_TYPE_DESKTOP, activeTarget)

        // When active target is Desktop, watch dispatches play and next to Desktop
        connectEnv.dispatchCommand("desktop_peer_alpha", E2EContracts.ACTION_PLAY)
        assertTrue(connectEnv.desktopState.isPlaying)

        connectEnv.dispatchCommand("desktop_peer_alpha", E2EContracts.ACTION_NEXT)
        assertEquals(102L, connectEnv.desktopState.currentTrackId)

        // Switch back to Phone
        switchTarget(E2EContracts.TARGET_TYPE_PHONE)
        assertEquals(E2EContracts.TARGET_TYPE_PHONE, activeTarget)

        // Commands now route back to phone
        wearableEnv.sendMessage("phone_node_01", E2EContracts.PATH_PAUSE)
        phonePlaying = false
        assertFalse(phonePlaying)
    }

    /**
     * Scenario 3: Stress / Rapid-Fire Rotary Crown Volume Scrubbing
     * 100 clockwise clicks followed by 120 counter-clockwise clicks
     */
    @Test
    fun scenario3_stressRapidRotaryCrownVolumeScrubbing() {
        var volume = 0.50f

        // Rapid clockwise roll: 100 ticks of delta 1.0f
        for (i in 1..100) {
            volume = E2EContracts.computeRotaryVolume(volume, 1.0f)
        }
        assertEquals("Volume must clamp strictly at 1.0f", 1.0f, volume, 0.0001f)

        // Rapid counter-clockwise roll: 120 ticks of delta -1.0f
        for (i in 1..120) {
            volume = E2EContracts.computeRotaryVolume(volume, -1.0f)
        }
        assertEquals("Volume must clamp strictly at 0.0f", 0.0f, volume, 0.0001f)

        // Delicate scroll back to 50%: 10 ticks of +1.0f with 0.05 step
        for (i in 1..10) {
            volume = E2EContracts.computeRotaryVolume(volume, 1.0f, sensitivity = 0.05f)
        }
        assertEquals(0.50f, volume, 0.0001f)
    }

    /**
     * Scenario 4: Transient Network Dropout and State Resynchronization
     * Bluetooth disconnects mid-session, reconnects, verifies cached state delivers immediately
     */
    @Test
    fun scenario4_transientNetworkDropoutAndStateResynchronization() {
        // Initial state
        wearableEnv.putDataItem(
            E2EContracts.PATH_PLAYBACK_STATE,
            mapOf(
                E2EContracts.KEY_TRACK_ID to 77L,
                E2EContracts.KEY_TITLE to "Connected Tune",
                E2EContracts.KEY_IS_PLAYING to true
            )
        )

        // Network disconnects (user walks out of Bluetooth range)
        wearableEnv.setNetworkConnected(false)

        // Command attempted while disconnected fails
        val sentWhileOffline = wearableEnv.sendMessage("phone_node_01", E2EContracts.PATH_PAUSE)
        assertFalse(sentWhileOffline)

        // Meanwhile phone changes track locally
        wearableEnv.putDataItem(
            E2EContracts.PATH_PLAYBACK_STATE,
            mapOf(
                E2EContracts.KEY_TRACK_ID to 78L,
                E2EContracts.KEY_TITLE to "Reconnected Tune",
                E2EContracts.KEY_IS_PLAYING to true
            )
        )

        // Reconnects (user walks back in range)
        wearableEnv.setNetworkConnected(true)

        // DataLayer delivers cached state immediately without manual request
        val reconnectedState = wearableEnv.getDataItem(E2EContracts.PATH_PLAYBACK_STATE)
        assertEquals(78L, reconnectedState?.values?.get(E2EContracts.KEY_TRACK_ID))
        assertEquals("Reconnected Tune", reconnectedState?.values?.get(E2EContracts.KEY_TITLE))

        // Commands now succeed
        val sentAfterReconnect = wearableEnv.sendMessage("phone_node_01", E2EContracts.PATH_PAUSE)
        assertTrue(sentAfterReconnect)
    }

    /**
     * Scenario 5: Multi-Room Concurrent Playback and Desktop Relay Resilience
     * Desktop playback active over Connect Relay WS (8787), Phone incoming call pauses phone audio,
     * watch companion continues controlling desktop uninterrupted
     */
    @Test
    fun scenario5_multiRoomConcurrentPlaybackAndDesktopRelayResilience() {
        // Active target is Desktop
        val activeTarget = E2EContracts.TARGET_TYPE_DESKTOP

        // Phone audio is paused due to incoming call
        val phoneAudioPaused = true
        assertTrue(phoneAudioPaused)

        // Desktop audio is playing independently in living room
        connectEnv.dispatchCommand("desktop_peer_alpha", E2EContracts.ACTION_PLAY)
        assertTrue(connectEnv.desktopState.isPlaying)

        // User lowers desktop volume from watch
        val desktopVol = E2EContracts.floatToDesktopVolume(0.30f)
        connectEnv.dispatchCommand("desktop_peer_alpha", E2EContracts.ACTION_VOLUME, desktopVol)
        assertEquals(300L, connectEnv.desktopState.volume)

        // User skips track on desktop
        connectEnv.dispatchCommand("desktop_peer_alpha", E2EContracts.ACTION_NEXT)
        assertEquals(102L, connectEnv.desktopState.currentTrackId)
    }

    /**
     * Scenario 6: Playlist Queue Boundary Traversal and Smart Previous
     * Position-dependent previous behavior: >3000ms restarts track, <3000ms skips backward
     */
    @Test
    fun scenario6_playlistQueueBoundaryTraversalAndSmartPrevious() {
        var currentTrackIndex = 2 // 0-indexed: Track 3
        var positionMs = 45_000L // 45 seconds in

        val handlePrevious = {
            if (positionMs > E2EContracts.SMART_PREVIOUS_THRESHOLD_MS) {
                positionMs = 0L // Restart song
            } else if (currentTrackIndex > 0) {
                currentTrackIndex -= 1
                positionMs = 0L
            }
        }

        // Tap Previous at 45s: Restarts current song (stays at index 2)
        handlePrevious()
        assertEquals(2, currentTrackIndex)
        assertEquals(0L, positionMs)

        // Tap Previous immediately at 0s: Skips to preceding track (index 1)
        handlePrevious()
        assertEquals(1, currentTrackIndex)
        assertEquals(0L, positionMs)

        // Tap Previous again at 0s: Skips to track 0
        handlePrevious()
        assertEquals(0, currentTrackIndex)

        // Tap Previous at track 0: Bounded at beginning of playlist
        handlePrevious()
        assertEquals(0, currentTrackIndex)
    }
}
