package com.alananasss.kittytune.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class PlaybackSetupTest {
    @Test fun `prepared playback never queries storage`() {
        assertNull(PlaybackSetup.offlineKey(true, null) { error("Unexpected database wait") })
    }

    @Test fun `embedded offline key takes precedence and is copied`() {
        val key = byteArrayOf(1, 2, 3)
        val selected = PlaybackSetup.offlineKey(false, key) { error("Unexpected database wait") }
        assertArrayEquals(key, selected)
        assertNotSame(key, selected)
    }

    @Test fun `unresolved legacy playback retains the storage fallback`() {
        var calls = 0
        val key = PlaybackSetup.offlineKey(false, null) { calls++; byteArrayOf(5) }
        assertArrayEquals(byteArrayOf(5), key)
        assertEquals(1, calls)
        assertNull(PlaybackSetup.offlineKey(false, null) { null })
    }

    @Test(timeout = 10_000) fun `blocked cover does not hold up playback setup`() = runBlocking {
        val gate = CompletableDeferred<ByteArray?>()
        var artPublished = false
        val loader = DeferredPlaybackArtwork<Any>(Dispatchers.Unconfined)
        val request = Any()
        val job = loader.load(this, request, { gate.await() }) { item, bytes ->
            assertSame(request, item)
            assertArrayEquals(byteArrayOf(9), bytes)
            artPublished = true
        }
        yield()
        assertFalse(artPublished)
        assertTrue(job.isActive)
        gate.complete(byteArrayOf(9)); job.join()
        assertTrue(artPublished)
    }

    @Test(timeout = 10_000) fun `newer request rejects an older result even for the same track`() = runBlocking {
        data class Request(val track: Long)
        val loader = DeferredPlaybackArtwork<Request>(Dispatchers.Unconfined)
        val old = Request(1)
        val fresh = Request(1)
        val gate = CompletableDeferred<ByteArray?>()
        val published = mutableListOf<Request>()
        val oldJob = loader.load(this, old, { gate.await() }) { item, _ -> published += item }
        yield()
        val freshJob = loader.load(this, fresh, { byteArrayOf(2) }) { item, _ -> published += item }
        gate.complete(byteArrayOf(1)); oldJob.join(); freshJob.join()
        assertEquals(1, published.size)
        assertSame(fresh, published.single())
    }

    @Test(timeout = 10_000) fun `failed and missing artwork leave audio ownership alone`() = runBlocking {
        val loader = DeferredPlaybackArtwork<Any>(Dispatchers.Unconfined)
        var published = false
        loader.load(this, Any(), { throw IllegalStateException("image unavailable") }) { _, _ -> published = true }.join()
        loader.load(this, Any(), { null }) { _, _ -> published = true }.join()
        assertFalse(published)
    }

    @Test(timeout = 10_000) fun `cancelled playback scope cannot publish late artwork`() = runBlocking {
        val gate = CompletableDeferred<ByteArray?>()
        var published = false
        val parent = launch {
            DeferredPlaybackArtwork<Any>(Dispatchers.Unconfined).load(this, Any(), { gate.await() }) { _, _ -> published = true }
        }
        yield(); parent.cancel(); gate.complete(byteArrayOf(1)); parent.join()
        assertFalse(published)
    }
}
