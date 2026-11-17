package com.alananasss.kittytune

import com.alananasss.kittytune.data.mix.MixProfile
import com.alananasss.kittytune.data.mix.MixRanking
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Which candidates a mix may keep, and where their audio is allowed to come from (issue #33).
 *
 * "Les titres injouables pourquoi ils sont injouables, car c'est des titres Go+ ? Si c'est le cas pourquoi ne pas
 * faire un fallback YouTube ? […] assure-toi que tous les titres du mix viennent uniquement de SoundCloud, mais
 * l'audio tu peux mettre YouTube uniquement si c'est Go+ ou injouable."
 *
 * He was right and my first filter was wrong. `SNIP` and `SUB_HIGH_TIER` are Go+ — a real SoundCloud track whose
 * full audio needs a subscription — and `StreamResolver` has resolved exactly those through YouTube for as long as
 * the setting has existed. Excluding them threw away tracks the player would have played.
 *
 * So the rule these tests hold is the one he stated: a restricted track stays in the mix when there is a route to
 * its audio, and is dropped only when there is none. The candidates are always SoundCloud's; only the route may
 * differ.
 */
class MixPlayabilityTest {

    private val now = 1_700_000_000_000L

    private fun track(
        id: Long,
        policy: String? = "MONETIZE",
        streamable: Boolean? = true,
        durationMs: Long = 200_000,
        monetization: String? = null,
    ) = Track(
        id = id,
        title = "track $id",
        artworkUrl = null,
        user = User(id = id * 100, username = "artist $id", avatarUrl = null),
        durationMs = durationMs,
        playbackCount = 1_000,
        policy = policy,
        monetizationModel = monetization,
        streamable = streamable,
    )

    private val emptyTaste = MixProfile.build(emptyList(), emptyList(), now)

    /**
     * The answer to his question, as an assertion. `SNIP` is Go+, the resolver gets it from YouTube, so it belongs
     * in the mix — and the value is `SNIP`, not `SNIPPET`, which is what my first filter looked for and why it was
     * a second wrong copy of a predicate that already existed.
     */
    @Test
    fun `a Go+ track stays in the mix when YouTube can play it`() {
        assertTrue(MixRanking.isPlayable(track(1, policy = "SNIP"), youtubeFallback = true))
        assertTrue(MixRanking.isPlayable(track(2, monetization = "SUB_HIGH_TIER"), youtubeFallback = true))
    }

    /** A blocked or unstreamable track is still a real SoundCloud track, and YouTube still has the song. */
    @Test
    fun `a blocked or unstreamable track stays in when YouTube can play it`() {
        assertTrue(MixRanking.isPlayable(track(3, policy = "BLOCK"), youtubeFallback = true))
        assertTrue(MixRanking.isPlayable(track(4, streamable = false), youtubeFallback = true))
    }

    /** With no route to the audio there is nothing to keep: a queue nobody vetted cannot carry a silent row. */
    @Test
    fun `the same tracks are dropped when the fallback is off`() {
        assertFalse(MixRanking.isPlayable(track(5, policy = "SNIP"), youtubeFallback = false))
        assertFalse(MixRanking.isPlayable(track(6, policy = "BLOCK"), youtubeFallback = false))
        assertFalse(MixRanking.isPlayable(track(7, monetization = "SUB_HIGH_TIER"), youtubeFallback = false))
        assertFalse(MixRanking.isPlayable(track(8, streamable = false), youtubeFallback = false))
    }

    /** The normal case, and the fields being absent must never be read as a refusal. */
    @Test
    fun `an ordinary track is playable either way`() {
        listOf(true, false).forEach { fallback ->
            assertTrue(MixRanking.isPlayable(track(9, policy = "MONETIZE"), fallback))
            assertTrue(MixRanking.isPlayable(track(10, policy = "ALLOW"), fallback))
            assertTrue(MixRanking.isPlayable(track(11, policy = null, streamable = null), fallback))
        }
    }

    /** The running order follows the same rule, since that is what actually reaches the queue. */
    @Test
    fun `the running order keeps restricted tracks only when they can be heard`() {
        val candidates = listOf(
            MixRanking.Candidate(track(20, policy = "BLOCK"), 1f),
            MixRanking.Candidate(track(21, policy = "SNIP"), 1f),
            MixRanking.Candidate(track(22), 0.2f),
        )
        assertEquals(
            listOf(20L, 21L, 22L),
            MixRanking.order(candidates, emptyTaste, size = 10, seed = 1, youtubeFallback = true)
                .map { it.id }.sorted(),
        )
        assertEquals(
            listOf(22L),
            MixRanking.order(candidates, emptyTaste, size = 10, seed = 1, youtubeFallback = false)
                .map { it.id },
        )
    }

    /**
     * A hundred is what he asked for, and it is reachable only if the candidates are there — so this checks the
     * shaping does not quietly cap it lower. Fifty artists, two tracks each, is exactly the boundary.
     */
    @Test
    fun `a hundred slots are filled when there are candidates for them`() {
        val candidates = (1L..60L).flatMap { artist ->
            listOf(
                MixRanking.Candidate(
                    Track(
                        id = artist * 10,
                        title = "a$artist",
                        artworkUrl = null,
                        user = User(id = artist, username = "artist$artist", avatarUrl = null),
                        durationMs = 200_000,
                        playbackCount = 100,
                    ),
                    1f,
                ),
                MixRanking.Candidate(
                    Track(
                        id = artist * 10 + 1,
                        title = "b$artist",
                        artworkUrl = null,
                        user = User(id = artist, username = "artist$artist", avatarUrl = null),
                        durationMs = 200_000,
                        playbackCount = 90,
                    ),
                    1f,
                ),
            )
        }
        val order = MixRanking.order(candidates, emptyTaste, size = 100, seed = 7, youtubeFallback = true)
        assertEquals(100, order.size, "a hundred candidates' worth of artists should fill a hundred slots")
        assertEquals(100, order.map { it.id }.distinct().size, "and none of them twice")
    }
}
