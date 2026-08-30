package com.alananasss.kittytune

import com.alananasss.kittytune.data.stats.ListenRules
import com.alananasss.kittytune.data.stats.ListenSessionAccumulator
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * The listening accounting (issue #33).
 *
 * The desktop's copy of this file, run against this side's copy of the accumulator. Both apps have to agree
 * about what a listen is — the last time they did not, the same album produced different statistics on each
 * device — so the same tests hold both of them to it.
 *
 * Reported as "sometimes tracks just don't appear". Both causes are here: time that was credited when
 * nothing was heard, and a verdict that depended on which button ended the track rather than on how
 * much of it was listened to.
 */
class ListenSessionTest {

    /** The progress loop reports roughly four times a second. */
    private fun ListenSessionAccumulator.play(from: Long, to: Long, stepMs: Long = 250L) {
        onPlaying(from)
        var position = from
        while (position < to) {
            position = (position + stepMs).coerceAtMost(to)
            onPosition(position)
        }
    }

    @Test
    fun `nothing is credited before playback starts`() {
        val session = ListenSessionAccumulator()
        session.onPosition(5_000)
        session.onPosition(10_000)
        assertEquals(0L, session.listenedMs)
    }

    @Test
    fun `playing through a track credits its length`() {
        val session = ListenSessionAccumulator()
        session.play(from = 0, to = 60_000)
        assertEquals(60_000L, session.listenedMs)
    }

    @Test
    fun `a pause credits nothing however long it lasts`() {
        val session = ListenSessionAccumulator()
        session.play(from = 0, to = 20_000)
        session.onPaused()
        // The loop may keep reporting, and the clock keeps running; neither is listening.
        session.onPosition(20_000)
        session.onPosition(20_000)
        session.onPlaying(20_000)
        session.play(from = 20_000, to = 30_000)
        assertEquals(30_000L, session.listenedMs)
    }

    @Test
    fun `a seek forward does not count as listening`() {
        val session = ListenSessionAccumulator()
        session.play(from = 0, to = 10_000)
        session.onSeek(120_000)
        session.play(from = 120_000, to = 125_000)
        assertEquals(15_000L, session.listenedMs)
    }

    @Test
    fun `a jump with no seek notification is still not credited`() {
        // A reload or a loop can move the position without anyone telling us.
        val session = ListenSessionAccumulator()
        session.onPlaying(0)
        session.onPosition(250)
        session.onPosition(200_000)
        assertEquals(250L, session.listenedMs)
    }

    @Test
    fun `replaying the same passage counts it twice, because it was heard twice`() {
        val session = ListenSessionAccumulator()
        session.play(from = 30_000, to = 60_000)
        session.onSeek(30_000)
        session.play(from = 30_000, to = 60_000)
        assertEquals(60_000L, session.listenedMs)
    }

    @Test
    fun `listened time never goes backwards on a rewind`() {
        val session = ListenSessionAccumulator()
        session.play(from = 0, to = 30_000)
        session.onSeek(0)
        assertEquals(30_000L, session.listenedMs)
    }

    @Test
    fun `the furthest point reached is remembered across seeks`() {
        val session = ListenSessionAccumulator()
        session.play(from = 0, to = 100_000)
        session.onSeek(10_000)
        session.play(from = 10_000, to = 20_000)
        assertEquals(100_000L, session.furthestPositionMs)
    }

    @Test
    fun `resuming from the middle does not credit the part that was skipped`() {
        val session = ListenSessionAccumulator(startPositionMs = 90_000)
        session.play(from = 90_000, to = 120_000)
        assertEquals(30_000L, session.listenedMs)
    }

    @Test
    fun `thirty seconds is a play whatever the track's length`() {
        assertTrue(ListenRules.countsAsPlay(30_000, trackDurationMs = 10 * 60_000))
        assertFalse(ListenRules.countsAsPlay(29_999, trackDurationMs = 10 * 60_000))
    }

    @Test
    fun `half of a short track is a play even below thirty seconds`() {
        // A twenty-second interlude can never reach thirty seconds of listening.
        assertTrue(ListenRules.countsAsPlay(10_000, trackDurationMs = 20_000))
        assertFalse(ListenRules.countsAsPlay(4_000, trackDurationMs = 20_000))
    }

    @Test
    fun `an unknown duration is judged on the absolute threshold alone`() {
        assertTrue(ListenRules.countsAsPlay(30_000, trackDurationMs = 0))
        assertFalse(ListenRules.countsAsPlay(5_000, trackDurationMs = 0))
    }

    @Test
    fun `nothing listened is never a play`() {
        assertFalse(ListenRules.countsAsPlay(0, trackDurationMs = 200_000))
        assertFalse(ListenRules.countsAsPlay(-1, trackDurationMs = 200_000))
    }

    @Test
    fun `reaching the outro counts as finishing the track`() {
        val duration = 200_000L
        assertTrue(ListenRules.isComplete(furthestPositionMs = 195_000, trackDurationMs = duration))
        assertFalse(ListenRules.isComplete(furthestPositionMs = 150_000, trackDurationMs = duration))
    }

    @Test
    fun `skipping to the end still counts as finishing it`() {
        // Judged on where playback reached, not on how much was heard: this is what the old code got
        // backwards by keying off which button ended the track.
        val session = ListenSessionAccumulator()
        session.play(from = 0, to = 5_000)
        session.onSeek(199_000)
        session.play(from = 199_000, to = 200_000)
        assertTrue(ListenRules.isComplete(session.furthestPositionMs, trackDurationMs = 200_000))
    }

    @Test
    fun `a track played to the end and then skipped is still a play`() {
        // The case that used to score nothing at all, because the row was written as SKIP_NEXT and the
        // aggregates only counted PLAY_COMPLETE.
        val duration = 200_000L
        val session = ListenSessionAccumulator()
        session.play(from = 0, to = 190_000)
        assertTrue(ListenRules.countsAsPlay(session.listenedMs, duration))
        assertFalse(ListenRules.isSkip(session.listenedMs, session.furthestPositionMs, duration))
    }

    @Test
    fun `a real skip is leaving early having heard almost nothing`() {
        val duration = 200_000L
        val session = ListenSessionAccumulator()
        session.play(from = 0, to = 4_000)
        assertTrue(ListenRules.isSkip(session.listenedMs, session.furthestPositionMs, duration))
    }

    @Test
    fun `pausing halfway through is not a skip`() {
        val duration = 200_000L
        val session = ListenSessionAccumulator()
        session.play(from = 0, to = 100_000)
        session.onPaused()
        assertFalse(ListenRules.isSkip(session.listenedMs, session.furthestPositionMs, duration))
    }

    @Test
    fun `a full listen at any speed credits the track's own length`() {
        // Media time, not clock time: the loop reports bigger steps at higher speed, and the total is
        // the music that went past either way.
        val fast = ListenSessionAccumulator()
        fast.play(from = 0, to = 180_000, stepMs = 500L)
        val normal = ListenSessionAccumulator()
        normal.play(from = 0, to = 180_000, stepMs = 250L)
        assertEquals(normal.listenedMs, fast.listenedMs)
    }
}
