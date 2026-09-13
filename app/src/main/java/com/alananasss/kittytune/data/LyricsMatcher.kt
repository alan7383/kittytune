package com.alananasss.kittytune.data

import com.alananasss.kittytune.ui.player.lyrics.LyricLine
import java.text.Normalizer
import java.util.Locale

/**
 * Decides how well a lyrics-provider result matches the track being played.
 *
 * The problem this exists for is SoundCloud (issue #33). A track there is whatever the uploader
 * typed: the field the app treats as the artist is really the account that posted the file, so a
 * re-upload of a well known song carries a completely unrelated "artist", and the title is padded
 * with `(Official Video)`, `[FREE DL]`, `prod. by …` and similar. Matching on title *and* artist
 * together, then discarding anything whose duration is not within fifteen seconds, threw away the
 * correct lyrics for exactly those tracks — the user had to delete part of the artist name by hand
 * before anything was found.
 *
 * So the title and the artist are scored separately and the duration only nudges the ranking:
 * a confident title match is enough on its own, and an artist match reinforces it rather than
 * gating it.
 */
object LyricsMatcher {

    /** What we are looking for: the track as the app knows it. */
    data class Target(
        val title: String,
        val artist: String,
        val durationMs: Long,
        val alternativeTitles: List<String> = emptyList(),
        val alternativeArtists: List<String> = emptyList(),
    )

    /** Word-level timings. */
    const val SYNC_TIER_WORD = 3

    /** Line-level timings. */
    const val SYNC_TIER_LINE = 2

    /** The words, with no usable timings. */
    const val SYNC_TIER_PLAIN = 1

    /** Nothing worth showing. */
    const val SYNC_TIER_NONE = 0

    /**
     * How much timing a provider result actually carries — the first thing results are ranked on,
     * ahead of which provider they came from.
     *
     * Lines only count as synced when their timings advance. A provider that has the words but no
     * timings can still answer with a whole list of lines — a Musixmatch subtitle whose entries
     * carry no time, an LRC where every stamp is `[00:00.00]` — and judging by line count alone let
     * that outrank a genuinely synced result from the other provider. That is the "switches to the
     * version without synchronisation even though a synchronised one exists" report in issue #33.
     */
    fun syncTier(lines: List<LyricLine>, plain: String?): Int {
        val timingsAdvance = lines.size > 1 && lines.distinctBy { it.startTime }.size > 1
        return when {
            !timingsAdvance ->
                if (!plain.isNullOrBlank() || lines.isNotEmpty()) SYNC_TIER_PLAIN else SYNC_TIER_NONE
            lines.any { it.words.isNotEmpty() } -> SYNC_TIER_WORD
            else -> SYNC_TIER_LINE
        }
    }

    /**
     * Above this, a candidate has matched both title and artist with high fidelity,
     * so it is definitively the right song and should never be outranked by a stranger.
     */
    const val STRONG_MATCH = 0.78f

    /**
     * Above this, a candidate's title and artist agree with the track well enough that it is believed
     * over a rival that merely carries better timings. Below it, the candidate is plausible and no more.
     *
     * The same threshold [isAcceptable] uses for a title that passes on its own, and for the same
     * reason: it is the point at which the words are about this song rather than about a song with some
     * words in common.
     */
    const val CONFIDENT_MATCH = 0.60f

    /**
     * How provider results are ordered against each other.
     *
     * Identity comes first. A strong match (where both title and artist agree) outranks
     * title-only/partial matches regardless of sync tier, so that a stranger with word-level sync
     * cannot steal the place of the right song that has line-level sync or plain text.
     * Within the same match bracket, sync tier decides.
     */
    fun rank(syncTier: Int, matchScore: Float, providerBonus: Float = 0f): Float =
        rank(syncTier, matchScore, titleSimilarity = matchScore, providerBonus = providerBonus)

