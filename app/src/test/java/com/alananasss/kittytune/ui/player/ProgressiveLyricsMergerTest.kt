package com.alananasss.kittytune.ui.player

import com.alananasss.kittytune.data.LyricsMatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test
import kotlin.coroutines.CoroutineContext

class ProgressiveLyricsMergerTest {
    private class QueuedWorker : CoroutineDispatcher() {
        val tasks = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.addLast(block) }
        fun runNext() { tasks.removeFirst().run() }
    }
    private val target = LyricsMatcher.Target("Song", "Artist", 180_000)
    private fun hit(id: String, provider: String = "GENIUS", duration: Double = 180.0) =
        UnifiedLyricResult(id, "Song", "Artist", null, duration, true, false, provider)

    @Test(timeout = 10_000) fun `simultaneous providers retain both batches and publish on the caller context`() = runBlocking {
        val worker = QueuedWorker()
        val merger = ProgressiveLyricsMerger(worker)
        var results = emptyList<UnifiedLyricResult>()
        val callerThread = Thread.currentThread()
        val first = launch { merger.merge(listOf(hit("a")), target, { results }, { null }, { true }) {
            assertSame(callerThread, Thread.currentThread()); results = it
        } }
        val second = launch { merger.merge(listOf(hit("b")), target, { results }, { null }, { true }) { results = it } }
        yield()
        assertEquals(1, worker.tasks.size)
        worker.runNext(); yield(); yield()
        assertEquals(listOf("a"), results.map { it.id })
        worker.runNext(); first.join(); second.join()
        assertEquals(listOf("a", "b"), results.map { it.id })
    }

    @Test(timeout = 10_000) fun `a superseded search never publishes a delayed merge`() = runBlocking {
        val worker = QueuedWorker()
        var current = true
        var published = false
        val job = launch { ProgressiveLyricsMerger(worker).merge(listOf(hit("old")), target,
            { emptyList() }, { null }, { current }) { published = true } }
        yield(); current = false; worker.runNext(); job.join()
        assertFalse(published)
    }

    @Test(timeout = 10_000) fun `cancellation during computation cannot publish stale results`() = runBlocking {
        val worker = QueuedWorker()
        var published = false
        val job = launch { ProgressiveLyricsMerger(worker).merge(listOf(hit("old")), target,
            { emptyList() }, { null }, { true }) { published = true } }
        yield(); job.cancel(); worker.runNext(); job.join()
        assertFalse(published)
    }

    @Test(timeout = 10_000) fun `selection changed during computation keeps the newly selected duplicate`() = runBlocking {
        val worker = QueuedWorker()
        val near = hit("near", "LRCLIB")
        val selected = hit("selected", "LRCLIB", 185.0)
        var active: String? = null
        var published = emptyList<UnifiedLyricResult>()
        val job = launch { ProgressiveLyricsMerger(worker).merge(listOf(near, selected), target,
            { emptyList() }, { active }, { true }) { published = it } }
        yield(); active = selected.key; worker.runNext(); yield()
        assertTrue(published.isEmpty())
        assertEquals(1, worker.tasks.size)
        worker.runNext(); job.join()
        assertEquals(listOf(selected), published)
    }
}
