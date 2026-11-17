package com.alananasss.kittytune.wear

import com.alananasss.kittytune.data.sync.ConnectManager
import com.alananasss.kittytune.data.sync.ConnectPeerState
import com.alananasss.kittytune.data.sync.PlaybackSnapshot
import com.alananasss.kittytune.data.together.SharedTrack
import com.alananasss.kittytune.data.together.Together
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.TrackPublisherMetadata
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.wear.common.TargetSelectionPayload
import com.alananasss.kittytune.wear.common.WearCodecs
import com.alananasss.kittytune.wear.common.WearConstants
import com.alananasss.kittytune.wear.common.WearDataMapMapper
import com.google.android.gms.wearable.DataMap
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Milestone 4 Integration Test verifying Wear OS command bridging to desktop peers,
 * volume scaling (0.0f..1.0f -> 0L..1000L), resolution of ACTION_PLAY_PAUSE,
 * desktop state mirroring across all 14 contract keys, and target switching.
 */
class WearableDesktopRemoteIntegrationTest {

    private lateinit var service: WearableMessageReceiverService

    @Before
    fun setUp() {
        service = WearableMessageReceiverService()
        WearableStatePublisher.resetTargetForTesting()
        ConnectManager.resetForTesting()
        Together.resetForTesting()
    }

    @After
    fun tearDown() {
        WearableStatePublisher.resetTargetForTesting()
        ConnectManager.resetForTesting()
        Together.resetForTesting()
    }

    private fun createSampleTrack(
        id: Long = 8888L,
        title: String = "Cyberpunk Synth",
        artist: String = "Kavinsky",
        album: String = "Nightcall",
        durationMs: Long = 210000L,
        artworkUrl: String = "https://example.com/art.jpg"
    ): Track {
        return Track(
            id = id,
            title = title,
            artworkUrl = artworkUrl,
            durationMs = durationMs,
            user = User(id = 101L, username = artist, avatarUrl = null),
            publisherMetadata = TrackPublisherMetadata(artist = artist, albumTitle = album)
        )
    }

    private fun createSampleSnapshot(
        deviceId: String = "desktop_pc_1",
        track: Track = createSampleTrack(),
        isPlaying: Boolean = false,
        volume: Float = 0.70f,
        positionMs: Long = 45000L,
        isLiked: Boolean = true
    ): PlaybackSnapshot {
        return PlaybackSnapshot(
            deviceId = deviceId,
            updatedAtMs = System.currentTimeMillis(),
            queue = listOf(track),
            currentIndex = 0,
            positionMs = positionMs,
            isPlaying = isPlaying,
            shuffleEnabled = false,
            repeatMode = "none",
            volume = volume,
            isLiked = isLiked
        )
    }

    @Test
    fun testDesktopCommandForwardingPlay() = runBlocking {
        val peerId = "desktop_pc_1"
        val peer = ConnectPeerState(
            deviceId = peerId,
            name = "Living Room PC",
            connected = true,
            snapshot = createSampleSnapshot(deviceId = peerId, isPlaying = false)
        )
        ConnectManager.setPeersForTesting(mapOf(peerId to peer))
        WearableStatePublisher.setActiveTarget(WearConstants.TARGET_TYPE_DESKTOP, peerId, "Living Room PC")

        var interceptedPeerId: String? = null
        var interceptedAction: String? = null
        var interceptedValue: Long? = null

        ConnectManager.testCommandDispatcher = { targetPeerId, action, value ->
            interceptedPeerId = targetPeerId
            interceptedAction = action
            interceptedValue = value
        }

        service.handleMessage(WearConstants.ACTION_PLAY, ByteArray(0))

        assertEquals(peerId, interceptedPeerId)
        assertEquals("play", interceptedAction)
        assertEquals(0L, interceptedValue)
    }

