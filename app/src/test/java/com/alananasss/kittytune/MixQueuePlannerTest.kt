package com.alananasss.kittytune

import com.alananasss.kittytune.data.mix.MixPlaylistAnalyzer
import com.alananasss.kittytune.data.mix.MixQueuePlanner
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The playlist Mix arithmetic: the Camelot cost table, the badge fit, and the greedy smart
// sort. The rules the tests pin are the ones a DJ checks by ear: a relative major/minor costs
// nothing, one wheel step is the cheap move, anything further is expensive, and the sort must
// always beat (or match) the order it was handed.
class MixQueuePlannerTest {

    private fun profile(bpm: Int, camelot: String?) =
        MixPlaylistAnalyzer.MixTrackProfile(songId = camelot ?: bpm.toString(), bpm = bpm, camelot = camelot)

    // Camelot step distance

    @Test
    fun `same number any letter is free - the relative swap`() {
        assertEquals(0f, MixQueuePlanner.camelotStepDistance("8A", "8A"))
        assertEquals(0f, MixQueuePlanner.camelotStepDistance("8A", "8B"))
        assertEquals(0f, MixQueuePlanner.camelotStepDistance("11B", "11A"))
    }

    @Test
    fun `one wheel step is the cheap move and wraps through twelve`() {
        assertEquals(0.5f, MixQueuePlanner.camelotStepDistance("8A", "9A"))
        assertEquals(0.5f, MixQueuePlanner.camelotStepDistance("8B", "7B"))
        // 12 -> 1 is one step around the wheel, not eleven.
        assertEquals(0.5f, MixQueuePlanner.camelotStepDistance("12A", "1A"))
    }

    @Test
    fun `further around the wheel costs proportionally more`() {
        val twoSteps = MixQueuePlanner.camelotStepDistance("8A", "10A")
        val far = MixQueuePlanner.camelotStepDistance("8A", "2B")
        val oneStep = MixQueuePlanner.camelotStepDistance("8A", "9A")
        assertTrue(twoSteps > oneStep, "two steps costs more than one step")
    }

    @Test
    fun `unknown codes pay the neutral cost`() {
        assertEquals(MixQueuePlanner.UNKNOWN_HARMONY_COST, MixQueuePlanner.camelotStepDistance(null, "8A"))
        assertEquals(MixQueuePlanner.UNKNOWN_HARMONY_COST, MixQueuePlanner.camelotStepDistance("8A", null))
        assertEquals(MixQueuePlanner.UNKNOWN_HARMONY_COST, MixQueuePlanner.camelotStepDistance("garbage", "8A"))
    }

    // tempo distance

    @Test
    fun `tempo cost is relative drift and symmetric`() {
        // 4 BPM around 120 is a bigger relative drift than 4 BPM around 160.
        val slow = MixQueuePlanner.tempoDistance(120, 124)
        val fast = MixQueuePlanner.tempoDistance(160, 164)
        assertEquals(4f / 122f, slow, absoluteTolerance = 0.001f)
        assertEquals(4f / 162f, fast, absoluteTolerance = 0.001f)
        assertTrue(slow > fast)
        assertEquals(slow, MixQueuePlanner.tempoDistance(124, 120), absoluteTolerance = 0.001f)
        assertEquals(0f, MixQueuePlanner.tempoDistance(124, 124))
        assertEquals(MixQueuePlanner.UNKNOWN_TEMPO_COST, MixQueuePlanner.tempoDistance(0, 124))
    }

    // badge fit

    @Test
    fun `key fit matches the badge colour rules`() {
        assertEquals(MixPlaylistAnalyzer.MixKeyFit.SAME, MixPlaylistAnalyzer.keyFit("8A", "8B"))
        assertEquals(MixPlaylistAnalyzer.MixKeyFit.NEIGHBOUR, MixPlaylistAnalyzer.keyFit("9A", "8A"))
        assertEquals(MixPlaylistAnalyzer.MixKeyFit.CLASH, MixPlaylistAnalyzer.keyFit("3B", "8A"))
        assertEquals(MixPlaylistAnalyzer.MixKeyFit.UNKNOWN, MixPlaylistAnalyzer.keyFit(null, "8A"))
        assertEquals(MixPlaylistAnalyzer.MixKeyFit.UNKNOWN, MixPlaylistAnalyzer.keyFit("8A", null))
    }

