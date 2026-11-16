package com.alananasss.kittytune.wear

import com.alananasss.kittytune.data.sync.ConnectManager
import com.alananasss.kittytune.data.sync.ConnectPeerState
import com.alananasss.kittytune.data.sync.PlaybackSnapshot
import com.alananasss.kittytune.wear.common.TargetSelectionPayload
import com.alananasss.kittytune.wear.common.WearCodecs
import com.alananasss.kittytune.wear.common.WearConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.random.Random

/**
 * Adversarial stress and boundary test suite for Milestone 4:
 * Validates offline peer command dispatch, IEEE 754 float boundary sanitization,
 * strict [0L, 1000L] desktop volume clamping, corrupted target payloads,
 * rapid target switching concurrency safety, and 500-message burst resilience.
 */
class WearableDesktopAdversarialTest {

    private lateinit var service: WearableMessageReceiverService

    @Before
    fun setUp() {
        service = WearableMessageReceiverService()
        WearableStatePublisher.resetTargetForTesting()
        ConnectManager.resetForTesting()
    }

    @After
    fun tearDown() {
        WearableStatePublisher.resetTargetForTesting()
        ConnectManager.resetForTesting()
    }

    private fun floatToBytes(value: Float): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putFloat(value).array()

    @Test
    fun testDesktopCommandDispatchWithOfflinePeerHandlesGracefully() = runBlocking {
        // Active target is DESKTOP, but peer is offline (connected = false)
        val peerId = "offline_peer_007"
        val offlinePeer = ConnectPeerState(
            deviceId = peerId,
            name = "Offline Laptop",
            connected = false,
            snapshot = null
        )
        ConnectManager.setPeersForTesting(mapOf(peerId to offlinePeer))
        WearableStatePublisher.setActiveTarget(WearConstants.TARGET_TYPE_DESKTOP, peerId, "Offline Laptop")

        val desktopActions = listOf(
            WearConstants.ACTION_PLAY to ByteArray(0),
            WearConstants.ACTION_PAUSE to ByteArray(0),
            WearConstants.ACTION_PLAY_PAUSE to ByteArray(0),
            WearConstants.ACTION_NEXT to ByteArray(0),
            WearConstants.ACTION_PREVIOUS to ByteArray(0),
            WearConstants.ACTION_VOLUME_UP to ByteArray(0),
            WearConstants.ACTION_VOLUME_DOWN to ByteArray(0),
            WearConstants.ACTION_SET_VOLUME to floatToBytes(0.5f)
        )

        for ((action, data) in desktopActions) {
            var caughtThrowable: Throwable? = null
            try {
                assertTrue("Offline command must report a failure", service.handleMessage(action, data).isFailure)
            } catch (t: Throwable) {
                caughtThrowable = t
            }
            assertNull("Action $action on offline peer threw exception: $caughtThrowable", caughtThrowable)
        }
    }

    @Test
    fun testDesktopVolumeUpClampsStrictlyAtThousand() = runBlocking {
        val peerId = "desktop_clamp_up"
        val snapshot = PlaybackSnapshot(
            deviceId = peerId,
            updatedAtMs = System.currentTimeMillis(),
            queue = emptyList(),
            currentIndex = 0,
            positionMs = 0L,
            isPlaying = true,
            shuffleEnabled = false,
            repeatMode = "none",
            volume = 0.98f
        )
        val peer = ConnectPeerState(peerId, "PC", connected = true, snapshot = snapshot)
        ConnectManager.setPeersForTesting(mapOf(peerId to peer))
        WearableStatePublisher.setActiveTarget(WearConstants.TARGET_TYPE_DESKTOP, peerId, "PC")

        var interceptedValue: Long? = null
        ConnectManager.testCommandDispatcher = { _, _, value ->
            interceptedValue = value
        }

        // 0.98f + 0.05f = 1.03f -> Must be strictly clamped to 1000L
        service.handleMessage(WearConstants.ACTION_VOLUME_UP, ByteArray(0))
        assertEquals(1000L, interceptedValue)

        // At exactly 1.0f -> Must still clamp to 1000L
        val maxPeer = peer.copy(snapshot = snapshot.copy(volume = 1.0f))
        ConnectManager.setPeersForTesting(mapOf(peerId to maxPeer))
        service.handleMessage(WearConstants.ACTION_VOLUME_UP, ByteArray(0))
        assertEquals(1000L, interceptedValue)
    }

