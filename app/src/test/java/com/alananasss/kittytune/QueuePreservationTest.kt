package com.alananasss.kittytune

import com.alananasss.kittytune.data.QueueHistoryRepository
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.player.PlaybackContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueuePreservationTest {

    private fun createDummyTrack(id: Long, title: String, artist: String = "Test Artist"): Track {
        return Track(
            id = id,
            title = title,
            artworkUrl = "https://example.com/art/$id.jpg",
            durationMs = 180_000L,
            user = User(
                id = id * 10,
                username = artist,
                avatarUrl = null
            )
        )
    }

    private fun areTrackListsEquivalent(list1: List<Track>, list2: List<Track>): Boolean {
        if (list1.size != list2.size) return false
        for (i in list1.indices) {
            if (list1[i].id != list2[i].id) return false
        }
        return true
    }

    private fun isEligibleForAutoArchive(queue: List<Track>, context: PlaybackContext?): Boolean {
        if (queue.isEmpty()) return false
        return queue.size >= 2 || context != null
    }

    @Test
    fun testTrackListsEquivalence() {
        val track1 = createDummyTrack(1L, "Song 1")
        val track2 = createDummyTrack(2L, "Song 2")
        val track3 = createDummyTrack(3L, "Song 3")

        val listA = listOf(track1, track2)
        val listB = listOf(track1, track2)
        val listC = listOf(track2, track1)
        val listD = listOf(track1)

        assertTrue("Identical lists should be equivalent", areTrackListsEquivalent(listA, listB))
        assertFalse("Reordered lists should not be equivalent", areTrackListsEquivalent(listA, listC))
        assertFalse("Different length lists should not be equivalent", areTrackListsEquivalent(listA, listD))
    }

    @Test
    fun testAutoArchiveEligibilityRules() {
        val track1 = createDummyTrack(1L, "Song 1")
        val track2 = createDummyTrack(2L, "Song 2")
        val context = PlaybackContext("album_123", "Favorites Album")

        // Empty queue -> Not eligible
        assertFalse(isEligibleForAutoArchive(emptyList(), null))

        // Single track with no context (browsing single tracks) -> Not eligible (avoids cluttering history)
        assertFalse(isEligibleForAutoArchive(listOf(track1), null))

        // Single track with context (e.g. 1-song playlist) -> Eligible
        assertTrue(isEligibleForAutoArchive(listOf(track1), context))

        // Multi-track queue without context (user created queue on the fly) -> Eligible
        assertTrue(isEligibleForAutoArchive(listOf(track1, track2), null))

        // Multi-track playlist with context -> Eligible
        assertTrue(isEligibleForAutoArchive(listOf(track1, track2), context))
    }

    @Test
    fun testTrackSerializationAndDeserialization() {
        val tracks = listOf(
            createDummyTrack(101L, "Midnight City", "M83"),
            createDummyTrack(102L, "Nightcall", "Kavinsky"),
            createDummyTrack(103L, "Starboy", "The Weeknd")
        )

        val json = com.alananasss.kittytune.utils.AppUtils.gson.toJson(tracks)
        assertNotNull(json)

        val deserialized = QueueHistoryRepository.deserializeTracks(json)
        assertEquals(3, deserialized.size)
        assertEquals(101L, deserialized[0].id)
        assertEquals("Midnight City", deserialized[0].title)
        assertEquals("M83", deserialized[0].displayArtist)
        assertEquals(102L, deserialized[1].id)
        assertEquals(103L, deserialized[2].id)
    }

    @Test
    fun testPlaybackContextSerializationAndDeserialization() {
        val context = PlaybackContext(
            navigationId = "playlist_synthwave",
            displayText = "Synthwave Anthems"
        )

        val json = com.alananasss.kittytune.utils.AppUtils.gson.toJson(context)
        assertNotNull(json)

        val deserialized = QueueHistoryRepository.deserializeContext(json)
        assertNotNull(deserialized)
        assertEquals("playlist_synthwave", deserialized?.navigationId)
        assertEquals("Synthwave Anthems", deserialized?.displayText)

        // Null and blank handling
        assertEquals(null, QueueHistoryRepository.deserializeContext(null))
        assertEquals(null, QueueHistoryRepository.deserializeContext(""))
    }

    @Test
    fun testQueueTitleGenerationLogic() {
        val track1 = createDummyTrack(1L, "Stargazing", "Kygo")
        val track2 = createDummyTrack(2L, "Firestone", "Kygo")
        val queue = listOf(track1, track2)

        fun computeTitle(customTitle: String?, context: PlaybackContext?, q: List<Track>): String {
            return customTitle?.takeIf { it.isNotBlank() }
                ?: context?.displayText?.takeIf { it.isNotBlank() }
                ?: q.firstOrNull()?.let {
                    if (q.size > 1) "${it.title} & ${q.size - 1} more" else it.title
                }
                ?: "Queue (${q.size} tracks)"
        }

        // 1. Explicit custom title takes precedence
        assertEquals("My Custom Mix", computeTitle("My Custom Mix", PlaybackContext(displayText = "Context Title", navigationId = "ctx"), queue))

        // 2. Playback context displayText is used when no custom title
        assertEquals("Context Title", computeTitle(null, PlaybackContext(displayText = "Context Title", navigationId = "ctx"), queue))

        // 3. Fallback to first track + count when no context
        assertEquals("Stargazing & 1 more", computeTitle(null, null, queue))

        // 4. Single track fallback
        assertEquals("Stargazing", computeTitle(null, null, listOf(track1)))
    }

    @Test
    fun testHistoryRetentionTrim() {
        val maxLimit = 30
        val historyIds = (1..45).map { "id_$it" }

        val trimmed = if (historyIds.size > maxLimit) {
            historyIds.take(maxLimit)
        } else {
            historyIds
        }

        val toDelete = if (historyIds.size > maxLimit) {
            historyIds.drop(maxLimit)
        } else {
            emptyList()
        }

        assertEquals(30, trimmed.size)
        assertEquals(15, toDelete.size)
        assertEquals("id_1", trimmed.first())
        assertEquals("id_30", trimmed.last())
        assertEquals("id_31", toDelete.first())
        assertEquals("id_45", toDelete.last())
    }
}
