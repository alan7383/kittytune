package com.alananasss.kittytune.data

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/** Matches a recording's identity before using its duration to break ties (issue #76). */
internal object YoutubeFallbackMatcher {
    data class Recording(
        val title: String,
        val artist: String,
        val durationSec: Long,
        val restricted: Boolean = false,
        val snippetDuration: Boolean = false,
    )

    data class Candidate(
        val id: String,
        val title: String,
        val artists: List<String>,
        val durationSec: Long,
        val album: String = "",
    )

    private val noise = Regex(
        "(?i)\\b(?:official\\s+(?:music\\s+)?(?:video|audio|visuali[sz]er)|lyrics?\\s+video|lyrics?|audio|hd|hq|4k|visuali[sz]er)\\b"
    )
    private val brackets = Regex("\\[([^]]*)]|\\(([^)]*)\\)")
    private val separators = Regex("\\s+[-–—|:]\\s+")
    private val features = Regex("(?i)\\s*(?:\\(|\\[)(?:feat\\.?|ft\\.?|featuring)\\s+[^)\\]]*(?:\\)|\\])|\\s+(?:feat\\.?|ft\\.?|featuring)\\s+.*$")
    private val editions = mapOf(
        "remix" to Regex("\\bremix(?:ed)?\\b"),
        "live" to Regex("\\blive\\b"),
        "acoustic" to Regex("\\bacoustic\\b|\\bunplugged\\b"),
        "cover" to Regex("\\bcover\\b|\\btribute\\b"),
        "instrumental" to Regex("\\binstrumental\\b|\\bkaraoke\\b"),
        "sped up" to Regex("\\bsped up\\b|\\bnightcore\\b|\\bspeed up\\b"),
        "slowed" to Regex("\\bslowed\\b"),
        "extended" to Regex("\\bextended\\b"),
        "radio edit" to Regex("\\bradio edit\\b"),
        "remaster" to Regex("\\bremaster(?:ed)?\\b"),
        "8d" to Regex("\\b8d\\b"),
        "preview" to Regex("\\bpreview\\b|\\bteaser\\b|\\bsnippet\\b|\\btrailer\\b"),
    )

    /** Remove presentation labels; retain remix, featured artist, and recording information. */
    fun searchTitle(title: String?): String {
        val raw = title.orEmpty().trim()
        val cleaned = brackets.replace(raw) { match ->
            val inside = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }.orEmpty().trim()
            if (inside.isBlank() || noise.replace(inside, "").trim().isBlank()) "" else match.value
        }.trim(' ', '-', '_', '/', '|', ':')
        if (cleaned.isNotBlank()) {
            return if (brackets.matches(cleaned)) cleaned.substring(1, cleaned.lastIndex).trim() else cleaned
        }
        // A bracket-only real title such as [Intro] is still searchable; a noise-only label isn't.
        return ""
    }

    fun queries(recording: Recording): List<String> {
        val title = searchTitle(recording.title)
        if (title.isBlank()) return emptyList()
        val artist = recording.artist.trim()
        val primary = "$title $artist".trim()
        return listOf(primary, "$primary audio").distinct()
    }

    fun match(recording: Recording, candidates: List<Candidate>): Candidate? {
        val requested = titleKey(recording.title, recording.artist)
        if (requested.isBlank()) return null
        val requestedWords = requested.split(' ').toSet()
        val requestedEditions = editionKeys(requested)
        val wantedArtist = artistKey(recording.artist)
        return candidates.mapNotNull { candidate ->
            val title = titleKey(candidate.title, recording.artist)
            if (title.isBlank()) return@mapNotNull null
            // Album metadata sometimes carries a remix name missing from the song's title.
            val album = normalize(candidate.album)
            val albumIdentifiesSong = album.split(' ').toSet().containsAll(requestedWords)
            val editionText = if (editionKeys(title).isEmpty() && albumIdentifiesSong) "$title $album" else title
            if (editionKeys(editionText) != requestedEditions) return@mapNotNull null
            val identityTitle = if (requestedEditions.isNotEmpty() && editionKeys(title) != requestedEditions) editionText else title
            val titleWords = identityTitle.split(' ').toSet()
            val matchingWords = requestedWords.intersect(titleWords).size.toDouble()
            val recall = matchingWords / requestedWords.size
            val precision = matchingWords / titleWords.size
            val compactExact = identityTitle.replace(" ", "") == requested.replace(" ", "")
            if (!compactExact && (recall < 0.85 || precision < 0.70)) return@mapNotNull null

            val artistMatches = wantedArtist.isBlank() || candidate.artists.any { sameArtist(wantedArtist, artistKey(it)) } ||
                sameArtist(wantedArtist, artistKey(candidate.artists.joinToString(" "))) ||
                artistMentioned(candidate.title, wantedArtist)
            if (!artistMatches) return@mapNotNull null

            val durationKnown = recording.durationSec > 0 && !recording.snippetDuration
            val delta = abs(candidate.durationSec - recording.durationSec)
            if (durationKnown && candidate.durationSec > 0 && delta > if (recording.restricted) 60 else 25) return@mapNotNull null
            if (recording.snippetDuration && candidate.durationSec in 1..45) return@mapNotNull null
            val identityScore = if (compactExact) 2.0 else recall + precision / 2
            // An unknown candidate duration is usable only after title, artist, and edition matched.
            val durationScore = if (candidate.durationSec <= 0) -1.0 else if (!durationKnown) 0.0 else -delta / 1000.0
            Triple(candidate, identityScore, durationScore)
        }.maxWithOrNull(compareBy<Triple<Candidate, Double, Double>> { it.second }.thenBy { it.third })?.first
    }

    private fun editionKeys(text: String): Set<String> = editions.filterValues { it.containsMatchIn(text) }.keys

    private fun titleKey(title: String, artist: String): String {
        val cleaned = searchTitle(title).replace(features, "")
        val parts = cleaned.split(separators)
        val withoutArtist = when {
            parts.size > 1 && sameArtist(artistKey(artist), artistKey(parts.first())) -> parts.drop(1).joinToString(" - ")
            parts.size > 1 && sameArtist(artistKey(artist), artistKey(parts.last())) -> parts.dropLast(1).joinToString(" - ")
            else -> cleaned
        }
        // Unbracketed presentation labels occur at the end of video names; keep real titles like Audio.
        val trailingLabel = Regex("(?i)\\s+(?:[-–—|:]\\s*)?(?:${noise.pattern})(?:\\s+${noise.pattern})*$")
        return normalize(withoutArtist.replace(trailingLabel, ""))
    }

    private fun artistKey(value: String): String = normalize(value)
        .replace(Regex("\\b(?:topic|official|vevo|music|records|and)\\b"), "")
        .replace(" ", "")

    private fun sameArtist(expected: String, actual: String): Boolean = expected.isNotBlank() &&
        (expected == actual || (expected.length >= 4 && actual == expected + "vevo"))

    private fun artistMentioned(title: String, expected: String): Boolean {
        if (expected.isBlank()) return false
        // A video uploaded by a label can identify its artist in its title instead of its channel.
        val parts = title.split(separators)
        return parts.size > 1 && (sameArtist(expected, artistKey(parts.first())) || sameArtist(expected, artistKey(parts.last())))
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
        .replace("&", " and ").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
}
