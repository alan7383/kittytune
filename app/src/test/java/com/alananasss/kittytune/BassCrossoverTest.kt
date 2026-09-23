package com.alananasss.kittytune

import com.alananasss.kittytune.audio.automix.BeatPhaseLock
import com.alananasss.kittytune.audio.automix.LinkwitzRiley4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sin

/**
 * Measures the bass crossover rather than trusting it. The previous implementation carried a
 * doc comment claiming a 4th-order Linkwitz-Riley crossover while implementing a single biquad
 * subtraction that left -2.8 dB at 80 Hz; a test like this would have caught that immediately.
 */
class BassCrossoverTest {

    private val sampleRate = 48000
    private val crossoverHz = 160.0

    /** Steady-state RMS of a band at one frequency, in dB relative to the input. */
    private fun bandGainDb(freqHz: Double, band: (LinkwitzRiley4, Float) -> Float): Double {
        val filter = LinkwitzRiley4(sampleRate, crossoverHz)
        // Long enough for the IIR to settle, then measure only the tail.
        val total = sampleRate
        val settle = total / 2
        var sumSq = 0.0
        var refSq = 0.0
        for (n in 0 until total) {
            val x = sin(2.0 * Math.PI * freqHz * n / sampleRate).toFloat()
            val y = band(filter, x)
            if (n >= settle) {
                sumSq += y.toDouble() * y
                refSq += x.toDouble() * x
            }
        }
        return 10.0 * log10(sumSq / refSq)
    }

    private fun lowGainDb(f: Double) = bandGainDb(f) { flt, x -> flt.low(x) }
    private fun highGainDb(f: Double) = bandGainDb(f) { flt, x -> flt.high(x) }

    @Test
    fun `high band actually removes the kick fundamental`() {
        // The old mid - LP160(mid) approach managed only -2.8 dB here, which is why two decks
        // summed to +4.7 dB at 80 Hz and drove the limiter.
        val at80 = highGainDb(80.0)
        assertTrue("80 Hz must be cut by at least 20 dB, got ${"%.1f".format(at80)} dB", at80 < -20.0)

        val at40 = highGainDb(40.0)
        assertTrue("40 Hz must be cut by at least 40 dB, got ${"%.1f".format(at40)} dB", at40 < -40.0)
    }

    @Test
    fun `high band never boosts above the corner`() {
        // The old subtraction overshot to +2.1 dB around 200 Hz - it added bass while claiming
        // to remove it.
        for (f in listOf(200.0, 300.0, 500.0, 1000.0, 4000.0)) {
            val g = highGainDb(f)
            assertTrue("$f Hz must not be boosted, got ${"%.2f".format(g)} dB", g <= 0.2)
        }
    }

    @Test
    fun `passband is flat and stopband rolls off at 24 dB per octave`() {
        assertEquals("Well above the corner the high band is unity", 0.0, highGainDb(2000.0), 0.3)
        assertEquals("Well below the corner the low band is unity", 0.0, lowGainDb(20.0), 0.5)

        // Linkwitz-Riley is -6 dB at the corner, not -3 dB.
        assertEquals("Corner is -6 dB", -6.0, highGainDb(crossoverHz), 0.7)
        assertEquals("Corner is -6 dB", -6.0, lowGainDb(crossoverHz), 0.7)

        // One octave down should drop roughly 24 dB more.
        val slope = highGainDb(40.0) - highGainDb(80.0)
        assertTrue("Slope must be about 24 dB/oct, measured ${"%.1f".format(slope)}", slope < -18.0)
    }

    @Test
    fun `the two bands sum back to the original signal`() {
        // The defining Linkwitz-Riley property: low + high is allpass, so a handover between
        // them has neither a dip nor a bump at any frequency.
        for (f in listOf(40.0, 80.0, 160.0, 320.0, 1000.0)) {
            val filter = LinkwitzRiley4(sampleRate, crossoverHz)
            val total = sampleRate
            val settle = total / 2
            var sumSq = 0.0
            var refSq = 0.0
            for (n in 0 until total) {
                val x = sin(2.0 * Math.PI * f * n / sampleRate).toFloat()
                val y = filter.low(x) + filter.high(x)
                if (n >= settle) {
                    sumSq += y.toDouble() * y
                    refSq += x.toDouble() * x
                }
            }
            val db = 10.0 * log10(sumSq / refSq)
            assertEquals("low+high must be flat at $f Hz", 0.0, db, 0.3)
        }
    }

    @Test
    fun `two decks no longer pile up in the sub band`() {
        // Deck A at full bass, deck B fully killed, both with a kick at the same frequency.
        for (f in listOf(40.0, 60.0, 80.0, 100.0)) {
            val leak = Math.pow(10.0, highGainDb(f) / 20.0)
            val sumDb = 20.0 * log10(1.0 + leak)
            assertTrue(
                "Summed low end at $f Hz must stay under +1.5 dB, got ${"%.2f".format(sumDb)} dB",
                sumDb < 1.5,
            )
        }
    }