    @Test
    fun testDesktopCommandForwardingPause() = runBlocking {
        val peerId = "desktop_pc_1"
        val peer = ConnectPeerState(
            deviceId = peerId,
            name = "Living Room PC",
            connected = true,
            snapshot = createSampleSnapshot(deviceId = peerId, isPlaying = true)
        )
        ConnectManager.setPeersForTesting(mapOf(peerId to peer))
        WearableStatePublisher.setActiveTarget(WearConstants.TARGET_TYPE_DESKTOP, peerId, "Living Room PC")

        var interceptedAction: String? = null
        ConnectManager.testCommandDispatcher = { _, action, _ ->
            interceptedAction = action
        }

        service.handleMessage(WearConstants.ACTION_PAUSE, ByteArray(0))

        assertEquals("pause", interceptedAction)
    }

    @Test
    fun testDesktopCommandForwardingPlayPauseResolvesPauseWhenPlaying() = runBlocking {
        val peerId = "desktop_pc_1"
        val peer = ConnectPeerState(
            deviceId = peerId,
            name = "Studio Mac",
            connected = true,
            snapshot = createSampleSnapshot(deviceId = peerId, isPlaying = true)
        )
        ConnectManager.setPeersForTesting(mapOf(peerId to peer))
        WearableStatePublisher.setActiveTarget(WearConstants.TARGET_TYPE_DESKTOP, peerId, "Studio Mac")

        var interceptedAction: String? = null
        ConnectManager.testCommandDispatcher = { _, action, _ ->
            interceptedAction = action
        }

        // Must NOT forward "toggle"; since isPlaying == true, must resolve to "pause"
        service.handleMessage(WearConstants.ACTION_PLAY_PAUSE, ByteArray(0))

        assertEquals("pause", interceptedAction)
    }

    @Test
    fun testDesktopCommandForwardingPlayPauseResolvesPlayWhenPaused() = runBlocking {
        val peerId = "desktop_pc_1"
        val peer = ConnectPeerState(
            deviceId = peerId,
            name = "Studio Mac",
            connected = true,
            snapshot = createSampleSnapshot(deviceId = peerId, isPlaying = false)
        )
        ConnectManager.setPeersForTesting(mapOf(peerId to peer))
        WearableStatePublisher.setActiveTarget(WearConstants.TARGET_TYPE_DESKTOP, peerId, "Studio Mac")

        var interceptedAction: String? = null
        ConnectManager.testCommandDispatcher = { _, action, _ ->
            interceptedAction = action
        }

        // Must NOT forward "toggle"; since isPlaying == false, must resolve to "play"
        service.handleMessage(WearConstants.ACTION_PLAY_PAUSE, ByteArray(0))

        assertEquals("play", interceptedAction)
    }

    @Test
    fun testDesktopCommandForwardingNext() = runBlocking {
        val peerId = "desktop_pc_1"
        ConnectManager.setPeersForTesting(mapOf(peerId to ConnectPeerState(peerId, "PC", connected = true)))
        WearableStatePublisher.setActiveTarget(WearConstants.TARGET_TYPE_DESKTOP, peerId, "PC")

        var interceptedAction: String? = null
        ConnectManager.testCommandDispatcher = { _, action, _ ->
            interceptedAction = action
        }

        service.handleMessage(WearConstants.ACTION_NEXT, ByteArray(0))

        assertEquals("next", interceptedAction)
    }

    @Test
    fun testDesktopCommandForwardingPrevious() = runBlocking {
        val peerId = "desktop_pc_1"
        ConnectManager.setPeersForTesting(mapOf(peerId to ConnectPeerState(peerId, "PC", connected = true)))
        WearableStatePublisher.setActiveTarget(WearConstants.TARGET_TYPE_DESKTOP, peerId, "PC")

        var interceptedAction: String? = null
        ConnectManager.testCommandDispatcher = { _, action, _ ->
            interceptedAction = action
        }

        service.handleMessage(WearConstants.ACTION_PREVIOUS, ByteArray(0))

        assertEquals("previous", interceptedAction)
    }

