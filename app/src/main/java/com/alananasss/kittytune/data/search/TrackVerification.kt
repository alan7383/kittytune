package com.alananasss.kittytune.data.search

import com.alananasss.kittytune.data.LyricsMatcher
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.resolveThumbnailUrl
import kotlin.math.abs

/**
 * Decides whether a search result really is the official version of the song that was asked for.
 *
 * SoundCloud, YouTube and VK are full of re-uploads. Two rows can carry the same title and look
 * identical at a glance, while one is the actual release and the other is a trimmed, sped-up,
 * re-covered or simply mislabelled copy. Comparing the rows against the same song as the official
 * catalogues (Deezer, Tidal, Qobuz, Spotify) know it is the only way to tell them apart.
 *
 * Every candidate is matched to the closest official release and then checked on four things:
 * title, artist, duration and cover art. The title and artist gate the match; a duration more than
 * [MAX_DURATION_DELTA_MS] away or a cover more than [MAX_COVER_DISTANCE] bits away is thrown out;
 * anything that survives is ranked with the verified rows first.
 *
 * This file is deliberately free of Android and network code so the rules can be tested on their
 * own. [TrackVerifier] gathers the platform references and the cover fingerprints, then calls
 * [buildClusters] and [rank].
 */
object TrackVerification {

    enum class Verdict {
        /** Title, artist and at least one of duration/cover agree with the official release. */
        VERIFIED,

        /** The release is right, but at least one comparison was unavailable or only close. */
        LIKELY,

        /** No official release close enough to judge. */
        UNKNOWN,

        /** The row contradicts the official release (wrong artist, duration or cover). */
        REJECTED,
    }

    enum class Status {
        IDLE,
        VERIFIED,
        NO_MATCHES,
        INCONCLUSIVE,
        UNAVAILABLE,
    }

    /** One track as an official platform has it. */
    data class Reference(
        val id: String,
        val title: String,
        val artist: String,
        val durationMs: Long,
        val coverUrl: String? = null,
        val isrc: String? = null,
        val albumTitle: String? = null,
        val platform: String,
    )

    /** One search result to judge. */
    data class Candidate(
        val key: String,
        val title: String,
        val artistNames: List<String>,
        val durationMs: Long,
        val isSnipped: Boolean = false,
        val isrc: String? = null,
        val coverUrl: String? = null,
        val coverHash: Long? = null,
        val originalIndex: Int = 0,
    )

    /** The official releases that share a title, artist and version. */
    data class Cluster(
        val titleVariants: List<String>,
        val artistVariants: List<String>,
        val versionTags: Set<String>,
        val references: List<Reference>,
        val coverUrls: List<String> = emptyList(),
        val coverFingerprints: Set<Long> = emptySet(),
        val isrcs: Set<String> = emptySet(),
        val queryScore: Float = 0f,
        val artistQueryCoverage: Float = 0f,
        val matchesQueryIdentity: Boolean = false,
    )

    data class Evaluation(
        val key: String,
        val originalIndex: Int,
        val verdict: Verdict,
        val score: Float,
        val titleSimilarity: Float,
        val artistSimilarity: Float,
        val durationDeltaMs: Long?,
        val coverDistance: Int?,
        val versionMatchesQuery: Boolean,
        val cluster: Cluster?,
    )

    data class Ranking(
        val evaluations: List<Evaluation>,
        val visible: List<Evaluation>,
        val verifiedKeys: Set<String>,
        val trustedQuery: Boolean,
        val status: Status,
    )

    /** How far a candidate's duration may be from the nearest official duration. */
    const val MAX_DURATION_DELTA_MS = 8_000L

    /** Hamming distance at which two covers are no longer considered the same artwork. */
    const val MAX_COVER_DISTANCE = 14

    /** The score two titles need before they are treated as the same song. */
    private const val MIN_TITLE_SIMILARITY = 0.55f

    /** The score two artist credits need before they are treated as the same act. */
    private const val MIN_ARTIST_SIMILARITY = 0.35f

    /** The score at which a candidate is confidently about the song rather than near it. */
    private const val STRONG_TITLE_SIMILARITY = 0.80f
    private const val STRONG_ARTIST_SIMILARITY = 0.60f

