package com.alananasss.kittytune.data.filter

import com.alananasss.kittytune.domain.Comment
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

internal object AiDates {
    const val DAY_MS = 86_400_000L

    private val SLASH_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss Z", Locale.US)

    // SoundCloud answers in ISO-8601 ("2024-03-15T12:34:56Z"); the old API wrote "2013/05/17 10:24:36 +0000".
    private val PARSERS: List<(String) -> Long> = listOf(
        { Instant.parse(it).toEpochMilli() },
        { OffsetDateTime.parse(it).toInstant().toEpochMilli() },
        { ZonedDateTime.parse(it, SLASH_FORMAT).toInstant().toEpochMilli() },
        { LocalDate.parse(it.take(10)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
    )

    fun parseEpochMs(value: String?): Long? {
        val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        for (parse in PARSERS) {
            try {
                return parse(text)
            } catch (_: Exception) {
            }
        }
        return null
    }

    fun year(epochMs: Long): Int = Instant.ofEpochMilli(epochMs).atZone(ZoneOffset.UTC).year
}

/**
 * Evidence from how a track and its uploader behave rather than from what they say: the uploader's profile,
 * their other uploads, the comment section, and the play/like ratio.
 */
internal object AiBehaviorSignals {

    // "Song (1)", "Song v2", "Song - Alt Take": generators hand back two takes per prompt and both get uploaded.
    private val VARIANT_SUFFIX = Regex(
        """(?:[\s\-–—_]+|[\s\-–—_]*[\(\[])\s*(?:v(?:ersion)?\.?\s*\d+|\d{1,2}|alt(?:ernate|ernative)?""" +
            """(?:\s+(?:version|take|mix))?|take\s*\d+|var(?:iant|iation)?\s*\d*|copy)\s*[\)\]]?\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val NON_WORD = Regex("""[^\p{L}\p{N}]+""")
    private val GENERIC_TITLES = setOf(
        "intro", "outro", "interlude", "untitled", "skit", "demo", "unknown", "track", "new song", "sample", "test",
        "mix", "beat", "instrumental", "remix"
    )

    private val GENRE_FAMILIES: List<Pair<String, Regex>> = listOf(
        "hiphop" to """hip[\s-]?hop|rap|trap|drill|boom\s?bap|grime|phonk|plugg|rage""",
        "latin" to """latin|reggaeton|salsa|bachata|cumbia|merengue|dembow|samba|bossa|funk\s+carioca|baile|""" +
            """corrido|banda|mariachi|tango|flamenco""",
        "reggae" to """reggae|dancehall|dub(?!step)|ska|roots""",
        "rnb" to """r\s?&\s?b|rnb|soul|funk|motown|disco""",
        "electronic" to """house|techno|trance|edm|dubstep|drum\s?(?:&|and|n)\s?bass|dnb|d&b|electro|dance|""" +
            """hardstyle|hardcore|garage|breakbeat|breaks|jungle|idm|synthwave|retrowave|vaporwave|future\s?bass|""" +
            """bass\s?music|psy[\s-]?trance|goa|minimal|club|rave|eurobeat""",
        "rock" to """rock|metal|punk|grunge|emo|shoegaze|alternative|core$""",
        "pop" to """pop|top\s?40""",
        "jazz" to """jazz|blues|swing|bebop|lounge|big\s?band""",
        "country" to """country|folk|bluegrass|americana|singer[\s-]?songwriter|western""",
        "classical" to """classical|orchestral|cinematic|soundtrack|score|film|epic|piano|neoclassical|opera""",
        "chill" to """lo[\s-]?fi|chill|ambient|downtempo|relax|sleep|study|meditation|new\s?age""",
        "world" to """afro|amapiano|bhangra|bollywood|arab|celtic|world|balkan|turk|greek|indian""",
        "schlager" to """schlager|volksmusik|chanson|liedermacher|polka""",
        "religious" to """gospel|worship|christian|praise""",
        "kids" to """kids|children|nursery|lullaby"""
    ).map { (family, pattern) -> family to Regex(pattern, RegexOption.IGNORE_CASE) }

    /** Plays that arrive without anyone liking or reposting them: bought or bot-farmed streams. */
    fun engagement(track: Track): List<AiSignal> {
        val plays = track.playbackCount
        if (plays < 2000) return emptyList()
        val ratio = (track.likesCount + track.repostsCount).toDouble() / plays
        if (ratio >= 0.003) return emptyList()
        return listOf(
            AiSignal(
                "ENGAGEMENT_RATIO", 25, AiSignalFamily.ENGAGEMENT,
                String.format(Locale.US, "%.2f%% of %d plays liked or reposted", ratio * 100, plays)
            )
        )
    }

    /** What the uploader says about themselves, how fast they upload, and whether anybody follows them. */
    fun uploader(profile: User?, nowMs: Long): List<AiSignal> {
        profile ?: return emptyList()
        val signals = mutableListOf<AiSignal>()

        val declaration = AiTextSignals.bioDeclaration(profile.description)
        if (declaration != null) {
            signals += AiSignal("UPLOADER_BIO", 70, AiSignalFamily.UPLOADER, declaration, declared = true)
        } else {
            AiTextSignals.termMention(profile.description)?.let {
                signals += AiSignal("UPLOADER_BIO_MENTION", 25, AiSignalFamily.UPLOADER, it)
            }
        }

        val createdAt = AiDates.parseEpochMs(profile.createdAt)
        if (createdAt != null && profile.trackCount > 0) {
            val ageDays = ((nowMs - createdAt) / AiDates.DAY_MS).coerceAtLeast(1)
            val perDay = profile.trackCount.toDouble() / ageDays
            val points = when {
                ageDays < 365 && perDay >= 4.0 -> 35
                ageDays < 365 && perDay >= 2.0 -> 25
                ageDays < 365 && perDay >= 1.0 -> 15
                perDay >= 3.0 -> 25
                perDay >= 1.5 -> 15
                else -> 0
            }
            if (points > 0) {
                signals += AiSignal(
                    "UPLOADER_VELOCITY", points, AiSignalFamily.UPLOADER,
                    String.format(Locale.US, "%.1f uploads/day over %d days", perDay, ageDays)
                )
            }
        }

        val tracks = profile.trackCount
        val followers = profile.followersCount
        val farmPoints = when {
            tracks >= 500 && followers < tracks -> 20
            tracks >= 150 && followers < tracks / 4 -> 15
            else -> 0
        }
        if (farmPoints > 0) {
            signals += AiSignal("FARM_PROFILE", farmPoints, AiSignalFamily.UPLOADER, "$tracks tracks, $followers followers")
        }
        return signals
    }

    /** The uploader's latest tracks, which give away an AI account even when this one upload does not. */
    fun catalog(track: Track, catalog: List<Track>, nowMs: Long): List<AiSignal> {
        val others = catalog.filter { it.id != track.id }
        if (others.isEmpty()) return emptyList()
        val signals = mutableListOf<AiSignal>()

        val labeled = others.count { AiTextSignals.explicitEvidence(it) != null }
        val labeledShare = labeled.toDouble() / others.size
        val labeledPoints = when {
            labeled >= 3 || (labeled >= 2 && labeledShare >= 0.2) -> 60
            labeled >= 1 -> 35
            else -> 0
        }
        if (labeledPoints > 0) {
            signals += AiSignal(
                "CATALOG_AI_LABELED", labeledPoints, AiSignalFamily.CATALOG,
                "$labeled of ${others.size} other uploads", declared = true
            )
        }

        val prompted = others.count { AiTextSignals.workflowSignals(it).isNotEmpty() }
        if (prompted >= 3 && prompted * 4 >= others.size) {
            signals += AiSignal("CATALOG_PROMPT_STYLE", 20, AiSignalFamily.CATALOG, "$prompted of ${others.size}")
        }

        // An album goes up in one day; an AI account does that day after day.
        val uploadTimes = catalog.mapNotNull { AiDates.parseEpochMs(it.createdAt) }
        val burstDays = uploadTimes.groupingBy { it / AiDates.DAY_MS }.eachCount().values.count { it >= 4 }
        val lastMonth = uploadTimes.count { nowMs - it in 0..30 * AiDates.DAY_MS }
        val burstPoints = max(
            when {
                burstDays >= 3 -> 25
                burstDays == 2 -> 12
                else -> 0
            },
            if (lastMonth >= 40) 25 else 0
        )
        if (burstPoints > 0) {
            signals += AiSignal(
                "UPLOAD_BURST", burstPoints, AiSignalFamily.CATALOG,
                "$burstDays days with 4+ uploads, $lastMonth uploads in 30 days"
            )
        }

        val duplicateGroups = catalog.mapNotNull { variantKey(it.title) }
            .groupingBy { it }.eachCount().values.filter { it >= 2 }
        val duplicatePoints = when {
            duplicateGroups.size >= 3 || (duplicateGroups.size >= 2 && duplicateGroups.sum() * 100 >= catalog.size * 15) -> 25
            duplicateGroups.isNotEmpty() -> 10
            else -> 0
        }
        if (duplicatePoints > 0) {
            signals += AiSignal(
                "VARIANT_DUPLICATES", duplicatePoints, AiSignalFamily.CATALOG,
                "${duplicateGroups.size} titles uploaded more than once"
            )
        }

        val families = catalog.mapNotNull { genreFamily(it.genre) }
        val distinctFamilies = families.toSet().size
        if (families.size >= 8 && distinctFamilies >= 6) {
            signals += AiSignal("GENRE_HOPPING", 15, AiSignalFamily.CATALOG, "$distinctFamilies genre families")
        }
        return signals
    }

    /** Listeners who call the track AI, weighed against those who defend it, plus the uploader admitting it. */
    fun comments(track: Track, comments: List<Comment>): List<AiSignal> {
        val uploaderId = track.user?.numericId ?: 0L
        var admission: String? = null
        val accusers = HashMap<Long, Double>()
        val defenders = HashSet<Long>()

        for (comment in comments) {
            val body = comment.body
            if (body.isBlank()) continue
            val author = comment.user?.numericId?.takeIf { it > 0 } ?: body.hashCode().toLong()
            if (uploaderId > 0 && author == uploaderId) {
                if (admission == null) admission = AiTextSignals.explicitMention(body)
                continue
            }
            when (AiTextSignals.commentStance(body)) {
                AiTextSignals.CommentStance.ACCUSES -> accusers[author] = 1.0
                AiTextSignals.CommentStance.ASKS -> accusers[author] = max(accusers[author] ?: 0.0, 0.5)
                AiTextSignals.CommentStance.DEFENDS -> defenders += author
                AiTextSignals.CommentStance.NONE -> Unit
            }
        }

        val signals = mutableListOf<AiSignal>()
        admission?.let {
            signals += AiSignal("UPLOADER_COMMENT", 70, AiSignalFamily.COMMENTS, it, declared = true)
        }
        val weight = accusers.values.sum() - 0.5 * defenders.size
        val points = when {
            weight >= 5 -> 70
            weight >= 3 -> 60
            weight >= 2 -> 35
            weight >= 1 -> 15
            weight >= 0.5 -> 8
            else -> 0
        }
        if (points > 0) {
            signals += AiSignal(
                "LISTENER_CALLOUTS", points, AiSignalFamily.COMMENTS,
                "${accusers.size} listeners suspect AI, ${defenders.size} disagree"
            )
        }
        return signals
    }

    private fun variantKey(title: String?): String? {
        if (title.isNullOrBlank()) return null
        var key = AiText.lowerFold(title).trim()
        repeat(2) { key = key.replace(VARIANT_SUFFIX, "").trim() }
        key = key.replace(NON_WORD, " ").trim()
        return key.takeIf { it.length >= 3 && it !in GENERIC_TITLES }
    }

    private fun genreFamily(genre: String?): String? {
        if (genre.isNullOrBlank()) return null
        val folded = AiText.lowerFold(genre)
        return GENRE_FAMILIES.firstOrNull { (_, pattern) -> pattern.containsMatchIn(folded) }?.first
    }
}