    @Test
    fun testDesktopVolumeDownClampsStrictlyAtZero() = runBlocking {
        val peerId = "desktop_clamp_down"
        val snapshot = PlaybackSnapshot(
            deviceId = peerId,
            updatedAtMs = System.currentTimeMillis(),
            queue = emptyList(),
            currentIndex = 0,
            positionMs = 0L,
            isPlaying = true,
            shuffleEnabled = false,
            repeatMode = "none",
            volume = 0.02f
        )
        val peer = ConnectPeerState(peerId, "PC", connected = true, snapshot = snapshot)
        ConnectManager.setPeersForTesting(mapOf(peerId to peer))
        WearableStatePublisher.setActiveTarget(WearConstants.TARGET_TYPE_DESKTOP, peerId, "PC")

        var interceptedValue: Long? = null
        ConnectManager.testCommandDispatcher = { _, _, value ->
            interceptedValue = value
        }

        // 0.02f - 0.05f = -0.03f -> Must be strictly clamped to 0L
        service.handleMessage(WearConstants.ACTION_VOLUME_DOWN, ByteArray(0))
        assertEquals(0L, interceptedValue)

        // At exactly 0.0f -> Must still clamp to 0L
        val zeroPeer = peer.copy(snapshot = snapshot.copy(volume = 0.0f))
        ConnectManager.setPeersForTesting(mapOf(peerId to zeroPeer))
        service.handleMessage(WearConstants.ACTION_VOLUME_DOWN, ByteArray(0))
        assertEquals(0L, interceptedValue)
    }

    @Test
    fun testDesktopSetVolumeSanitizesIeee754SpecialFloats() = runBlocking {
        val peerId = "desktop_specials"
        val peer = ConnectPeerState(peerId, "PC", connected = true)
        ConnectManager.setPeersForTesting(mapOf(peerId to peer))
        WearableStatePublisher.setActiveTarget(WearConstants.TARGET_TYPE_DESKTOP, peerId, "PC")

        val capturedValues = mutableListOf<Long>()
        ConnectManager.testCommandDispatcher = { _, _, value ->
            capturedValues.add(value)
        }

        val testCases = listOf(
            floatToBytes(Float.NaN) to 0L,
            floatToBytes(Float.POSITIVE_INFINITY) to 1000L,
            floatToBytes(Float.NEGATIVE_INFINITY) to 0L,
            floatToBytes(Float.MAX_VALUE) to 1000L,
            floatToBytes(Float.MIN_VALUE) to 0L,
            floatToBytes(-99.0f) to 0L,
            floatToBytes(150.0f) to 1000L,
            ByteArray(0) to 0L,
            Random.nextBytes(7) to 0L
        )

        for ((bytes, expectedVal) in testCases) {
            capturedValues.clear()
            service.handleMessage(WearConstants.ACTION_SET_VOLUME, bytes)
            assertEquals("Expected $expectedVal for test byte payload", 1, capturedValues.size)
            assertEquals(expectedVal, capturedValues.first())
        }
    }