    @Test
    fun `reset clears filter memory`() {
        val filter = LinkwitzRiley4(sampleRate, crossoverHz)
        repeat(1000) { filter.low(1.0f); filter.high(1.0f) }
        filter.reset()
        assertEquals("Low band starts from silence", 0.0, filter.low(0f).toDouble(), 1e-9)
        assertEquals("High band starts from silence", 0.0, filter.high(0f).toDouble(), 1e-9)
    }

    @Test
    fun `crossover survives an absurd corner frequency`() {
        // Guards the Nyquist clamp: without it the bilinear warping blows the coefficients up.
        val filter = LinkwitzRiley4(8000, 100_000.0)
        var out = 0f
        repeat(500) { out = filter.low(0.5f) + filter.high(0.5f) }
        assertTrue("Output must stay finite", out.isFinite())
        assertTrue("Output must stay bounded", abs(out) < 10f)
    }

    // --- Swap placement ---

    @Test
    fun `bass swap lands on a phrase line, not a percentage of the fade`() {
        // 160 BPM: beat 375 ms, bar 1500 ms, 16-beat phrase 6000 ms.
        val grid = BeatPhaseLock.Grid(anchorMs = 1000, periodMs = 60_000.0 / 160.0)
        val phraseMs = grid.periodMs * 16

        val fadeStart = 40_000L
        val fadeMid = fadeStart + 3_000L
        val swap = grid.nearestLineMs(fadeMid, 16)

        val phrasesFromAnchor = (swap - grid.anchorMs) / phraseMs
        assertEquals(
            "Swap must sit exactly on a phrase boundary",
            0.0,
            abs(phrasesFromAnchor - Math.round(phrasesFromAnchor)),
            0.001,
        )
        assertTrue("Nearest line must be within half a phrase", abs(swap - fadeMid) <= phraseMs / 2 + 1)
    }

    @Test
    fun `nearest line beats next line for placement inside a short fade`() {
        // Anchor chosen so a phrase line sits at 2900 ms, just before the middle of the fade.
        val grid = BeatPhaseLock.Grid(anchorMs = 2900, periodMs = 60_000.0 / 160.0)

        // A 6 s fade starting at 0; its middle is 3000 ms, 100 ms past that phrase line.
        val fadeStart = 0L
        val fadeLen = 6_000L
        val mid = fadeStart + fadeLen / 2

        val next = grid.nextBarLineMs(mid, 16)
        val nearest = grid.nearestLineMs(mid, 16)

        assertTrue("The next phrase line falls past the end of the fade", next > fadeStart + fadeLen)
        assertTrue("The nearest one lands inside it", nearest in fadeStart..(fadeStart + fadeLen))
    }

    @Test
    fun `a line outside the window is rejected rather than clamped into it`() {
        // 128 BPM: beat 468.75 ms, bar 1875 ms, phrase 7500 ms.
        val grid = BeatPhaseLock.Grid(anchorMs = 6929, periodMs = 60_000.0 / 128.0)
        // The real fade that exposed this: no phrase line fits, so the answer must be null and
        // the caller must fall back - not a value clamped onto the window edge, which sits on
        // no musical boundary at all while still looking grid-aligned.
        val windowStart = 69_156L
        val windowEnd = 73_506L
        val target = 71_331L

        val phrase = grid.nearestLineWithin(target, 16, windowStart, windowEnd)
        assertTrue("No phrase line fits this window", phrase == null)

        val bar = grid.nearestLineWithin(target, 4, windowStart, windowEnd)
        assertTrue("A bar line must fit", bar != null)
        assertTrue("And it must be inside the window", bar!! in windowStart..windowEnd)

        val barsFromAnchor = (bar - grid.anchorMs) / (grid.periodMs * 4)
        assertEquals(
            "The fallback must sit exactly on a bar line",
            0.0,
            abs(barsFromAnchor - Math.round(barsFromAnchor)),
            0.001,
        )
    }

    @Test
    fun `windowed search finds a line when one exists anywhere in range`() {
        val grid = BeatPhaseLock.Grid(anchorMs = 0, periodMs = 500.0) // bar = 2000 ms
        // Target sits far from the window; the search must still walk to a line inside it.
        val found = grid.nearestLineWithin(target = 100_000L, beatsPerLine = 4, minMs = 10_000L, maxMs = 14_000L)
        assertTrue("Must find a line inside the window", found != null)
        assertTrue(found!! in 10_000L..14_000L)
        assertEquals("On a bar line", 0.0, (found % 2000L).toDouble(), 0.001)
    }

    @Test
    fun `an empty window yields no line`() {
        val grid = BeatPhaseLock.Grid(anchorMs = 0, periodMs = 500.0)
        // A 100 ms window between two bar lines contains none.
        assertTrue(grid.nearestLineWithin(2_500L, 4, 2_100L, 2_200L) == null)
        assertTrue("Inverted window is rejected", grid.nearestLineWithin(2_500L, 4, 5_000L, 1_000L) == null)
    }
}