    /** A query needs this much of the official title and artist before strict filtering is safe. */
    private const val TRUSTED_QUERY_SCORE = 0.80f

    private val STOP_WORDS = setOf(
        "the", "a", "an", "and", "or", "of", "in", "on", "with", "for", "to",
        "feat", "ft", "featuring", "prod", "by", "official", "video", "audio",
        "lyric", "lyrics", "visualizer", "visualiser", "hd", "hq", "4k", "mv",
        "music", "full", "album", "single", "ep", "remaster", "remastered",
        "explicit", "clean", "version", "free", "dl", "download", "reupload",
        "upload", "topic",
    )

    private val BRACKETS = Regex("""[\[\](){}（）【】「」『』《》〈〉［］〔〕〖〗]""")

    private val PACKAGING = Regex(
        """(?i)\b(official|lyrics?|audio|video|visuali[sz]er|hd|hq|4k|mv|music\s+video|full\s+album|album\s+version|single\s+version|original\s+(?:mix|version|edit)|deluxe(?:\s+edition)?|(?:special|expanded|collector'?s?|limited)\s+edition|(?:\d+(?:st|nd|rd|th)\s+)?anniversary(?:\s+edition)?|bonus\s+track|explicit|clean|version|edition|remaster(?:ed)?|re-?upload|upload|free\s+dl|download)\b""",
    )

    /** A year glued to a remaster/edition label is packaging, not part of the song title. */
    private val REMASTER_YEAR = Regex(
        """(?i)\b(?:(?:19|20)\d{2}\s+(?:digital\s+)?remaster(?:ed)?|(?:digital\s+)?remaster(?:ed)?\s+(?:19|20)\d{2})\b""",
    )

    private val EDITION_YEAR = Regex(
        """(?i)\b(?:19|20)\d{2}\s+(?:deluxe|expanded|special|collector'?s?|anniversary)(?:\s+edition)?\b""",
    )

    private val ARTIST_SPLIT = Regex(
        """\s*(?:,|&|/|;|\bfeaturing\b|\b(?:feat|ft)\.?|\s+x\s+)\s*""",
        RegexOption.IGNORE_CASE,
    )

    private val WHITESPACE = Regex("""\s+""")

    fun keyOf(track: Track): String = "${track.source.orEmpty()}:${track.id}"

    /**
     * Folds a title down to the part that identifies the recording. Brackets are flattened rather
     * than dropped so "(Skrillex Remix)" stays distinguishable from the original, while packaging
     * such as "(Official Audio)" and "[FREE DL]" disappears.
     */
    fun prepareTitle(raw: String): String {
        var text = raw.replace(BRACKETS, " ")
        text = REMASTER_YEAR.replace(text, " ")
        text = EDITION_YEAR.replace(text, " ")
        text = LyricsMatcher.cleanNoiseAndBrackets(text)
        text = text.replace(PACKAGING, " ")
        val cleaned = trimTitle(text)
        // A title made only of packaging words ("Music", "Album") would otherwise become
        // empty and the whole official release would disappear from the comparison.
        if (cleaned.isNotEmpty()) return cleaned
        return trimTitle(raw.replace(BRACKETS, " "))
    }

    private fun trimTitle(value: String): String =
        value.trim().replace(WHITESPACE, " ").trim(' ', '-', '–', '—', ':', '|', '/')

    /** The cleaned artist names worth comparing, including each collaborator on a shared credit. */
    fun artistVariants(raw: String): List<String> {
        val cleaned = LyricsMatcher.cleanArtist(raw).trim()
        if (cleaned.isEmpty()) return emptyList()
        val split = cleaned.split(ARTIST_SPLIT).map { it.trim() }.filter { it.isNotEmpty() }
        return (listOf(cleaned) + split).distinctBy { it.lowercase() }
    }

