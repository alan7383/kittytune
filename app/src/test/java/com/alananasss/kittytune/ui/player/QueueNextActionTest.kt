package com.alananasss.kittytune.ui.player

import com.alananasss.kittytune.domain.Track
import org.junit.Assert.*
import org.junit.Test

class QueueNextActionTest {
    @Test fun upcomingOccurrenceMovesToTheFrontWithoutAddingACopy() {
        assertEquals(QueueNextAction.Move(8, 4), queueNextAction(10, 3, 8))
        assertEquals(QueueNextAction.Move(4, 4), queueNextAction(10, 3, 4))
    }

    @Test fun playedOccurrenceIsReplayedWithoutRemovingHistory() {
        assertEquals(QueueNextAction.Replay(1), queueNextAction(10, 3, 1))
    }

    @Test fun currentAndStaleSelectionsAreIgnored() {
        for (selection in listOf(-1, 3, 10, Int.MAX_VALUE)) {
            assertEquals(QueueNextAction.None, queueNextAction(10, 3, selection))
        }
        assertEquals(QueueNextAction.None, queueNextAction(0, -1, 0))
        assertEquals(QueueNextAction.None, queueNextAction(10, -1, 1))
    }

    @Test fun everyValidQueuePositionHasTheExpectedAction() {
        for (size in 1..20) for (current in 0 until size) for (selected in 0 until size) {
            val action = queueNextAction(size, current, selected)
            when {
                selected < current -> assertEquals(QueueNextAction.Replay(selected), action)
                selected > current -> assertEquals(QueueNextAction.Move(selected, current + 1), action)
                else -> assertEquals(QueueNextAction.None, action)
            }
        }
    }
    @Test fun replayedOccurrenceStaysSelectedAcrossShuffleOrders() {
        val past = Track(78L, "Song", null, 120_000, null)
        val current = Track(79L, "Current", null, 120_000, null)
        val replay = past.copy()
        val original = listOf(past, current, replay)
        val shuffled = listOf(current, replay, past)
        assertEquals(2, matchingQueueIndex(shuffled, 1, original))
        assertEquals(0, matchingQueueIndex(shuffled, 2, original))
    }
}
