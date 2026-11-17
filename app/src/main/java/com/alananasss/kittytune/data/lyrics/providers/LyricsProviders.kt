package com.alananasss.kittytune.data.lyrics.providers

import com.alananasss.kittytune.KittyTuneApp
import com.alananasss.kittytune.data.lyrics.clients.BetterLyricsClient
import com.alananasss.kittytune.data.lyrics.clients.MegalobizClient
import com.alananasss.kittytune.data.lyrics.clients.PaxsenixClient
import com.alananasss.kittytune.data.lyrics.clients.SimpMusicClient
import com.alananasss.kittytune.data.lyrics.clients.UnisonClient
import com.alananasss.kittytune.data.lyrics.clients.YouLyPlusClient
import com.alananasss.kittytune.data.network.GeniusClient
import com.alananasss.kittytune.data.network.LrcLibClient
import com.alananasss.kittytune.data.network.MusixmatchClient
import com.alananasss.kittytune.data.LyricsMatcher
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.models.WatchEndpoint
import com.zionhuang.kugou.KuGou
import java.util.Locale
import kotlin.math.abs

/** Returns a valid 11-char YouTube video ID for [title]+[artist], or null if search fails. */
private suspend fun resolveYouTubeVideoId(
    fallbackId: String,
    title: String,
    artist: String,
    durationSec: Int,
): String? {
    val isYtId = fallbackId.length == 11 && fallbackId.matches(Regex("^[a-zA-Z0-9_-]{11}$"))
    if (isYtId) return fallbackId
    if (title.isBlank()) return null

    val query = if (artist.isNotBlank()) "$title $artist" else title
    val target = LyricsMatcher.Target(
        title = title,
        artist = artist,
        durationMs = durationSec * 1000L,
    )
    return runCatching {
        val songs = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
            .getOrNull()?.items
            ?.filterIsInstance<SongItem>()
            ?: return@runCatching null
        if (songs.isEmpty()) return@runCatching null
        songs.minByOrNull { song ->
            val score = LyricsMatcher.score(
                song.title, song.artists.joinToString(", ") { it.name },
                (song.duration ?: 0).toDouble(), target
            )
            // higher score = better match; invert so minByOrNull picks the best
            val durDiff = if (durationSec > 0 && (song.duration ?: 0) > 0)
                abs((song.duration ?: 0) - durationSec).toFloat() else 0f
            -score + durDiff * 0.001f
        }?.id
    }.getOrNull()
}

object BetterLyricsProvider : LyricsProvider {
    override val id = PreferredLyricsProvider.BETTER_LYRICS
    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> = BetterLyricsClient.getLyrics(title = title, artist = artist, album = album, durationSeconds = duration)
}

object BetterLyricsPortatoProvider : LyricsProvider {
    override val id = PreferredLyricsProvider.BETTER_LYRICS_PORTATO
    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> = BetterLyricsClient.getPortatoLyrics(title = title, artist = artist, album = album, durationSeconds = duration)
}

object YouLyPlusLyricsProvider : LyricsProvider {
    override val id = PreferredLyricsProvider.YOULY_PLUS
    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> = YouLyPlusClient.getLyrics(title = title, artist = artist, album = album, durationSeconds = duration)
}

object LrcLibLyricsProvider : LyricsProvider {
    override val id = PreferredLyricsProvider.LRCLIB
    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> = runCatching {
        val direct = runCatching {
            LrcLibClient.api.getLyrics(
                trackName = title,
                artistName = artist,
                duration = duration.toLong(),
            )
        }.getOrNull()

        val synced = direct?.syncedLyrics?.takeIf { it.isNotBlank() }
        val plain = direct?.plainLyrics?.takeIf { it.isNotBlank() }

        if (synced != null) return@runCatching synced
        if (plain != null) return@runCatching plain

        val search = LrcLibClient.api.searchLyrics("$artist $title".trim())
        val usable = search.filter {
            !it.syncedLyrics.isNullOrBlank() || !it.plainLyrics.isNullOrBlank()
        }
        if (usable.isEmpty()) throw IllegalStateException("Lyrics not found on LrcLib")

        // Closest duration within ±2s wins; otherwise fall back to text similarity
        // (avg title/artist > 0.6, synced preferred), mirroring ArchiveTune.
        val match = if (duration > 0) {
            usable.minByOrNull { abs(it.duration - duration.toDouble()) }
                ?.takeIf { abs(it.duration - duration.toDouble()) <= MAX_DURATION_DELTA_SECONDS }
                ?: usable.bestTextMatch(title, artist)
        } else {
            usable.bestTextMatch(title, artist) ?: usable.firstOrNull { !it.syncedLyrics.isNullOrBlank() }
        } ?: throw IllegalStateException("Lyrics not found on LrcLib")

        match.syncedLyrics?.takeIf { it.isNotBlank() }
            ?: match.plainLyrics?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Lyrics not found on LrcLib")
    }

    private const val MAX_DURATION_DELTA_SECONDS = 2.0
    private const val MIN_TEXT_SIMILARITY = 0.6

    private fun List<com.alananasss.kittytune.data.network.LrcLibResponse>.bestTextMatch(
        title: String,
        artist: String,
    ): com.alananasss.kittytune.data.network.LrcLibResponse? {
        val normTitle = title.trim().lowercase()
        val normArtist = artist.trim().lowercase()
        return maxByOrNull { track ->
            var score = (similarity(normTitle, track.name.trim().lowercase()) +
                similarity(normArtist, track.artistName.trim().lowercase())) / 2.0
            if (!track.syncedLyrics.isNullOrBlank()) score += 0.1
            score
        }?.takeIf { track ->
            (similarity(normTitle, track.name.trim().lowercase()) +
                similarity(normArtist, track.artistName.trim().lowercase())) / 2.0 > MIN_TEXT_SIMILARITY
        }
    }

