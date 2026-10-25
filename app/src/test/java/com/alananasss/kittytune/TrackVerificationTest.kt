package com.alananasss.kittytune

import com.alananasss.kittytune.data.search.TrackVerification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Verified search filter has to tell an official release from a re-upload that shares its
 * title. These tests exercise the pure comparison rules: the same song at the same duration with
 * the same cover passes; a different cover, a trimmed/extended duration, a wrong artist or a
 * remix does not. A row that cannot be judged is kept for non-strict searches rather than hidden.
 */
class TrackVerificationTest {

    private val coverUrl = "https://cdn.example/cover-300.jpg"
    private val officialCover = 0L
    private val differentCover = -1L

    @Test
    fun `official release with matching title artist duration and cover is verified`() {
        val ranking = rank(
            query = "Get Lucky Daft Punk",
            references = listOf(
                reference("Get Lucky", "Daft Punk", 248_000, platform = "deezer"),
                reference("Get Lucky", "Daft Punk", 248_400, platform = "tidal"),
            ),
            candidates = listOf(
                candidate("Get Lucky", listOf("Daft Punk"), 248_500, officialCover),
            ),
        )

        assertEquals(TrackVerification.Verdict.VERIFIED, ranking.evaluations.single().verdict)
        assertEquals(1, ranking.visible.size)
        assertEquals(1, ranking.verifiedKeys.size)
        assertEquals(TrackVerification.Status.VERIFIED, ranking.status)
    }

    @Test
    fun `packaging noise around an official title still verifies`() {
        val ranking = rank(
            query = "Get Lucky Daft Punk",
            references = listOf(reference("Get Lucky", "Daft Punk", 248_000)),
            candidates = listOf(
                candidate(
                    "Get Lucky (Official Audio) [FREE DL]",
                    listOf("Daft Punk"),
                    248_000,
                    officialCover,
                ),
            ),
        )

        assertEquals(TrackVerification.Verdict.VERIFIED, ranking.evaluations.single().verdict)
    }

    @Test
    fun `a re-upload with different cover art is rejected`() {
        val ranking = rank(
            query = "Get Lucky Daft Punk",
            references = listOf(reference("Get Lucky", "Daft Punk", 248_000)),
            candidates = listOf(
                candidate("Get Lucky", listOf("Daft Punk"), 248_000, differentCover),
            ),
        )

        assertEquals(TrackVerification.Verdict.REJECTED, ranking.evaluations.single().verdict)
        assertTrue(ranking.visible.isEmpty())
        assertEquals(TrackVerification.Status.NO_MATCHES, ranking.status)
    }

    @Test
    fun `a re-upload with the wrong duration is rejected`() {
        val ranking = rank(
            query = "Get Lucky Daft Punk",
            references = listOf(reference("Get Lucky", "Daft Punk", 248_000)),
            candidates = listOf(
                candidate("Get Lucky", listOf("Daft Punk"), 301_000, officialCover),
            ),
        )

        assertEquals(TrackVerification.Verdict.REJECTED, ranking.evaluations.single().verdict)
        assertTrue(ranking.visible.isEmpty())
    }

    @Test
    fun `a re-upload under an unrelated uploader is not verified`() {
        val ranking = rank(
            query = "Get Lucky Daft Punk",
            references = listOf(reference("Get Lucky", "Daft Punk", 248_000)),
            candidates = listOf(
                candidate("Get Lucky", listOf("xX_bootleg_uploads_Xx"), 248_000, officialCover),
            ),
        )

        assertFalse(ranking.visible.any { it.verdict == TrackVerification.Verdict.VERIFIED })
        assertTrue(ranking.visible.isEmpty())
    }

    @Test
    fun `a remix does not stand in for the original`() {
        val ranking = rank(
            query = "Get Lucky Daft Punk",
            references = listOf(reference("Get Lucky", "Daft Punk", 248_000)),
            candidates = listOf(
                candidate(
                    "Get Lucky (Daft Punk Remix)",
                    listOf("Daft Punk"),
                    248_000,
                    officialCover,
                ),
            ),
        )

        assertTrue(ranking.visible.isEmpty())
    }

