package com.alananasss.kittytune.data.musicimport

import android.content.Context
import com.alananasss.kittytune.audio.providers.AudioProviderOrder
import com.alananasss.kittytune.audio.providers.AudioProviderOrderItem
import com.alananasss.kittytune.data.deezer.DeezerSearchRepository
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.data.qobuz.QobuzSearchRepository
import com.alananasss.kittytune.data.search.ProviderLinks
import com.alananasss.kittytune.data.spotify.SpotifyRepository
import com.alananasss.kittytune.data.tidal.TidalSearchRepository
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.TrackPublisherMetadata
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.utils.AppUtils
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.models.WatchEndpoint
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

enum class PortableMatchConfidence {
    /** The URL named the exact track, or every word of title and artist matched. */
    EXACT,

    /** The best result found, but one field was partial. */
    LIKELY,

    /** Nothing close enough was found. */
    NONE,
}

data class PortableResolvedEntry(
    val entry: PortableTrackEntry,
    val tracks: List<Track> = emptyList(),
    val confidence: PortableMatchConfidence = PortableMatchConfidence.NONE,
    val provider: String? = null,
    val error: String? = null,
) {
    val isMatched: Boolean
        get() = tracks.isNotEmpty() && confidence != PortableMatchConfidence.NONE
}

/**
 * Turns parsed TXT/CSV rows into catalogue tracks.
 *
 * The rules are deliberately conservative:
 *  - a service URL is authoritative and is resolved first;
 *  - otherwise the row is searched in the user's configured provider order;
 *  - a result is accepted only when title and artist are close enough, and
 *    alternate versions (remix, sped up, cover, live...) are penalised when
 *    the imported row did not ask for them.
 *
 * Keeping the provider metadata on the resulting [Track] is what makes the
 * radio and playback work after an import: a Deezer/TIDAL/Qobuz track keeps
 * its own id and permalink instead of being mistaken for a SoundCloud one.
 */
