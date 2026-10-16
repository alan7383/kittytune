package com.alananasss.kittytune.data.artist

import android.util.Log
import com.alananasss.kittytune.data.StreamResolver
import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
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

/**
 * An artist's music videos from YouTube, for watching on their page without leaving the app (tester's list,
 * ported from desktop).
 *
 * A search for the artist's videos returns everyone who sings with them, covers them or reacts to them, so only
 * videos the artist uploaded, or whose title starts with their name, are kept. Lyric videos, audio uploads and
 * teasers are left out: they are not clips.
 *
 * A duo's page is named for both ("Kai Angel & 9mice", "A / B", "A + B"), and a search for that name finds only
 * what they made together. Each member is looked for too, and the clips of all are taken in turn, so neither
 * member's own videos are missing.
 */
object ArtistClips {

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

    /** Names in credits are separated by any of these: "A & B", "A, B", "A x B", "A ft. B", "A and B". */
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

    /** Highest muxed resolution asked for; YouTube rarely offers more than 360p with sound in one file. */
    private const val MAX_HEIGHT = 720

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
                // Artists who sing in Russian rarely title anything "official music video"; their clips are "клип".
                if (clips.size < ENOUGH_CLIPS) {
                    found = found + searchAll(names, "клип")
                    clips = pickClips(names, found)
                }
                clips
            }.onFailure { Log.w("ArtistClips", "Clip search failed for $artistName: ${it.message}") }.getOrNull()
        } ?: return emptyList()
        synchronized(cache) { cache[key] = clips }
        return clips
    }

    /** The name as written, then each member of a duo: "A & B", "A / B", "A + B", "A, B", "A x B". */
    internal fun namesOf(artistName: String): List<String> {
        val whole = artistName.trim()
        val members = splitNames(whole.replace('/', '&')).filter { it.isNotBlank() }
        return (listOf(whole) + members).distinctBy { normalize(it) }.filter { normalize(it).isNotEmpty() }
    }

    /** One search per name, side by side; what comes back is the searches' results in turn, one from each. */
    private suspend fun searchAll(names: List<String>, suffix: String): List<Clip> = coroutineScope {
        val perName = names.map { name -> async { runCatching { search("$name $suffix") }.getOrDefault(emptyList()) } }.awaitAll()
        interleave(perName)
    }

    private fun <T> interleave(lists: List<List<T>>): List<T> {
        val longest = lists.maxOfOrNull { it.size } ?: return emptyList()
        return (0 until longest).flatMap { i -> lists.mapNotNull { it.getOrNull(i) } }
    }

    private fun search(query: String): List<Clip> {
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

    /** A playable address for [clip]: one file with picture and sound together. Expires after a few hours. */
    suspend fun streamUrl(clip: Clip): String? = withContext(Dispatchers.IO) {
        runCatching {
            StreamResolver.ensureExtractorReady()
            val extractor = ServiceList.YouTube.getStreamExtractor(clip.url)
            extractor.fetchPage()
            val progressive = extractor.videoStreams
                .filter { it.url != null && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
                .map { it to (it.resolution.substringBefore('p').toIntOrNull() ?: 0) }
                .filter { (_, height) -> height <= MAX_HEIGHT }
                .maxByOrNull { (_, height) -> height }
                ?.first?.url
            val resolvedUrl = progressive ?: extractor.videoStreams.firstOrNull { it.url != null }?.url
            if (resolvedUrl != null) {
                Log.d("ArtistClips", "Resolved stream for '${clip.title}': ${resolvedUrl.take(80)}...")
            }
            resolvedUrl
        }.onFailure { Log.w("ArtistClips", "No stream for ${clip.url}: ${it.message}") }.getOrNull()
    }

    /**
     * The clips among [found], for [names] (the whole name, then each member): one that an artist uploaded, or whose
     * title starts with one of them. Ones the title says are a video go first, since a clip is what is wanted
     * and a lyric card or a stretched audio upload is not.
     */
    internal fun pickClips(names: List<String>, found: List<Clip>): List<Clip> {
        val wanted = names.map { normalize(it) }.filter { it.isNotEmpty() }.toSet()
        val seenSongs = mutableSetOf<String>()
        val seenUrls = mutableSetOf<String>()
        val picked = found.filter { clip ->
            val title = clip.title.lowercase()
            // "Artist - Topic" is YouTube's own channel of audio uploads over a still cover, never a clip.
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

    /** The artists named in the part of a title before the dash: "A ft. B", "A & B", "A, B". */
    private fun namesIn(artistsPart: String): List<String> =
        splitNames(artistsPart.replace('/', '&')).map { normalize(it) }.filter { it.isNotEmpty() } + normalize(artistsPart)

    private fun isTheirChannel(wanted: Set<String>, uploader: String): Boolean {
        val channel = normalize(uploader)
        return wanted.any { channel == it || channel == it + "vevo" || channel == it + "official" }
    }

    /** The song a video is of, so two versions of one video count once. */
    private fun songOf(title: String): String =
        normalize(title.substringAfter(" - ").replace(BRACKETS, "").split(FEATURING).first())

    private fun normalize(text: String) = text.lowercase().filter { it.isLetterOrDigit() }

    private val NOT_CLIPS = listOf(
        "lyric", "official audio", "(audio)", "[audio]", "audio only", "visualizer", "visualiser", "teaser", "trailer",
        "#shorts", "текст", "караоке", "karaoke", "slowed", "sped up", "speed up", "nightcore", "reverb", "8d audio",
        "instrumental", "type beat", "reaction", "реакция", "cover", "кавер",
    )

    /** Words a title carries when it is a video, not just a song on a still picture. */
    private val VIDEO_MARKS = listOf("official video", "music video", "(video)", "[video]", "клип", "(mv)", "[mv]", "official clip")
    private val FEATURING = Regex("""(?i)\s(ft\.?|feat\.?|featuring)\s""")
    private val BRACKETS = Regex("""\(.*?\)|\[.*?]""")
    private val TOPIC_CHANNEL = Regex("""(?i)\s-\stopic$""")
}
