package com.alananasss.kittytune.ui.player

import android.content.Context
import com.alananasss.kittytune.data.DataSaver
import com.alananasss.kittytune.data.LyricsMatcher
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.lyrics.clients.SimpMusicClient
import com.alananasss.kittytune.data.lyrics.providers.LyricsProviders
import com.alananasss.kittytune.data.lyrics.providers.PreferredLyricsProvider
import com.alananasss.kittytune.data.network.GeniusClient
import com.alananasss.kittytune.data.network.LrcLibClient
import com.alananasss.kittytune.data.network.MusixmatchClient
import com.alananasss.kittytune.data.lyrics.clients.UnisonClient
import com.alananasss.kittytune.ui.player.lyrics.LyricLine
import com.alananasss.kittytune.ui.player.lyrics.LyricsUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/** The track a manual lyrics search is for, as far as the providers need to know it. */
data class LyricsSearchTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
)

/**
 * The lyrics search the user runs by hand: every source asked separately, so each one's results can be shown
 * as soon as they arrive instead of after the slowest source has answered.
 */
object ManualLyricsSearch {

    /** How long one source gets. Long enough for LrcLib on a bad day: the others are shown meanwhile. */
    const val PROVIDER_TIMEOUT_MS = 10_000L

    /** The sources to ask for [selection]: one source by name, or every enabled one, in the user's order. */
    fun providersFor(
        selection: String,
        prefs: PlayerPreferences,
        context: Context,
    ): List<PreferredLyricsProvider> {
        if (!selection.equals(ALL, ignoreCase = true)) {
            val chosen = PreferredLyricsProvider.fromName(selection) ?: return emptyList()
            return if (DataSaver.isLyricsProviderAllowed(context, chosen)) listOf(chosen) else emptyList()
        }
        val enabled = enabledProviders(prefs)
        return DataSaver.restrictLyricsProviders(context, enabled)
    }

    /** Every selectable source, Data Saver allowlist applied. Used by the source chips. */
    fun availableProviders(prefs: PlayerPreferences, context: Context): List<PreferredLyricsProvider> {
        val all = (prefs.getLyricsProviderOrder() + PreferredLyricsProvider.entries).distinct()
        val (enabled, disabled) = all.partition { prefs.getLyricsProviderEnabled(it) }
        return DataSaver.restrictLyricsProviders(context, enabled + disabled)
    }

    private fun enabledProviders(prefs: PlayerPreferences): List<PreferredLyricsProvider> {
        val all = (prefs.getLyricsProviderOrder() + PreferredLyricsProvider.entries).distinct()
        return all.filter { prefs.getLyricsProviderEnabled(it) }.ifEmpty { all }
    }

    suspend fun search(
        provider: PreferredLyricsProvider,
        query: String,
        track: LyricsSearchTrack,
    ): List<UnifiedLyricResult> {
        // The query as typed, then with its punctuation spaced and run-together words spelled out: "NEWYORK" found
        // nothing on LrcLib or Musixmatch and "NEW YORK" found it at once (issue #66).
        suspend fun searchAs(variant: String): List<UnifiedLyricResult> = runCatching {
            when (provider) {
                PreferredLyricsProvider.LRCLIB -> searchLrcLib(variant, track)
                PreferredLyricsProvider.GENIUS -> searchGenius(variant)
                PreferredLyricsProvider.MUSIXMATCH -> searchMusixmatch(variant)
                PreferredLyricsProvider.SIMPMUSIC -> searchSimpMusic(variant, track.durationMs)
                else -> searchByTitle(provider, variant, track)
            }
        }.getOrDefault(emptyList())

        for (variant in LyricsMatcher.queryVariants(query, track.title)) {
            val found = searchAs(variant)
            if (found.isNotEmpty()) return found
        }
        // Nothing under any spelling of ours: the one Genius gives the title, which spells out run-together words.
        val respelled = com.alananasss.kittytune.data.lyrics.TitleSpellings.respelled(query) ?: return emptyList()
        return searchAs(respelled)
    }

    /**
     * [incoming] added to [current], with repeats dropped and the whole list ordered: the right song first,
     * then the best timings, then the closest match.
     *
     * LrcLib in particular holds the same song many times over, uploaded by different people with slightly
     * different lengths; only the entry closest to the track's length is kept for each title, artist and kind
     * of timing.
     */
    fun merge(
        current: List<UnifiedLyricResult>,
        incoming: List<UnifiedLyricResult>,
        target: LyricsMatcher.Target,
        activeKey: String? = null,
    ): List<UnifiedLyricResult> = ManualLyricsRanking.merge(current, incoming, target, activeKey)

