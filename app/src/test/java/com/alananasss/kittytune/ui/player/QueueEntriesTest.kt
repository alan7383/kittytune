package com.alananasss.kittytune.ui.player

import com.alananasss.kittytune.domain.Track
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class QueueEntriesTest {
    private fun track(id: Long) = Track(id = id, title = "Song $id", artworkUrl = null, durationMs = 180_000, user = null)
    private val queue = (0L..9L).map(::track)

    @Test fun `collapsed section keeps the latest three entries and every upcoming entry`() {
        val items = queueListItems(queue, 7, false)
        assertEquals(QueueListItem.PastHeader(7, false), items.first())
        assertEquals(listOf(4, 5, 6, 7, 8, 9), items.filterIsInstance<QueueListItem.TrackItem>().map { it.queueIndex })
        assertEquals(QueueListItem.UpNextHeader(2), items[5])
    }

    @Test fun `all earlier entries stay under one header when expanded`() {
        val items = queueListItems(queue, 7, true)
        assertEquals(QueueListItem.PastHeader(7, true), items.first())
        assertEquals(queue.indices.toList(), items.filterIsInstance<QueueListItem.TrackItem>().map { it.queueIndex })
        assertEquals(1, items.filterIsInstance<QueueListItem.PastHeader>().size)
    }

    @Test fun `short history is visible below its header`() {
        for (current in 1..3) {
            val items = queueListItems(queue, current, true)
            assertEquals(QueueListItem.PastHeader(current, true), items.first())
            assertEquals(queue.indices.toList(), items.filterIsInstance<QueueListItem.TrackItem>().map { it.queueIndex })
        }
    }

    @Test fun `duplicate ids and restored queues do not affect section membership`() {
        val repeated = List(10) { track(1) }
        val items = queueListItems(repeated, 8, false)
        assertEquals(QueueListItem.PastHeader(8, false), items.first())
        assertEquals(listOf(5, 6, 7, 8, 9), items.filterIsInstance<QueueListItem.TrackItem>().map { it.queueIndex })
    }

    @Test fun `empty queue and first or last track have no empty sections`() {
        assertTrue(queueListItems(emptyList(), -1, false).isEmpty())
        assertFalse(queueListItems(queue, 0, false).any { it is QueueListItem.PastHeader })
        assertFalse(queueListItems(queue, queue.lastIndex, false).any { it is QueueListItem.UpNextHeader })
        assertEquals(queue.size, queueListItems(queue, -1, false).size)
    }

    @Test fun `going backwards recomputes the past prefix`() {
        for (current in queue.indices.reversed()) {
            val visible = queueListItems(queue, current, false).filterIsInstance<QueueListItem.TrackItem>()
            assertEquals(((current - 3).coerceAtLeast(0)..queue.lastIndex).toList(), visible.map { it.queueIndex })
        }
    }

    @Test fun `selected duplicate survives filtering and playlist start`() {
        val tracks = listOf(track(5), track(2), track(5), track(3), track(5))
        assertEquals(3, filteredQueueIndex(tracks, 4) { it.id != 2L })
        assertEquals(-1, filteredQueueIndex(tracks, 1) { it.id != 2L })
        assertEquals(-1, filteredQueueIndex(tracks, -1) { true })
        assertEquals(4, filteredQueueIndex(tracks, 4) { true })
    }

    @Test fun `saved index restores the selected occurrence even after deserialization`() {
        val tracks = listOf(track(5), track(2), track(5), track(3), track(5))
        assertEquals(4, resolveQueueIndex(tracks.map { it.copy() }, 5, 4))
        assertEquals(0, resolveQueueIndex(tracks, 5, -1)) // Legacy preferences have no index.
        assertEquals(1, resolveQueueIndex(tracks, 2, 4)) // Stale or invalid saved index.
        assertEquals(-1, resolveQueueIndex(tracks, 999, 4))
        assertEquals(-1, resolveQueueIndex(tracks, null, 0))
    }

    @Test fun `play next duplicate remains distinct through shuffle and removal`() {
        val first = track(5)
        val playing = track(2)
        val inserted = first.copy()
        val tail = track(3)
        val original = mutableListOf(first, playing, inserted, tail)
        val shuffled = listOf(first, tail, playing, inserted)
        assertEquals(2, matchingQueueIndex(shuffled, 3, original))
        original.removeAt(matchingQueueIndex(shuffled, 0, original))
        assertEquals(listOf(playing, inserted, tail), original)
        assertEquals(1, matchingQueueIndex(shuffled, 3, original))
        assertEquals(3, resolveQueueIndex(shuffled, 5, 3))
    }

    @Test fun `identical references use their occurrence and invalid positions are rejected`() {
        val repeated = track(5)
        val tracks = listOf(repeated, track(2), repeated)
        assertEquals(2, matchingQueueIndex(tracks, 2, tracks))
        assertEquals(-1, matchingQueueIndex(tracks, -1, tracks))
        assertEquals(-1, matchingQueueIndex(tracks, 2, emptyList()))
    }

    @Test fun `random permutations preserve every occurrence in the original order`() {
        val random = Random(77)
        repeat(200) {
            val original = List(30) { track(random.nextLong(5)) }
            val shuffled = original.shuffled(random)
            shuffled.indices.forEach { index ->
                assertSame(shuffled[index], original[matchingQueueIndex(shuffled, index, original)])
            }
        }
    }

    @Test fun `all queue sizes and selected positions partition without loss or duplication`() {
        for (size in 0..50) for (current in -1 until size) for (expanded in listOf(false, true)) {
            val tracks = List(size) { track((it % 3).toLong()) }
            val items = queueListItems(tracks, current, expanded)
            val visible = items.filterIsInstance<QueueListItem.TrackItem>().map { it.queueIndex }
            val first = if (expanded) 0 else (current - 3).coerceAtLeast(0)
            assertEquals((first until size).toList(), visible)
            assertEquals(if (current > 0) 1 else 0, items.filterIsInstance<QueueListItem.PastHeader>().size)
            if (current >= 0) assertTrue(current in visible)
        }
    }
}
