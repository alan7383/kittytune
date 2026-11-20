package com.alananasss.kittytune.data.cover

import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class FeedVisualSource {
    ANIMATED_COVERS,
    SPOTIFY_CANVAS,
    YOUTUBE_SAMPLES,
}

data class FeedVisual(
    val url: String,
    val source: FeedVisualSource,
    val clipStartMs: Long = 0L,
    val clipEndMs: Long = 0L,
    val userAgent: String? = null,
)

object FeedVisualResolver {

    data class Config(
        val animatedCovers: Boolean,
        val spotifyCanvas: Boolean,
        val youtubeSamples: Boolean,
        val priorityRaw: String,
    ) {
        fun mask(): String = "$animatedCovers|$spotifyCanvas|$youtubeSamples|$priorityRaw"
    }

    fun readConfig(prefs: PlayerPreferences): Config = Config(
        animatedCovers = prefs.getFeedAnimatedCoversEnabled(),
        spotifyCanvas = prefs.getFeedSpotifyCanvasEnabled(),
        youtubeSamples = prefs.getFeedYouTubeSamplesEnabled(),
        priorityRaw = prefs.getFeedVisualPriorityRaw(),
    )

    private data class CacheEntry(
        val visual: FeedVisual?,
        val mask: String,
        val atMs: Long,
    )