    fun candidateOf(track: Track, index: Int = 0): Candidate {
        val artists = linkedSetOf<String>()
        track.publisherMetadata?.artist?.takeIf { it.isNotBlank() }?.let(artists::add)
        track.displayArtist.takeIf { it.isNotBlank() }?.let(artists::add)
        track.user?.username?.takeIf { it.isNotBlank() }?.let(artists::add)
        track.artists?.mapNotNull { it.name.takeIf(String::isNotBlank) }?.let(artists::addAll)

        return Candidate(
            key = keyOf(track),
            title = track.title.orEmpty(),
            artistNames = artists.flatMap(::artistVariants).distinctBy { it.lowercase() },
            durationMs = track.actualDurationMs,
            isSnipped = track.isSnipped,
            isrc = track.publisherMetadata?.isrc,
            coverUrl = track.artworkUrl
                ?.takeIf { it.isNotBlank() }
                ?.let(::resolveThumbnailUrl),
            originalIndex = index,
        )
    }

    fun referenceOf(track: Track, platform: String): Reference? {
        val title = track.title?.takeIf { it.isNotBlank() } ?: return null
        val artist = track.publisherMetadata?.artist?.takeIf { it.isNotBlank() }
            ?: track.displayArtist.takeIf { it.isNotBlank() }
            ?: track.user?.username?.takeIf { it.isNotBlank() }
            ?: return null
        return Reference(
            id = "$platform:${track.id}",
            title = title,
            artist = artist,
            durationMs = track.actualDurationMs,
            coverUrl = track.artworkUrl?.takeIf { it.isNotBlank() }?.let(::resolveThumbnailUrl),
            isrc = track.publisherMetadata?.isrc,
            albumTitle = track.publisherMetadata?.albumTitle,
            platform = platform,
        )
    }

    /**
     * Groups references into official releases and measures how much of the query each group
     * actually covers. [Cluster.matchesQueryIdentity] is the important flag: it is only set when
     * the query names enough of the title and artist for strict filtering to be meaningful, so a
     * one-word search never hides every result it cannot pin down.
     */
    fun buildClusters(query: String, references: List<Reference>): List<Cluster> {
        val queryTokens = queryTokens(query)
        val groups = LinkedHashMap<String, MutableList<Reference>>()

        references
            .filter { it.title.isNotBlank() && it.artist.isNotBlank() }
            .distinctBy { it.id.ifBlank { "${it.platform}|${it.title}|${it.artist}|${it.durationMs}" } }
            .forEach { reference ->
                val title = prepareTitle(reference.title)
                val artist = LyricsMatcher.cleanArtist(reference.artist).trim()
                if (title.isBlank() || artist.isBlank()) return@forEach
                val versions = TrackVersionTags.of(reference.title)
                val titleKey = LyricsMatcher.normalize(title).replace(" ", "")
                val artistKey = artistTokens(artist).firstOrNull()
                    ?: LyricsMatcher.normalize(artist).replace(" ", "")
                val key = "$titleKey|$artistKey|${versions.sorted().joinToString("+")}"
                groups.getOrPut(key) { mutableListOf() }.add(reference)
            }

        return groups.values.map { group ->
            val titleVariants = group.map { prepareTitle(it.title) }
                .filter { it.isNotBlank() }
                .distinctBy { LyricsMatcher.normalize(it) }
            val artistVariants = group.flatMap { artistVariants(it.artist) }
                .filter { it.isNotBlank() }
                .distinctBy { LyricsMatcher.normalize(it) }
            val versions = group.flatMap { TrackVersionTags.of(it.title) }.toSet()
            val relevance = queryRelevance(queryTokens, titleVariants, artistVariants, versions)

            Cluster(
                titleVariants = titleVariants,
                artistVariants = artistVariants,
                versionTags = versions,
                references = group,
                coverUrls = group.mapNotNull { it.coverUrl }.distinct(),
                isrcs = group.mapNotNull { it.isrc?.trim()?.uppercase(java.util.Locale.ROOT) }
                    .filter { it.isNotBlank() }
                    .toSet(),
                queryScore = relevance.score,
                artistQueryCoverage = relevance.artistCoverage,
                matchesQueryIdentity = relevance.matchesIdentity,
            )
        }.sortedWith(
            compareByDescending<Cluster> { it.queryScore }
                .thenByDescending { it.references.size },
        )
    }

    /** Attaches the collected cover fingerprints to the clusters whose covers were checked. */
    fun withCoverFingerprints(clusters: List<Cluster>, hashes: Map<String, Long>): List<Cluster> =
        clusters.map { cluster ->
            cluster.copy(
                coverFingerprints = cluster.coverUrls.mapNotNull(hashes::get).toSet(),
            )
        }

