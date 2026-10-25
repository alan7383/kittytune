package com.alananasss.kittytune.data.search

import com.alananasss.kittytune.domain.Track
import java.text.Normalizer

/**
 * Keeps a provider's looser text search from answering with a different song.
 *
 * Deezer and Tidal happily return results that share no word with the query (the
 * provider fills a page with "related" material). A listener looking for a track by
 * its title should not have to scroll past those, so provider track results are
 * narrowed to rows whose title, artist or album actually contains everything typed.
 * If nothing survives, the original list is kept rather than turning a misspelling
 * into an empty screen.
 *
 * Exact and prefix title matches are then moved to the top. That is deliberately the
 * order YouTube Music uses: the thing you named first, the other versions after it.
 */
object ProviderSearchRanker {

    /** Punctuation becomes spaces; accents are folded; brackets stay searchable. */
    private fun normalize(raw: String): String = Normalizer
        .normalize(raw.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .replace(Regex("[^\\p{L}\\p{Nd}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    private fun searchableFields(track: Track): List<String> = listOfNotNull(
        track.title,
        track.publisherMetadata?.artist,
        track.publisherMetadata?.albumTitle,
        track.user?.username,
    ).map(::normalize).filter { it.isNotBlank() }

    fun rankTracks(query: String, tracks: List<Track>): List<Track> {
        if (tracks.size < 2) return tracks
        val normalizedQuery = normalize(query)
        val compactQuery = normalizedQuery.replace(" ", "")
        val tokens = normalizedQuery.split(' ').filter { it.length > 1 }
        if (tokens.isEmpty()) return tracks

        val matching = tracks.filter { track ->
            val fields = searchableFields(track)
            val words = fields.flatMap { it.split(' ') }.toSet()
            val everyWord = tokens.all { token -> token in words }
            // A query typed without spaces still finds "New York" when it is the
            // whole of one field; comparing compact fields keeps "are" out of "care".
            val compactField = tokens.size == 1 && fields.any { it.replace(" ", "") == compactQuery }
            everyWord || compactField
        }
        if (matching.isEmpty()) return tracks

        return matching.sortedBy { track -> score(track, normalizedQuery, compactQuery) }
    }

    private fun score(track: Track, normalizedQuery: String, compactQuery: String): Int {
        val title = normalize(track.title.orEmpty())
        val compactTitle = title.replace(" ", "")
        val artist = normalize(
            track.publisherMetadata?.artist ?: track.user?.username.orEmpty(),
        )
        val album = normalize(track.publisherMetadata?.albumTitle.orEmpty())
        val titleWords = title.split(' ').toSet()

        return when {
            title == normalizedQuery || compactTitle == compactQuery -> 0
            title.startsWith(normalizedQuery) || compactTitle.startsWith(compactQuery) -> 1
            normalizedQuery.isNotBlank() && title.contains(normalizedQuery) -> 2
            artist == normalizedQuery -> 2
            normalizedQuery.split(' ').filter { it.length > 1 }.all { it in titleWords } -> 3
            artist.isNotBlank() && artist.contains(normalizedQuery) -> 3
            album.isNotBlank() && album.contains(normalizedQuery) -> 4
            else -> 5
        }
    }
}
