package com.alananasss.kittytune.data.vk

import com.alananasss.kittytune.data.DownloadManager
import com.alananasss.kittytune.data.StreamResolver
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs

/** How far an import has got: tracks looked up so far, of how many, and how many were found. */
data class VkImportProgress(val done: Int, val total: Int, val found: Int)

/** What an import made: the new playlist, and the tracks no catalogue had. */
data class VkImportResult(val playlistId: Long, val title: String, val imported: Int, val missing: List<VkTrack>)

/**
 * Turns a VK playlist into a KittyTune playlist.
 */
object VkPlaylistImporter {

    /** Lookups in flight at once: fast enough for a few hundred tracks, gentle on both services. */
    private const val PARALLEL_LOOKUPS = 4

    /** SoundCloud keeps a near tie, since its tracks carry likes, comments and the account's playlists. */
    private const val SOUNDCLOUD_TIE = 0.03f

    private val api by lazy { RetrofitClient.create() }

    suspend fun import(playlist: VkImportedPlaylist, onProgress: (VkImportProgress) -> Unit): VkImportResult = coroutineScope {
        val total = playlist.tracks.size
        val done = AtomicInteger(0)
        val found = AtomicInteger(0)
        onProgress(VkImportProgress(0, total, 0))
        val gate = Semaphore(PARALLEL_LOOKUPS)

        val matches = playlist.tracks.map { vkTrack ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    val match = runCatching { findMatch(vkTrack) }.getOrNull()
                    if (match != null) found.incrementAndGet()
                    onProgress(VkImportProgress(done.incrementAndGet(), total, found.get()))
                    match
                }
            }
        }.awaitAll()

        val seen = HashSet<Long>()
        val tracks = mutableListOf<Track>()
        val missing = mutableListOf<VkTrack>()
        playlist.tracks.forEachIndexed { index, vkTrack ->
            val match = matches[index]
            when {
                match == null -> missing += vkTrack
                seen.add(match.id) -> tracks += match
            }
        }

        val playlistId = -System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            DownloadManager.importPlaylistToLibrary(
                playlist = Playlist(
                    id = playlistId,
                    title = playlist.title.ifBlank { "VK" },
                    artworkUrl = playlist.coverUrl,
                    calculatedArtworkUrl = null,
                    trackCount = tracks.size,
                    user = User(0, playlist.author.ifBlank { null }, null),
                ),
                tracks = tracks,
                syncToCloud = false,
                likePlaylist = false,
            )
        }
        VkImportResult(playlistId, playlist.title, tracks.size, missing)
    }

    /** The best playable copy of [track], or null when nothing is close enough to be it. */
    internal suspend fun findMatch(track: VkTrack): Track? = coroutineScope {
        val query = VkTrackMatcher.query(track)
        val soundCloud = async { soundCloudCandidates(query) }
        val youtube = async { youtubeMusicCandidates(query) }

        val bestSoundCloud = best(track, soundCloud.await())
        var bestYoutube = best(track, youtube.await())
        if (bestSoundCloud == null && bestYoutube == null) {
            bestYoutube = best(track, youtubeMusicCandidates(track.title))
        }
        if (bestSoundCloud == null && bestYoutube == null) {
            bestYoutube = best(track, youtubeVideoCandidates(query))
        }

        when {
            bestSoundCloud == null -> bestYoutube?.first
            bestYoutube == null -> bestSoundCloud.first
            bestSoundCloud.second + SOUNDCLOUD_TIE >= bestYoutube.second -> bestSoundCloud.first
            else -> bestYoutube.first
        }
    }

    private fun best(track: VkTrack, candidates: List<Track>): Pair<Track, Float>? =
        candidates
            .map { it to VkTrackMatcher.score(track, it.asCandidate()) }
            .filter { it.second >= VkTrackMatcher.ACCEPT }
            .maxByOrNull { it.second }

    private suspend fun soundCloudCandidates(query: String): List<Track> = withContext(Dispatchers.IO) {
        runCatching { api.searchTracks(query, limit = 15).collection }.getOrDefault(emptyList())
    }

    private suspend fun youtubeMusicCandidates(query: String): List<Track> = withContext(Dispatchers.IO) {
        val songs = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
            ?.items?.filterIsInstance<SongItem>().orEmpty()
        songs.take(10).map { song ->
            val videoUrl = "https://www.youtube.com/watch?v=${song.id}"
            Track(
                id = abs(videoUrl.hashCode().toLong()).coerceAtLeast(1L),
                title = song.title,
                user = User(0L, song.artists.joinToString(", ") { it.name }, null),
                artworkUrl = song.thumbnail,
                durationMs = (song.duration ?: 0) * 1000L,
                permalinkUrl = videoUrl,
                source = "youtube_music",
            )
        }
    }

    private suspend fun youtubeVideoCandidates(query: String): List<Track> = withContext(Dispatchers.IO) {
        runCatching {
            StreamResolver.ensureExtractorReady()
            val service = ServiceList.YouTube
            SearchInfo.getInfo(service, service.searchQHFactory.fromQuery(query, listOf("videos"), ""))
                .relatedItems.filterIsInstance<StreamInfoItem>()
                .take(8)
                .map { item ->
                    Track(
                        id = abs(item.url.hashCode().toLong()).coerceAtLeast(1L),
                        title = item.name,
                        user = User(0L, item.uploaderName?.removeSuffix(" - Topic") ?: "YouTube", null),
                        artworkUrl = item.thumbnails.firstOrNull()?.url,
                        durationMs = item.duration * 1000L,
                        permalinkUrl = item.url,
                        source = "youtube",
                    )
                }
        }.getOrDefault(emptyList())
    }

    private fun Track.asCandidate() = VkTrackMatcher.Candidate(
        title = title.orEmpty(),
        artist = displayArtist.ifBlank { user?.username.orEmpty() },
        durationSec = ((durationMs ?: 0L) / 1000L).toInt(),
    )
}