    /**
     * Ranks the candidates against the official clusters.
     *
     * When the query names both a title and an artist, only clusters whose identity the query
     * actually covers are considered, and candidates for other recordings are filtered out. That
     * is what keeps a re-upload of a different song by the same artist out of a specific search.
     */
    fun rank(
        query: String,
        candidates: List<Candidate>,
        clusters: List<Cluster>,
    ): Ranking {
        val tokens = queryTokens(query)
        val queryVersions = TrackVersionTags.of(query)
        val identityClusters = clusters.filter { it.matchesQueryIdentity }
        // If the strongest official-looking cluster names an artist that the query also names,
        // restrict the pool to releases by that artist. This keeps a cover whose title happens to
        // contain the query ("Humble by Kendrick Lamar" by a cover band) from being treated as
        // the requested recording.
        val strongestIdentity = identityClusters.maxWithOrNull(
            compareBy<Cluster> { it.queryScore }
                .thenBy { it.artistQueryCoverage }
                .thenBy { it.references.size },
        )
        val namesArtist = (strongestIdentity?.artistQueryCoverage ?: 0f) >= 0.5f
        val trustedClusters = if (namesArtist) {
            identityClusters.filter { it.artistQueryCoverage >= 0.5f }
        } else {
            identityClusters
        }
        val trusted = tokens.size >= 2 && trustedClusters.isNotEmpty()
        val pool = if (trusted) trustedClusters else clusters

        val evaluations = candidates.map { candidate ->
            evaluate(candidate, queryVersions, pool)
        }

        val visible = (if (trusted) {
            evaluations.filter {
                (it.verdict == Verdict.VERIFIED || it.verdict == Verdict.LIKELY) &&
                    (it.cluster?.matchesQueryIdentity == true) &&
                    it.versionMatchesQuery
            }
        } else {
            evaluations.filter { it.verdict != Verdict.REJECTED }
        }).sortedWith(
            compareBy<Evaluation> { verdictOrder(it.verdict) }
                .thenByDescending { it.score }
                .thenBy { it.originalIndex },
        )

        val verifiedKeys = evaluations
            .filter { it.verdict == Verdict.VERIFIED && it.versionMatchesQuery }
            .map { it.key }
            .toSet()

        val status = when {
            visible.isEmpty() -> Status.NO_MATCHES
            verifiedKeys.isNotEmpty() -> Status.VERIFIED
            else -> Status.INCONCLUSIVE
        }

        return Ranking(
            evaluations = evaluations,
            visible = visible,
            verifiedKeys = verifiedKeys,
            trustedQuery = trusted,
            status = status,
        )
    }

    /** Hamming distance between two 64-bit difference hashes: 0 is the same artwork. */
    fun coverDistance(first: Long, second: Long): Int = java.lang.Long.bitCount(first xor second)

    private data class QueryRelevance(
        val score: Float,
        val artistCoverage: Float,
        val matchesIdentity: Boolean,
    )

    private fun queryRelevance(
        queryTokens: Set<String>,
        titleVariants: List<String>,
        artistVariants: List<String>,
        versions: Set<String>,
    ): QueryRelevance {
        if (queryTokens.isEmpty() || titleVariants.isEmpty() || artistVariants.isEmpty()) {
            return QueryRelevance(0f, 0f, false)
        }

        val clusterArtistTokens = artistVariants.flatMap(::artistTokens).toSet()
        val artistInQuery = clusterArtistTokens.intersect(queryTokens)
        val titleQueryTokens = if (artistInQuery.isNotEmpty()) {
            queryTokens - artistInQuery
        } else {
            queryTokens
        }
        if (titleQueryTokens.isEmpty()) return QueryRelevance(0f, 0f, false)

        val titleScore = titleVariants.maxOf { variant ->
            f1(titleQueryTokens, titleTokens(variant) + versions)
        }
        val artistScore = if (artistInQuery.isEmpty()) {
            0f
        } else {
            artistInQuery.size.toFloat() / clusterArtistTokens.size
        }
        val score = if (artistInQuery.isNotEmpty()) {
            titleScore * 0.75f + artistScore * 0.25f
        } else {
            titleScore
        }

        val matchesIdentity = queryTokens.size >= 2 &&
            titleScore >= TRUSTED_QUERY_SCORE &&
            score >= TRUSTED_QUERY_SCORE

        return QueryRelevance(score, artistScore, matchesIdentity)
    }

