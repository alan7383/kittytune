package com.alananasss.kittytune

import com.alananasss.kittytune.data.sync.PlaybackSnapshot
import com.alananasss.kittytune.data.sync.SyncExchange
import com.alananasss.kittytune.data.sync.sanitized
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class SyncPlaybackTest {
    private val track = Track(
        id = 42L, title = "Test", artworkUrl = "", durationMs = 180_000L,
        user = User(id = 1L, username = "Artist", avatarUrl = ""),
    )

    @Test fun legacyExchangeWithoutPlaybackStillDecodes() {
        val json = """{"deviceId":"desktop","deviceName":"Desktop","marks":{},"events":[]}"""
        assertNull(Gson().fromJson(json, SyncExchange::class.java).playback)
    }

    @Test fun snapshotRoundTripsAndProjectsPosition() {
        val snapshot = PlaybackSnapshot("desktop", 1_000L, listOf(track), 0, 25_000L,
            isPlaying = true, shuffleEnabled = false, repeatMode = "NONE")
        val exchange = SyncExchange("desktop", "Desktop", emptyMap(), emptyList(), playback = snapshot)
        val decoded = Gson().fromJson(Gson().toJson(exchange), SyncExchange::class.java).playback!!
        assertEquals(42L, decoded.queue.single().id)
        assertEquals(30_000L, decoded.projectedPosition(6_000L))
        assertEquals(180_000L, decoded.projectedPosition(999_000L))
        assertEquals(25_000L, decoded.copy(isPlaying = false).projectedPosition(6_000L))
    }

    @Test fun snapshotWithMapQueueIsRejected() {
        @Suppress("UNCHECKED_CAST")
        val corruptQueue = listOf(linkedMapOf("id" to 42L, "title" to "Test")) as List<Track>
        val snapshot = PlaybackSnapshot("desktop", 1_000L, corruptQueue, 0, 0L,
            isPlaying = false, shuffleEnabled = false, repeatMode = "NONE")
        assertNull(snapshot.sanitized())
    }

    @Test fun snapshotWithOutOfRangeIndexIsRejected() {
        val snapshot = PlaybackSnapshot("desktop", 1_000L, listOf(track), 1, 0L,
            isPlaying = false, shuffleEnabled = false, repeatMode = "NONE")
        assertNull(snapshot.sanitized())
    }

    @Test fun queueJsonDecodesThroughConcreteArrayWithoutTypeToken() {
        val json = Gson().toJson(listOf(track))
        val decoded = Gson().fromJson(json, Array<Track>::class.java)
        assertNotNull(decoded)
        assertEquals(42L, decoded!!.single().id)
        assertEquals("Artist", decoded.single().user?.username)
    }

    @Test fun snapshotDeserializationWithJsonAdapterProducesRealTracks() {
        val snapshot = PlaybackSnapshot("desktop", 1_000L, listOf(track), 0, 0L,
            isPlaying = false, shuffleEnabled = false, repeatMode = "NONE")
        val json = Gson().toJson(snapshot)
        val decoded = Gson().fromJson(json, PlaybackSnapshot::class.java)
        assertNotNull(decoded)
        assertEquals(1, decoded.queue.size)
        val item = decoded.queue.first()
        assertEquals(Track::class.java, item.javaClass)
        assertEquals(42L, item.id)
    }

    @Test fun snapshotDeserializationWithCorruptElementsInQueueDropsInvalidElements() {
        val corruptJson = """{
            "deviceId": "desktop",
            "updatedAtMs": 1000,
            "currentIndex": 0,
            "positionMs": 0,
            "isPlaying": false,
            "shuffleEnabled": false,
            "repeatMode": "NONE",
            "queue": [
                {"id": 42, "title": "Valid"},
                {"broken": "no id"},
                "just a string"
            ]
        }"""
        val decoded = Gson().fromJson(corruptJson, PlaybackSnapshot::class.java)
        assertNotNull(decoded)
        assertEquals(1, decoded.queue.size)
        assertEquals(42L, decoded.queue.first().id)
    }
}