    @Test
    fun testDesktopTargetSelectionRejectsCorruptedPayloads() = runBlocking {
        // Set a valid baseline target
        WearableStatePublisher.setActiveTarget(WearConstants.TARGET_TYPE_DESKTOP, "valid_peer", "Valid PC")

        // Payloads that are null or empty byte arrays should be ignored and preserve existing target
        val emptyPayloads = listOf(
            null,
            ByteArray(0),
            "   ".toByteArray(Charsets.UTF_8)
        )

        for (emptyData in emptyPayloads) {
            var caughtThrowable: Throwable? = null
            try {
                service.handleMessage(WearConstants.ACTION_TARGET_SELECT, emptyData)
            } catch (t: Throwable) {
                caughtThrowable = t
            }
            assertNull("Empty target payload threw exception: $caughtThrowable", caughtThrowable)
            assertEquals(WearConstants.TARGET_TYPE_DESKTOP, WearableStatePublisher.activeTargetType)
            assertEquals("valid_peer", WearableStatePublisher.activeTargetId)
        }

        // Malformed and corrupted payloads must be handled safely without throwing any exceptions
        val malformedPayloads = listOf(
            Random.nextBytes(1),
            Random.nextBytes(32),
            "{\"invalid_json\": true}".toByteArray(Charsets.UTF_8),
            "random text".toByteArray(Charsets.UTF_8),
            ByteArray(100) { 0xFF.toByte() }
        )

        for (malformed in malformedPayloads) {
            var caughtThrowable: Throwable? = null
            try {
                service.handleMessage(WearConstants.ACTION_TARGET_SELECT, malformed)
            } catch (t: Throwable) {
                caughtThrowable = t
            }
            assertNull("Malformed target payload threw exception: $caughtThrowable", caughtThrowable)
        }
    }

    @Test
    fun testDesktopPeerDisconnectMidPlaybackEmitsOfflineState() {
        val peerId = "mid_playback_peer"
        val snapshot = PlaybackSnapshot(
            deviceId = peerId,
            updatedAtMs = System.currentTimeMillis(),
            queue = emptyList(),
            currentIndex = 0,
            positionMs = 30000L,
            isPlaying = true,
            shuffleEnabled = false,
            repeatMode = "none",
            volume = 0.6f
        )
        // Initially connected and playing
        val connectedPeer = ConnectPeerState(peerId, "Studio Mac", connected = true, snapshot = snapshot)
        ConnectManager.setPeersForTesting(mapOf(peerId to connectedPeer))
        WearableStatePublisher.setActiveTarget(WearConstants.TARGET_TYPE_DESKTOP, peerId, "Studio Mac")

        val playingState = WearableStatePublisher.buildCurrentPlaybackState(null)
        assertTrue(playingState.isPlaying)

        // Peer disconnects: offline() sets connected = false
        val disconnectedPeer = connectedPeer.offline()
        ConnectManager.setPeersForTesting(mapOf(peerId to disconnectedPeer))

        val offlineState = WearableStatePublisher.buildCurrentPlaybackState(null)
        assertFalse("Playback state must not be playing after peer disconnects", offlineState.isPlaying)
        assertEquals("Studio Mac", offlineState.targetDevice)
        assertEquals(WearConstants.TARGET_TYPE_DESKTOP, offlineState.targetType)
    }

    @Test
    fun testRapidTargetSwitching500CyclesConcurrencySafety() = runBlocking {
        val peerIds = listOf("desktop_1", "desktop_2", "desktop_3")
        val peers = peerIds.associateWith { ConnectPeerState(it, "PC_$it", connected = true) }
        ConnectManager.setPeersForTesting(peers)

        val exceptions = ConcurrentLinkedQueue<Throwable>()

        val jobs = (0 until 500).map { i ->
            async(Dispatchers.Default) {
                try {
                    if (i % 2 == 0) {
                        val payload = TargetSelectionPayload.phone()
                        service.handleMessage(WearConstants.ACTION_TARGET_SELECT, WearCodecs.encodeTarget(payload))
                    } else {
                        val selectedPeerId = peerIds[i % peerIds.size]
                        val payload = TargetSelectionPayload.desktop(selectedPeerId, "PC_$selectedPeerId")
                        service.handleMessage(WearConstants.ACTION_TARGET_SELECT, WearCodecs.encodeTarget(payload))
                    }
                    val state = WearableStatePublisher.buildCurrentPlaybackState(null)
                    assertNotNull(state)
                } catch (t: Throwable) {
                    exceptions.add(t)
                }
            }
        }

        jobs.awaitAll()
        assertEquals("Zero exceptions expected during rapid target switching, caught: ${exceptions.toList()}", 0, exceptions.size)
    }

