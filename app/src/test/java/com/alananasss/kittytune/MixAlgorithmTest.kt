package com.alananasss.kittytune

import com.alananasss.kittytune.data.mix.MixProfile
import com.alananasss.kittytune.data.mix.MixRanking
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The arithmetic behind "start mixing" (issue #33).
 *
 * "According to your interests, gives out songs that you like **(these are not your favorite songs)**."
 *
 * That parenthesis is the specification, and it is the thing a recommender gets wrong by default: the easiest
 * mix to build is the one made of what somebody already liked, and it is the one nobody wants. Most of what is
 * below exists to hold that line — the profile comes from plays rather than likes, and anything already known is
 * disqualified rather than merely ranked down.
 */
class MixAlgorithmTest {

    private val day = 24 * 60 * 60 * 1000L
    private val now = 1_700_000_000_000L

    private fun play(
        id: Long,
        artist: String,
        ageDays: Long = 0,
        listened: Long = 200_000,
        duration: Long = 200_000,
        genre: String? = null,
    ) = MixProfile.Play(
        trackId = id,
        artist = artist,
        genre = genre,
        atMs = now - ageDays * day,
        listenedMs = listened,
        durationMs = duration,
    )

    private fun track(
        id: Long,
        artist: String,
        plays: Int = 5_000,
        durationMs: Long = 200_000,
        followers: Int = 1_000,
        verified: Boolean = false,
    ) = Track(
        id = id,
        title = "track $id",
        artworkUrl = null,
        user = User(id = id * 100, username = artist, avatarUrl = null, followersCount = followers, verified = verified),
        durationMs = durationMs,
        playbackCount = plays,
    )

    // ---- the profile ---------------------------------------------------------------------------

    /** A skip is evidence against a track, and the stats table records it exactly like a play. */
    @Test
    fun `a track skipped early counts for nothing`() {
        val skipped = play(1, "A", listened = 8_000, duration = 200_000)
        assertEquals(0f, MixProfile.weightOf(skipped, now))
    }

    @Test
    fun `a track played through counts fully`() {
        assertEquals(1f, MixProfile.weightOf(play(1, "A"), now))
    }

    /** Half heard is half a vote, which is the honest reading of somebody leaving halfway. */
    @Test
    fun `a half-heard track counts by how much was heard`() {
        val half = play(1, "A", listened = 100_000, duration = 200_000)
        assertEquals(0.5f, MixProfile.weightOf(half, now), absoluteTolerance = 0.001f)
    }

    /**
     * Without decay a profile converges on whatever somebody listened to most in their life and then never
     * changes again, which is what makes a recommender feel stale.
     */
    @Test
    fun `an old play counts for less than a new one`() {
        val fresh = MixProfile.weightOf(play(1, "A", ageDays = 0), now)
        val threeWeeks = MixProfile.weightOf(play(1, "A", ageDays = 21), now)
        val sixWeeks = MixProfile.weightOf(play(1, "A", ageDays = 42), now)
        assertEquals(0.5f, threeWeeks / fresh, absoluteTolerance = 0.02f)
        assertEquals(0.25f, sixWeeks / fresh, absoluteTolerance = 0.02f)
    }

    @Test
    fun `the profile ranks artists by weighted plays`() {
        val taste = MixProfile.build(
            plays = listOf(
                play(1, "Often"), play(2, "Often"), play(3, "Often"),
                play(4, "Rarely"),
                play(5, "Skipped", listened = 4_000, duration = 200_000),
            ),
            likes = emptyList(),
            nowMs = now,
        )
        assertEquals(listOf("often", "rarely"), taste.rankedArtists)
        assertTrue("skipped" !in taste.artists, "a skipped artist has no weight")
    }

    /** Skipped or not, it was played — so the mix must not offer it back as a discovery. */
    @Test
    fun `everything played is remembered as known`() {
        val taste = MixProfile.build(
            plays = listOf(play(1, "A"), play(2, "B", listened = 1_000, duration = 200_000)),
            likes = emptyList(),
            nowMs = now,
        )
        assertEquals(setOf(1L, 2L), taste.knownTrackIds)
        assertTrue("b" in taste.knownArtists, "an artist skipped is still an artist heard")
    }

    /** A like is a signal, and a much weaker one than a play: recorded once, out of politeness, never decaying. */
    @Test
    fun `a like counts for less than a play`() {
        val taste = MixProfile.build(
            plays = emptyList(),
            likes = listOf(track(1, "Liked")),
            nowMs = now,
        )
        assertEquals(MixProfile.LIKE_WEIGHT, taste.artists["liked"])
        assertTrue(MixProfile.LIKE_WEIGHT < 1f)
    }

    // ---- the scoring --------------------------------------------------------------------------

    /** The whole point of the feature, as one assertion. */
    @Test
    fun `a track already liked is not a candidate`() {
        val taste = MixProfile.build(emptyList(), listOf(track(7, "A")), now)
        assertNull(MixRanking.score(MixRanking.Candidate(track(7, "A"), 1f), taste))
    }

    @Test
    fun `a track already played is not a candidate`() {
        val taste = MixProfile.build(listOf(play(7, "A")), emptyList(), now)
        assertNull(MixRanking.score(MixRanking.Candidate(track(7, "A"), 1f), taste))
    }

    /** SoundCloud is full of thirty-second interludes that nothing else tells apart from songs. */
    @Test
    fun `a snippet is not a candidate`() {
        val taste = MixProfile.build(listOf(play(1, "A")), emptyList(), now)
        assertNull(MixRanking.score(MixRanking.Candidate(track(9, "B", durationMs = 30_000), 1f), taste))
    }

    /** A duration of zero is unknown rather than short, and dropping those would drop half of SoundCloud. */
    @Test
    fun `an unknown duration is not treated as a snippet`() {
        val taste = MixProfile.build(listOf(play(1, "A")), emptyList(), now)
        assertTrue(MixRanking.score(MixRanking.Candidate(track(9, "B", durationMs = 0), 1f), taste) != null)
    }

    /** The seed that matched the listener best should carry its candidates highest. */
    @Test
    fun `a stronger seed scores higher`() {
        val taste = MixProfile.build(listOf(play(1, "A")), emptyList(), now)
        val strong = MixRanking.score(MixRanking.Candidate(track(10, "X"), 1f), taste)!!
        val weak = MixRanking.score(MixRanking.Candidate(track(11, "Y"), 0.2f), taste)!!
        assertTrue(strong > weak)
    }

    /**
     * A familiar artist is allowed but pushed down. A mix with none of your artists feels like somebody else's;
     * a mix that is only your artists is not discovery.
     */
    @Test
    fun `an artist already listened to is penalised but not excluded`() {
        val taste = MixProfile.build(listOf(play(1, "Known")), emptyList(), now)
        val familiar = MixRanking.score(MixRanking.Candidate(track(20, "Known"), 1f), taste)
        val stranger = MixRanking.score(MixRanking.Candidate(track(21, "Stranger"), 1f), taste)
        assertTrue(familiar != null, "a familiar artist is still a candidate")
        assertTrue(stranger!! > familiar!!)
    }

    /** Mild and logarithmic: "not obscure" rather than "famous", or everybody gets the same global hits. */
    @Test
    fun `popularity helps a little and does not decide`() {
        val taste = MixProfile.build(listOf(play(1, "A")), emptyList(), now)
        val popular = MixRanking.score(MixRanking.Candidate(track(30, "X", plays = 5_000_000), 0.4f), taste)!!
        val obscure = MixRanking.score(MixRanking.Candidate(track(31, "Y", plays = 3), 0.4f), taste)!!
        assertTrue(popular > obscure, "popularity should count for something")

        val obscureButRelevant = MixRanking.score(MixRanking.Candidate(track(32, "Z", plays = 3), 1f), taste)!!
        assertTrue(obscureButRelevant > popular, "but never more than fitting the listener")
    }

    @Test
    fun `artists with more subscribers are prioritized when trusted sources is enabled`() {
        val taste = MixProfile.build(listOf(play(1, "A")), emptyList(), now)
        val bigArtist = MixRanking.score(
            MixRanking.Candidate(track(40, "Star", plays = 10_000, followers = 500_000), 0.8f),
            taste,
            prioritizeTrusted = true,
        )!!
        val smallArtist = MixRanking.score(
            MixRanking.Candidate(track(41, "Indie", plays = 10_000, followers = 100), 0.8f),
            taste,
            prioritizeTrusted = true,
        )!!
        assertTrue(bigArtist > smallArtist, "more followers should boost priority when enabled")
    }

    @Test
    fun `verified trusted artists get authenticity bonus`() {
        val taste = MixProfile.build(listOf(play(1, "A")), emptyList(), now)
        val verified = MixRanking.score(
            MixRanking.Candidate(track(42, "Official", plays = 10_000, followers = 5_000, verified = true), 0.8f),
            taste,
            prioritizeTrusted = true,
        )!!
        val unverified = MixRanking.score(
            MixRanking.Candidate(track(43, "Fan", plays = 10_000, followers = 5_000, verified = false), 0.8f),
            taste,
            prioritizeTrusted = true,
        )!!
        assertTrue(verified > unverified, "verified badge should provide an authenticity bonus")
    }

    @Test
    fun `when prioritizeTrusted is disabled, remixers and small uploaders are scored equally on same plays`() {
        val taste = MixProfile.build(listOf(play(1, "A")), emptyList(), now)
        val bigArtist = MixRanking.score(
            MixRanking.Candidate(track(44, "MajorLabel", plays = 10_000, followers = 1_000_000, verified = true), 0.8f),
            taste,
            prioritizeTrusted = false,
        )!!
        val smallRemixer = MixRanking.score(
            MixRanking.Candidate(track(45, "SmallRemixer", plays = 10_000, followers = 5, verified = false), 0.8f),
            taste,
            prioritizeTrusted = false,
        )!!
        assertEquals(bigArtist, smallRemixer, "disabling prioritizeTrusted treats creators equally without follower bias")
    }

    // ---- the running order --------------------------------------------------------------------

    /** An artist station returns one artist's whole catalogue, so the raw ranking clumps badly. */
    @Test
    fun `no artist appears three times in a row`() {
        val taste = MixProfile.build(listOf(play(1, "Seed")), emptyList(), now)
        val candidates = (100L..140L).map {
            MixRanking.Candidate(track(it, if (it % 2 == 0L) "Alpha" else "Beta"), 1f)
        }
        val order = MixRanking.order(candidates, taste, size = 10, seed = 1)
        val artists = order.map { it.user?.username }
        artists.windowed(3).forEach { window ->
            assertTrue(window.distinct().size > 1, "three in a row by $window")
        }
    }

    @Test
    fun `no artist appears more than twice at all`() {
        val taste = MixProfile.build(listOf(play(1, "Seed")), emptyList(), now)
        val candidates = (100L..160L).map { MixRanking.Candidate(track(it, "Only"), 1f) }
        val order = MixRanking.order(candidates, taste, size = 10, seed = 1)
        assertTrue(order.size <= 2, "one artist should not fill a mix, got ${order.size}")
    }

    /** Pressing the button twice must give two mixes, or it is a playlist with extra steps. */
    @Test
    fun `two presses give two orders`() {
        val taste = MixProfile.build(listOf(play(1, "Seed")), emptyList(), now)
        val candidates = (100L..160L).map { MixRanking.Candidate(track(it, "Artist$it"), 1f) }
        val first = MixRanking.order(candidates, taste, size = 12, seed = 1)
        val second = MixRanking.order(candidates, taste, size = 12, seed = 2)
        assertTrue(first.map { it.id } != second.map { it.id }, "the order should differ between presses")
        assertEquals(first.map { it.id }.toSet(), second.map { it.id }.toSet(), "the same tracks, reordered")
    }

    @Test
    fun `duplicates from different seeds appear once`() {
        val taste = MixProfile.build(listOf(play(1, "Seed")), emptyList(), now)
        val same = track(200, "Once")
        val order = MixRanking.order(
            listOf(MixRanking.Candidate(same, 1f), MixRanking.Candidate(same, 0.5f)),
            taste,
            size = 10,
            seed = 1,
        )
        assertEquals(1, order.size)
    }

    /** Nothing to work from is not a crash. */
    @Test
    fun `no candidates gives an empty mix`() {
        val taste = MixProfile.build(emptyList(), emptyList(), now)
        assertTrue(MixRanking.order(emptyList(), taste, size = 20, seed = 1).isEmpty())
    }
}