    @Test
    fun `the requested remix is verified when the query names it`() {
        val ranking = rank(
            query = "Get Lucky Daft Punk Remix",
            references = listOf(reference("Get Lucky (Daft Punk Remix)", "Daft Punk", 248_000)),
            candidates = listOf(
                candidate(
                    "Get Lucky - Daft Punk Remix",
                    listOf("Daft Punk"),
                    248_200,
                    officialCover,
                ),
            ),
        )

        assertEquals(TrackVerification.Verdict.VERIFIED, ranking.evaluations.single().verdict)
    }

    @Test
    fun `a specific song hides other official songs from the same artist`() {
        val references = listOf(
            reference("HUMBLE.", "Kendrick Lamar", 177_000, platform = "deezer"),
            reference("DNA.", "Kendrick Lamar", 185_000, platform = "tidal"),
        )
        val ranking = rank(
            query = "Kendrick Lamar HUMBLE",
            references = references,
            candidates = listOf(
                candidate("HUMBLE.", listOf("Kendrick Lamar"), 177_000, officialCover),
                candidate("DNA.", listOf("Kendrick Lamar"), 185_000, officialCover),
            ),
        )

        assertEquals(listOf("HUMBLE."), ranking.visible.mapNotNull { evaluation ->
            ranking.evaluations.first { it.key == evaluation.key }.cluster?.titleVariants?.firstOrNull()
        })
    }

    @Test
    fun `a version word in the artist name is not read as a version request`() {
        val ranking = rank(
            query = "Live Selling the Drama",
            references = listOf(reference("Selling the Drama", "Live", 200_000)),
            candidates = listOf(
                candidate("Selling the Drama", listOf("Live"), 200_000, officialCover),
            ),
        )

        assertEquals(TrackVerification.Verdict.VERIFIED, ranking.evaluations.single().verdict)
        assertEquals(1, ranking.visible.size)
    }

    @Test
    fun `remaster years are packaging rather than part of the title`() {
        assertEquals("Get Lucky", TrackVerification.prepareTitle("Get Lucky (Remastered 2011)"))
        assertEquals("Get Lucky", TrackVerification.prepareTitle("Get Lucky - 2011 Remaster"))
        assertEquals(
            "Get Lucky",
            TrackVerification.prepareTitle("Get Lucky (Deluxe Edition)"),
        )
    }

    @Test
    fun `a platform's album-version suffix does not hide an official match`() {
        val ranking = rank(
            query = "Get Lucky Daft Punk",
            references = listOf(reference("Get Lucky", "Daft Punk", 248_000, platform = "spotify")),
            candidates = listOf(
                candidate("Get Lucky (Album Version)", listOf("Daft Punk"), 248_000, officialCover),
            ),
        )

        assertEquals(TrackVerification.Verdict.VERIFIED, ranking.evaluations.single().verdict)
    }

    @Test
    fun `a title made only of packaging words still compares`() {
        val ranking = rank(
            query = "Music Madonna",
            references = listOf(reference("Music", "Madonna", 220_000)),
            candidates = listOf(
                candidate("Music", listOf("Madonna"), 220_000, officialCover),
            ),
        )

        assertEquals(TrackVerification.Verdict.VERIFIED, ranking.evaluations.single().verdict)
        assertEquals(1, ranking.visible.size)
    }

    @Test
    fun `verified rows are ordered before merely likely ones`() {
        val ranking = rank(
            query = "Get Lucky Daft Punk",
            references = listOf(reference("Get Lucky", "Daft Punk", 248_000)),
            candidates = listOf(
                // Right metadata, but its cover is close rather than exact and its duration is
                // only just inside the tolerance, so it is likely rather than verified.
                candidate("Get Lucky", listOf("Daft Punk"), 253_000, 0b1111111111L),
                candidate("Get Lucky (Official Audio)", listOf("Daft Punk"), 248_000, officialCover),
            ),
        )

        assertEquals(TrackVerification.Verdict.LIKELY, ranking.evaluations.first().verdict)
        assertEquals(TrackVerification.Verdict.VERIFIED, ranking.evaluations.last().verdict)
        assertEquals(1, ranking.visible.first().originalIndex)
        assertEquals(0, ranking.visible.last().originalIndex)
        assertEquals(TrackVerification.Verdict.VERIFIED, ranking.visible.first().verdict)
    }

