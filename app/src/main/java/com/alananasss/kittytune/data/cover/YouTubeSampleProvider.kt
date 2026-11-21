package com.alananasss.kittytune.data.cover

import android.util.Log
import com.alananasss.kittytune.data.StreamResolver
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.models.YouTubeClient
import com.zionhuang.innertube.models.response.PlayerResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * "Samples"-style video clips: finds the official music video on YouTube using the same proven
 * NewPipe video search as ArtistClips, and returns a muted, looped fragment played through
 * [com.alananasss.kittytune.ui.player.cover.CanvasVideo].
 */
object YouTubeSampleProvider {

    private const val TAG = "YouTubeSampleProvider"

    private const val HOOK_WINDOW_MS = 30_000L
    private const val MIN_WINDOW_MS = 18_000L
    private const val MAX_VIDEO_SEC = 15 * 60
    private const val NEWPIPE_TIMEOUT_MS = 8_000L
    private const val NEWPIPE_SEARCH_TIMEOUT_MS = 6_500L
    private const val RESOLVED_CACHE_TTL_MS = 20 * 60 * 1000L
    private const val NEGATIVE_CACHE_TTL_MS = 45 * 1000L

    private data class CachedSample(val sample: Sample, val atMs: Long)
    private val resolvedCache = ConcurrentHashMap<String, CachedSample>()
    private val negativeCache = ConcurrentHashMap<String, Long>()
    private val failedUrls = ConcurrentHashMap.newKeySet<String>()

    private val BRACKETS_REGEX = Regex("""(\[[^]]*]|\([^)]*\))""")
    private val TRAILING_CREDITS_REGEX = Regex("""(?i)\s+(?:feat\.?|ft\.?|featuring|prod\.?\s*(?:by)?)\s+.*$""")
    private val TOPIC_CHANNEL = Regex("""(?i)\s-\stopic$""")
    private val VERSION_SUFFIX_REGEX = Regex(
        """(?i)\b(remix|edit|acoustic|live|slowed|sped\s*up|speed\s*up|version|mix|demo|intro|outro|part\s*\d*|pt\.?\s*\d*|remaster(?:ed)?|instrumental|clean|explicit|radio|extended|vip|bootleg|flip)\b"""
    )
    private val VIDEO_ID_PATTERNS = listOf(
        Regex("""[?&]v=([A-Za-z0-9_-]{11})"""),
        Regex("""youtu\.be/([A-Za-z0-9_-]{11})"""),
        Regex("""/shorts/([A-Za-z0-9_-]{11})"""),
        Regex("""/embed/([A-Za-z0-9_-]{11})"""),
    )

    data class Sample(
        val videoUrl: String,
        /** Hook window in milliseconds; 0 start / 0 end means "the whole video". */
        val startMs: Long,
        val endMs: Long,
        val userAgent: String? = null,
    )

    fun dropByUrl(url: String) {
        failedUrls.add(url)
        if (failedUrls.size > 64) failedUrls.clear()
        resolvedCache.entries.removeIf { it.value.sample.videoUrl == url }
    }