    @Test
    fun testDesktopCommandForwardingVolumeUpDiscreteStep() = runBlocking {
        val peerId = "desktop_pc_1"
        val peer = ConnectPeerState(
            deviceId = peerId,
            name = "PC",
            connected = true,
            snapshot = createSampleSnapshot(deviceId = peerId, volume = 0.70f)
        )
        ConnectManager.setPeersForTesting(mapOf(peerId to peer))
        WearableStatePublisher.setActiveTarget(WearConstants.TARGET_TYPE_DESKTOP, peerId, "PC")

        var interceptedAction: String? = null
        var interceptedValue: Long? = null
        ConnectManager.testCommandDispatcher = { _, action, value ->
            interceptedAction = action
            interceptedValue = value
        }

        // Volume 0.70f + 0.05f = 0.75f -> maps to 750L
        service.handleMessage(WearConstants.ACTION_VOLUME_UP, ByteArray(0))

        assertEquals("volume", interceptedAction)
        assertEquals(750L, interceptedValue)
    }

    @Test
    fun testDesktopCommandForwardingVolumeDownDiscreteStep() = runBlocking {
        val peerId = "desktop_pc_1"
        val peer = ConnectPeerState(
            deviceId = peerId,
            name = "PC",
            connected = true,
            snapshot = createSampleSnapshot(deviceId = peerId, volume = 0.70f)
        )
        ConnectManager.setPeersForTesting(mapOf(peerId to peer))
        WearableStatePublisher.setActiveTarget(WearConstants.TARGET_TYPE_DESKTOP, peerId, "PC")

        var interceptedAction: String? = null
        var interceptedValue: Long? = null
        ConnectManager.testCommandDispatcher = { _, action, value ->
            interceptedAction = action
            interceptedValue = value
        }

        // Volume 0.70f - 0.05f = 0.65f -> maps to 650L
        service.handleMessage(WearConstants.ACTION_VOLUME_DOWN, ByteArray(0))

        assertEquals("volume", interceptedAction)
        assertEquals(650L, interceptedValue)
    }

    @Test
    fun testDesktopCommandForwardingSetVolumeExactScaling() = runBlocking {
        val peerId = "desktop_pc_1"
        ConnectManager.setPeersForTesting(mapOf(peerId to ConnectPeerState(peerId, "PC", connected = true)))
        WearableStatePublisher.setActiveTarget(WearConstants.TARGET_TYPE_DESKTOP, peerId, "PC")

        var interceptedAction: String? = null
        var interceptedValue: Long? = null
        ConnectManager.testCommandDispatcher = { _, action, value ->
            interceptedAction = action
            interceptedValue = value
        }

        // Ratio 0.82f -> maps exactly to 820L
        val volumePayload = WearCodecs.encodeVolume(0.82f)
        service.handleMessage(WearConstants.ACTION_SET_VOLUME, volumePayload)

        assertEquals("volume", interceptedAction)
        assertEquals(820L, interceptedValue)
    }

    @Test
    fun testDesktopTargetSelectionUpdatesActiveTargetAndPublishes() = runBlocking {
        ConnectManager.setPeersForTesting(mapOf("studio_pc" to ConnectPeerState("studio_pc", "Studio Workstation", connected = true)))
        val targetPayload = TargetSelectionPayload.desktop(peerId = "studio_pc", peerName = "Studio Workstation")
        val data = WearCodecs.encodeTarget(targetPayload)

        service.handleMessage(WearConstants.ACTION_TARGET_SELECT, data)

        assertEquals(WearConstants.TARGET_TYPE_DESKTOP, WearableStatePublisher.activeTargetType)
        assertEquals("studio_pc", WearableStatePublisher.activeTargetId)
        assertEquals("Studio Workstation", WearableStatePublisher.activeTargetName)
        assertEquals(null, ConnectManager.selectedDevice.value) // A watch remote does not transfer phone output.
    }