    private const val CACHE_TTL_MS = 15 * 60 * 1000L
    private const val NEGATIVE_CACHE_TTL_MS = 60 * 1000L
    private val cache = object : LinkedHashMap<Long, CacheEntry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, CacheEntry>): Boolean = size > 48
    }

    private const val HEDGE_DELAY_MS = 1_500L
    private const val VISUAL_DEADLINE_MS = 18_000L

    private class Answer(val visual: FeedVisual?)

    suspend fun resolve(
        track: Track,
        config: Config,
        excludeSources: Set<FeedVisualSource> = emptySet(),
    ): FeedVisual? = withContext(Dispatchers.IO) {
        if (track.title.isNullOrBlank()) return@withContext null
        val mask = config.mask()
        if (excludeSources.isEmpty()) {
            synchronized(cache) {
                cache[track.id]?.let { entry ->
                    val ttl = if (entry.visual != null) CACHE_TTL_MS else NEGATIVE_CACHE_TTL_MS
                    if (entry.mask == mask && System.currentTimeMillis() - entry.atMs < ttl) {
                        return@withContext entry.visual
                    }
                    cache.remove(track.id)
                }
            }
        }

        var finished = false
        val visual = withTimeoutOrNull(VISUAL_DEADLINE_MS) {
            resolveInternal(track, config, excludeSources).also { finished = true }
        }
        if (finished && (excludeSources.isEmpty() || visual != null)) {
            synchronized(cache) { cache[track.id] = CacheEntry(visual, mask, System.currentTimeMillis()) }
        }
        visual
    }

    private suspend fun resolveInternal(
        track: Track,
        config: Config,
        excludeSources: Set<FeedVisualSource>,
    ): FeedVisual? {
        val title = track.title?.trim().orEmpty()
        val artist = track.displayArtist.ifBlank { track.user?.username.orEmpty() }.trim()
        if (title.isBlank() || artist.isBlank()) return null
        val album = track.publisherMetadata?.albumTitle
        val durationSec = (track.actualDurationMs / 1000L).toInt().takeIf { it > 0 }
        val isrc = track.publisherMetadata?.isrc

        val candidates = priorityOrder(config.priorityRaw)
            .filter { sourceEnabled(it, config) && it !in excludeSources }
        if (candidates.isEmpty()) return null

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return try {
            val answers = Channel<Answer>(capacity = Channel.UNLIMITED)
            var launched = 0
            val launchNext: () -> Unit = {
                if (launched < candidates.size) {
                    val source = candidates[launched++]
                    scope.launch {
                        val visual = runCatching {
                            resolveSource(source, title, artist, album, durationSec, isrc)
                        }.getOrNull()
                        try {
                            answers.send(Answer(visual))
                        } catch (_: Exception) {
                        }
                    }
                }
            }

            launchNext()
            var found: FeedVisual? = null
            var settled = 0
            while (found == null) {
                if (launched - settled == 0 && launched >= candidates.size) break
                val answer: Answer? = if (launched < candidates.size) {
                    withTimeoutOrNull(HEDGE_DELAY_MS) { answers.receive() }
                } else {
                    answers.receive()
                }
                if (answer == null) {
                    launchNext()
                } else {
                    settled++
                    if (answer.visual != null) {
                        found = answer.visual
                    } else {
                        launchNext()
                    }
                }
            }
            found
        } finally {
            scope.cancel()
        }
    }

    private fun sourceEnabled(source: FeedVisualSource, config: Config): Boolean = when (source) {
        FeedVisualSource.ANIMATED_COVERS -> config.animatedCovers
        FeedVisualSource.SPOTIFY_CANVAS -> config.spotifyCanvas
        FeedVisualSource.YOUTUBE_SAMPLES -> config.youtubeSamples
    }

    private suspend fun resolveSource(
        source: FeedVisualSource,
        title: String,
        artist: String,
        album: String?,
        durationSec: Int?,
        isrc: String?,
    ): FeedVisual? = when (source) {
        FeedVisualSource.SPOTIFY_CANVAS -> {
            val url = SpotifyCanvasProvider.resolveCanvas(title, artist, album, durationSec, isrc)
            if (!url.isNullOrBlank()) FeedVisual(url, FeedVisualSource.SPOTIFY_CANVAS) else null
        }

        FeedVisualSource.ANIMATED_COVERS -> {
            val resolved = AnimatedCoverResolver.resolve(
                title = title,
                artist = artist,
                album = album,
                durationSeconds = durationSec,
                isrc = isrc,
            )
            val url = resolved?.tallUrl ?: resolved?.squareUrl
            if (!url.isNullOrBlank()) FeedVisual(url, FeedVisualSource.ANIMATED_COVERS) else null
        }

        FeedVisualSource.YOUTUBE_SAMPLES -> {
            val sample = YouTubeSampleProvider.resolveSample(title, artist, album, durationSec)
            if (sample != null) {
                FeedVisual(
                    url = sample.videoUrl,
                    source = FeedVisualSource.YOUTUBE_SAMPLES,
                    clipStartMs = sample.startMs,
                    clipEndMs = sample.endMs,
                    userAgent = sample.userAgent,
                )
            } else null
        }
    }

    fun priorityOrder(raw: String): List<FeedVisualSource> {
        val defaults = listOf(
            FeedVisualSource.SPOTIFY_CANVAS,
            FeedVisualSource.ANIMATED_COVERS,
            FeedVisualSource.YOUTUBE_SAMPLES,
        )
        if (raw.isBlank()) return defaults
        val parsed = raw.split(',')
            .mapNotNull { part ->
                runCatching { FeedVisualSource.valueOf(part.trim()) }.getOrNull()
            }
            .filter { it in defaults }
        return parsed + defaults.filterNot { it in parsed }
    }

    fun priorityRaw(order: List<FeedVisualSource>): String = order.joinToString(",") { it.name }

    fun clearCache() {
        synchronized(cache) { cache.clear() }
    }

    fun invalidate(trackId: Long) {
        val entry = synchronized(cache) { cache.remove(trackId) } ?: return
        val visual = entry.visual ?: return
        when (visual.source) {
            FeedVisualSource.YOUTUBE_SAMPLES -> YouTubeSampleProvider.dropByUrl(visual.url)
            FeedVisualSource.SPOTIFY_CANVAS -> SpotifyCanvasProvider.dropByUrl(visual.url)
            FeedVisualSource.ANIMATED_COVERS -> Unit
        }
    }
}