    @Test
    fun `a full track reported through a preview field still verifies`() {
        val ranking = rank(
            query = "Kendrick Lamar HUMBLE",
            references = listOf(reference("HUMBLE.", "Kendrick Lamar", 177_000)),
            candidates = listOf(
                // SoundCloud exposes many official tracks as duration=30s plus a
                // full_duration; the verification must use the full length, not the preview flag.
                candidate("HUMBLE.", listOf("Kendrick Lamar"), 177_000, officialCover)
                    .copy(isSnipped = true),
            ),
        )

        assertEquals(TrackVerification.Verdict.VERIFIED, ranking.evaluations.single().verdict)
        assertEquals(1, ranking.visible.size)
    }

    @Test
    fun `a 30 second preview cannot stand in for the full track`() {
        val ranking = rank(
            query = "Kendrick Lamar HUMBLE",
            references = listOf(reference("HUMBLE.", "Kendrick Lamar", 177_000)),
            candidates = listOf(
                candidate("HUMBLE.", listOf("Kendrick Lamar"), 30_000, officialCover)
                    .copy(isSnipped = true),
            ),
        )

        assertEquals(TrackVerification.Verdict.REJECTED, ranking.evaluations.single().verdict)
        assertTrue(ranking.visible.isEmpty())
    }

    @Test
    fun `a cover whose title names the artist is not the requested recording`() {
        val references = listOf(
            reference("HUMBLE.", "Kendrick Lamar", 177_000, platform = "deezer"),
            reference("HUMBLE.", "Kendrick Lamar", 177_000, platform = "tidal"),
            reference("Humble by Kendrick Lamar", "Cover Band", 177_000, platform = "qobuz"),
        )
        val ranking = rank(
            query = "Kendrick Lamar HUMBLE",
            references = references,
            candidates = listOf(
                candidate("HUMBLE.", listOf("Kendrick Lamar"), 177_000, officialCover),
                candidate("Humble by Kendrick Lamar", listOf("Cover Band"), 177_000, officialCover),
            ),
        )

        assertEquals(1, ranking.visible.size)
        assertEquals(0, ranking.visible.single().originalIndex)
    }

    @Test
    fun `a one-word query does not hide rows it cannot identify`() {
        val ranking = rank(
            query = "HUMBLE",
            references = listOf(reference("HUMBLE.", "Kendrick Lamar", 177_000)),
            candidates = listOf(
                candidate("HUMBLE.", listOf("Kendrick Lamar"), 177_000, officialCover),
                candidate("HUMBLE. (Live)", listOf("Kendrick Lamar"), 185_000, officialCover),
            ),
        )

        assertFalse(ranking.trustedQuery)
        assertTrue(ranking.visible.isNotEmpty())
    }

    @Test
    fun `cover fingerprints of the same artwork stay close and different art does not`() {
        assertEquals(0, TrackVerification.coverDistance(0L, 0L))
        assertEquals(3, TrackVerification.coverDistance(0L, 0b111L))
        assertTrue(TrackVerification.coverDistance(0L, -1L) > TrackVerification.MAX_COVER_DISTANCE)
    }

    private fun rank(
        query: String,
        references: List<TrackVerification.Reference>,
        candidates: List<TrackVerification.Candidate>,
    ): TrackVerification.Ranking {
        val clusters = TrackVerification.buildClusters(query, references)
        val hashes = references
            .mapNotNull { reference -> reference.coverUrl?.let { it to officialCover } }
            .toMap()
        return TrackVerification.rank(
            query = query,
            candidates = candidates.mapIndexed { index, candidate -> candidate.copy(originalIndex = index) },
            clusters = TrackVerification.withCoverFingerprints(clusters, hashes),
        )
    }

    private fun reference(
        title: String,
        artist: String,
        durationMs: Long,
        platform: String = "deezer",
        cover: String? = coverUrl,
    ): TrackVerification.Reference = TrackVerification.Reference(
        id = "$platform:$title:$artist",
        title = title,
        artist = artist,
        durationMs = durationMs,
        coverUrl = cover,
        platform = platform,
    )

    private fun candidate(
        title: String,
        artists: List<String>,
        durationMs: Long,
        coverHash: Long?,
    ): TrackVerification.Candidate = TrackVerification.Candidate(
        key = "$title:${artists.joinToString()}",
        title = title,
        artistNames = artists,
        durationMs = durationMs,
        coverUrl = if (coverHash != null) coverUrl else null,
        coverHash = coverHash,
    )
}
