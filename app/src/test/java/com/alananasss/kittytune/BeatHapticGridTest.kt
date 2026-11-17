package com.alananasss.kittytune

import com.alananasss.kittytune.audio.haptics.BeatHapticGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The haptics used to run their own transient detector, independent of the analysis engine —
 * it fired on whatever rose in the low band, with no idea where the bar started. These tests
 * pin down the grid that now decides when a vibration is musically legitimate.
 */
class BeatHapticGridTest {

    // 128 BPM: beat 468.75 ms, bar 1875 ms. Anchor is a classified downbeat.
    private val grid = BeatHapticGrid(periodMs = 60_000.0 / 128.0, anchorMs = 500, beatsPerBar = 4)

    @Test
    fun `a transient on the beat passes`() {
        assertTrue(grid.isOnBeat(500))
        assertTrue(grid.isOnBeat(500 + 469))
        assertTrue("Small timing error is still the beat", grid.isOnBeat(500 + 469 + 40))
    }

    @Test
    fun `a transient between beats is rejected`() {
        // A fill, a vocal, a sample edge - real in the audio, not what a listener taps to.
        val offBeat = 500 + (grid.periodMs / 2).toLong()
        assertTrue("Exactly between two beats", !grid.isOnBeat(offBeat))
    }

    @Test
    fun `the tolerance window scales with tempo`() {
        // 60 ms is comfortable at 90 BPM and a third of a beat at 180, so a fixed window would
        // be far too loose on fast material - which is exactly where this app lives.
        val slow = BeatHapticGrid(60_000.0 / 90.0, 0, 4)
        val fast = BeatHapticGrid(60_000.0 / 180.0, 0, 4)
        assertTrue(slow.isOnBeat(100))
        assertTrue("100 ms off is not the beat at 180 BPM", !fast.isOnBeat(100))
    }

    @Test
    fun `bar position gives the bar a shape`() {
        val beat = grid.periodMs
        assertEquals(1, grid.beatInBar(500))
        assertEquals(2, grid.beatInBar(500 + beat.toLong()))
        assertEquals(3, grid.beatInBar(500 + (2 * beat).toLong()))
        assertEquals(4, grid.beatInBar(500 + (3 * beat).toLong()))
        assertEquals("Wraps to the next bar", 1, grid.beatInBar(500 + (4 * beat).toLong()))

        assertEquals(true, grid.isDownbeat(500))
        assertEquals(false, grid.isDownbeat(500 + beat.toLong()))

        // Beat 1 is the one a listener counts from, so it gets the strongest pulse.
        assertTrue(grid.emphasisAt(500) > grid.emphasisAt(500 + beat.toLong()))
        assertTrue(
            "Beat 3 is the secondary accent",
            grid.emphasisAt(500 + (2 * beat).toLong()) > grid.emphasisAt(500 + beat.toLong()),
        )
    }

    @Test
    fun `an untrusted downbeat claims no bar position`() {
        // beatsPerBar 0 means the classifier wasn't confident. Timing is still corrected, but a
        // wrong "this is beat 1" drives the strongest pulse in the bar, so it is never guessed.
        val flat = BeatHapticGrid(60_000.0 / 128.0, 500, beatsPerBar = 0)
        assertNull(flat.beatInBar(500))
        assertNull(flat.isDownbeat(500))
        assertEquals("Flat emphasis rather than a guessed accent", 1.0f, flat.emphasisAt(500), 1e-6f)
        assertTrue("Timing still works", flat.isOnBeat(500))
    }

    @Test
    fun `positions before the anchor are handled`() {
        // The anchor is the first downbeat, but playback can start before it.
        assertTrue(grid.isOnBeat(500 - grid.periodMs.toLong()))
        assertEquals(1, grid.beatInBar(500 - (4 * grid.periodMs).toLong()))
    }

    @Test
    fun `a nonsense grid is never usable`() {
        val broken = BeatHapticGrid(periodMs = 0.0, anchorMs = 0, beatsPerBar = 4)
        assertTrue(!broken.isUsable)
        assertTrue(!broken.isOnBeat(1234))
        assertNull(broken.beatInBar(1234))
    }
}
