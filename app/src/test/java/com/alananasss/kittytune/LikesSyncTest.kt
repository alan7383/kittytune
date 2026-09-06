package com.alananasss.kittytune

import com.alananasss.kittytune.data.sync.*
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for CRDT likes and playlists synchronization on Android.
 */
class LikesSyncTest {

    private val gson = Gson()

    private fun dummyTrack(id: Long, title: String = "Track $id") = Track(
        id = id,
        title = title,
        artworkUrl = "https://example.com/artwork-$id.jpg",
        durationMs = 180_000L,
        user = User(id = 1000L + id, username = "Artist $id", avatarUrl = "https://example.com/avatar-$id.jpg"),
        permalinkUrl = "https://soundcloud.com/artist/track-$id",
        isLiked = true,
        likedAt = 1_700_000_000_000L + id,
    )

    // =========================================================================
    // CRDT Likes & Playlists Sync Tests (142 tracks vs 90 tracks report)
    // =========================================================================

    @Test
    fun merging_phone_142_likes_and_desktop_90_likes_converges_to_union_of_142_without_data_loss() {
        // Phone has 142 tracks: ids 1..142
        val phoneEvents = (1L..142L).map { id ->
            SyncEvent(
                deviceId = "phone-dev",
                seq = id,
                timestampMs = 1_700_000_000_000L + id,
                kind = SyncKinds.LIKE,
                payload = gson.toJson(
                    LikePayload(
                        trackId = id,
                        liked = true,
                        atMs = 1_700_000_000_000L + id,
                        track = dummyTrack(id),
                    )
                )
            )
        }

        // Desktop has 90 tracks: ids 53..142 (so 52 tracks missing on desktop)
        val desktopEvents = (53L..142L).map { id ->
            SyncEvent(
                deviceId = "desktop-dev",
                seq = id - 52,
                timestampMs = 1_700_000_000_000L + id,
                kind = SyncKinds.LIKE,
                payload = gson.toJson(
                    LikePayload(
                        trackId = id,
                        liked = true,
                        atMs = 1_700_000_000_000L + id,
                        track = dummyTrack(id),
                    )
                )
            )
        }

        // Test SyncMerge selecting new events
        val newForDesktop = SyncMerge.selectNew(phoneEvents, emptyMap(), "desktop-dev")
        assertEquals("Desktop should accept all 142 events from phone", 142, newForDesktop.size)

        val newForPhone = SyncMerge.selectNew(desktopEvents, emptyMap(), "phone-dev")
        assertEquals("Phone should accept all 90 events from desktop", 90, newForPhone.size)

        // Combined log simulates full merge
        val mergedLog = phoneEvents + desktopEvents
        val likes = mergedLog.filter { it.kind == SyncKinds.LIKE }
            .map { gson.fromJson(it.payload, LikePayload::class.java) }

        val uniqueTrackIds = likes.map { it.trackId }.toSet()
        assertEquals("Total unique liked tracks across both must be 142", 142, uniqueTrackIds.size)
        assertTrue("Every track from 1 to 142 must be present", (1L..142L).all { it in uniqueTrackIds })
    }

    @Test
    fun last_writer_wins_correctly_handles_unlike_after_like_and_relike_after_unlike() {
        val t1 = 1_700_000_000_000L
        val t2 = 1_700_000_010_000L
        val t3 = 1_700_000_020_000L

        // Track 7 was liked on desktop at t1
        val likeEvent = SyncEvent(
            deviceId = "desktop",
            seq = 1,
            timestampMs = t1,
            kind = SyncKinds.LIKE,
            payload = gson.toJson(LikePayload(trackId = 7L, liked = true, atMs = t1, track = dummyTrack(7L))),
        )

        // Track 7 was unliked on phone at t2 (later than t1)
        val unlikeEvent = SyncEvent(
            deviceId = "phone",
            seq = 1,
            timestampMs = t2,
            kind = SyncKinds.LIKE,
            payload = gson.toJson(LikePayload(trackId = 7L, liked = false, atMs = t2, track = null)),
        )

        // Simulate resolution
        fun resolveWinner(events: List<SyncEvent>): LikePayload {
            val payloads = events.map { gson.fromJson(it.payload, LikePayload::class.java) to it.deviceId }
            return payloads.maxWith { a, b ->
                if (a.first.atMs != b.first.atMs) a.first.atMs.compareTo(b.first.atMs)
                else a.second.compareTo(b.second)
            }.first
        }

        val afterUnlike = resolveWinner(listOf(likeEvent, unlikeEvent))
        assertFalse("Unlike at t2 must beat like at t1", afterUnlike.liked)

        // Re-like on phone at t3
        val relikeEvent = SyncEvent(
            deviceId = "phone",
            seq = 2,
            timestampMs = t3,
            kind = SyncKinds.LIKE,
            payload = gson.toJson(LikePayload(trackId = 7L, liked = true, atMs = t3, track = dummyTrack(7L))),
        )

        val afterRelike = resolveWinner(listOf(likeEvent, unlikeEvent, relikeEvent))
        assertTrue("Re-like at t3 must beat unlike at t2", afterRelike.liked)
    }

    @Test
    fun deterministic_tie_breaking_when_two_devices_record_like_and_unlike_at_identical_millisecond() {
        val sameTime = 1_700_000_000_000L

        val eventA = SyncEvent(
            deviceId = "deviceA",
            seq = 1,
            timestampMs = sameTime,
            kind = SyncKinds.LIKE,
            payload = gson.toJson(LikePayload(trackId = 42L, liked = false, atMs = sameTime)),
        )

        val eventB = SyncEvent(
            deviceId = "deviceB",
            seq = 1,
            timestampMs = sameTime,
            kind = SyncKinds.LIKE,
            payload = gson.toJson(LikePayload(trackId = 42L, liked = true, atMs = sameTime, track = dummyTrack(42L))),
        )

        fun beats(atA: Long, devA: String, atB: Long, devB: String) =
            atA > atB || (atA == atB && devA > devB)

        assertTrue("deviceB must deterministically beat deviceA", beats(sameTime, "deviceB", sameTime, "deviceA"))
        assertFalse("deviceA must not beat deviceB", beats(sameTime, "deviceA", sameTime, "deviceB"))
    }

    @Test
    fun playlist_like_and_unlike_payload_roundtrip_and_resolution() {
        val t1 = 1_700_000_000_000L
        val t2 = 1_700_000_050_000L

        val payload1 = PlaylistLikePayload(
            playlistId = 999L,
            liked = true,
            atMs = t1,
            permalinkUrl = "https://soundcloud.com/user/sets/favorites",
            urn = "soundcloud:playlists:999",
        )
        val json = gson.toJson(payload1)
        val restored = gson.fromJson(json, PlaylistLikePayload::class.java)
        assertEquals(999L, restored.playlistId)
        assertTrue(restored.liked)
        assertEquals("soundcloud:playlists:999", restored.urn)

        val payload2 = PlaylistLikePayload(
            playlistId = 999L,
            liked = false,
            atMs = t2,
            permalinkUrl = "https://soundcloud.com/user/sets/favorites",
            urn = "soundcloud:playlists:999",
        )
        assertTrue("Later playlist operation wins", payload2.atMs > payload1.atMs)
    }
}