    fun syncTier(result: UnifiedLyricResult): Int = ManualLyricsRanking.syncTier(result)

    fun cleanPreview(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        // TTML must be parsed as XML, not tag-stripped: its attributes span lines and the
        // unparsed markup would leak into the result list.
        if (LyricsUtils.looksLikeTtml(raw)) {
            val lines = LyricsUtils.parseTtml(raw, 0L)
                .asSequence()
                .map { it.text.trim() }
                .filter { it.isNotBlank() }
                .take(2)
                .toList()
            return lines.joinToString(" • ").ifBlank { null }
        }
        val lines = LyricsUtils.decodeHtmlEntities(raw).lines()
            .map { it.replace(Regex("<[^>]+>"), "").replace(Regex("""\(\d+:\d+\)"""), "").trim() }
            .filter { it.isNotBlank() && !it.startsWith("[") && !it.startsWith("{") }
            .take(2)
        return lines.joinToString(" • ").ifBlank { null }
    }

    const val ALL = "ALL"

    fun isConfidentMatch(result: UnifiedLyricResult, target: LyricsMatcher.Target): Boolean =
        ManualLyricsRanking.isConfidentMatch(result, target)

    fun isConfirmedInstrumental(result: UnifiedLyricResult, target: LyricsMatcher.Target): Boolean =
        ManualLyricsRanking.isConfirmedInstrumental(result, target)

    fun isInstrumental(raw: String?, lines: List<LyricLine>): Boolean {
        val marker = if (lines.isEmpty()) raw else lines.filterNot { it.isInstrumental }
            .joinToString(" ") { it.text }
        return when (marker?.trim()?.removeSurrounding("[", "]")?.trim()?.lowercase()) {
            "instrumental", "instrumental only" -> true
            else -> false
        }
    }

    /** What timings [raw] really has, read from the parsed lines rather than guessed from its characters. */
    private fun timingsOf(raw: String, durationMs: Long): Pair<Boolean, Boolean> {
        val lines = LyricsUtils.parseLyricsContent(raw, durationMs)
        return lines.isNotEmpty() to lines.any { it.words.size > 1 }
    }

    private suspend fun searchLrcLib(query: String, track: LyricsSearchTrack): List<UnifiedLyricResult> =
        LrcLibClient.api.searchLyrics(query).map { hit ->
            val raw = hit.syncedLyrics?.takeIf { it.isNotBlank() }
                ?: hit.plainLyrics
            val (lineSync, wordSync) = if (raw != null) timingsOf(raw, track.durationMs) else (false to false)
            UnifiedLyricResult(
                id = hit.id.toString(),
                name = hit.name,
                artistName = hit.artistName,
                albumName = hit.albumName,
                durationSec = hit.duration,
                hasLineSync = lineSync,
                hasWordSync = wordSync,
                provider = "LRCLIB",
                rawContent = raw,
                previewText = cleanPreview(hit.plainLyrics ?: raw),
                noLyrics = hit.instrumental,
                instrumental = hit.instrumental,
            )
        }

    private suspend fun searchGenius(query: String): List<UnifiedLyricResult> =
        GeniusClient.search(query).map {
            UnifiedLyricResult(
                id = it.id.toString(),
                name = it.title ?: "",
                artistName = it.artist,
                albumName = it.releaseDate,
                durationSec = 0.0,
                hasLineSync = false,
                hasWordSync = false,
                provider = "GENIUS",
            )
        }

    private suspend fun searchMusixmatch(query: String): List<UnifiedLyricResult> =
        MusixmatchClient.search(query).map {
            UnifiedLyricResult(
                id = it.trackId.toString(),
                name = it.trackName,
                artistName = it.artistName,
                albumName = it.albumName,
                durationSec = it.trackLength.toDouble(),
                hasLineSync = it.hasSubtitles == 1,
                hasWordSync = it.hasRichSync == 1,
                provider = "MUSIXMATCH",
                noLyrics = it.instrumental == 1 ||
                    (it.hasLyrics == 0 && it.hasSubtitles == 0 && it.hasRichSync == 0),
                instrumental = it.instrumental == 1,
            )
        }

