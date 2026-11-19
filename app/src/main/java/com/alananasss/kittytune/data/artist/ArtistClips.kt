package com.alananasss.kittytune.data.artist

import android.util.Log
import com.alananasss.kittytune.data.StreamResolver
import com.alananasss.kittytune.domain.Track
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.models.response.PlayerResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/** A music video on YouTube. */
data class Clip(
    val url: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String?,
    val durationSec: Long,
    val viewCount: Long,
)

object ArtistClips {

    private const val TAG = "ArtistClips"

    const val HOME_CLIP_ARTISTS = 4
    const val HOME_CLIPS_EACH = 3
    const val HOME_CLIPS_MAX = 12

    private val _homeClips = MutableStateFlow<List<Clip>>(emptyList())
    val homeClips = _homeClips.asStateFlow()

    private const val MAX_CLIPS = 10
    private const val MAX_CLIPS_DUO = 14
    private const val ENOUGH_CLIPS = 6
    private const val MIN_CLIP_SEC = 90L
    private const val MAX_CLIP_SEC = 15 * 60L
    private const val CACHE_SIZE = 32
    private const val NEWPIPE_STREAMS_TIMEOUT_MS = 8_000L
    private const val NEWPIPE_GRACE_WITH_4K_MS = 2_000L
    private const val NEWPIPE_GRACE_FOR_4K_MS = 6_000L

    private val NAME_SEPARATOR =
        Regex("""\s*(?:&|,|\s+x\s+|\s+и\s+|\s+and\s+|\s*\+\s*|\s+feat\.?\s+|\s+ft\.?\s+|\s+with\s+)\s*""", RegexOption.IGNORE_CASE)

    internal fun splitNames(credit: String): List<String> =
        credit.split(NAME_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }

    suspend fun loadHomeClips(likes: List<Track>): List<Clip> {
        val artists = likes.mapNotNull { it.displayArtist.takeIf { name -> name.isNotBlank() } }
            .groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }
            .take(HOME_CLIP_ARTISTS).map { it.key }
        if (artists.isEmpty()) return emptyList()
        val clips = withContext(Dispatchers.IO) {
            coroutineScope { artists.map { async { clipsFor(it).take(HOME_CLIPS_EACH) } }.awaitAll().flatten() }
        }.distinctBy { it.url }.take(HOME_CLIPS_MAX)
        if (clips.isNotEmpty()) {
            _homeClips.value = clips
        }
        return clips
    }

    internal const val MAX_HEIGHT = 720

    private val cache = object : LinkedHashMap<String, List<Clip>>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Clip>>) = size > CACHE_SIZE
    }

    suspend fun clipsFor(artistName: String): List<Clip> {
        val key = normalize(artistName)
        if (key.isEmpty()) return emptyList()
        synchronized(cache) { cache[key] }?.let { return it }
        val clips = withContext(Dispatchers.IO) {
            runCatching {
                StreamResolver.ensureExtractorReady()
                val names = namesOf(artistName)
                var found = searchAll(names, "official music video")
                var clips = pickClips(names, found)
                if (clips.size < ENOUGH_CLIPS) {
                    found = found + searchAll(names, "клип")
                    clips = pickClips(names, found)
                }
                clips
            }.onFailure { Log.w(TAG, "Clip search failed for $artistName: ${it.message}") }.getOrNull()
        } ?: return emptyList()
        if (clips.isNotEmpty()) {
            synchronized(cache) { cache[key] = clips }
        }
        return clips
    }

    internal fun namesOf(artistName: String): List<String> {
        val whole = artistName.trim()
        val members = splitNames(whole.replace('/', '&')).filter { it.isNotBlank() }
        return (listOf(whole) + members).distinctBy { normalize(it) }.filter { normalize(it).isNotEmpty() }
    }

    private suspend fun searchAll(names: List<String>, suffix: String): List<Clip> = coroutineScope {
        val perName = names.map { name ->
            async {
                val query = "$name $suffix"
                val fromNewPipe = runCatching { searchViaNewPipe(query) }.getOrDefault(emptyList())
                fromNewPipe.ifEmpty {
                    runCatching { searchViaInnertube(query) }.getOrDefault(emptyList())
                }
            }
        }.awaitAll()
        interleave(perName)
    }

    private fun <T> interleave(lists: List<List<T>>): List<T> {
        val longest = lists.maxOfOrNull { it.size } ?: return emptyList()
        return (0 until longest).flatMap { i -> lists.mapNotNull { it.getOrNull(i) } }
    }

    private fun searchViaNewPipe(query: String): List<Clip> {
        val youtube = ServiceList.YouTube
        val handler = youtube.searchQHFactory.fromQuery(query, listOf("videos"), "")
        return SearchInfo.getInfo(youtube, handler).relatedItems.filterIsInstance<StreamInfoItem>().map { item ->
            Clip(
                url = item.url,
                title = item.name,
                uploader = item.uploaderName.orEmpty(),
                thumbnailUrl = item.thumbnails.maxByOrNull { it.height }?.url,
                durationSec = item.duration,
                viewCount = item.viewCount,
            )
        }
    }

    private suspend fun searchViaInnertube(query: String): List<Clip> {
        val result = YouTube.search(query, YouTube.SearchFilter.FILTER_VIDEO).getOrNull() ?: return emptyList()
        return result.items.filterIsInstance<SongItem>().map { item ->
            Clip(
                url = "https://www.youtube.com/watch?v=${item.id}",
                title = item.title,
                uploader = item.artists.joinToString(", ") { it.name },
                thumbnailUrl = item.thumbnail,
                durationSec = item.duration?.toLong() ?: 0L,
                viewCount = 0L,
            )
        }
    }

    private fun isDirectPlayableUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        if (url.contains("/manifest/") || url.contains("itag=616")) return false
        return true
    }

    private fun extractorStreams(clip: Clip): ClipStreams? {
        StreamResolver.ensureExtractorReady()
        val extractor = ServiceList.YouTube.getStreamExtractor(clip.url)
        extractor.fetchPage()

        val muxed = extractor.videoStreams
            .filter {
                isDirectPlayableUrl(it.content) &&
                    it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
                    !it.isVideoOnly &&
                    it.codec?.startsWith("av01") != true
            }
            .map { it to (it.resolution?.substringBefore('p')?.toIntOrNull() ?: 0) }
            .filter { (_, height) -> height <= MAX_HEIGHT }
            .maxByOrNull { (_, height) -> height }
            ?.first?.content

        val audioCandidates = extractor.audioStreams
            .filter { isDirectPlayableUrl(it.content) && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        val audio = audioCandidates
            .filter { it.format?.mimeType?.startsWith("audio/mp4") == true }
            .maxByOrNull { it.averageBitrate }
            ?.content
            ?: audioCandidates.maxByOrNull { it.averageBitrate }?.content
            ?: extractor.audioStreams.filter { isDirectPlayableUrl(it.content) }.maxByOrNull { it.averageBitrate }?.content

        // Prefer H.264 MP4 when available (<=1080p) and VP9 WebM for 1440p/2160p 4K; exclude AV1.
        val videoOnly = extractor.videoOnlyStreams
            .filter {
                isDirectPlayableUrl(it.content) &&
                    it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
                    it.codec?.startsWith("av01") != true
            }
            .mapNotNull { stream ->
                val url = stream.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val height = stream.resolution?.substringBefore('p')?.toIntOrNull() ?: 0
                if (height <= 0) null
                else Triple(
                    VideoRendition(url = url, height = height, audioUrl = audio),
                    stream.format?.mimeType?.startsWith("video/mp4") == true,
                    stream.bitrate,
                )
            }
            .sortedWith(
                compareByDescending<Triple<VideoRendition, Boolean, Int>> { it.first.height }
                    .thenByDescending { it.second }
                    .thenByDescending { it.third }
            )
            .distinctBy { it.first.height }
            .map { it.first }

        return ClipStreams(
            muxedUrl = muxed ?: extractor.videoStreams.firstOrNull { isDirectPlayableUrl(it.content) }?.content,
            videoOnly = videoOnly,
            audioUrl = audio,
        )
    }

    /** Each rendition carries the audio URL minted by the same client session so video+audio never mismatch. */
    data class VideoRendition(
        val url: String,
        val height: Int,
        val audioUrl: String? = null,
    )

    data class ClipStreams(
        val muxedUrl: String?,
        val videoOnly: List<VideoRendition>,
        val audioUrl: String?,
        val backupVideoOnly: List<VideoRendition> = emptyList(),
        val backupAudioUrl: String? = null,
    )

    suspend fun streams(clip: Clip): ClipStreams? = withContext(Dispatchers.IO) {
        var fromInnertube: ClipStreams? = null
        var fromNewPipe: ClipStreams? = null
        coroutineScope {
            val innertube = async { runCatching { innertubeStreams(clip) }.getOrNull() }
            val newPipe = async { runCatching { newPipeStreams(clip) }.getOrNull() }

            fromInnertube = innertube.await()
            val innertubeFull = fromInnertube != null &&
                fromInnertube!!.videoOnly.isNotEmpty() &&
                fromInnertube!!.audioUrl != null
            val innertubeHas4k = fromInnertube?.videoOnly?.any { it.height >= 2160 } == true

            fromNewPipe = if (innertubeFull && innertubeHas4k) {
                withTimeoutOrNull(NEWPIPE_GRACE_WITH_4K_MS) { newPipe.await() }
                    .also { if (it == null) newPipe.cancel() }
            } else if (innertubeFull) {
                // VISIONOS usually caps at 1080p H.264; give NewPipe up to 6s so 1440p/2160p VP9 can arrive.
                withTimeoutOrNull(NEWPIPE_GRACE_FOR_4K_MS) { newPipe.await() }
                    .also { if (it == null) newPipe.cancel() }
            } else {
                newPipe.await()
            }
        }

        if (fromInnertube == null && fromNewPipe == null) return@withContext null

        val innertubeFull = fromInnertube != null &&
            fromInnertube!!.videoOnly.isNotEmpty() &&
            fromInnertube!!.audioUrl != null
        val newPipeFull = fromNewPipe != null &&
            fromNewPipe!!.videoOnly.isNotEmpty() &&
            fromNewPipe!!.audioUrl != null

        val primaryByHeight = LinkedHashMap<Int, VideoRendition>()
        val backupList = mutableListOf<VideoRendition>()

        if (innertubeFull) {
            for (r in fromInnertube!!.videoOnly) {
                primaryByHeight[r.height] = r
            }
        }
        if (newPipeFull) {
            for (r in fromNewPipe!!.videoOnly) {
                if (r.height !in primaryByHeight) {
                    // Captures 1440p / 2160p (4K) VP9 renditions from NewPipe alongside VISIONOS <=1080p
                    primaryByHeight[r.height] = r
                } else {
                    backupList += r
                }
            }
        }

        val primaryVideo = primaryByHeight.values.sortedByDescending { it.height }
        val backupVideo = backupList.sortedByDescending { it.height }
        val primaryAudio = fromInnertube?.audioUrl ?: fromNewPipe?.audioUrl
        val backupAudio = fromNewPipe?.audioUrl
        val muxedUrl = fromNewPipe?.muxedUrl ?: fromInnertube?.muxedUrl

        Log.d(
            TAG,
            "Resolved streams for '${clip.title}': primaryHeights=${primaryVideo.map { it.height }}, " +
                "backupHeights=${backupVideo.map { it.height }}, hasMuxed=${muxedUrl != null}",
        )

        if (muxedUrl == null && primaryVideo.isEmpty()) return@withContext null
        ClipStreams(
            muxedUrl = muxedUrl,
            videoOnly = primaryVideo,
            audioUrl = primaryAudio,
            backupVideoOnly = backupVideo,
            backupAudioUrl = backupAudio,
        )
    }

    private suspend fun innertubeStreams(clip: Clip): ClipStreams? {
        val videoId = videoIdOf(clip.url) ?: return null
        val response = YouTube.player(videoId)
            .onFailure { Log.w(TAG, "InnerTube player failed for $videoId: ${it.message}") }
            .getOrNull() ?: return null
        if (response.playabilityStatus.status != "OK") {
            Log.w(TAG, "InnerTube playability=${response.playabilityStatus.status} for $videoId")
            return null
        }

        val adaptive = response.streamingData?.adaptiveFormats.orEmpty()
        val audioUrl = adaptive
            .filter { it.isAudio && isDirectPlayableUrl(it.url) && it.mimeType.startsWith("audio/mp4") }
            .maxByOrNull { it.bitrate }
            ?.url
            ?: adaptive
                .filter { it.isAudio && isDirectPlayableUrl(it.url) }
                .maxByOrNull { it.bitrate }
                ?.url

        val videoOnly = adaptive
            .filter {
                !it.isAudio &&
                    isDirectPlayableUrl(it.url) &&
                    (it.height ?: 0) > 0 &&
                    !it.mimeType.contains("av01")
            }
            .sortedWith(
                compareByDescending<PlayerResponse.StreamingData.Format> { it.height ?: 0 }
                    .thenByDescending { it.mimeType.startsWith("video/mp4") }
                    .thenByDescending { it.bitrate }
            )
            .distinctBy { it.height }
            .map { VideoRendition(url = it.url!!, height = it.height!!, audioUrl = audioUrl) }

        val muxedUrl = response.streamingData?.formats.orEmpty()
            .filter { isDirectPlayableUrl(it.url) && it.mimeType.startsWith("video/mp4") && !it.mimeType.contains("av01") }
            .filter { (it.height ?: 0) <= MAX_HEIGHT }
            .maxByOrNull { (it.height ?: 0) * 1_000_000 + it.bitrate }
            ?.url

        if (muxedUrl == null && videoOnly.isEmpty()) return null
        return ClipStreams(muxedUrl = muxedUrl, videoOnly = videoOnly, audioUrl = audioUrl)
    }

    private suspend fun newPipeStreams(clip: Clip): ClipStreams? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(NEWPIPE_STREAMS_TIMEOUT_MS) {
            runCatching { extractorStreams(clip) }
                .onFailure { Log.w(TAG, "NewPipe streams failed for ${clip.url}: ${it.message}") }
                .getOrNull()
        }
    }

    internal fun videoIdOf(url: String): String? =
        Regex("""[?&]v=([A-Za-z0-9_-]{11})""").find(url)?.groupValues?.get(1)
            ?: Regex("""youtu\.be/([A-Za-z0-9_-]{11})""").find(url)?.groupValues?.get(1)
            ?: Regex("""/shorts/([A-Za-z0-9_-]{11})""").find(url)?.groupValues?.get(1)
            ?: Regex("""/embed/([A-Za-z0-9_-]{11})""").find(url)?.groupValues?.get(1)

    internal fun pickClips(names: List<String>, found: List<Clip>): List<Clip> {
        val wanted = names.map { normalize(it) }.filter { it.isNotEmpty() }.toSet()
        val seenSongs = mutableSetOf<String>()
        val seenUrls = mutableSetOf<String>()
        val picked = found.filter { clip ->
            val title = clip.title.lowercase()
            val isTheirs = !TOPIC_CHANNEL.containsMatchIn(clip.uploader) &&
                (isTheirChannel(wanted, clip.uploader) || namesIn(clip.title.substringBefore(" - ", missingDelimiterValue = "")).any { it in wanted })
            isTheirs &&
                clip.durationSec in MIN_CLIP_SEC..MAX_CLIP_SEC &&
                NOT_CLIPS.none { it in title } &&
                seenUrls.add(clip.url) &&
                seenSongs.add(songOf(clip.title))
        }
        val limit = if (wanted.size > 2) MAX_CLIPS_DUO else MAX_CLIPS
        return picked.sortedByDescending { isMarkedAsVideo(it.title) }.take(limit)
    }

    private fun isMarkedAsVideo(title: String): Boolean = VIDEO_MARKS.any { it in title.lowercase() }

    private fun namesIn(artistsPart: String): List<String> =
        splitNames(artistsPart.replace('/', '&')).map { normalize(it) }.filter { it.isNotEmpty() } + normalize(artistsPart)

    private fun isTheirChannel(wanted: Set<String>, uploader: String): Boolean {
        val channel = normalize(uploader)
        return wanted.any { channel == it || channel == it + "vevo" || channel == it + "official" }
    }

    private fun songOf(title: String): String =
        normalize(title.substringAfter(" - ").replace(BRACKETS, "").split(FEATURING).first())

    private fun normalize(text: String) = text.lowercase().filter { it.isLetterOrDigit() }

    private val NOT_CLIPS = listOf(
        "lyric", "official audio", "(audio)", "[audio]", "audio only", "visualizer", "visualiser", "teaser", "trailer",
        "#shorts", "текст", "караоке", "karaoke", "slowed", "sped up", "speed up", "nightcore", "reverb", "8d audio",
        "instrumental", "type beat", "reaction", "реакция", "cover", "кавер",
    )

    private val VIDEO_MARKS = listOf("official video", "music video", "(video)", "[video]", "клип", "(mv)", "[mv]", "official clip")
    private val FEATURING = Regex("""(?i)\s(ft\.?|feat\.?|featuring)\s""")
    private val BRACKETS = Regex("""\(.*?\)|\[.*?]""")
    private val TOPIC_CHANNEL = Regex("""(?i)\s-\stopic$""")
}