    @Test
    fun testDesktopStatePublisherMirrorsAllFourteenContractKeys() {
        val peerId = "desktop_rig_01"
        val track = createSampleTrack(
            id = 9999L,
            title = "Midnight City",
            artist = "M83",
            album = "Hurry Up, We're Dreaming",
            durationMs = 243000L,
            artworkUrl = "https://example.com/m83.jpg"
        )
        val snapshot = createSampleSnapshot(
            deviceId = peerId,
            track = track,
            isPlaying = true,
            volume = 0.85f,
            positionMs = 60000L,
            isLiked = true
        )
        val peer = ConnectPeerState(
            deviceId = peerId,
            name = "Gaming Rig",
            connected = true,
            snapshot = snapshot
        )
        ConnectManager.setPeersForTesting(mapOf(peerId to peer))
        WearableStatePublisher.setActiveTarget(WearConstants.TARGET_TYPE_DESKTOP, peerId, "Gaming Rig")

        val state = WearableStatePublisher.buildCurrentPlaybackState(null)

        // 1. track_id
        assertEquals(9999L, state.trackId)
        // 2. title
        assertEquals("Midnight City", state.title)
        // 3. artist
        assertEquals("M83", state.artist)
        // 4. album
        assertEquals("Hurry Up, We're Dreaming", state.album)
        // 5. artwork_url
        assertEquals("https://example.com/m83.jpg", state.artworkUrl)
        // 6. is_playing
        assertTrue(state.isPlaying)
        // 7. is_loading
        assertFalse(state.isLoading)
        // 8. position_ms (projected or baseline)
        assertTrue(state.positionMs >= 60000L)
        // 9. duration_ms
        assertEquals(243000L, state.durationMs)
        // 10. volume
        assertEquals(0.85f, state.volume, 0.001f)
        // 11. target_device
        assertEquals("Gaming Rig", state.targetDevice)
        // 12. target_type
        assertEquals(WearConstants.TARGET_TYPE_DESKTOP, state.targetType)
        // 13. is_liked
        assertTrue(state.isLiked)
        // 14. timestamp
        assertTrue(state.timestamp > 0L)

        // Verify DataMap roundtrip with all 14 keys
        val dataMap = DataMap()
        WearDataMapMapper.writeToDataMap(state, dataMap)

        assertEquals(9999L, dataMap.getLong(WearConstants.KEY_TRACK_ID))
        assertEquals("Midnight City", dataMap.getString(WearConstants.KEY_TITLE))
        assertEquals("M83", dataMap.getString(WearConstants.KEY_ARTIST))
        assertEquals("Hurry Up, We're Dreaming", dataMap.getString(WearConstants.KEY_ALBUM))
        assertEquals("https://example.com/m83.jpg", dataMap.getString(WearConstants.KEY_ARTWORK_URL))
        assertTrue(dataMap.getBoolean(WearConstants.KEY_IS_PLAYING))
        assertFalse(dataMap.getBoolean(WearConstants.KEY_IS_LOADING))
        assertTrue(dataMap.getLong(WearConstants.KEY_POSITION_MS) >= 60000L)
        assertEquals(243000L, dataMap.getLong(WearConstants.KEY_DURATION_MS))
        assertEquals(0.85f, dataMap.getFloat(WearConstants.KEY_VOLUME), 0.001f)
        assertEquals("Gaming Rig", dataMap.getString(WearConstants.KEY_TARGET_DEVICE))
        assertEquals(WearConstants.TARGET_TYPE_DESKTOP, dataMap.getString(WearConstants.KEY_TARGET_TYPE))
        assertTrue(dataMap.getBoolean(WearConstants.KEY_IS_LIKED))
        assertTrue(dataMap.getLong(WearConstants.KEY_TIMESTAMP) > 0L)

        val deserialized = WearDataMapMapper.fromDataMap(dataMap)
        assertEquals(state.trackId, deserialized.trackId)
        assertEquals(state.title, deserialized.title)
        assertEquals(state.artist, deserialized.artist)
        assertEquals(state.volume, deserialized.volume, 0.001f)
        assertEquals(state.targetDevice, deserialized.targetDevice)
        assertEquals(state.targetType, deserialized.targetType)
    }