    fun rank(
        syncTier: Int,
        matchScore: Float,
        titleSimilarity: Float,
        providerBonus: Float = 0f,
    ): Float {
        val confidenceBonus = when {
            matchScore >= STRONG_MATCH && titleSimilarity >= 0.65f -> STRONG_MATCH_WEIGHT
            matchScore >= CONFIDENT_MATCH && titleSimilarity >= CONFIDENT_MATCH -> CONFIDENCE_WEIGHT
            else -> 0f
        }
        return confidenceBonus + syncTier * TIER_WEIGHT + matchScore + providerBonus
    }

    /** Larger than every tier put together, so a verified artist match beats any stranger. */
    private const val STRONG_MATCH_WEIGHT = 200f

    /** Larger than every tier put together, because identity is not a tie-break. */
    private const val CONFIDENCE_WEIGHT = 100f

    /** Larger than any [score] difference, so the tier still decides within a bracket. */
    private const val TIER_WEIGHT = 10f

    fun titleSimilarity(candidateTitle: String?, target: Target): Float {
        val cand = candidateTitle ?: return 0f
        var best = similarity(cand, target.title)
        for (alt in target.alternativeTitles) {
            val s = similarity(cand, alt)
            if (s > best) best = s
        }
        return best
    }

    fun artistSimilarity(candidateArtist: String?, target: Target): Float {
        val cand = candidateArtist ?: return 0f
        var best = similarity(cand, target.artist)
        for (alt in target.alternativeArtists) {
            val s = similarity(cand, alt)
            if (s > best) best = s
        }
        return best
    }

    /**
     * How close a candidate is, in `0f..1f`. Only comparable between candidates for the same
     * [Target]; the absolute value means nothing on its own beyond [isAcceptable].
     */
    fun score(
        candidateTitle: String?,
        candidateArtist: String?,
        candidateDurationSec: Double,
        target: Target,
    ): Float {
        val titleSim = titleSimilarity(candidateTitle, target)
        val artistSim = artistSimilarity(candidateArtist, target)
        return titleSim * 0.60f + artistSim * 0.25f + durationCloseness(candidateDurationSec, target.durationMs) * 0.15f
    }

    /**
     * Whether a candidate is worth showing at all.
     *
     * A strong title match passes by itself — that is the whole point for re-uploads, where the
     * artist we hold is the uploader's account name and cannot match. A weaker title needs the
     * artist to back it up, but still requires significant title overlap so another song by the
     * same artist is not accepted as this one.
     */
    fun isAcceptable(
        candidateTitle: String?,
        candidateArtist: String?,
        target: Target,
    ): Boolean {
        val titleSim = titleSimilarity(candidateTitle, target)
        if (titleSim >= CONFIDENT_MATCH) return true
        val artistSim = artistSimilarity(candidateArtist, target)
        return titleSim >= 0.50f && artistSim >= 0.45f
    }

    /**
     * 1f when the durations agree, tapering to 0f at half a minute apart. A candidate that does
     * not report a duration scores neutrally rather than being punished: LrcLib and Musixmatch
     * both return 0 for plain-text-only entries, which say nothing about whether the words fit.
     */
    private fun durationCloseness(candidateSec: Double, targetMs: Long): Float {
        if (candidateSec <= 0.0 || targetMs <= 0L) return 0.5f
        val deltaSec = kotlin.math.abs(candidateSec - targetMs / 1000.0)
        return (1.0 - (deltaSec / 30.0)).coerceIn(0.0, 1.0).toFloat()
    }

    /**
     * Token-overlap similarity, with a containment shortcut.
     *
     * Containment first, because the common shape here is one string being the other plus noise
     * ("Song Name" vs "Song Name (Official Video) [FREE]"), and overlap alone under-rates that.
     * Otherwise it is the share of the shorter side's words that appear on the longer side, which
     * ignores word order — titles and artist credits get reordered constantly.
     */
    fun similarity(a: String, b: String): Float {
        val normA = normalize(a)
        val normB = normalize(b)
        if (normA.isEmpty() || normB.isEmpty()) return 0f
        if (normA == normB) return 1f

        val tokensA = tokens(normA)
        val tokensB = tokens(normB)
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0f

        // The same words in a different order are the same credit, not a near miss: artist credits
        // and title fragments get reordered constantly.
        if (tokensA == tokensB) return 1f

        val shared = tokensA.count { it in tokensB }
        val fewer = minOf(tokensA.size, tokensB.size)
        val more = maxOf(tokensA.size, tokensB.size)

        if (shared == fewer) {
            if (fewer >= 2) return 0.9f
            val onlyToken = tokensA.intersect(tokensB).first()
            if (more <= 2 && onlyToken.length >= DISTINCTIVE_TOKEN_LENGTH) return 0.75f
        }

        return shared.toFloat() / more
    }

