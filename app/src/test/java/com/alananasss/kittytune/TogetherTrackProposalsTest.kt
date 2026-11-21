package com.alananasss.kittytune

import com.alananasss.kittytune.data.together.SharedTrack
import com.alananasss.kittytune.data.together.Together
import com.alananasss.kittytune.data.together.TogetherMessage
import com.alananasss.kittytune.data.together.TogetherPlayer
import com.alananasss.kittytune.data.together.TogetherWire
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TogetherTrackProposalsTest {
    @Test
    fun approvedQueueProposalEnqueuesOnceAndLocalTrackCannotBeProposed() = runBlocking {
        Together.resetForTesting()
        val queued = mutableListOf<SharedTrack>()
        val track = SharedTrack(48L, "Nightcall", "Kavinsky", null, 60_000L, null, "soundcloud", 0L, null)
        val local = track.copy(source = "local", id = -15L)
        var room: String? = null
        try {
            Together.start(object : TogetherPlayer {
                override fun sharedCurrent(): SharedTrack? = null
                override fun sharedPositionMs(): Long = 0L
                override fun sharedIsPlaying(): Boolean = false
                override fun sharedUpcoming(): List<SharedTrack> = emptyList()
                override fun sharedAutomix(): Boolean = false
                override fun followHost(
                    track: SharedTrack, positionMs: Long, isPlaying: Boolean,
                    upcoming: List<SharedTrack>?, automix: Boolean,
                ) = Unit
                override fun hostControl(action: String, positionMs: Long) = Unit
                override fun hostPlayNext(track: SharedTrack) {
                    queued.add(track)
                }
                override fun hostPlay(tracks: List<SharedTrack>, index: Int) = Unit
            })

            val code = Together.startLiveBroadcast(lockControls = true)
            room = code
            val now = System.currentTimeMillis()
            Together.handle(code, TogetherMessage.PlayNext("listener", now, local, "Alice"))
            Together.handle(code, TogetherMessage.Suggest("listener", now, local, "Alice"))
            Together.playNext(local, code)
            Together.playNext(track, "WRONG123")
            assertTrue(queued.isEmpty())
            assertTrue(Together.rooms.value.getValue(code).suggestions.isEmpty())

            Together.handle(code, TogetherMessage.PlayNext("listener", now, track, "Alice"))
            val suggestion = Together.rooms.value.getValue(code).suggestions.single()
            assertTrue(suggestion.forQueue)
            assertTrue(queued.isEmpty())

            Together.acceptSuggestion(code, suggestion)
            Together.acceptSuggestion(code, suggestion)
            assertEquals(listOf(track), queued)
            assertFalse(Together.rooms.value.getValue(code).playlist.contains(track))
            assertTrue(Together.rooms.value.getValue(code).suggestions.isEmpty())

            val second = track.copy(id = 49L)
            val third = track.copy(id = 50L)
            val batch = TogetherMessage.PlayNextBatch("listener", now, listOf(second, local, third), "Alice")
            assertEquals(batch, TogetherWire.open(code, TogetherWire.seal(code, batch)))
            Together.handle(code, batch, isSnapshot = true)
            assertTrue(Together.rooms.value.getValue(code).suggestions.isEmpty())
            Together.handle(code, batch)
            Together.handle(code, batch)
            val proposals = Together.rooms.value.getValue(code).suggestions
            assertEquals(listOf(second, third), proposals.map { it.track })
            proposals.forEach { Together.acceptSuggestion(code, it) }
            assertEquals(listOf(track, second, third), queued)

            Together.proposePlaylist(code, listOf(third, local, second).map { it.toTrack() })
            assertEquals(listOf(48L, 49L, 50L, 50L, 49L), queued.map { it.id })
        } finally {
            room?.let { Together.forget(it) }
            Together.resetForTesting()
        }
    }

    @Test
    fun startingLiveDoesNotTruncateSavedPlaylistOrShareLocalSeeds() {
        Together.resetForTesting()
        val track = SharedTrack(1L, "Track", "Artist", null, 60_000L, null, "soundcloud", 0L, null)
        val tracks = (1L..40L).map { track.copy(id = it) }
        val local = track.copy(source = "local", id = -15L)
        val code = Together.startLiveBroadcast(seedTracks = tracks + local)
        try {
            assertEquals(tracks, Together.rooms.value.getValue(code).playlist)
            assertEquals(code, Together.startLiveBroadcast())
            assertEquals(tracks, Together.saved.value.first { it.code == code }.tracks)
        } finally {
            Together.forget(code)
            Together.resetForTesting()
        }
    }

    @Test
    fun retainedPlaylistMergesWithLegacyQueueWithoutReplacingTheLiveQueue() = runBlocking {
        Together.resetForTesting()
        val code = TogetherWire.newCode()
        val track = SharedTrack(1L, "Track", "Artist", null, 60_000L, null, "soundcloud", 0L, null)
        val upcoming = track.copy(id = 2)
        val added = track.copy(id = 3)
        val local = track.copy(id = -15, source = "local")
        val now = System.currentTimeMillis()
        try {
            Together.handle(code, TogetherMessage.ClaimHost("host", now))
            Together.handle(code, TogetherMessage.Queue("host", now, 4, listOf(upcoming), listOf(track), "Shared"))
            val snapshot = TogetherMessage.Playlist("editor", now, listOf(added, track, local), "Shared")
            assertEquals(snapshot, TogetherWire.open(code, TogetherWire.seal(code, snapshot)))
            Together.handle(code, snapshot, isSnapshot = true)
            Together.handle(code, TogetherMessage.Queue("legacy-editor", now, 0, emptyList(), listOf(track), "Shared"))
            val room = Together.rooms.value.getValue(code)
            assertEquals(listOf(track, added), room.playlist)
            assertEquals(listOf(upcoming), room.upcoming)
            assertEquals(4, room.queueVersion)
        } finally {
            Together.resetForTesting()
        }
    }
}
