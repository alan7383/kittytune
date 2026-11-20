package com.alananasss.kittytune

import com.alananasss.kittytune.data.together.SharedTrack
import com.alananasss.kittytune.data.together.Together
import com.alananasss.kittytune.data.together.TogetherSyncPlayback
import com.alananasss.kittytune.data.together.TogetherSyncStatus
import com.alananasss.kittytune.data.together.syncStatus
import com.alananasss.kittytune.domain.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TogetherSyncStatusTest {
    private val track = SharedTrack(42, "Nightcall", "Kavinsky", null, 60_000, null, "soundcloud", 0L, null)
    private val room = Together.Room(
        code = "K7M9P3Q4",
        name = "Live",
        hostId = "host",
        nowPlaying = track,
        isPlaying = true,
        positionMs = 10_000,
        stateReceivedAt = 20_000,
        syncStateHostId = "host",
        syncTravelMs = 120,
    )
    private val local = TogetherSyncPlayback(track.id, track.source, 11_120, isPlaying = true)

    @Test
    fun comparesWithExtrapolatedHostPositionAndRequiresMatchingPlayback() {
        assertEquals(TogetherSyncStatus.SYNCED, room.syncStatus(21_000, 12_000) { local })
        assertEquals(TogetherSyncStatus.SYNCED, room.syncStatus(21_000, 12_000) { local.copy(positionMs = 11_870) })
        assertEquals(TogetherSyncStatus.SYNCING, room.syncStatus(21_000, 12_000) { local.copy(positionMs = 11_871) })
        assertEquals(TogetherSyncStatus.SYNCING, room.syncStatus(21_000, 12_000) { local.copy(isPlaying = false) })
        assertEquals(TogetherSyncStatus.SYNCING, room.syncStatus(21_000, 12_000) { local.copy(trackId = 12) })
        assertEquals(TogetherSyncStatus.SYNCING, room.syncStatus(21_000, 12_000) { local.copy(source = "youtube") })
        assertEquals(TogetherSyncStatus.SYNCING, room.syncStatus(21_000, 12_000) { null })
    }

    @Test
    fun waitsForLivePlaybackFromTheCurrentHost() {
        assertEquals(TogetherSyncStatus.WAITING_FOR_HOST, room.syncStatus(32_001, 12_000) { local })
        assertEquals(TogetherSyncStatus.WAITING_FOR_HOST, room.copy(syncStateHostId = null).syncStatus(21_000, 12_000) { local })
        assertEquals(TogetherSyncStatus.WAITING_FOR_HOST, room.copy(hostId = "new-host").syncStatus(21_000, 12_000) { local })
        assertEquals(TogetherSyncStatus.WAITING_FOR_HOST, room.copy(nowPlaying = null).syncStatus(21_000, 12_000) { local })
    }

    @Test
    fun pausedHostPositionStaysFixed() {
        val paused = room.copy(isPlaying = false)
        val localPaused = local.copy(isPlaying = false, positionMs = 10_000)
        assertEquals(TogetherSyncStatus.SYNCED, paused.syncStatus(29_000, 12_000) { localPaused })
    }

    @Test
    fun localTrackIsUnavailableAndPrivatePathsStayOnTheDevice() {
        val localTrack = SharedTrack.of(
            Track(
                id = -15L,
                title = "Local MP3",
                artworkUrl = "file:///music/cover.png",
                durationMs = null,
                user = null,
                permalinkUrl = "file:///music/song.mp3",
            ),
        )
        assertTrue(localTrack.isLocalOnly)
        assertEquals("local", localTrack.source)
        assertNull(localTrack.artworkUrl)
        assertNull(localTrack.permalinkUrl)
        assertFalse(track.isLocalOnly)
        assertFalse(track.copy(id = -15L, source = "youtube").isLocalOnly)
        assertEquals(TogetherSyncStatus.LOCAL_TRACK, room.copy(nowPlaying = localTrack).syncStatus(21_000, 12_000) { null })
    }
}
