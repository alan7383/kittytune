package com.alananasss.kittytune.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QueueHistoryWritesTest {
    @Test fun pendingSaveCannotReappearAfterClearAll() = runTest {
        val gate = CompletableDeferred<Unit>()
        val rows = mutableListOf<String>()
        val events = mutableListOf<String>()
        val queue = QueueHistoryWrites(backgroundScope) { throw it }
        queue.submit {
            gate.await() // A long encode or suspended Room insert.
            rows += "old queue"
            events += "save callback"
        }
        runCurrent()
        queue.submit { rows.clear(); events += "clear complete" }
        queue.submit { rows += "new queue"; events += "new callback" }
        runCurrent()
        assertTrue(events.isEmpty())
        gate.complete(Unit)
        runCurrent()
        assertEquals(listOf("save callback", "clear complete", "new callback"), events)
        assertEquals(listOf("new queue"), rows)
    }

    @Test fun failedWriteDoesNotDiscardLaterDeleteOrSave() = runTest {
        val failures = mutableListOf<Exception>()
        val rows = mutableListOf("existing")
        val queue = QueueHistoryWrites(backgroundScope) { failures += it }
        queue.submit { throw IllegalStateException("failed insert") }
        queue.submit { rows.remove("existing") }
        queue.submit { rows += "replacement" }
        runCurrent()
        assertEquals(1, failures.size)
        assertEquals(listOf("replacement"), rows)
    }
}