    suspend fun resolveSample(
        title: String,
        artist: String,
        album: String? = null,
        durationSeconds: Int? = null,
    ): Sample? = withContext(Dispatchers.IO) {
        val (cleanArtist, cleanTitle) = normalizeTrackQuery(artist, title)
        if (cleanTitle.isBlank() || cleanArtist.isBlank()) return@withContext null

        val key = "${cleanTitle.lowercase(Locale.ROOT)}|${cleanArtist.lowercase(Locale.ROOT)}"
        resolvedCache[key]?.let { cached ->
            if (System.currentTimeMillis() - cached.atMs < RESOLVED_CACHE_TTL_MS &&
                cached.sample.videoUrl !in failedUrls
            ) {
                return@withContext cached.sample
            }
            resolvedCache.remove(key)
        }
        negativeCache[key]?.let { neg ->
            if (System.currentTimeMillis() - neg < NEGATIVE_CACHE_TTL_MS) return@withContext null
            negativeCache.remove(key)
        }

        val sample = runCatching {
            val candidates = findVideoCandidates(cleanTitle, cleanArtist, durationSeconds)
            Log.d(TAG, "Candidates for '$cleanArtist - $cleanTitle': ${candidates.size}")
            for (candidate in candidates) {
                val resolved = resolveStream(candidate)
                if (resolved != null) return@runCatching resolved
            }
            null
        }.onFailure {
            Log.w(TAG, "resolveSample failed for '$cleanTitle': ${it.message}")
        }.getOrNull()

        if (sample != null) {
            resolvedCache[key] = CachedSample(sample, System.currentTimeMillis())
            negativeCache.remove(key)
            Log.d(TAG, "Resolved sample for '$cleanArtist - $cleanTitle' (window=${sample.startMs}..${sample.endMs})")
        } else {
            negativeCache[key] = System.currentTimeMillis()
        }
        sample
    }

    /**
     * SoundCloud tracks frequently embed "Artist - Title (prod. ...)" in [rawTitle], or use a
     * suffix like "Title - Radio Edit". Split cleanly without turning a song title into the artist.
     */
    private fun normalizeTrackQuery(rawArtist: String, rawTitle: String): Pair<String, String> {
        var artist = rawArtist.trim()
        var title = rawTitle.trim()

        val dashSplit = Regex("""\s+[-–—]\s+""").split(title, limit = 2)
        if (dashSplit.size == 2 && dashSplit[0].isNotBlank() && dashSplit[1].isNotBlank()) {
            val left = dashSplit[0].trim()
            val right = dashSplit[1].trim()
            val leftWords = wordSet(left)
            val rightWords = wordSet(right)
            val artistWords = wordSet(artist)

            when {
                leftWords.intersect(artistWords).isNotEmpty() -> {
                    artist = left
                    title = right
                }
                rightWords.intersect(artistWords).isNotEmpty() -> {
                    artist = right
                    title = left
                }
                VERSION_SUFFIX_REGEX.containsMatchIn(right) -> {
                    // "Song Name - Radio Edit" -> keep rawArtist, strip version suffix
                    title = left
                }
                else -> {
                    // Archive/repost account: uploader is not the artist, "Real Artist - Song"
                    artist = left
                    title = right
                }
            }
        }

        val cleaned = TRAILING_CREDITS_REGEX.replace(BRACKETS_REGEX.replace(title, " "), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (cleaned.isNotBlank()) {
            title = cleaned
        }
        return artist to title
    }

    // ── Search: NewPipe video search first (same proven path as ArtistClips) ─────────────

    private data class VideoCandidate(val id: String, val score: Double)

    private suspend fun findVideoCandidates(
        title: String,
        artist: String,
        durationSeconds: Int?,
    ): List<VideoCandidate> {
        val seen = mutableSetOf<String>()
        val candidates = mutableListOf<VideoCandidate>()

        fun consider(
            id: String,
            itemTitle: String,
            foundArtist: String,
            foundDuration: Int?,
            isVideoResult: Boolean,
        ) {
            if (!seen.add(id)) return
            if (TOPIC_CHANNEL.containsMatchIn(foundArtist)) return
            if (foundDuration != null && (foundDuration <= 0 || foundDuration > MAX_VIDEO_SEC)) return
            if (isDisqualifiedVariant(itemTitle, title)) return
            if (!artistMatches(artist, foundArtist, itemTitle)) return

            val titleScore = videoTitleSimilarity(title, itemTitle, artist)
            if (titleScore < 0.45) return

            val durationScore = if (durationSeconds != null && durationSeconds > 0 && foundDuration != null && foundDuration > 0) {
                val diff = kotlin.math.abs(durationSeconds - foundDuration)
                when {
                    diff <= 25 -> 1.0
                    diff <= 90 -> 0.60
                    else -> 0.15
                }
            } else 0.7

            val videoMarkBonus = if (isVideoResult && isMarkedAsVideo(itemTitle)) 0.25 else if (isVideoResult) 0.10 else 0.0
            candidates += VideoCandidate(id, titleScore * 0.65 + durationScore * 0.25 + videoMarkBonus)
        }

        // 1. NewPipe YouTube video search FIRST (works reliably on regular youtube.com)
        val firstBatch = searchViaNewPipe("$artist $title official video")
        for (item in firstBatch) {
            val id = extractVideoId(item.url) ?: continue
            consider(
                id = id,
                itemTitle = item.name.orEmpty(),
                foundArtist = item.uploaderName.orEmpty(),
                foundDuration = item.duration.takeIf { it > 0 }?.toInt(),
                isVideoResult = true,
            )
        }

        if (candidates.size < 2) {
            val secondBatch = searchViaNewPipe("$artist $title")
            for (item in secondBatch) {
                val id = extractVideoId(item.url) ?: continue
                consider(
                    id = id,
                    itemTitle = item.name.orEmpty(),
                    foundArtist = item.uploaderName.orEmpty(),
                    foundDuration = item.duration.takeIf { it > 0 }?.toInt(),
                    isVideoResult = true,
                )
            }
        }

        // 2. Fallback to InnerTube YouTube Music video search if NewPipe returned nothing
        if (candidates.isEmpty()) {
            val result = YouTube.search("$artist $title", YouTube.SearchFilter.FILTER_VIDEO)
                .onFailure { Log.w(TAG, "InnerTube video search failed: ${it.message}") }
                .getOrNull()
            if (result != null) {
                for (item in result.items) {
                    if (item !is SongItem) continue
                    val foundArtist = item.artists.joinToString(", ") { it.name }
                    consider(item.id, item.title, foundArtist, item.duration, isVideoResult = true)
                }
            }
        }

        return candidates.sortedByDescending { it.score }.take(4)
    }

    private suspend fun searchViaNewPipe(query: String): List<StreamInfoItem> =
        withTimeoutOrNull(NEWPIPE_SEARCH_TIMEOUT_MS) {
            runCatching {
                StreamResolver.ensureExtractorReady()
                val yt = ServiceList.YouTube
                val handler = yt.searchQHFactory.fromQuery(query, listOf("videos"), "")
                SearchInfo.getInfo(yt, handler).relatedItems.filterIsInstance<StreamInfoItem>()
            }.onFailure {
                Log.w(TAG, "NewPipe search failed ($query): ${it.message}")
            }.getOrDefault(emptyList())
        }.orEmpty()

    private fun extractVideoId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        for (pattern in VIDEO_ID_PATTERNS) {
            pattern.find(url)?.groupValues?.getOrNull(1)?.let { return it }
        }
        return null
    }

