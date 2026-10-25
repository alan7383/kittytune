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
import com.alananasss.kittytune.ui.player.lyrics.LyricsUtils

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
                PreferredLyricsProvider.SIMPMUSIC -> searchSimpMusic(variant)
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
    ): List<UnifiedLyricResult> {
        val targetSec = target.durationMs / 1000.0
        return (current + incoming)
            .groupBy { duplicateKey(it) }
            .values
            .map { copies -> copies.minBy { lengthMiss(it, targetSec) } }
            // Word timings first, then line timings, then plain text, whoever the source is; within a kind the
            // closest to this song. An unrelated song never outranks the right one by its timings alone.
            .sortedWith(
                compareByDescending<UnifiedLyricResult> { isAboutThisSong(it, target) }
                    .thenByDescending { syncTier(it) }
                    .thenByDescending { rank(it, target) },
            )
    }

    fun syncTier(result: UnifiedLyricResult): Int = when {
        result.hasWordSync -> LyricsMatcher.SYNC_TIER_WORD
        result.hasLineSync -> LyricsMatcher.SYNC_TIER_LINE
        else -> LyricsMatcher.SYNC_TIER_PLAIN
    }

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

    private fun duplicateKey(result: UnifiedLyricResult): String =
        listOf(
            result.provider,
            LyricsMatcher.normalize(result.name),
            LyricsMatcher.normalize(result.artistName),
            syncTier(result).toString(),
        ).joinToString("|")

    private fun lengthMiss(result: UnifiedLyricResult, targetSec: Double): Double =
        if (result.durationSec <= 0.0 || targetSec <= 0.0) Double.MAX_VALUE / 2
        else kotlin.math.abs(result.durationSec - targetSec)

    private fun isAboutThisSong(result: UnifiedLyricResult, target: LyricsMatcher.Target): Boolean =
        LyricsMatcher.titleSimilarity(result.name, target) >= LyricsMatcher.CONFIDENT_MATCH

    /** Sure enough to be used without asking: this song's title, and its length when both lengths are known. */
    fun isConfidentMatch(result: UnifiedLyricResult, target: LyricsMatcher.Target): Boolean {
        if (!isAboutThisSong(result, target)) return false
        val targetSec = target.durationMs / 1000.0
        return result.durationSec <= 0.0 || targetSec <= 0.0 || lengthMiss(result, targetSec) <= MAX_LENGTH_MISS_SEC
    }

    /** Further off the track's length than this, a timed copy is probably another cut of the song. */
    private const val MAX_LENGTH_MISS_SEC = 6.0

    private fun rank(result: UnifiedLyricResult, target: LyricsMatcher.Target): Float {
        val score = LyricsMatcher.score(result.name, result.artistName, result.durationSec, target)
        val titleSimilarity = LyricsMatcher.titleSimilarity(result.name, target)
        return LyricsMatcher.rank(syncTier(result), score, titleSimilarity)
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
            )
        }

    private suspend fun searchSimpMusic(query: String): List<UnifiedLyricResult> =
        SimpMusicClient.search(query).mapNotNull {
            val videoId = it.videoId?.takeIf { id -> id.isNotBlank() } ?: return@mapNotNull null
            UnifiedLyricResult(
                id = videoId,
                name = it.title ?: query,
                artistName = it.artist ?: "",
                albumName = it.album,
                durationSec = (it.duration ?: 0).toDouble(),
                hasLineSync = true,
                hasWordSync = !it.richSyncLyrics.isNullOrBlank(),
                provider = "SIMPMUSIC",
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
            val raw = source.getLyrics(
                id = track.id,
                title = title,
                artist = artist,
                album = track.album,
                duration = (track.durationMs / 1000L).toInt(),
            ).getOrNull()?.takeIf { it.isNotBlank() } ?: continue
            val (lineSync, wordSync) = timingsOf(raw, track.durationMs)
            return listOf(
                UnifiedLyricResult(
                    id = query,
                    name = title,
                    artistName = artist.ifBlank { track.artist },
                    albumName = track.album,
                    durationSec = track.durationMs / 1000.0,
                    hasLineSync = lineSync,
                    hasWordSync = wordSync,
                    provider = provider.displayName,
                    rawContent = raw,
                    previewText = cleanPreview(raw),
                )
            )
        }
        return emptyList()
    }
}
