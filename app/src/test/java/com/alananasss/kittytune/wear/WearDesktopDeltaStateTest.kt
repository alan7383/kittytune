package com.alananasss.kittytune.wear

import com.alananasss.kittytune.data.sync.PlaybackSnapshot
import com.alananasss.kittytune.data.sync.sanitized
import com.alananasss.kittytune.data.sync.withConnectQueue
import com.alananasss.kittytune.domain.Track
import org.junit.Assert.*
import org.junit.Test

class WearDesktopDeltaStateTest {
    private val previous = PlaybackSnapshot("desktop", 1,
        listOf(Track(1, "First", null, 120_000, null), Track(2, "Second", null, 120_000, null)),
        0, 0, false, false, "NONE", volume = .5f)

    @Test fun playbackAndVolumeDeltasReuseTheValidatedQueue() {
        val delta = previous.copy(queue = emptyList(), currentIndex = 1, isPlaying = true, volume = .8f)
        val restored = delta.withConnectQueue(previous, "queue-v1", "queue-v1")!!
        assertEquals(previous.queue, restored.queue)
        assertEquals(1, restored.currentIndex)
        assertTrue(restored.isPlaying)
        assertEquals(.8f, restored.volume!!, .001f)
    }

    @Test fun deltaCannotReuseAMissingOrDifferentQueue() {
        val delta = previous.copy(queue = emptyList())
        assertNull(delta.withConnectQueue(null, "queue-v1", "queue-v1"))
        assertNull(delta.withConnectQueue(previous, "queue-v2", "queue-v1"))
        assertNull(delta.withConnectQueue(previous, "", ""))
    }

    @Test fun deltaCannotAddressAnInvalidTrackIndex() {
        val delta = previous.copy(queue = emptyList(), currentIndex = 99)
        assertNull(delta.withConnectQueue(previous, "queue-v1", "queue-v1"))
    }

    @Test fun fullSnapshotsRemainStrictAndRejectRawMapEntries() {
        @Suppress("UNCHECKED_CAST")
        val malformed = previous.copy(queue = listOf(mapOf("id" to 1)) as List<Track>)
        assertNull(malformed.withConnectQueue(previous, "queue-v2", "queue-v1"))
        assertNull(previous.copy(queue = emptyList()).sanitized())
        assertEquals(previous, previous.withConnectQueue(null, "queue-v1", ""))
    }
}