class PortableTrackResolver(
    private val context: Context,
    private val prefs: PlayerPreferences = PlayerPreferences(context),
) {
    private val gson = AppUtils.gson
    private val soundCloudApi by lazy { RetrofitClient.create(context) }

    private val spotifyTrack = Regex(
        """(?:spotify:track:|spotify_track:|open\.spotify\.com/track/)([A-Za-z0-9]+)""",
        RegexOption.IGNORE_CASE,
    )
    private val spotifyAlbum = Regex(
        """(?:spotify:album:|spotify_album:|open\.spotify\.com/album/)([A-Za-z0-9]+)""",
        RegexOption.IGNORE_CASE,
    )
    private val spotifyPlaylist = Regex(
        """(?:spotify:playlist:|spotify_playlist:|open\.spotify\.com/playlist/)([A-Za-z0-9]+)""",
        RegexOption.IGNORE_CASE,
    )
    private val youtubeId = Regex(
        """(?:youtu\.be/|youtube\.com/(?:watch\?[^#]*?v=|shorts/|embed/)|music\.youtube\.com/watch\?[^#]*?v=)([A-Za-z0-9_-]{6,})""",
        RegexOption.IGNORE_CASE,
    )

    suspend fun resolve(entry: PortableTrackEntry): PortableResolvedEntry {
        val direct = try {
            entry.url?.let { resolveDirect(it) }.orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
        if (direct.isNotEmpty()) {
            return PortableResolvedEntry(
                entry = entry,
                tracks = direct.distinctBy { it.id },
                confidence = PortableMatchConfidence.EXACT,
                provider = direct.firstOrNull()?.source ?: "url",
            )
        }

        // A bare URL that could not be resolved is not a title. Searching its
        // text would only return an unrelated page of songs.
        if (entry.title.isBlank()) {
            return PortableResolvedEntry(entry = entry, error = "unresolved_url")
        }

        val query = entry.query.ifBlank { entry.title }
        var best: Candidate? = null
        val order = providerOrder()
        for ((index, provider) in order.withIndex()) {
            val candidates = searchProvider(provider, query)
            candidates.forEach { track ->
                val score = score(entry, track)
                if (score > (best?.score ?: 0.0)) {
                    best = Candidate(track, score, provider.name)
                }
            }
            val bestScore = best?.score ?: 0.0
            // One strong answer is enough. Without one, ask two more providers
            // rather than hammering all five for every row of a large export.
            if (bestScore >= STRONG_MATCH || (index >= 1 && bestScore >= ACCEPT_MATCH)) break
        }

        val match = best
        if (match == null || match.score < ACCEPT_MATCH) {
            return PortableResolvedEntry(entry = entry, error = "not_found")
        }
        return PortableResolvedEntry(
            entry = entry,
            tracks = listOf(match.track),
            confidence = if (match.score >= STRONG_MATCH) {
                PortableMatchConfidence.EXACT
            } else {
                PortableMatchConfidence.LIKELY
            },
            provider = match.provider,
        )
    }

    // ------------------------------------------------------------ direct URLs

    private suspend fun resolveDirect(url: String): List<Track> {
        spotifyTrack.find(url)?.groupValues?.getOrNull(1)?.let { id ->
            SpotifyRepository.getTrack(id)?.toTrack()?.let { return listOf(it) }
        }
        spotifyAlbum.find(url)?.groupValues?.getOrNull(1)?.let { id ->
            SpotifyRepository.getAlbum(id)?.tracks?.map { it.toTrack() }?.takeIf { it.isNotEmpty() }
                ?.let { return it }
        }
        spotifyPlaylist.find(url)?.groupValues?.getOrNull(1)?.let { id ->
            SpotifyRepository.getPlaylist(id)?.tracks?.map { it.toTrack() }?.takeIf { it.isNotEmpty() }
                ?.let { return it }
        }

        ProviderLinks.parse(url)?.let { link ->
            val tracks = when (link.provider) {
                ProviderLinks.Provider.DEEZER -> when (link.kind) {
                    ProviderLinks.Kind.TRACK -> listOfNotNull(DeezerSearchRepository.getTrack(link.id))
                    ProviderLinks.Kind.ALBUM -> DeezerSearchRepository.getAlbum(link.id)?.tracks.orEmpty()
                    ProviderLinks.Kind.PLAYLIST -> DeezerSearchRepository.getPlaylist(link.id)?.tracks.orEmpty()
                    ProviderLinks.Kind.ARTIST -> emptyList()
                }
                ProviderLinks.Provider.TIDAL -> when (link.kind) {
                    ProviderLinks.Kind.TRACK -> listOfNotNull(TidalSearchRepository.getTrack(context, link.id))
                    ProviderLinks.Kind.ALBUM -> TidalSearchRepository.getAlbum(context, link.id)?.tracks.orEmpty()
                    ProviderLinks.Kind.PLAYLIST -> TidalSearchRepository.getPlaylist(context, link.id)?.tracks.orEmpty()
                    ProviderLinks.Kind.ARTIST -> emptyList()
                }
                ProviderLinks.Provider.QOBUZ -> when (link.kind) {
                    ProviderLinks.Kind.TRACK -> listOfNotNull(QobuzSearchRepository.getTrack(context, link.id))
                    ProviderLinks.Kind.ALBUM -> QobuzSearchRepository.getAlbum(context, link.id)?.tracks.orEmpty()
                    ProviderLinks.Kind.PLAYLIST -> QobuzSearchRepository.getPlaylist(context, link.id)?.tracks.orEmpty()
                    ProviderLinks.Kind.ARTIST -> emptyList()
                }
            }
            if (tracks.isNotEmpty()) return tracks
        }

        youtubeId.find(url)?.groupValues?.getOrNull(1)?.let { id ->
            val next = YouTube.next(WatchEndpoint(videoId = id)).getOrNull()
            val song = next?.items?.filterIsInstance<SongItem>()
                ?.firstOrNull { it.id == id }
                ?: next?.items?.filterIsInstance<SongItem>()?.firstOrNull()
            song?.toTrack()?.let { return listOf(it) }
        }

        if (url.contains("soundcloud.com", ignoreCase = true)) {
            val json = soundCloudApi.resolveUrl(url)
            if (json.get("kind")?.asString == "track") {
                return listOfNotNull(gson.fromJson(json, Track::class.java))
            }
        }
        return emptyList()
    }

    // ------------------------------------------------------------ provider search

    private fun providerOrder(): List<AudioProviderOrderItem> {
        val disabled = prefs.getDisabledAudioProviders()
        val configured = prefs.getAudioProviderOrder()
            .filter { it !in disabled }
        return if (configured.isEmpty()) {
            AudioProviderOrder.Default
        } else {
            configured
        }
    }

    private suspend fun searchProvider(
        provider: AudioProviderOrderItem,
        query: String,
    ): List<Track> = runCatching {
        when (provider) {
            AudioProviderOrderItem.QOBUZ ->
                QobuzSearchRepository.search(context, query, limit = 8).tracks

            AudioProviderOrderItem.TIDAL ->
                TidalSearchRepository.search(context, query, limit = 8).tracks

            AudioProviderOrderItem.DEEZER ->
                DeezerSearchRepository.searchTracks(query, limit = 8)

            AudioProviderOrderItem.YOUTUBE_MUSIC -> {
                val result = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                result?.items?.filterIsInstance<SongItem>()?.map { it.toTrack() }.orEmpty()
            }

            AudioProviderOrderItem.SOUNDCLOUD ->
                soundCloudApi.searchTracks(query, limit = 8).collection
        }
    }.getOrDefault(emptyList())

    // ------------------------------------------------------------ scoring

    private data class Candidate(val track: Track, val score: Double, val provider: String)

    private fun score(entry: PortableTrackEntry, track: Track): Double {
        val wantedTitle = normalize(entry.title)
        val foundTitle = normalize(track.title.orEmpty())
        if (wantedTitle.isEmpty() || foundTitle.isEmpty()) return 0.0

        var score = when {
            wantedTitle == foundTitle -> 0.64
            foundTitle.contains(wantedTitle) || wantedTitle.contains(foundTitle) -> 0.50
            else -> {
                val overlap = tokenOverlap(wantedTitle, foundTitle)
                if (overlap >= 0.5) 0.34 * overlap else 0.0
            }
        }

        val wantedArtist = normalize(entry.artist)
        val foundArtist = normalize(track.displayArtist.ifBlank { track.user?.username.orEmpty() })
        if (wantedArtist.isNotEmpty() && foundArtist.isNotEmpty()) {
            val artistOverlap = tokenOverlap(wantedArtist, foundArtist)
            score += when {
                wantedArtist == foundArtist ||
                    foundArtist.contains(wantedArtist) ||
                    wantedArtist.contains(foundArtist) -> 0.33
                artistOverlap > 0.0 -> 0.24 * artistOverlap
                else -> -0.20
            }
        } else if (wantedArtist.isEmpty()) {
            // A title-only row can still be accepted, just never as an exact match.
            score += 0.02
        } else {
            // The row named an artist and the candidate has none: suspicious.
            score -= 0.10
        }

        val wantedDuration = entry.durationMs
        val foundDuration = track.durationMs ?: 0L
        if (wantedDuration != null && wantedDuration > 0 && foundDuration > 0) {
            val difference = abs(wantedDuration - foundDuration)
            score += when {
                difference <= 3_000 -> 0.08
                difference <= 10_000 -> 0.04
                difference > 45_000 -> -0.25
                else -> 0.0
            }
        }

        // An export saying "Song" must not become "Song (Sped Up)" or a cover
        // just because that version happens to rank first.
        val variants = listOf(
            "remix", "sped up", "speed up", "slowed", "reverb", "cover",
            "instrumental", "karaoke", "live", "acoustic", "8d", "nightcore",
        )
        if (variants.any { it in foundTitle && it !in wantedTitle }) score -= 0.22

        return score.coerceIn(0.0, 1.0)
    }

    private fun tokenOverlap(a: String, b: String): Double {
        val left = a.split(' ').filter { it.length > 1 }.toSet()
        val right = b.split(' ').filter { it.length > 1 }.toSet()
        if (left.isEmpty() || right.isEmpty()) return 0.0
        val common = left.intersect(right).size.toDouble()
        return common / maxOf(left.size, right.size)
    }

    private fun normalize(raw: String): String = Normalizer
        .normalize(raw.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    // ------------------------------------------------------------ helpers

    private fun SongItem.toTrack(): Track {
        val displayArtist = artists.joinToString(", ") { it.name }.ifEmpty { "YouTube Music" }
        return Track(
            id = abs(id.hashCode().toLong()),
            title = title,
            artworkUrl = thumbnail,
            durationMs = (duration ?: 0) * 1000L,
            user = User(id = 0L, username = displayArtist, avatarUrl = null),
            publisherMetadata = TrackPublisherMetadata(
                albumTitle = album?.name,
                artist = displayArtist,
                explicit = explicit,
            ),
            permalinkUrl = "https://youtube.com/watch?v=$id",
            source = "youtube",
        )
    }

    private companion object {
        const val ACCEPT_MATCH = 0.50
        const val STRONG_MATCH = 0.78
    }
}
