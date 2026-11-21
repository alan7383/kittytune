package com.alananasss.kittytune.data.cover

import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedVisualResolverTest {
    @Test
    fun fallbackDoesNotOverwritePreferredVisual() = runBlocking {
        val first = FeedVisual("https://example.test/cover", FeedVisualSource.ANIMATED_COVER)
        val fallback = FeedVisual("https://example.test/video", FeedVisualSource.YOUTUBE)
        val order = listOf(first.source, fallback.source)
        val track = Track(1L, "Track", null, 180_000L, null)
        val resolver = FeedVisualResolver(
            resolveSource = { _, source -> if (source == first.source) first else fallback },
            now = { 1000L },
        )

        assertEquals(first, resolver.resolve(track, order))
        assertEquals(fallback, resolver.resolve(track, order, setOf(first.source)))
        assertEquals(first, resolver.resolve(track, order))
    }

    @Test
    fun samplesRejectDifferentSongsAndTopicUploads() {
        assertTrue(YouTubeSampleProvider.matches("Diaspora", "Lous", "Lous - Diaspora (Official Video)", "Label"))
        assertFalse(YouTubeSampleProvider.matches("Diaspora", "Lous", "Lous - Diaspora (Live)", "Lous"))
        assertFalse(YouTubeSampleProvider.matches("Diaspora", "Lous", "Diaspora", "Lous - Topic"))
    }
}