    /**
     * How long a lone shared word has to be before it identifies a song on its own.
     *
     * Six characters is past the length of the short English words that turn up inside other words
     * and inside every second track title.
     */
    private const val DISTINCTIVE_TOKEN_LENGTH = 6

    /** Words worth comparing: everything else is packaging, not identity. */
    private val NOISE = setOf(
        "official", "video", "audio", "lyric", "lyrics", "visualizer", "visualiser",
        "hd", "hq", "4k", "remaster", "remastered", "explicit", "clean", "version",
        "free", "dl", "download", "prod", "by", "feat", "ft", "featuring", "with",
        "the", "a", "an", "el", "la", "le", "les", "und", "and", "vs",
        "music", "mv", "full", "album", "single", "ep", "cover", "reupload", "upload",
    )

    private fun tokens(normalized: String): Set<String> =
        normalized.split(' ')
            .filter { it.length > 1 && it !in NOISE }
            .toSet()
            // A title made only of noise words ("The Video") would otherwise compare as empty,
            // so fall back to the raw words rather than throwing the candidate away.
            .ifEmpty { normalized.split(' ').filter { it.isNotBlank() }.toSet() }

    /**
     * Folds a title or artist credit down to comparable words: accents removed, bracketed asides
     * and everything after a "feat."-style marker dropped, punctuation flattened to spaces.
     */
    fun normalize(raw: String): String {
        var text = raw.lowercase()
        // Every bracket escaped, including the closing ones. The JVM treats a bare `]` or `}` outside a
        // character class as a literal; Android's ICU engine rejects it outright —
        // `PatternSyntaxException: Syntax error in regexp pattern near index 21`, which crashed the app on
        // the first lyrics lookup. Escaping is valid on both, so the file stays identical between them
        // (issue #33).
        text = text.replace(Regex("\\[.*?\\]|\\(.*?\\)|\\{.*?\\}"), " ")
        text = text.replace(Regex("(?i)\\b(feat|ft|featuring|prod|w)\\.?\\s.*$"), " ")
        text = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        text = text.replace(Regex("[^\\p{L}\\p{Nd}]+"), " ")
        return text.trim().replace(Regex("\\s+"), " ")
    }

    // --- ArchiveTune-inspired intelligent title detection & FuzzyMatcher ---

    const val DEFAULT_THRESHOLD = 0.85

    private val COMBINING_MARKS_REGEX = Regex("\\p{M}+")
    private val NON_ALPHANUMERIC_REGEX = Regex("[^\\p{L}\\p{N}]+")
    private val WHITESPACE_REGEX = Regex("\\s+")

    /**
     * ArchiveTune NFKD normalization: strips accents, diacritics, and symbols.
     */
    fun normalizeFuzzy(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKD)
            .lowercase(Locale.ROOT)
            .replace(COMBINING_MARKS_REGEX, "")
            .replace(NON_ALPHANUMERIC_REGEX, " ")
            .trim()
            .replace(WHITESPACE_REGEX, " ")

    /**
     * ArchiveTune Jaro-Winkler string similarity distance (0.0 to 1.0).
     */
    fun jaroWinkler(first: String, second: String): Double =
        jaroWinklerNormalized(normalizeFuzzy(first), normalizeFuzzy(second))

    private const val WINKLER_PREFIX_LIMIT = 4
    private const val WINKLER_SCALING_FACTOR = 0.1
    private const val WINKLER_BOOST_THRESHOLD = 0.7