    // greedy smart sort

    @Test
    fun `sort keeps the first track and produces a valid permutation`() {
        val items = listOf(
            profile(124, "8A"),
            profile(126, "9A"),
            profile(140, "3B"),
            profile(124, "8B"),
            profile(168, "10A"),
        )
        val ordered = MixQueuePlanner.greedyMixOrder(items) { it }
        assertEquals(items.size, ordered.size, "nothing lost")
        assertEquals(items.size, ordered.distinctBy { it.songId }.size, "nothing duplicated")
        assertEquals(items.first().songId, ordered.first().songId, "the playing track leads")
        assertTrue(ordered.containsAll(items))
    }

    // Total walk cost of the greedy order must not be worse than the input order.
    @Test
    fun `sort never worsens the total walk cost`() {
        fun totalCost(order: List<MixPlaylistAnalyzer.MixTrackProfile>): Float {
            var sum = 0f
            for (i in 1 until order.size) sum += MixQueuePlanner.mixDistance(order[i - 1], order[i])
            return sum
        }

        val items = listOf(
            profile(100, "1A"),
            profile(174, "12B"),
            profile(102, "1B"),
            profile(170, "11B"),
            profile(104, "2A"),
            profile(128, "8A"),
        )
        val ordered = MixQueuePlanner.greedyMixOrder(items) { it }
        assertTrue(
            totalCost(ordered) <= totalCost(items) + 1e-4f,
            "greedy ${totalCost(ordered)} should not lose to the input ${totalCost(items)}",
        )
    }

    @Test
    fun `sort prefers harmonic neighbours over tempo twins with clashing keys`() {
        // From 8A/124: the tempo twin at a clashing key must lose to the harmonic neighbour.
        val items = listOf(
            profile(124, "8A"),
            profile(124, "3B"), // same BPM, far key
            profile(126, "9A"), // +2 BPM, one wheel step
        )
        val ordered = MixQueuePlanner.greedyMixOrder(items) { it }
        assertEquals("9A", ordered[1].camelot, "the harmonic neighbour goes second")
    }

    @Test
    fun `sort is deterministic`() {
        val items = listOf(
            profile(120, "5A"),
            profile(122, "6A"),
            profile(96, "12B"),
            profile(121, "5B"),
        )
        val first = MixQueuePlanner.greedyMixOrder(items) { it }
        val second = MixQueuePlanner.greedyMixOrder(items) { it }
        assertEquals(first.map { it.songId }, second.map { it.songId })
    }

    @Test
    fun `sort handles missing profiles without blocking the walk`() {
        val items = listOf(
            profile(124, null),
            profile(126, "9A"),
            profile(0, "3B"),
            profile(128, null),
        )
        val ordered = MixQueuePlanner.greedyMixOrder(items) { it }
        assertEquals(items.size, ordered.size)
        assertEquals(items.first().songId, ordered.first().songId)
    }

    @Test
    fun `tiny lists pass through untouched`() {
        val one = listOf(profile(124, "8A"))
        assertEquals(one, MixQueuePlanner.greedyMixOrder(one) { it })
        val two = listOf(profile(124, "8A"), profile(126, "9A"))
        assertEquals(two, MixQueuePlanner.greedyMixOrder(two) { it })
    }

    // euclidean mix distance

    @Test
    fun `distance is zero to itself and harmony weighs double`() {
        val a = profile(124, "8A")
        assertEquals(0f, MixQueuePlanner.mixDistance(a, a))
        // Harmony axis weighing double: a one-step key move (raw cost 0.5 × weight 2)
        // costs 1.0, while a 40 % tempo swing (100 -> 150 folded at 200 vs 150 = 50 / 175 ≈ 0.286)
        // or 120 -> 160 (raw cost 40 / 140 = 0.286) is far outweighed by harmony.
        val expectedTempo = MixQueuePlanner.tempoDistance(100, 150)
        val tempoOnly = MixQueuePlanner.mixDistance(profile(100, "8A"), profile(150, "8A"))
        val harmonyOnly = MixQueuePlanner.mixDistance(a, profile(124, "9A"))
        assertEquals(expectedTempo, tempoOnly, absoluteTolerance = 0.001f)
        assertEquals(1.0f, harmonyOnly, absoluteTolerance = 0.001f)
        assertTrue(harmonyOnly > 2f * tempoOnly)
    }
}