    private fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        if (a.contains(b) || b.contains(a)) return maxOf(0.8, levenshteinSimilarity(a, b))
        return levenshteinSimilarity(a, b)
    }

    private fun levenshteinSimilarity(a: String, b: String): Double {
        val maxLen = maxOf(a.length, b.length)
        if (maxLen == 0) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / maxLen
    }

    private fun levenshtein(a: String, b: String): Int {
        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                curr[j] = minOf(
                    prev[j] + 1,
                    curr[j - 1] + 1,
                    prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1,
                )
            }
            prev.indices.forEach { prev[it] = curr[it] }
        }
        return prev[b.length]
    }
}

object KuGouLyricsProvider : LyricsProvider {
    override val id = PreferredLyricsProvider.KUGOU
    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> = KuGou.getLyrics(title, artist, duration)
}

object MegalobizLyricsProvider : LyricsProvider {
    override val id = PreferredLyricsProvider.MEGALOBIZ
    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> = MegalobizClient.getLyrics(title = title, artist = artist)
}

object SimpMusicLyricsProvider : LyricsProvider {
    override val id = PreferredLyricsProvider.SIMPMUSIC
    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> = SimpMusicClient.getLyrics(
        videoId = id,
        title = title,
        artist = artist,
        duration = duration,
    )
}

object UnisonLyricsProvider : LyricsProvider {
    override val id = PreferredLyricsProvider.UNISON
    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> = UnisonClient.getLyrics(videoId = id, title = title, artist = artist, album = album, durationSeconds = duration)
}

object PaxsenixAppleMusicLyricsProvider : LyricsProvider {
    override val id = PreferredLyricsProvider.PAXSENIX_APPLE_MUSIC
    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> = PaxsenixClient.getAppleMusicLyrics(title, artist, duration)
}

object PaxsenixSpotifyLyricsProvider : LyricsProvider {
    override val id = PreferredLyricsProvider.PAXSENIX_SPOTIFY
    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> = PaxsenixClient.getSpotifyLyrics(title, artist, duration)
}

object PaxsenixMusixmatchLyricsProvider : LyricsProvider {
    override val id = PreferredLyricsProvider.PAXSENIX_MUSIXMATCH
    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> = PaxsenixClient.getMusixmatchLyrics(title, artist, duration)
}

object YouTubeSubtitleLyricsProvider : LyricsProvider {
    override val id = PreferredLyricsProvider.YOUTUBE_SUBTITLE
    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> = runCatching {
        val ytId = resolveYouTubeVideoId(id, title, artist, duration)
            ?: throw IllegalStateException("Could not resolve YouTube video ID")
        YouTube.transcript(ytId).getOrThrow()
    }
}

object YouTubeLyricsProvider : LyricsProvider {
    override val id = PreferredLyricsProvider.YOUTUBE
    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> = runCatching {
        val ytId = resolveYouTubeVideoId(id, title, artist, duration)
            ?: throw IllegalStateException("Could not resolve YouTube video ID")
        val nextResult = YouTube.next(WatchEndpoint(videoId = ytId)).getOrThrow()
        val lyricsEndpoint = nextResult.lyricsEndpoint ?: throw IllegalStateException("Lyrics endpoint not found")
        YouTube.lyrics(lyricsEndpoint).getOrThrow() ?: throw IllegalStateException("Lyrics unavailable")
    }
}

object MusixmatchLyricsProvider : LyricsProvider {
    override val id = PreferredLyricsProvider.MUSIXMATCH
    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> = runCatching {
        val query = "$artist $title".trim()
        val results = MusixmatchClient.search(KittyTuneApp.instance, query)
        val pick = results.firstOrNull() ?: throw IllegalStateException("Song not found on Musixmatch")
        val data = MusixmatchClient.getLyricsData(
            KittyTuneApp.instance,
            pick.trackId,
            (duration * 1000L).coerceAtLeast(0L),
        )
        if (data.first.isNotEmpty()) {
            data.first.joinToString("\n") { line ->
                val min = line.startTime / 60000L
                val sec = (line.startTime % 60000L) / 1000L
                val ms = (line.startTime % 1000L) / 10L
                val time = String.format(Locale.US, "[%02d:%02d.%02d]", min, sec, ms)
                "$time${line.text}"
            }
        } else {
            data.second ?: throw IllegalStateException("Lyrics unavailable from Musixmatch")
        }
    }
}

object GeniusLyricsProvider : LyricsProvider {
    override val id = PreferredLyricsProvider.GENIUS
    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> = runCatching {
        val query = "$artist $title".trim()
        val pick = GeniusClient.search(query).firstOrNull() ?: throw IllegalStateException("Song not found on Genius")
        GeniusClient.lyrics(pick.id) ?: throw IllegalStateException("Lyrics unavailable on Genius")
    }
}

object LyricsProviders {
    val all: Map<PreferredLyricsProvider, LyricsProvider> = listOf(
        BetterLyricsProvider,
        BetterLyricsPortatoProvider,
        YouLyPlusLyricsProvider,
        LrcLibLyricsProvider,
        KuGouLyricsProvider,
        MegalobizLyricsProvider,
        SimpMusicLyricsProvider,
        UnisonLyricsProvider,
        PaxsenixAppleMusicLyricsProvider,
        PaxsenixSpotifyLyricsProvider,
        PaxsenixMusixmatchLyricsProvider,
        YouTubeSubtitleLyricsProvider,
        YouTubeLyricsProvider,
        MusixmatchLyricsProvider,
        GeniusLyricsProvider,
    ).associateBy { it.id }

    fun get(id: PreferredLyricsProvider): LyricsProvider? = all[id]
}