    @Test
    fun testDesktopSnapshotMissingOptionalFieldsDoesNotCrash() {
        val peerId = "sparse_peer"
        val sparseSnapshot = PlaybackSnapshot(
            deviceId = peerId,
            updatedAtMs = 0L,
            queue = emptyList(),
            currentIndex = 0,
            positionMs = -500L,
            isPlaying = false,
            shuffleEnabled = false,
            repeatMode = "none",
            volume = null,
            isLiked = null
        )
        val peer = ConnectPeerState(peerId, "", connected = true, snapshot = sparseSnapshot)
        ConnectManager.setPeersForTesting(mapOf(peerId to peer))
        WearableStatePublisher.setActiveTarget(WearConstants.TARGET_TYPE_DESKTOP, peerId, "")

        val state = WearableStatePublisher.buildCurrentPlaybackState(null)

        assertEquals(0L, state.trackId)
        assertEquals("", state.title)
        assertEquals("", state.artist)
        assertEquals("", state.album)
        assertNull(state.artworkUrl)
        assertFalse(state.isPlaying)
        assertFalse(state.isLoading)
        assertEquals(0L, state.positionMs)
        assertEquals(0L, state.durationMs)
        // Default fallback volume for null is 0.5f
        assertEquals(0.5f, state.volume, 0.001f)
        assertEquals("Desktop", state.targetDevice)
        assertEquals(WearConstants.TARGET_TYPE_DESKTOP, state.targetType)
        assertFalse(state.isLiked)
    }

    @Test
    fun testDesktopConcurrentCommandBurst500Messages() = runBlocking {
        val peerId = "burst_peer"
        val peer = ConnectPeerState(
            peerId,
            "Burst PC",
            connected = true,
            snapshot = PlaybackSnapshot(
                deviceId = peerId,
                updatedAtMs = System.currentTimeMillis(),
                queue = emptyList(),
                currentIndex = 0,
                positionMs = 1000L,
                isPlaying = true,
                shuffleEnabled = false,
                repeatMode = "none",
                volume = 0.5f
            )
        )
        ConnectManager.setPeersForTesting(mapOf(peerId to peer))
        WearableStatePublisher.setActiveTarget(WearConstants.TARGET_TYPE_DESKTOP, peerId, "Burst PC")

        ConnectManager.testCommandDispatcher = { _, _, _ ->
            // Simulating high-throughput sink
        }

        val sampleCommands = listOf(
            WearConstants.ACTION_PLAY to ByteArray(0),
            WearConstants.ACTION_PAUSE to ByteArray(0),
            WearConstants.ACTION_PLAY_PAUSE to ByteArray(0),
            WearConstants.ACTION_NEXT to ByteArray(0),
            WearConstants.ACTION_PREVIOUS to ByteArray(0),
            WearConstants.ACTION_VOLUME_UP to ByteArray(0),
            WearConstants.ACTION_VOLUME_DOWN to ByteArray(0),
            WearConstants.ACTION_SET_VOLUME to floatToBytes(0.75f)
        )

        val exceptions = ConcurrentLinkedQueue<Throwable>()

        val jobs = (0 until 500).map { i ->
            async(Dispatchers.Default) {
                try {
                    val (action, data) = sampleCommands[i % sampleCommands.size]
                    service.handleMessage(action, data)
                } catch (t: Throwable) {
                    exceptions.add(t)
                }
            }
        }

        jobs.awaitAll()
        assertEquals("Zero exceptions expected during 500 DESKTOP command burst, caught: ${exceptions.toList()}", 0, exceptions.size)
    }
}
