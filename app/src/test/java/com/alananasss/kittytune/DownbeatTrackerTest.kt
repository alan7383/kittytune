package com.alananasss.kittytune

import com.alananasss.kittytune.audio.automix.BeatAnalyzer
import com.alananasss.kittytune.audio.automix.BeatPhaseLock
import com.alananasss.kittytune.audio.automix.DownbeatTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

/**
 * Covers the part of the engine a plain peak detector cannot do: telling beat 1 from beats 2-4,
 * resolving the tempo octave, and locking two decks to the same beat phase.
 */
class DownbeatTrackerTest {

    private val frameRate = 86f // ~44100 / 512 hop

    /**
     * Builds band onset envelopes for a 4/4 drum pattern.
     *
     * @param downbeatAt which beat index carries beat 1.
     * @param fourOnFloor kick on every beat instead of only on 1 and 3.
     */
    private fun drumPattern(
        beats: Int,
        periodFrames: Float,
        phaseFrames: Float,
        downbeatAt: Int,
        fourOnFloor: Boolean = false,
        phraseAt: Int = 0,
    ): DownbeatTracker.BandOnsets {
        val frames = ((phaseFrames + beats * periodFrames).toInt() + 20)
        val low = FloatArray(frames)
        val mid = FloatArray(frames)
        val high = FloatArray(frames)
        val full = FloatArray(frames)

        for (k in 0 until beats) {
            val f = (phaseFrames + k * periodFrames).toInt()
            if (f !in 0 until frames) continue
            val posInBar = Math.floorMod(k - downbeatAt, 4)
            val barIndex = Math.floorDiv(k - downbeatAt, 4)

            // Kick: on the downbeat always, plus beat 3, plus every beat when four-on-the-floor.
            if (posInBar == 0 || fourOnFloor || posInBar == 2) {
                low[f] += if (posInBar == 0) 1.0f else 0.7f
            }
            // Snare / clap on the backbeats: beats 2 and 4.
            if (posInBar == 1 || posInBar == 3) {
                mid[f] += 0.8f
                high[f] += 0.9f
            }
            // Hats everywhere, weakly.
            high[f] += 0.2f
            full[f] += low[f] + mid[f] + high[f]

            // Phrase boundaries carry an arrangement change: a lasting energy shift.
            if (posInBar == 0 && Math.floorMod(barIndex - phraseAt, 4) == 0) {
                val span = periodFrames.toInt()
                for (o in 0 until span) {
                    val idx = f + o
                    if (idx < frames) {
                        low[idx] += 0.5f
                        high[idx] += 0.4f
                        full[idx] += 0.9f
                    }
                }
            }
        }
        return DownbeatTracker.BandOnsets(low, mid, high, full, frameRate)
    }

    @Test
    fun `identifies beat 1 when the grid anchor is beat 3`() {
        // The beat grid is correct but its beat 0 is musically beat 3 - exactly the case a
        // peak detector cannot distinguish and that drops a track two beats off the One.
        val period = 40f
        val onsets = drumPattern(beats = 64, periodFrames = period, phaseFrames = 7f, downbeatAt = 2)

        val result = DownbeatTracker.estimate(onsets, period, 7f)

        assertEquals("Beat 1 must be found at grid index 2", 2, result.barPhaseBeats)
        assertTrue("A clear backbeat pattern should be confident", result.confidence > 0.1f)
    }

    @Test
    fun `finds beat 1 on four-on-the-floor where the kick carries no information`() {
        // Every beat has a kick, so low-band energy is flat across all four candidates.
        // Only the clap on 2 and 4 separates them.
        val period = 34f
        val onsets = drumPattern(
            beats = 64, periodFrames = period, phaseFrames = 3f,
            downbeatAt = 1, fourOnFloor = true,
        )

        val result = DownbeatTracker.estimate(onsets, period, 3f)

        assertEquals("Backbeat must resolve the downbeat", 1, result.barPhaseBeats)
    }