    private fun evaluate(
        candidate: Candidate,
        queryVersions: Set<String>,
        clusters: List<Cluster>,
    ): Evaluation {
        val candidateTitle = prepareTitle(candidate.title)
        val candidateVersions = TrackVersionTags.of(candidate.title)
        val candidateArtists = candidate.artistNames
            .flatMap(::artistVariants)
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
        var versionMatchesQuery = candidateVersions == queryVersions

        if (candidateTitle.isBlank() || candidateArtists.isEmpty()) {
            return Evaluation(
                key = candidate.key,
                originalIndex = candidate.originalIndex,
                verdict = Verdict.UNKNOWN,
                score = 0f,
                titleSimilarity = 0f,
                artistSimilarity = 0f,
                durationDeltaMs = null,
                coverDistance = null,
                versionMatchesQuery = versionMatchesQuery,
                cluster = null,
            )
        }

        var bestCluster: Cluster? = null
        var bestTitle = 0f
        var bestArtist = 0f
        var bestIdentity = -1f

        for (cluster in clusters) {
            // A remix never stands in for the original (and vice versa): the version markers
            // have to agree before title, duration and cover are even worth comparing.
            if (TrackVersionTags.conflict(candidateVersions, cluster.versionTags)) continue

            val title = titleSimilarity(candidateTitle, cluster.titleVariants, candidateVersions)
            val artist = candidateArtists.maxOfOrNull { name ->
                cluster.artistVariants.maxOfOrNull { official -> LyricsMatcher.similarity(name, official) } ?: 0f
            } ?: 0f
            if (title < MIN_TITLE_SIMILARITY || artist < MIN_ARTIST_SIMILARITY) continue

            val identity = title * 0.72f + artist * 0.28f
            if (identity > bestIdentity) {
                bestIdentity = identity
                bestCluster = cluster
                bestTitle = title
                bestArtist = artist
            }
        }

        if (bestCluster == null) {
            return Evaluation(
                key = candidate.key,
                originalIndex = candidate.originalIndex,
                verdict = Verdict.UNKNOWN,
                score = 0f,
                titleSimilarity = bestTitle,
                artistSimilarity = bestArtist,
                durationDeltaMs = null,
                coverDistance = null,
                versionMatchesQuery = versionMatchesQuery,
                cluster = null,
            )
        }

        // A version word in the query may simply be part of the artist's name ("Live" the band).
        // Only the markers left once the official artist credit is accounted for are requests for
        // a particular version of the song.
        val artistVersions = bestCluster.artistVariants
            .flatMap { TrackVersionTags.of(it) }
            .toSet()
        versionMatchesQuery = candidateVersions == (queryVersions - artistVersions)

        val officialDurations = bestCluster.references
            .mapNotNull { it.durationMs.takeIf { duration -> duration > 0L } }
        val durationDelta = if (candidate.durationMs > 0L && officialDurations.isNotEmpty()) {
            officialDurations.minOf { abs(candidate.durationMs - it) }
        } else {
            null
        }
        val candidateCoverHash = candidate.coverHash
        val coverDistance = if (candidateCoverHash != null && bestCluster.coverFingerprints.isNotEmpty()) {
            bestCluster.coverFingerprints.minOf { coverDistance(candidateCoverHash, it) }
        } else {
            null
        }
        val isrcMatch = candidate.isrc?.trim()?.takeIf { it.isNotBlank() }?.let { isrc ->
            bestCluster.isrcs.any { it.equals(isrc, ignoreCase = true) }
        } ?: false

        val durationScore = when {
            durationDelta == null -> 0.5f
            durationDelta <= 2_000L -> 1f
            durationDelta <= 4_000L -> 0.8f
            durationDelta <= MAX_DURATION_DELTA_MS -> 0.5f
            else -> 0f
        }
        val coverScore = when {
            coverDistance == null -> 0.5f
            coverDistance <= 8 -> 1f
            coverDistance <= MAX_COVER_DISTANCE -> 0.65f
            else -> 0f
        }
        val score = (
            bestTitle * 0.40f +
                bestArtist * 0.22f +
                durationScore * 0.20f +
                coverScore * 0.18f +
                if (isrcMatch) 0.10f else 0f
            ).coerceIn(0f, 1f)

        val titleOk = bestTitle >= 0.68f
        val artistOk = bestArtist >= 0.45f
        val identityStrong = bestTitle >= STRONG_TITLE_SIMILARITY &&
            bestArtist >= STRONG_ARTIST_SIMILARITY
        val durationStrong = durationDelta != null && durationDelta <= 4_000L
        val coverStrong = coverDistance != null && coverDistance <= 8
        val durationOk = durationDelta == null || durationDelta <= MAX_DURATION_DELTA_MS
        val coverOk = coverDistance == null || coverDistance <= MAX_COVER_DISTANCE

        val verdict = when {
            !durationOk || !coverOk -> Verdict.REJECTED
            !titleOk || !artistOk -> Verdict.UNKNOWN
            !versionMatchesQuery -> Verdict.LIKELY
            (identityStrong || isrcMatch) &&
                (durationStrong || coverStrong || isrcMatch) -> Verdict.VERIFIED
            else -> Verdict.LIKELY
        }

        return Evaluation(
            key = candidate.key,
            originalIndex = candidate.originalIndex,
            verdict = verdict,
            score = score,
            titleSimilarity = bestTitle,
            artistSimilarity = bestArtist,
            durationDeltaMs = durationDelta,
            coverDistance = coverDistance,
            versionMatchesQuery = versionMatchesQuery,
            cluster = bestCluster,
        )
    }