    private fun jaroWinklerNormalized(first: String, second: String): Double {
        if (first == second) return 1.0
        if (first.isEmpty() || second.isEmpty()) return 0.0

        val matchDistance = (maxOf(first.length, second.length) / 2 - 1).coerceAtLeast(0)
        val firstMatches = BooleanArray(first.length)
        val secondMatches = BooleanArray(second.length)
        var matches = 0

        first.indices.forEach { firstIndex ->
            val start = (firstIndex - matchDistance).coerceAtLeast(0)
            val end = (firstIndex + matchDistance + 1).coerceAtMost(second.length)

            for (secondIndex in start until end) {
                if (secondMatches[secondIndex] || first[firstIndex] != second[secondIndex]) continue
                firstMatches[firstIndex] = true
                secondMatches[secondIndex] = true
                matches++
                break
            }
        }

        if (matches == 0) return 0.0

        var transpositions = 0
        var secondIndex = 0
        first.indices.forEach { firstIndex ->
            if (!firstMatches[firstIndex]) return@forEach
            while (!secondMatches[secondIndex]) secondIndex++
            if (first[firstIndex] != second[secondIndex]) transpositions++
            secondIndex++
        }

        val matchesAsDouble = matches.toDouble()
        val jaro = (
            (matchesAsDouble / first.length) +
            (matchesAsDouble / second.length) +
            ((matchesAsDouble - (transpositions / 2.0)) / matchesAsDouble)
        ) / 3.0

        if (jaro <= WINKLER_BOOST_THRESHOLD) return jaro

        var prefixLength = 0
        val maximumPrefixLength = minOf(WINKLER_PREFIX_LIMIT, first.length, second.length)
        while (prefixLength < maximumPrefixLength && first[prefixLength] == second[prefixLength]) {
            prefixLength++
        }

        return jaro + (prefixLength * WINKLER_SCALING_FACTOR * (1.0 - jaro))
    }

    /**
     * ArchiveTune & KuGou comprehensive bracket and packaging stripping.
     * Strips `()`, `[]`, `{}`, `（）`, `「」`, `『』`, `《》`, `〈〉`, `＜＞`, `【】`, `〔〕`, `〖〗`, `［］`.
     * Also strips trailing feature / producer credits and video packaging tags.
     */
    fun cleanNoiseAndBrackets(raw: String): String {
        var text = raw
        text = text.replace('–', '-').replace('—', '-')
        text = text.replace(Regex("""\([^\)]*\)|\[[^\]]*\]|\{[^\}]*\}|（[^）]*）|「[^」]*」|『[^』]*』|《[^》]*》|〈[^〉]*〉|＜[^＞]*＞|【[^】]*】|〔[^〕]*〕|〖[^〗]*〗|［[^］]*］"""), " ")
        text = text.replace(Regex("""(?i)\s+(w/|feat\.?|ft\.?|featuring|prod\.?|prod\.\s*by|produced\s+by|x(?=\s)).*$"""), " ")
        text = text.replace(Regex("""(?i)\b(official\s*(video|audio|music\s*video|lyric\s*video|visualizer|visualiser)?|lyric\s*video|audio|visualizer|visualiser|remaster(ed)?|hd|hq|4k|mv|color\s*coded|free\s*dl|download)\b"""), " ")
        return text.trim().replace(WHITESPACE_REGEX, " ")
    }

    /**
     * Cleans an artist name: removes Topic, VEVO, Official, Records, Music, etc.
     */
    fun cleanArtist(raw: String): String {
        var text = raw.trim()
        text = text.replace(Regex("""(?i)\s*-\s*topic$"""), "")
        text = text.replace(Regex("""(?i)\s*vevo$"""), "")
        text = text.replace(Regex("""(?i)\s+(official|records|music|audio)$"""), "")
        text = text.replace(Regex("""\([^\)]*\)|\[[^\]]*\]"""), "")
        return text.trim().replace(WHITESPACE_REGEX, " ")
    }