    @Test
    fun `finds the phrase boundary among the bars`() {
        val period = 30f
        val onsets = drumPattern(
            beats = 128, periodFrames = period, phaseFrames = 5f,
            downbeatAt = 0, phraseAt = 2,
        )

        val result = DownbeatTracker.estimate(onsets, period, 5f)

        assertEquals("Downbeat", 0, result.barPhaseBeats)
        assertEquals("Phrase must start on bar 2", 2, result.phrasePhaseBars)
    }

    @Test
    fun `degrades safely on material with no rhythmic content`() {
        val flat = FloatArray(500) { 0.01f }
        val onsets = DownbeatTracker.BandOnsets(flat, flat.copyOf(), flat.copyOf(), flat.copyOf(), frameRate)

        val result = DownbeatTracker.estimate(onsets, 40f, 0f)

        assertTrue("Phase must stay in range", result.barPhaseBeats in 0..3)
        assertTrue("Flat input must not claim confidence", result.confidence < 0.3f)
    }

    @Test
    fun `tempo prior favours the musically plausible octave`() {
        // A halftime 160 BPM track correlates identically at 80 BPM; the prior breaks the tie.
        assertTrue(
            "160 BPM must outrank 80 BPM",
            BeatAnalyzer.tempoPrior(160f) > BeatAnalyzer.tempoPrior(80f),
        )
        assertTrue(
            "128 BPM must outrank 256 BPM",
            BeatAnalyzer.tempoPrior(128f) > BeatAnalyzer.tempoPrior(256f),
        )
        assertTrue("Prior peaks near 125 BPM", BeatAnalyzer.tempoPrior(125f) > 0.99f)
    }

    @Test
    fun `octave resolution keeps a halftime track at its real tempo`() {
        // 160 BPM pulse with an accent every second beat, which is what makes autocorrelation
        // report 80 BPM. The resolver must stay at the 160 BPM period.
        val period = frameRate * 60f / 160f
        val frames = 4000
        val flux = FloatArray(frames)
        for (k in 0 until (frames / period).toInt()) {
            val f = (k * period).toInt()
            if (f < frames) flux[f] = if (k % 2 == 0) 1.0f else 0.75f
        }

        val resolved = BeatAnalyzer.resolveTempoOctave(flux, frameRate, period * 2f)
        val bpm = 60f * frameRate / resolved

        assertEquals("Halftime accent must not halve the tempo", 160f, bpm, 6f)
    }

    @Test
    fun `grid pipeline places the downbeat and phrase anchor on real bar lines`() {
        val sampleRate = 44100
        val bpm = 120f
        val periodMs = 60_000.0 / bpm
        val samplesPerBeat = (sampleRate * periodMs / 1000.0).toInt()
        val beats = 80
        val samples = FloatArray(samplesPerBeat * beats)

        // Synthesised 4/4: 60 Hz kick on beat 1 and 3, a broadband clap on 2 and 4.
        for (k in 0 until beats) {
            val start = k * samplesPerBeat
            when (k % 4) {
                0, 2 -> for (i in 0 until samplesPerBeat / 4) {
                    val t = i.toDouble() / sampleRate
                    val env = kotlin.math.exp(-25.0 * t)
                    samples[start + i] += (sin(2 * Math.PI * 60 * t) * env).toFloat()
                }
                else -> for (i in 0 until samplesPerBeat / 8) {
                    val t = i.toDouble() / sampleRate
                    val env = kotlin.math.exp(-60.0 * t)
                    val noise = ((i * 1103515245 + 12345) and 0x7FFFFFFF) / 1.073741824E9 - 1.0
                    samples[start + i] += (noise * env * 0.8).toFloat()
                }
            }
        }

        val grid = BeatAnalyzer.computeGrid(samples, sampleRate, windowStartMs = 0L)

        assertNotNull("Grid must be produced for a clean 4/4 pattern", grid)
        assertEquals("Tempo", 120.0, grid!!.bpm.toDouble(), 3.0)

        // The downbeat must sit on a beat line, and the phrase anchor on a bar line from it.
        val beatsFromFirst = (grid.downbeatOffsetMs - grid.firstBeatOffsetMs) / grid.periodMs
        assertTrue(
            "Downbeat must land on a beat of the grid (got $beatsFromFirst beats)",
            abs(beatsFromFirst - Math.round(beatsFromFirst)) < 0.02,
        )
        val barsFromDownbeat = (grid.phraseOffsetMs - grid.downbeatOffsetMs) / (grid.periodMs * 4)
        assertTrue(
            "Phrase anchor must land on a bar line (got $barsFromDownbeat bars)",
            abs(barsFromDownbeat - Math.round(barsFromDownbeat)) < 0.02,
        )
        assertTrue("Downbeat is within the first bar", grid.downbeatOffsetMs < 4 * grid.periodMs + 1)
        assertTrue("Phrase anchor is within the first phrase", grid.phraseOffsetMs < 16 * grid.periodMs + 1)
    }