    /**
     * Title comparison, with a containment shortcut when both sides name the same version. A
     * remix's label ("Song - Skrillex Remix" vs "Song (Skrillex Remix)") is extra packaging rather
     * than a different title, and the version markers have already established that both rows are
     * the remix.
     */
    private fun titleSimilarity(
        candidateTitle: String,
        officialTitles: List<String>,
        versions: Set<String>,
    ): Float = officialTitles.maxOfOrNull { official ->
        val normal = LyricsMatcher.similarity(candidateTitle, official)
        if (versions.isNotEmpty()) {
            val candidateTokens = titleTokens(candidateTitle)
            val officialTokens = titleTokens(official)
            val contains = candidateTokens.isNotEmpty() &&
                officialTokens.isNotEmpty() &&
                (candidateTokens.containsAll(officialTokens) || officialTokens.containsAll(candidateTokens))
            if (contains) maxOf(normal, 0.9f) else normal
        } else {
            normal
        }
    } ?: 0f

    private fun queryTokens(query: String): Set<String> {
        val normalized = LyricsMatcher.normalize(query)
        val words = normalized.split(' ')
            .filter { it.length > 1 && it !in STOP_WORDS }
        return words.toSet() + TrackVersionTags.of(query)
    }

    private fun titleTokens(text: String): Set<String> =
        LyricsMatcher.normalize(text)
            .split(' ')
            .filter { it.length > 1 && it !in STOP_WORDS }
            .toSet()

    private fun artistTokens(text: String): Set<String> {
        val normalized = LyricsMatcher.normalize(LyricsMatcher.cleanArtist(text))
        return normalized.split(' ')
            .filter { it.length > 1 && it !in STOP_WORDS }
            .toSet()
    }

    private fun f1(wanted: Set<String>, candidate: Set<String>): Float {
        if (wanted.isEmpty() || candidate.isEmpty()) return 0f
        val shared = wanted.count { it in candidate }
        if (shared == 0) return 0f
        val precision = shared.toFloat() / candidate.size
        val recall = shared.toFloat() / wanted.size
        return 2f * precision * recall / (precision + recall)
    }

    private fun verdictOrder(verdict: Verdict): Int = when (verdict) {
        Verdict.VERIFIED -> 0
        Verdict.LIKELY -> 1
        Verdict.UNKNOWN -> 2
        Verdict.REJECTED -> 3
    }
}
