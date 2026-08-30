package com.alananasss.kittytune.data

import com.alananasss.kittytune.ui.player.lyrics.LyricLine
import java.text.Normalizer

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
    data class Target(val title: String, val artist: String, val durationMs: Long)

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
            // `?.` because this side's [LyricLine.words] is nullable where the desktop's defaults to empty.
            // The two types should be one, but aligning them touches every lyrics parser here, so the
            // divergence is absorbed at this single point rather than papered over with a wider change.
            lines.any { it.words?.isNotEmpty() == true } -> SYNC_TIER_WORD
            else -> SYNC_TIER_LINE
        }
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
        val titleSim = similarity(candidateTitle ?: "", target.title)
        val artistSim = similarity(candidateArtist ?: "", target.artist)
        return titleSim * 0.60f + artistSim * 0.25f + durationCloseness(candidateDurationSec, target.durationMs) * 0.15f
    }

    /**
     * Whether a candidate is worth showing at all.
     *
     * A strong title match passes by itself — that is the whole point for re-uploads, where the
     * artist we hold is the uploader's account name and cannot match. A weaker title needs the
     * artist to back it up.
     */
    fun isAcceptable(
        candidateTitle: String?,
        candidateArtist: String?,
        target: Target,
    ): Boolean {
        val titleSim = similarity(candidateTitle ?: "", target.title)
        if (titleSim >= 0.60f) return true
        val artistSim = similarity(candidateArtist ?: "", target.artist)
        return titleSim >= 0.35f && artistSim >= 0.45f
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
        if (normA.contains(normB) || normB.contains(normA)) return 0.9f

        val tokensA = tokens(normA)
        val tokensB = tokens(normB)
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0f
        val shared = tokensA.count { it in tokensB }
        return shared.toFloat() / minOf(tokensA.size, tokensB.size)
    }

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
}