    internal suspend fun searchSimpMusic(
        query: String,
        durationMs: Long,
        target: LyricsMatcher.Target? = null,
    ): List<UnifiedLyricResult> = coroutineScope {
        val recordings = SimpMusicClient.search(query)
            .mapNotNull { hit -> hit.videoId?.takeIf(String::isNotBlank)?.let { it to hit } }
            .distinctBy { (videoId, _) -> videoId }
            .filter { (_, hit) -> target == null || (
                LyricsMatcher.isAcceptable(hit.title, hit.artist, target) &&
                    LyricsMatcher.durationMatches((hit.duration ?: 0).toDouble(), durationMs)
                ) }
        recordings.take(10).map { (videoId, hit) ->
            async {
                val raw = SimpMusicClient.lyricsText(hit)
                    ?: SimpMusicClient.getLyricsByVideoId(videoId)
                        .sortedBy { kotlin.math.abs((it.duration ?: 0) - (hit.duration ?: 0)) }
                        .firstNotNullOfOrNull(SimpMusicClient::lyricsText)
                    ?: return@async null
                val lines = LyricsUtils.parseLyricsContent(raw, durationMs)
                if (lines.isEmpty() && LyricsUtils.looksLikeTtml(raw)) return@async null
                val instrumental = isInstrumental(raw, lines)
                UnifiedLyricResult(
                    id = videoId,
                    name = hit.title ?: query,
                    artistName = hit.artist.orEmpty(),
                    albumName = hit.album,
                    durationSec = (hit.duration ?: 0).toDouble(),
                    hasLineSync = lines.isNotEmpty() && !instrumental,
                    hasWordSync = lines.any { it.words.size > 1 } && !instrumental,
                    provider = "SIMPMUSIC",
                    rawContent = raw,
                    previewText = cleanPreview(raw).takeUnless { instrumental },
                    noLyrics = instrumental,
                    instrumental = instrumental,
                )
            }
        }.awaitAll().filterNotNull()
    }

    internal fun genericResultId(title: String, artist: String, raw: String): String =
        "${LyricsMatcher.normalize(title)}/${LyricsMatcher.normalize(artist)}/${raw.hashCode()}"

    internal suspend fun lookupUnison(track: LyricsSearchTrack, title: String, artist: String): UnifiedLyricResult? {
        val hit = UnisonClient.fetchEntry(
            track.id, title, artist, track.album, (track.durationMs / 1000L).toInt(),
        ) ?: return null
        val lines = LyricsUtils.parseLyricsContent(hit.lyrics, track.durationMs)
        val instrumental = isInstrumental(hit.lyrics, lines)
        if (lines.isEmpty() && LyricsUtils.looksLikeTtml(hit.lyrics)) return null
        return UnifiedLyricResult(
            id = hit.id.toString(),
            name = hit.song,
            artistName = hit.artist,
            albumName = track.album,
            durationSec = hit.duration ?: 0.0,
            hasLineSync = lines.isNotEmpty() && !instrumental,
            hasWordSync = lines.any { it.words.size > 1 } && !instrumental,
            provider = PreferredLyricsProvider.UNISON.name,
            rawContent = hit.lyrics,
            previewText = cleanPreview(hit.lyrics).takeUnless { instrumental },
            noLyrics = instrumental,
            instrumental = instrumental,
        )
    }

    /**
     * Sources that look a song up by title and artist rather than search: the query is split into the
     * likely pairs and the first pair that finds anything wins.
     */
    private suspend fun searchByTitle(
        provider: PreferredLyricsProvider,
        query: String,
        track: LyricsSearchTrack,
    ): List<UnifiedLyricResult> {
        val source = LyricsProviders.all[provider] ?: return emptyList()
        for ((title, artist) in LyricsMatcher.generateCandidatePairs(query, track.artist).distinct()) {
            if (title.isBlank()) continue
            if (provider == PreferredLyricsProvider.UNISON) {
                val result = lookupUnison(track, title, artist)
                if (result != null) return listOf(result)
                continue
            }
            val raw = source.getLyrics(
                id = track.id,
                title = title,
                artist = artist,
                album = track.album,
                duration = (track.durationMs / 1000L).toInt(),
            ).getOrNull()?.takeIf { it.isNotBlank() } ?: continue
            val lines = LyricsUtils.parseLyricsContent(raw, track.durationMs)
            if (lines.isEmpty() && LyricsUtils.looksLikeTtml(raw)) continue
            val instrumental = isInstrumental(raw, lines)
            return listOf(
                UnifiedLyricResult(
                    id = genericResultId(title, artist, raw),
                    name = title,
                    artistName = artist.ifBlank { track.artist },
                    albumName = track.album,
                    durationSec = track.durationMs / 1000.0,
                    hasLineSync = lines.isNotEmpty() && !instrumental,
                    hasWordSync = lines.any { it.words.size > 1 } && !instrumental,
                    provider = provider.name,
                    rawContent = raw,
                    previewText = cleanPreview(raw).takeUnless { instrumental },
                    noLyrics = instrumental,
                    instrumental = instrumental,
                )
            )
        }
        return emptyList()
    }
}