    @Test
    fun testDesktopAvailableTargetsEnumeratesConnectedPeers() {
        val peer1 = ConnectPeerState("pc_1", "Workstation", connected = true)
        val peer2 = ConnectPeerState("pc_2", "Living Room", connected = true)
        val peer3 = ConnectPeerState("pc_3", "Offline Laptop", connected = false)

        ConnectManager.setPeersForTesting(mapOf("pc_1" to peer1, "pc_2" to peer2, "pc_3" to peer3))

        val targets = WearableStatePublisher.buildAvailableTargets()

        // Should contain Phone + 2 connected peers (offline peer excluded)
        val phoneTarget = targets.find { it.id == WearConstants.TARGET_PHONE_ID }
        assertNotNull(phoneTarget)
        assertEquals(WearConstants.TARGET_TYPE_PHONE, phoneTarget?.type)
        assertTrue(phoneTarget?.isConnected == true)

        val target1 = targets.find { it.id == "pc_1" }
        assertNotNull(target1)
        assertEquals("Workstation", target1?.name)
        assertTrue(target1?.isConnected == true)

        val target2 = targets.find { it.id == "pc_2" }
        assertNotNull(target2)
        assertEquals("Living Room", target2?.name)
        assertTrue(target2?.isConnected == true)

        val target3 = targets.find { it.id == "pc_3" }
        assertFalse(targets.any { it.id == "pc_3" })
    }

    @Test
    fun testTogetherRoomPlaybackCommandRouting() = runBlocking {
        val roomCode = "KTROOM99"
        val sharedTrack = SharedTrack(
            id = 5555L,
            title = "Together Track",
            artist = "Together Artist",
            artworkUrl = null,
            durationMs = 180000L,
            permalinkUrl = null,
            source = null,
            userId = 1L,
            username = "host"
        )
        val room = Together.Room(
            code = roomCode,
            name = "Party Room",
            isPlaying = false,
            nowPlaying = sharedTrack
        )
        Together.setRoomForTesting(roomCode, room)
        val peerId = "actual_pc"
        ConnectManager.setPeersForTesting(mapOf(peerId to ConnectPeerState(peerId, "PC", connected = true)))
        WearableStatePublisher.setActiveTarget(WearConstants.TARGET_TYPE_DESKTOP, peerId, "PC")
        val commands = mutableListOf<String>()
        ConnectManager.testCommandDispatcher = { id, action, _ ->
            assertEquals(peerId, id)
            commands.add(action)
        }
        Together.testControlDispatcher = { _, _ -> error("A Together room must not intercept desktop controls") }
        for (path in listOf(WearConstants.ACTION_PLAY, WearConstants.ACTION_NEXT, WearConstants.ACTION_PREVIOUS)) {
            assertTrue(service.handleMessage(path, ByteArray(0)).isSuccess)
        }
        assertEquals(listOf("play", "next", "previous"), commands)
        assertFalse(WearableStatePublisher.buildAvailableTargets().any { it.id == roomCode })
    }

    @Test
    fun testDesktopFallbackPeerResolution() = runBlocking {
        val peerId = "auto_pc"
        ConnectManager.setPeersForTesting(mapOf(peerId to ConnectPeerState(peerId, "Auto PC", connected = true)))
        // Active target set to DESKTOP with null activeTargetId
        WearableStatePublisher.setActiveTarget(WearConstants.TARGET_TYPE_DESKTOP, null, "Desktop")

        var interceptedPeerId: String? = null
        var interceptedAction: String? = null
        ConnectManager.testCommandDispatcher = { targetPeerId, action, _ ->
            interceptedPeerId = targetPeerId
            interceptedAction = action
        }

        val result = service.handleMessage(WearConstants.ACTION_PLAY, ByteArray(0))
        assertTrue("An unspecified target must not control an arbitrary peer", result.isFailure)
        assertEquals(null, interceptedPeerId)
        assertEquals(null, interceptedAction)
    }
}
