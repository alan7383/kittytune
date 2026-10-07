package com.alananasss.kittytune.data.sync

import kotlin.test.*
import org.junit.Test

class ConnectWireTest {
    @Test fun queuePayloadOmitsApiMetadataAndNegotiatesCompression() {
        val track = com.alananasss.kittytune.domain.Track(id = 42, title = "Music", artworkUrl = "https://example.com/art.jpg",
            durationMs = 120000, user = com.alananasss.kittytune.domain.User(7, "Artist", null,
                description = "Biography".repeat(10000)), description = "Description".repeat(10000),
            source = "soundcloud", permalinkUrl = "https://soundcloud.com/artist/music", secretToken = "private-track")
        val state = PlaybackSnapshot("phone", 1, (1L..500L).map { track.copy(id = it) }, 42, 12345, true, true, "ALL")
        val message = message(kind = "state").copy(state = state)
        val raw = ConnectWire.seal(credentials, message)
        val packed = ConnectWire.seal(credentials, message, compress = true)
        assertTrue(raw.length < 400000, "API descriptions must not be transmitted")
        assertTrue(packed.length < raw.length / 4, "Compress repetitive queue references before encryption")
        for (wire in listOf(raw, packed)) {
            val decoded = assertNotNull(ConnectWire.open(credentials, wire)).state!!
            assertEquals(500, decoded.queue.size)
            assertEquals(ConnectWire.queueVersion(state), ConnectWire.queueVersion(decoded))
            assertEquals(state.currentIndex, decoded.currentIndex)
            assertEquals(state.positionMs, decoded.positionMs)
            val restored = decoded.queue.first()
            assertEquals(track.title, restored.title)
            assertEquals(track.durationMs, restored.durationMs)
            assertEquals(track.artworkUrl, restored.artworkUrl)
            assertEquals(track.permalinkUrl, restored.permalinkUrl)
            assertEquals(track.secretToken, restored.secretToken)
            assertEquals("Artist", restored.displayArtist)
            assertNull(restored.description)
            assertNull(restored.user!!.description)
        }
    }
    private val credentials = ConnectCredentials.derive("phone", "a".repeat(27), "desktop", "b".repeat(27))
    private fun message(n: Long = 1, session: String = "session-1234567890", kind: String = "command") =
        ConnectMessage(kind = kind, sender = "phone", session = session, sequence = n, action = "seek", value = 42345)

    @Test fun bothDevicesDeriveSameRoomAndKey() {
        val reversed = ConnectCredentials.derive("desktop", "b".repeat(27), "phone", "a".repeat(27))
        assertEquals(credentials.room, reversed.room)
        assertEquals(credentials.token, reversed.token)
        assertContentEquals(credentials.key, reversed.key)
        assertNotEquals(credentials.room, credentials.token)
    }
    @Test fun commandsAreAuthenticatedAndEncrypted() {
        val encrypted = ConnectWire.seal(credentials, message())
        assertFalse(encrypted.contains("seek"))
        assertEquals(message(), ConnectWire.open(credentials, encrypted))
        val wrong = ConnectCredentials.derive("phone", "c".repeat(27), "desktop", "b".repeat(27))
        assertNull(ConnectWire.open(wrong, encrypted))
        assertNull(ConnectWire.open(credentials, encrypted.dropLast(4) + "AAAA"))
        assertNotEquals(encrypted, ConnectWire.seal(credentials, message()))
    }
    @Test fun replayAndRetiredSessionsCannotExecuteAgain() {
        val guard = ConnectReplayGuard()
        assertTrue(guard.accept(message(1)))
        assertFalse(guard.accept(message(1)))
        assertTrue(guard.accept(message(3)))
        assertFalse(guard.accept(message(2)))
        assertFalse(guard.accept(message(1, "new-session-1234567")))
        assertTrue(guard.accept(message(1, "new-session-1234567", "hello")))
        assertFalse(guard.accept(message(4, kind = "hello")))
    }
    @Test fun idleBackgroundPhoneHasNoConnection() {
        assertFalse(ConnectPolicy.shouldConnect(true, true, true, false, false))
        assertTrue(ConnectPolicy.shouldConnect(true, true, true, true, false))
        assertTrue(ConnectPolicy.shouldConnect(true, true, true, false, true))
        assertFalse(ConnectPolicy.shouldConnect(true, false, true, true, true))
        assertTrue(ConnectPolicy.shouldConnect(true, true, false, false, false))
    }
    @Test fun timelineUsesMonotonicTimeAndFreezesOnDisconnect() {
        val track = com.alananasss.kittytune.domain.Track(id = 42L, title = "Test", artworkUrl = "",
            durationMs = 180_000L, user = null)
        val state = PlaybackSnapshot("phone", 1L, listOf(track), 0, 5_000L, true, false, "NONE")
        val peer = ConnectPeerState("phone", "Phone", true, "LAN", state,
            receivedAtNanos = System.nanoTime() - 4_000_000_000L)
        assertTrue(peer.position() in 9_000L..9_500L)
        val offline = peer.offline()
        assertFalse(offline.connected)
        assertEquals(offline.snapshot!!.positionMs, offline.position())
        assertEquals(5_000L, peer.copy(snapshot = state.copy(isPlaying = false)).position())
        assertEquals(180_000L, peer.copy(snapshot = state.copy(positionMs = 179_000L)).position())
    }
    @Test fun receivingCommandsDoesNotDisableFollowingThePhone() {
        val selection = ConnectDeviceSelection()
        selection.activateLocalRenderer()
        selection.followActivePeer("phone", localPlaying = true)
        assertNull(selection.selectedDevice)
        // Local playback can finish restoring after the first remote state arrived.
        selection.followActivePeer("phone", localPlaying = false)
        assertEquals("phone", selection.selectedDevice)
        selection.choose(null)
        selection.followActivePeer("phone", localPlaying = false)
        assertNull(selection.selectedDevice)
        selection.activateLocalRenderer()
        selection.followActivePeer("phone", localPlaying = false)
        assertEquals("phone", selection.selectedDevice)
    }
}