    // --- Phase lock ---

    @Test
    fun `phase error is zero for decks already in sync`() {
        val grid = BeatPhaseLock.Grid(anchorMs = 200, periodMs = 500.0)
        val out = grid.deckAt(positionMs = 5_200)
        val incoming = BeatPhaseLock.Grid(anchorMs = 0, periodMs = 500.0).deckAt(positionMs = 10_000)

        assertEquals(0.0, BeatPhaseLock.phaseErrorMs(out, incoming), 0.001)
        assertTrue(BeatPhaseLock.isLocked(BeatPhaseLock.phaseErrorMs(out, incoming)))
    }

    @Test
    fun `detects the 15ms offset that causes galloping`() {
        // Both decks at 128 BPM, incoming started 15 ms late: the case from the bug report.
        val periodMs = 60_000.0 / 128f
        val out = BeatPhaseLock.Grid(anchorMs = 0, periodMs = periodMs).deckAt(positionMs = 30_000)
        val incoming = BeatPhaseLock.Grid(anchorMs = 0, periodMs = periodMs).deckAt(positionMs = 30_000 - 15)

        val error = BeatPhaseLock.phaseErrorMs(out, incoming)

        assertEquals("Incoming deck is 15 ms late", 15.0, error, 0.5)
        assertTrue("15 ms must not count as locked", !BeatPhaseLock.isLocked(error))
        assertTrue("15 ms is worth a seek while silent", BeatPhaseLock.needsSeek(error))
    }

    @Test
    fun `correction takes the short way round the beat`() {
        val periodMs = 500.0
        // 480 ms late is really 20 ms early; correcting the long way would skip almost a beat.
        val wrapped = BeatPhaseLock.wrapToHalfBeat(480.0, periodMs)
        assertEquals(-20.0, wrapped, 0.001)
        assertTrue("Correction stays within half a beat", abs(wrapped) <= periodMs / 2)
    }

    @Test
    fun `seek correction is scaled into the stretched deck timeline`() {
        // A deck played at 1.05x covers 1.05 ms of its own timeline per wall-clock ms.
        val incoming = BeatPhaseLock.Grid(anchorMs = 0, periodMs = 468.75).deckAt(positionMs = 1000, rate = 1.05f)
        assertEquals(21L, BeatPhaseLock.correctionSeekMs(20.0, incoming))
    }

