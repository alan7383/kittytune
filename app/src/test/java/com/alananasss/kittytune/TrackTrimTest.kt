package com.alananasss.kittytune

import com.alananasss.kittytune.audio.TrackTrim
import com.alananasss.kittytune.audio.TrimAction
import com.alananasss.kittytune.audio.TrimMode
import com.alananasss.kittytune.audio.TrimSegment
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Playing only the wanted parts of a track (issue #33).
 *
 * The reason this is tested rather than eyeballed: the failure mode of a wrong answer here is not a cosmetic
 * one. A jump that lands back inside the span it just left asks to jump again, immediately, for ever — the
 * track freezes and the app spins. So the properties worth writing down are "a jump always leaves the region
 * it jumped out of" and "the answer at the destination is Continue".
 */
class TrackTrimTest {

    private val minute = 60_000L
    private val track = 4 * minute

    private fun cut(vararg spans: Pair<Long, Long>) =
        TrackTrim.of(TrimMode.CUT, spans.map { TrimSegment(it.first, it.second) })

    private fun keep(vararg spans: Pair<Long, Long>) =
        TrackTrim.of(TrimMode.KEEP, spans.map { TrimSegment(it.first, it.second) })

    // --- nothing configured ---------------------------------------------------------------------

    @Test
    fun `no segments means nothing happens`() {
        assertEquals(TrimAction.Continue, TrackTrim.none().actionFor(0, track))
        assertEquals(TrimAction.Continue, TrackTrim.none().actionFor(track / 2, track))
        assertEquals(0L, TrackTrim.none().startPositionMs())
        assertTrue(TrackTrim.none().isEmpty)
    }

    // --- cut ------------------------------------------------------------------------------------

    @Test
    fun `outside a cut, playback continues`() {
        val trim = cut(75_000L to 100_000L)
        assertEquals(TrimAction.Continue, trim.actionFor(0, track))
        assertEquals(TrimAction.Continue, trim.actionFor(74_999L, track))
        assertEquals(TrimAction.Continue, trim.actionFor(100_000L, track))
        assertEquals(TrimAction.Continue, trim.actionFor(track - 1, track))
    }

    @Test
    fun `entering a cut jumps to its end`() {
        val trim = cut(75_000L to 100_000L)
        assertEquals(TrimAction.JumpTo(100_000L), trim.actionFor(75_000L, track))
        assertEquals(TrimAction.JumpTo(100_000L), trim.actionFor(90_000L, track))
        assertEquals(TrimAction.JumpTo(100_000L), trim.actionFor(99_999L, track))
    }

    /** The property that stops the loop: the destination is never itself a jump. */
    @Test
    fun `the destination of a jump is somewhere that continues`() {
        val trim = cut(10_000L to 20_000L, 20_000L to 30_000L, 45_000L to 50_000L)
        for (position in 0L..track step 137L) {
            val action = trim.actionFor(position, track)
            if (action is TrimAction.JumpTo) {
                assertEquals(
                    "jumping from $position landed on ${action.positionMs}, which jumps again",
                    TrimAction.Continue,
                    trim.actionFor(action.positionMs, track),
                    )
            }
        }
    }

    @Test
    fun `a run of adjacent cuts is one jump`() {
        // Merged into a single 10s–30s hole, so the far side is 30s and not 20s.
        val trim = cut(10_000L to 20_000L, 20_000L to 30_000L)
        assertEquals(TrimAction.JumpTo(30_000L), trim.actionFor(12_000L, track))
        assertEquals(1, trim.segments.size)
    }

    @Test
    fun `a cut that reaches the end of the track ends it`() {
        val trim = cut(3 * minute to 10 * minute)
        assertEquals(TrimAction.Finished, trim.actionFor(3 * minute + 5, track))
    }

    @Test
    fun `a cut at the very start moves the starting point`() {
        assertEquals(30_000L, cut(0L to 30_000L).startPositionMs())
        assertEquals(0L, cut(30_000L to 60_000L).startPositionMs())
    }

    // --- keep -----------------------------------------------------------------------------------

    @Test
    fun `keep starts at the first kept instant`() {
        assertEquals(30_000L, keep(30_000L to 120_000L).startPositionMs())
    }

    @Test
    fun `before the kept span, playback is forwarded into it`() {
        val trim = keep(30_000L to 120_000L)
        assertEquals(TrimAction.JumpTo(30_000L), trim.actionFor(0, track))
        assertEquals(TrimAction.JumpTo(30_000L), trim.actionFor(29_999L, track))
    }

    @Test
    fun `inside the kept span, playback continues`() {
        val trim = keep(30_000L to 120_000L)
        assertEquals(TrimAction.Continue, trim.actionFor(30_000L, track))
        assertEquals(TrimAction.Continue, trim.actionFor(119_999L, track))
    }

    /** The half of "keep" that "cut" cannot express: the track ends early. */
    @Test
    fun `past the kept span, the track is over`() {
        val trim = keep(30_000L to 120_000L)
        assertEquals(TrimAction.Finished, trim.actionFor(120_000L, track))
        assertEquals(TrimAction.Finished, trim.actionFor(track, track))
    }

    @Test
    fun `a gap between two kept spans is skipped`() {
        val trim = keep(10_000L to 20_000L, 40_000L to 50_000L)
        assertEquals(TrimAction.JumpTo(40_000L), trim.actionFor(20_000L, track))
        assertEquals(TrimAction.JumpTo(40_000L), trim.actionFor(30_000L, track))
        assertEquals(TrimAction.Continue, trim.actionFor(45_000L, track))
        assertEquals(TrimAction.Finished, trim.actionFor(50_000L, track))
    }

    @Test
    fun `keep never lands anywhere that jumps again`() {
        val trim = keep(10_000L to 20_000L, 40_000L to 50_000L)
        for (position in 0L..track step 97L) {
            val action = trim.actionFor(position, track)
            if (action is TrimAction.JumpTo) {
                assertEquals(TrimAction.Continue, trim.actionFor(action.positionMs, track))
            }
        }
    }

    // --- normalisation --------------------------------------------------------------------------

    @Test
    fun `overlapping spans are merged`() {
        val trim = cut(10_000L to 30_000L, 20_000L to 40_000L)
        assertEquals(listOf(TrimSegment(10_000L, 40_000L)), trim.segments)
    }

    @Test
    fun `spans out of order are sorted`() {
        val trim = cut(60_000L to 70_000L, 10_000L to 20_000L)
        assertEquals(listOf(10_000L, 60_000L), trim.segments.map { it.startMs })
    }

    /** Dragging one handle past the other is normal; it must not produce a span that eats the track. */
    @Test
    fun `impossible spans are dropped`() {
        assertTrue(cut(30_000L to 30_000L).isEmpty)
        assertTrue(cut(40_000L to 10_000L).isEmpty)
        assertTrue(cut(-5_000L to -1_000L).isEmpty)
    }

    // --- what a screen wants to say -------------------------------------------------------------

    @Test
    fun `cut reports the track minus the holes`() {
        assertEquals(track - 25_000L, cut(75_000L to 100_000L).playedDurationMs(track))
    }

    @Test
    fun `keep reports only what is kept`() {
        assertEquals(90_000L, keep(30_000L to 120_000L).playedDurationMs(track))
    }

    /** A span written past the end of the track counts only as far as the track goes. */
    @Test
    fun `spans beyond the track are clamped when measuring`() {
        assertEquals(minute, keep(3 * minute to 10 * minute).playedDurationMs(track))
    }
}