    // ── Stream: pick a light H.264 MP4 for the muted background loop ─────────────────────

    private data class ResolvedVideo(val url: String, val durationMs: Long, val userAgent: String?)

    private fun isDirectPlayableUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        if (url in failedUrls) return false
        if (url.contains("/manifest/") || url.contains("itag=616")) return false
        return true
    }

    private suspend fun resolveStream(candidate: VideoCandidate): Sample? {
        val video = resolveVideo(candidate.id) ?: return null
        val hook = hookWindow(video.durationMs)
        return Sample(
            videoUrl = video.url,
            startMs = hook.first,
            endMs = hook.second,
            userAgent = video.userAgent,
        )
    }

    private suspend fun resolveVideo(videoId: String): ResolvedVideo? {
        val response = YouTube.player(videoId)
            .onFailure { Log.w(TAG, "InnerTube player failed for $videoId: ${it.message}") }
            .getOrNull()
        val videoDurationMs = response?.videoDetails?.lengthSeconds?.toLongOrNull()?.times(1000L) ?: 0L
        if (response != null && response.playabilityStatus.status == "OK") {
            val streaming = response.streamingData
            val adaptiveList = streaming?.adaptiveFormats.orEmpty()
                .filter { !it.isAudio && isDirectPlayableUrl(it.url) && !it.mimeType.contains("av01") }

            val qualityOrder = compareBy<PlayerResponse.StreamingData.Format> { it.height ?: 0 }
                .thenBy { it.bitrate }

            val adaptive = adaptiveList
                .filter { it.mimeType.startsWith("video/mp4") && (it.height ?: 0) in 240..720 }
                .maxWithOrNull(qualityOrder)
                ?: adaptiveList
                    .filter { it.mimeType.startsWith("video/mp4") && (it.height ?: 0) in 240..1080 }
                    .minByOrNull { it.height ?: Int.MAX_VALUE }
                ?: adaptiveList
                    .filter { it.mimeType.startsWith("video/webm") && (it.height ?: 0) in 240..720 }
                    .maxWithOrNull(qualityOrder)

            if (adaptive?.url != null) {
                return ResolvedVideo(adaptive.url!!, videoDurationMs, userAgentFor(adaptive.url!!))
            }

            val muxed = streaming?.formats.orEmpty()
                .filter { isDirectPlayableUrl(it.url) && it.mimeType.startsWith("video/mp4") && !it.mimeType.contains("av01") }
                .filter { (it.height ?: 0) in 0..720 }
                .maxWithOrNull(qualityOrder)
            if (muxed?.url != null) {
                return ResolvedVideo(muxed.url!!, videoDurationMs, userAgentFor(muxed.url!!))
            }
        } else if (response != null) {
            Log.w(TAG, "InnerTube playability=${response.playabilityStatus.status} for $videoId, trying NewPipe")
        }
        return newPipeVideo(videoId)
    }

    private fun userAgentFor(url: String): String? = when {
        url.contains("c=VISIONOS") -> YouTubeClient.VISIONOS.userAgent
        url.contains("c=IOS") -> YouTubeClient.IOS.userAgent
        url.contains("c=ANDROID_VR") -> YouTubeClient.ANDROID_VR.userAgent
        YoutubeParsingHelper.isVisionOsStreamingUrl(url) -> YoutubeParsingHelper.getVisionOsUserAgent(null)
        YoutubeParsingHelper.isAndroidStreamingUrl(url) -> YoutubeParsingHelper.getAndroidUserAgent(null)
        else -> null
    }

    private suspend fun newPipeVideo(videoId: String): ResolvedVideo? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(NEWPIPE_TIMEOUT_MS) {
            runCatching {
                StreamResolver.ensureExtractorReady()
                val extractor = ServiceList.YouTube.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
                extractor.fetchPage()
                val realDurationMs = extractor.length.takeIf { it > 0 }?.times(1000L) ?: 0L

                val videoOnlyCandidates = extractor.videoOnlyStreams
                    .filter {
                        isDirectPlayableUrl(it.content) &&
                            it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
                            it.codec?.startsWith("av01") != true
                    }
                    .map { it to (it.resolution.substringBefore('p').toIntOrNull() ?: 0) }
                    .filter { (_, h) -> h in 240..720 }

                val videoOnly = videoOnlyCandidates
                    .filter { (s, _) -> s.format?.mimeType?.startsWith("video/mp4") == true }
                    .maxByOrNull { (_, h) -> h }
                    ?.first?.content
                    ?: videoOnlyCandidates.maxByOrNull { (_, h) -> h }?.first?.content

                val muxed = extractor.videoStreams
                    .filter {
                        isDirectPlayableUrl(it.content) &&
                            it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
                            !it.isVideoOnly &&
                            it.codec?.startsWith("av01") != true
                    }
                    .map { it to (it.resolution.substringBefore('p').toIntOrNull() ?: 0) }
                    .filter { (_, h) -> h in 240..720 }
                    .maxByOrNull { (_, h) -> h }
                    ?.first?.content

                val url = videoOnly ?: muxed ?: return@runCatching null
                ResolvedVideo(url = url, durationMs = realDurationMs, userAgent = userAgentFor(url))
            }.onFailure { Log.w(TAG, "NewPipe sample stream failed for $videoId: ${it.message}") }.getOrNull()
        }
    }

    private fun hookWindow(videoDurationMs: Long): Pair<Long, Long> {
        if (videoDurationMs <= MIN_WINDOW_MS + 2_000L) return 0L to 0L
        val safeEndLimit = (videoDurationMs - 1_500L).coerceAtLeast(MIN_WINDOW_MS)
        val window = minOf(HOOK_WINDOW_MS, safeEndLimit)
        val start = (videoDurationMs * 0.35f).toLong().coerceIn(0L, (safeEndLimit - window).coerceAtLeast(0L))
        return start to (start + window).coerceAtMost(safeEndLimit)
    }

    // ── Matching helpers ─────────────────────────────────────────────────────────────────

    private fun isMarkedAsVideo(title: String): Boolean {
        val t = title.lowercase(Locale.ROOT)
        return VIDEO_MARKS.any { t.contains(it) }
    }

    private fun isDisqualifiedVariant(candidateTitle: String, wantedTitle: String): Boolean {
        val candidate = candidateTitle.lowercase(Locale.ROOT)
        val wanted = wantedTitle.lowercase(Locale.ROOT)
        return DISQUALIFIED_PATTERNS.any { regex ->
            regex.containsMatchIn(candidate) && !regex.containsMatchIn(wanted)
        }
    }

    private fun wordSet(value: String?): Set<String> =
        Normalizer.normalize(value.orEmpty(), Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotBlank() && it !in NOISE_WORDS }
            .toSet()

    private fun videoTitleSimilarity(wantedTitle: String, candidateTitle: String, wantedArtist: String): Double {
        val artistWords = wordSet(wantedArtist)
        val rawWanted = wordSet(wantedTitle)
        if (rawWanted.isEmpty()) return 0.0
        val wanted = (rawWanted - artistWords).ifEmpty { rawWanted }
        val rawCandidate = wordSet(candidateTitle)
        if (rawCandidate.isEmpty()) return 0.0
        val candidate = (rawCandidate - (artistWords - wanted)).ifEmpty { rawCandidate }

        val common = wanted.intersect(candidate).size.toDouble()
        val recall = common / wanted.size
        if (recall < 0.50) return 0.0
        val precision = common / candidate.size
        return recall * 0.75 + precision * 0.25
    }

    private fun artistMatches(wanted: String, foundChannel: String, candidateTitle: String): Boolean {
        val wantedWords = wordSet(wanted)
        if (wantedWords.isEmpty()) return false
        val foundWords = wordSet(foundChannel) + wordSet(candidateTitle)
        if (foundWords.isEmpty()) return false
        return wantedWords.intersect(foundWords).isNotEmpty()
    }

    private val VIDEO_MARKS = listOf(
        "official video", "official music video", "music video", "(video)", "[video]",
        "клип", "(mv)", "[mv]", "official clip", "visualizer",
    )

    private val DISQUALIFIED_PATTERNS = listOf(
        "live", "концерт", "cover", "кавер", "remix", "ремикс", "karaoke", "караоке",
        "instrumental", "инструментал", "reaction", "реакция", "slowed", "sped\\s+up",
        "speed\\s+up", "8d", "nightcore", "текст", "lyrics", "lyric", "lesson", "разбор",
        "tutorial", "guitar", "piano", "acoustic", "акустика", "official\\s+audio", "audio\\s+only",
    ).map { Regex("""(?<![\p{L}\p{N}])$it(?![\p{L}\p{N}])""") }

    private val NOISE_WORDS = setOf(
        "feat", "ft", "featuring", "official", "music", "video", "audio",
        "hd", "4k", "mv", "m", "v", "clip", "клип", "премьера", "официальный",
        "visualizer", "prod", "by", "explicit", "clean", "remastered",
    )
}