    /**
     * Generates intelligent (title, artist) candidate pairs for exact-match providers
     * (BetterLyrics, BetterLyrics Portato, KuGou, Paxsenix, YouLyPlus, Unison).
     *
     * Handles:
     * - "Artist - Title" and reversed "Title - Artist"
     * - Delimiters (-, –, —, ~, :, |, /, //)
     * - Artist inside title ("Not Allowed TV Girl" with artist "TV Girl")
     * - Multi-bracket and packaging noise removal
     * - Multi-artist collaborations ("A & B" -> "A")
     */
    fun generateCandidatePairs(title: String, artist: String): List<Pair<String, String>> {
        val pairs = mutableListOf<Pair<String, String>>()
        val cleanT = cleanNoiseAndBrackets(title)
        val cleanA = cleanArtist(artist)
        val rawT = title.trim()
        val rawA = artist.trim()

        // 1. If title contains delimiters (- / : ~ |)
        val normalizedTitle = rawT.replace('–', '-').replace('—', '-')
        val delimiterPattern = Regex("""\s+[-/|~:]\s+""")
        if (delimiterPattern.containsMatchIn(normalizedTitle) || normalizedTitle.contains(" - ")) {
            val parts = if (normalizedTitle.contains(" - ")) {
                normalizedTitle.split(" - ", limit = 2)
            } else {
                normalizedTitle.split(delimiterPattern, limit = 2)
            }
            if (parts.size == 2) {
                val part0Clean = cleanNoiseAndBrackets(parts[0])
                val part1Clean = cleanNoiseAndBrackets(parts[1])
                val part0Artist = cleanArtist(parts[0])
                val part1Artist = cleanArtist(parts[1])

                // Standard: "Artist - Title" -> title = part1, artist = part0
                if (part1Clean.isNotBlank() && part0Artist.isNotBlank()) {
                    pairs.add(part1Clean to part0Artist)
                }
                // Reversed: "Title - Artist" -> title = part0, artist = part1
                if (part0Clean.isNotBlank() && part1Artist.isNotBlank()) {
                    pairs.add(part0Clean to part1Artist)
                }
                // With raw/clean uploader as artist
                if (part1Clean.isNotBlank() && cleanA.isNotBlank()) {
                    pairs.add(part1Clean to cleanA)
                }
                if (part0Clean.isNotBlank() && cleanA.isNotBlank()) {
                    pairs.add(part0Clean to cleanA)
                }
            }
        }

        // 2. If title contains the artist (e.g. "Not Allowed TV Girl" or "TV Girl Not Allowed")
        if (cleanA.isNotBlank()) {
            val withoutArtist = cleanT.replace(Regex("(?i)\\b${Regex.escape(cleanA)}\\b"), "")
                .replace(Regex("""^\s*[-/|~:]+\s*|\s*[-/|~:]+\s*$"""), "")
                .trim()
            if (withoutArtist.isNotBlank() && withoutArtist != cleanT) {
                pairs.add(withoutArtist to cleanA)
            }
        }

        // 3. Cleaned title + clean artist
        if (cleanT.isNotBlank() && cleanA.isNotBlank()) {
            pairs.add(cleanT to cleanA)
        }

        // 4. Raw title + raw artist
        if (rawT.isNotBlank() && rawA.isNotBlank()) {
            pairs.add(rawT to rawA)
        }

        // 5. Cleaned title with raw artist
        if (cleanT.isNotBlank() && rawA.isNotBlank()) {
            pairs.add(cleanT to rawA)
        }

        // 6. Multi-artist split for featured or collaborative artists (e.g. "A & B")
        val multiArtists = cleanA.split(Regex("""(?i)\s*(?:&|and|feat\.?|ft\.?|x|,)\s*"""))
            .map { it.trim() }
            .filter { it.length > 1 }
        if (multiArtists.size > 1) {
            val primaryArtist = multiArtists.first()
            if (cleanT.isNotBlank()) {
                pairs.add(cleanT to primaryArtist)
            }
        }

        // 7. Split words if query was entered manually without delimiters ("Not Allowed TV Girl")
        val words = cleanT.split(WHITESPACE_REGEX)
        if (words.size >= 2) {
            pairs.add(words.dropLast(1).joinToString(" ") to words.last())
            pairs.add(words.first() to words.drop(1).joinToString(" "))
            if (words.size >= 4) {
                pairs.add(words.take(2).joinToString(" ") to words.drop(2).joinToString(" "))
            }
        }

        return pairs.filter { it.first.isNotBlank() && it.second.isNotBlank() }.distinct()
    }
}
