package com.alananasss.kittytune.data.cover

import android.os.SystemClock
import com.alananasss.kittytune.data.PlaybackTrackKey
import com.alananasss.kittytune.data.playbackKey
import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

internal enum class FeedVisualSource { ANIMATED_COVER, YOUTUBE }
internal data class FeedVisual(
    val url: String,
    val source: FeedVisualSource,
    val startMs: Long = 0,
    val endMs: Long? = null,
)

internal class FeedVisualResolver(
    private val resolveSource: suspend (Track, FeedVisualSource) -> FeedVisual? = ::loadFeedVisual,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    private data class Key(val track: PlaybackTrackKey, val order: List<FeedVisualSource>)
    private data class Entry(val visual: FeedVisual, val time: Long)
    private val cache = object : LinkedHashMap<Key, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Entry>) = size > 48
    }

    suspend fun resolve(
        track: Track,
        order: List<FeedVisualSource>,
        excluded: Set<FeedVisualSource> = emptySet(),
    ): FeedVisual? {
        val sources = order.distinct()
        val key = Key(track.playbackKey(), sources)
        if (excluded.isEmpty()) {
            synchronized(cache) {
                cache[key]?.takeIf { now() - it.time < CACHE_TTL_MS }?.let { return it.visual }
            }
        }
        for (source in sources) {
            if (source in excluded) continue
            val visual = withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                try {
                    resolveSource(track, source)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            } ?: continue
            if (excluded.isEmpty()) synchronized(cache) { cache[key] = Entry(visual, now()) }
            return visual
        }
        return null
    }

    private companion object {
        const val CACHE_TTL_MS = 5 * 60 * 1000L
        const val SOURCE_TIMEOUT_MS = 8_000L
    }
}

private suspend fun loadFeedVisual(track: Track, source: FeedVisualSource): FeedVisual? {
    val title = track.title?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val artist = track.displayArtist.trim().takeIf(String::isNotEmpty) ?: return null
    val duration = (track.actualDurationMs / 1000L).toInt().takeIf { it > 0 }
    return when (source) {
        FeedVisualSource.ANIMATED_COVER -> {
            val cover = AnimatedCoverResolver.resolve(
                title, artist, track.publisherMetadata?.albumTitle, duration, track.publisherMetadata?.isrc
            )
            val url = cover.tallUrl ?: cover.squareUrl ?: return null
            FeedVisual(url, source)
        }
        FeedVisualSource.YOUTUBE -> YouTubeSampleProvider.resolve(title, artist, duration)
    }
}
