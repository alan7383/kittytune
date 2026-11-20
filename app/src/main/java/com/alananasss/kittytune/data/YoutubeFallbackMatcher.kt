package com.alananasss.kittytune.data

import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/** Checks recording identity before ranking cross-catalog playback matches by duration. */
object YoutubeFallbackMatcher {
    data class Recording(
        val title: String,
        val artist: String,
        val durationSec: Long,
        val restricted: Boolean = false,
        val snippetDuration: Boolean = false,
        val artists: List<String> = emptyList(),
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
    private val featured = Regex("(?i)^(?:feat\\.?|ft\\.?|featuring|with)\\s+(.+)$")
    private val trailingFeature = Regex("(?i)\\s+(?:feat\\.?|ft\\.?|featuring)\\s+(.+)$")
    private val artistSeparators = Regex("(?i)\\s*,\\s*|\\s+[&+]\\s+|\\s+(?:and|x|feat\\.?|ft\\.?|featuring|with)\\s+")
    private val soundtrack = Regex(
        "(?i)^from\\s+(?:the\\s+)?(?:series|film|movie|motion picture|original soundtrack|soundtrack)\\b.*$"
    )
    private val channelSuffix = Regex("(?i)\\s*[-–—]\\s*topic$|\\s*vevo$")
    private val trailingLabel = Regex("(?i)\\s+(?:[-–—|:]\\s*)?(?:${noise.pattern})(?:\\s+${noise.pattern})*$")
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
        "opening title" to Regex("\\bopening title\\b"),
        "8d" to Regex("\\b8d\\b"),
        "preview" to Regex("\\bpreview\\b|\\bteaser\\b|\\bsnippet\\b|\\btrailer\\b"),
    )

    /** Remove presentation labels and soundtrack subtitles while preserving recording editions. */
    fun searchTitle(title: String?): String {
        val cleaned = brackets.replace(title.orEmpty().trim()) { match ->
            val inside = match.innerText()
            if (inside.isBlank() || noise.replace(inside, "").trim().isBlank() || soundtrack.matches(inside)) {
                ""
            } else {
                match.value
            }
        }.trim(' ', '-', '_', '/', '|', ':')
        val parts = cleaned.split(separators)
        val subtitle = parts.indexOfFirst { soundtrack.matches(it) }
        val name = if (subtitle > 0) parts.take(subtitle).joinToString(" - ") else cleaned
        return if (brackets.matches(name)) name.substring(1, name.lastIndex).trim() else name
    }

    fun queries(recording: Recording): List<String> {
        val title = searchTitle(recording.title)
        if (title.isBlank()) return emptyList()
        val artist = artistNames(recording).firstOrNull().orEmpty()
        val primary = "$title $artist".trim()
        return listOf(primary, "$primary audio").distinct()
    }

    /** Optional guard for catalog queries also used outside playback. */
    internal fun matches(
        recording: Recording?,
        title: String,
        artists: List<String>,
        durationMs: Long?,
        album: String?,
        exactIsrc: Boolean = false,
    ): Boolean {
        if (recording == null) return true
        val durationSec = (durationMs ?: 0L) / 1000
        if (exactIsrc) {
            return when {
                durationSec <= 0 -> true
                recording.snippetDuration -> durationSec > 45
                recording.durationSec <= 0 -> true
                else -> abs(recording.durationSec - durationSec) <= if (recording.restricted) 60 else 25
            }
        }
        return match(recording, listOf(Candidate("", title, artists, durationSec, album.orEmpty()))) != null
    }

    fun match(recording: Recording, candidates: List<Candidate>): Candidate? =
        rankedMatches(recording, candidates).firstOrNull()