    @Test
    fun `correction authority scales with how audible the deck is`() {
        // A silent deck can be pulled hard; a deck at full level cannot.
        assertEquals(BeatPhaseLock.MAX_NUDGE_SILENT, BeatPhaseLock.nudgeAuthority(0f), 1e-6f)
        assertEquals(BeatPhaseLock.MAX_NUDGE, BeatPhaseLock.nudgeAuthority(1f), 1e-6f)
        assertTrue(
            "Authority must fall as the deck comes up",
            BeatPhaseLock.nudgeAuthority(0.2f) > BeatPhaseLock.nudgeAuthority(0.8f),
        )
        // Even at full authority the pitch shift stays under a semitone (~5.9%).
        assertTrue("Never audible as a pitch change", BeatPhaseLock.MAX_NUDGE_SILENT < 0.059f)
    }

    @Test
    fun `a start-up offset is absorbed in the first third of the fade`() {
        // The measured non-prebuffered case: ~120 ms of offset, corrected in 240 ms windows.
        // Correction rate is bounded by authority alone, so this is the honest floor:
        // 120 ms / 5 % ≈ 2.5 s, roughly a third of a typical 8 s fade. Good enough that the
        // decks are in phase before the incoming one is prominent, but the reason a
        // prebuffered transition is preferable - that one locks by seek inside 1 ms.
        val windowMs = 240L
        val authority = BeatPhaseLock.nudgeAuthority(0.1f) // deck barely audible
        val correctionMs = 120.0 / authority

        assertTrue(
            "120 ms must be gone within a third of an 8 s fade (needed ${correctionMs.toInt()} ms)",
            correctionMs < 8_000 / 3.0,
        )
        // With the old flat 1.5 % clamp the same error needed the entire fade.
        assertTrue(
            "Scaled authority must beat the flat clamp",
            correctionMs < 120.0 / BeatPhaseLock.MAX_NUDGE,
        )
        assertEquals("Window length does not change the rate", 240L, windowMs)
    }

    @Test
    fun `drift nudge stays below the audible pitch threshold`() {
        val base = 1.0f
        // A deliberately huge error must still be clamped.
        val nudged = BeatPhaseLock.nudgeSpeed(errorMs = 5000.0, windowMs = 240L, baseSpeed = base)

        assertTrue("Nudge must be clamped", nudged <= base * (1f + BeatPhaseLock.MAX_NUDGE) + 1e-6f)
        assertTrue("Nudge must speed a late deck up", nudged > base)

        val slowed = BeatPhaseLock.nudgeSpeed(errorMs = -5000.0, windowMs = 240L, baseSpeed = base)
        assertTrue("Nudge must slow an early deck down", slowed < base)
        assertTrue("Nudge must be clamped both ways", slowed >= base * (1f - BeatPhaseLock.MAX_NUDGE) - 1e-6f)
    }

    @Test
    fun `small drift is erased within one correction window`() {
        assertEquals(
            "Errors inside the lock tolerance must not be touched",
            1.0f,
            BeatPhaseLock.nudgeSpeed(errorMs = 2.0, windowMs = 240L, baseSpeed = 1.0f),
            1e-6f,
        )

        // 6 ms over a 1000 ms window is 0.6 % - above tolerance, inside the clamp,
        // so it is corrected exactly within that window.
        val windowMs = 1000L
        val nudged = BeatPhaseLock.nudgeSpeed(errorMs = 6.0, windowMs = windowMs, baseSpeed = 1.0f)
        assertEquals(1.0f + 6.0f / windowMs, nudged, 1e-5f)
        assertTrue("Correction stays inaudible", nudged - 1.0f < BeatPhaseLock.MAX_NUDGE)
    }

    @Test
    fun `bass handover lands on a bar line`() {
        val grid = BeatPhaseLock.Grid(anchorMs = 320, periodMs = 500.0) // 120 BPM, bar = 2000 ms
        // Mid-bar position: the next bar line is the one after it, never the current position.
        val next = grid.nextBarLineMs(positionMs = 5_000)

        assertEquals(6_320L, next)
        assertTrue("Handover must be in the future", next > 5_000)
        assertEquals("Must be a whole number of bars from the anchor", 0.0, (next - 320) % 2000.0, 0.001)
    }
}