    fun rankedMatches(recording: Recording, candidates: List<Candidate>): List<Candidate> {
        val artists = artistNames(recording)
        val leadArtist = artists.firstOrNull()?.let(::artistKey)?.takeIf { it.isNotBlank() } ?: return emptyList()
        val wanted = describeTitle(recording.title, leadArtist)
        if (wanted.key.isBlank()) return emptyList()

        val wantedWords = wanted.key.split(' ').toSet()
        val wantedEditions = editionKeys(wanted.key)
        val knownArtists = artists.flatMapTo(mutableSetOf(), ::artistKeys) + wanted.guests
        val durationKnown = recording.durationSec > 0 && !recording.snippetDuration

        return candidates.mapNotNull { candidate ->
            val title = describeTitle(candidate.title, leadArtist)
            if (title.key.isBlank()) return@mapNotNull null
            val candidateArtists = candidate.artists.flatMapTo(mutableSetOf(), ::artistKeys) + title.byline
            if (leadArtist !in candidateArtists) return@mapNotNull null
            if (!(candidateArtists + title.guests).containsAll(wanted.guests)) return@mapNotNull null
            if (!knownArtists.containsAll(title.guests)) return@mapNotNull null

            // Some catalogs put the named remix on the album rather than the track title.
            val album = normalize(candidate.album)
            val albumIdentifiesSong = album.split(' ').toSet().containsAll(wantedWords)
            val editionText = if (editionKeys(title.key).isEmpty() && albumIdentifiesSong) "${title.key} $album" else title.key
            if (editionKeys(editionText) != wantedEditions) return@mapNotNull null
            val identityTitle = if (wantedEditions.isNotEmpty() && editionKeys(title.key) != wantedEditions) editionText else title.key

            val titleWords = identityTitle.split(' ').toSet()
            val matchingWords = wantedWords.intersect(titleWords).size.toDouble()
            val recall = matchingWords / wantedWords.size
            val precision = matchingWords / titleWords.size
            val exactTitle = identityTitle.replace(" ", "") == wanted.key.replace(" ", "")
            if (!exactTitle && (recall < 0.85 || precision < 0.70)) return@mapNotNull null

            val delta = abs(candidate.durationSec - recording.durationSec)
            if (durationKnown && candidate.durationSec > 0 && delta > if (recording.restricted) 60 else 25) return@mapNotNull null
            if (recording.snippetDuration && candidate.durationSec in 1..45) return@mapNotNull null
            val identityScore = if (exactTitle) 2.0 else recall + precision / 2
            val durationScore = if (candidate.durationSec <= 0) -1.0 else if (!durationKnown) 0.0 else -delta / 1000.0
            Triple(candidate, identityScore, durationScore)
        }.sortedWith(compareByDescending<Triple<Candidate, Double, Double>> { it.second }.thenByDescending { it.third })
            .map { it.first }
    }

    private data class Title(val key: String, val guests: Set<String>, val byline: Set<String>)

    private fun describeTitle(title: String, leadArtist: String): Title {
        val guests = mutableSetOf<String>()
        val cleaned = brackets.replace(searchTitle(title)) { match ->
            val credit = featured.matchEntire(match.innerText()) ?: return@replace match.value
            guests.addAll(credit.groupValues[1].split(artistSeparators).map(::artistKey).filter { it.isNotBlank() })
            ""
        }.trim()
        val parts = cleaned.split(separators)
        val byline = when {
            parts.size > 1 && leadArtist in artistKeys(parts.first()) -> artistKeys(parts.first()) to parts.drop(1)
            parts.size > 1 && leadArtist in artistKeys(parts.last()) -> artistKeys(parts.last()) to parts.dropLast(1)
            else -> emptySet<String>() to parts
        }
        val songName = trailingFeature.replace(byline.second.joinToString(" - ")) { match ->
            guests.addAll(match.groupValues[1].split(artistSeparators).map(::artistKey).filter { it.isNotBlank() })
            ""
        }.trim()
        return Title(normalize(songName.replace(trailingLabel, "")), guests, byline.first)
    }

    private fun MatchResult.innerText(): String = groupValues.drop(1).firstOrNull { it.isNotEmpty() }.orEmpty().trim()

    private fun artistNames(recording: Recording): List<String> =
        recording.artists.filter { it.isNotBlank() }.ifEmpty {
            listOf(recording.artist.trim()).filter { it.isNotBlank() }
        }.filterNot { it.equals("Unknown Artist", ignoreCase = true) }

    private fun editionKeys(text: String): Set<String> = editions.filterValues { it.containsMatchIn(text) }.keys

    private fun artistKeys(value: String): Set<String> =
        (listOf(value) + value.split(artistSeparators)).map(::artistKey).filter { it.isNotBlank() }.toSet()

    private fun artistKey(value: String): String = normalize(value.trim().replace(channelSuffix, ""))
        .replace(" ", "")

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
        .replace("&", " and ").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
}
