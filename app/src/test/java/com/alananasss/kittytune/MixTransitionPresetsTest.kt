package com.alananasss.kittytune

import com.alananasss.kittytune.audio.automix.KeyHarmony
import com.alananasss.kittytune.audio.automix.MixTransitionPresets
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The eight Spotify-Mix transition presets, as math. Everything a listener hears out of a
// transition starts as one of these frames, so the tests pin the invariants the ear depends
// on: loudness-flat fades, monotonic sweeps, a tape stop that actually stops, and the Auto
// rulebook matching what a DJ would pick by hand.
class MixTransitionPresetsTest {

    private fun frame(style: MixTransitionPresets.MixTransitionStyle, p: Float) =
        MixTransitionPresets.frame(style, p, beatsTotal = 16, bpm = 124f)

    // equal-power fades

    // sin² + cos² = 1 at every point of the fade: the loudness-flat promise of equal power.
    @Test
    fun `fade keeps constant power`() {
        var p = 0f
        while (p <= 1f) {
            val f = frame(MixTransitionPresets.MixTransitionStyle.FADE, p)
            val power = f.fadeIn * f.fadeIn + f.fadeOut * f.fadeOut
            assertEquals(1f, power, absoluteTolerance = 0.002f, message = "at p=$p")
            p += 0.05f
        }
    }

    @Test
    fun `fade starts silent in and ends silent out`() {
        val start = frame(MixTransitionPresets.MixTransitionStyle.FADE, 0f)
        val end = frame(MixTransitionPresets.MixTransitionStyle.FADE, 1f)
        assertEquals(0f, start.fadeIn, absoluteTolerance = 1e-4f)
        assertEquals(1f, start.fadeOut, absoluteTolerance = 1e-4f)
        assertEquals(1f, end.fadeIn, absoluteTolerance = 1e-4f)
        assertEquals(0f, end.fadeOut, absoluteTolerance = 1e-4f)
    }

    // Both hands never leave silence or double up: each frame has at most one deck audible.
    @Test
    fun `slam is a hard cut with limiter only at the seam`() {
        val before = frame(MixTransitionPresets.MixTransitionStyle.SLAM, 0.49f)
        val after = frame(MixTransitionPresets.MixTransitionStyle.SLAM, 0.51f)
        assertEquals(1f, before.fadeOut)
        assertEquals(0f, before.fadeIn)
        assertEquals(0, before.limiterHoldMs)
        assertEquals(0f, after.fadeOut)
        assertEquals(1f, after.fadeIn)
        assertEquals(50, after.limiterHoldMs)
    }

    // blend

    // The bass handover happens once, at the midpoint, and mids ride equal power.
    @Test
    fun `blend swaps bass at the midpoint`() {
        val before = frame(MixTransitionPresets.MixTransitionStyle.BLEND, 0.45f)
        val after = frame(MixTransitionPresets.MixTransitionStyle.BLEND, 0.55f)
        assertTrue(!before.bassSwapOut)
        assertTrue(!before.bassSwapIn)
        assertTrue(after.bassSwapOut)
        assertTrue(after.bassSwapIn)
        // Duck shelves move mirror-wise: as A's low end leaves, B's arrives.
        assertTrue(before.bassDuckOut < after.bassDuckOut)
        assertTrue(before.bassDuckIn > after.bassDuckIn)
        // Levels still ride equal power.
        val power = before.fadeIn * before.fadeIn + before.fadeOut * before.fadeOut
        assertEquals(1f, power, absoluteTolerance = 0.002f)
    }

    // rise

    @Test
    fun `rise sweeps the cutoff up and cuts on the drop`() {
        var previousHz = 0f
        var p = 0.02f
        while (p < 0.45f) {
            val f = frame(MixTransitionPresets.MixTransitionStyle.RISE, p)
            val hz = f.sweepHz ?: 0f
            // Geometric sweep: strictly increasing, always inside 40..3000.
            assertTrue(hz > previousHz, "sweep must rise, at p=$p hz=$hz")
            assertTrue(hz >= 40f && hz <= 3000f)
            // Resonance grows with the sweep.
            assertTrue(f.sweepQ >= 0.7f)
            previousHz = hz
            p += 0.05f
        }
        val drop = frame(MixTransitionPresets.MixTransitionStyle.RISE, 0.9f)
        assertEquals(1f, drop.fadeIn)
        assertEquals(0f, drop.fadeOut)
        assertEquals(50, drop.limiterHoldMs)
    }

    // wave

    // Anti-phase: the pair starts at opposite band ends, crosses mid-cycle, and swaps roles.
    @Test
    fun `wave sweeps both decks in counter-phase`() {
        val start = frame(MixTransitionPresets.MixTransitionStyle.WAVE, 0.0f)
        val crossing = frame(MixTransitionPresets.MixTransitionStyle.WAVE, 0.125f)
        val swapped = frame(MixTransitionPresets.MixTransitionStyle.WAVE, 0.25f)
        val out0 = start.sweepHz ?: 0f
        val in0 = start.sweepHzIn ?: 0f
        // At p=0 the pair sits at opposite ends of the band...
        assertTrue((out0 - in0) > 600f, "counter-phase start, got $out0 vs $in0")
        // ...a quarter cycle later both pass through the middle...
        val midGap = kotlin.math.abs((crossing.sweepHz ?: 0f) - (crossing.sweepHzIn ?: 0f))
        assertTrue(midGap < 100f, "crossing the middle, got gap $midGap")
        // ...and at half a cycle the roles have swapped (A low, B high).
        val outS = swapped.sweepHz ?: 0f
        val inS = swapped.sweepHzIn ?: 0f
        assertTrue((inS - outS) > 600f, "roles swapped, got $outS vs $inS")
        assertTrue(out0 in 300f..2400f && in0 in 300f..2400f)
    }

    // melt (tape stop)

    @Test
    fun `melt slows monotonically and stops exactly`() {
        val meltStart = 0.75f
        val before = frame(MixTransitionPresets.MixTransitionStyle.MELT, meltStart - 0.1f)
        assertEquals(1f, before.speedOut)

        var previous = Float.MAX_VALUE
        var p = meltStart
        while (p <= 1f) {
            val f = frame(MixTransitionPresets.MixTransitionStyle.MELT, p)
            assertTrue(f.speedOut <= previous, "monotonic slowdown at p=$p")
            assertTrue(f.speedOut in 0f..1f)
            previous = f.speedOut
            p += 0.05f
        }
        val stopped = frame(MixTransitionPresets.MixTransitionStyle.MELT, 1f)
        assertEquals(0f, stopped.speedOut, absoluteTolerance = 1e-4f)
        assertEquals(1f, stopped.fadeIn)
        assertEquals(0f, stopped.fadeOut)
    }

    // dissolve

    @Test
    fun `dissolve decays the tail exponentially under the incoming track`() {
        var previousOut = Float.MAX_VALUE
        var p = 0.4f
        while (p <= 1f) {
            val f = frame(MixTransitionPresets.MixTransitionStyle.DISSOLVE, p)
            assertTrue(f.fadeOut <= previousOut, "tail must decay, at p=$p")
            assertTrue(f.fadeIn >= previousOut * 0f) // incoming keeps rising overall
            previousOut = f.fadeOut
            p += 0.05f
        }
        // Under the tail the incoming deck is already arriving: it is audible early.
        val under = frame(MixTransitionPresets.MixTransitionStyle.DISSOLVE, 0.5f)
        assertTrue(under.fadeIn > 0.4f)
    }

    // Auto rulebook

    @Test
    fun `auto picks blend for a tight harmonic pair`() {
        val style = MixTransitionPresets.autoSelect(
            outBpm = 124f, inBpm = 125f,
            keyFit = KeyHarmony.Fit.SAME, energyGap = 0.1f,
        )
        assertEquals(MixTransitionPresets.MixTransitionStyle.BLEND, style)
    }

    @Test
    fun `auto picks rise for a fast incoming track`() {
        val style = MixTransitionPresets.autoSelect(
            outBpm = 120f, inBpm = 132f, // +10 %
            keyFit = KeyHarmony.Fit.SAME, energyGap = 0.1f,
        )
        assertEquals(MixTransitionPresets.MixTransitionStyle.RISE, style)
    }

    @Test
    fun `auto picks slam when keys clash and energy jumps`() {
        val style = MixTransitionPresets.autoSelect(
            outBpm = 124f, inBpm = 124f,
            keyFit = KeyHarmony.Fit.CLASH, energyGap = 0.5f,
        )
        assertEquals(MixTransitionPresets.MixTransitionStyle.SLAM, style)
    }

    @Test
    fun `auto picks dissolve for a clash without an energy jump`() {
        val style = MixTransitionPresets.autoSelect(
            outBpm = 124f, inBpm = 124f,
            keyFit = KeyHarmony.Fit.CLASH, energyGap = 0.1f,
        )
        assertEquals(MixTransitionPresets.MixTransitionStyle.DISSOLVE, style)
    }

    @Test
    fun `auto falls back to fade for mild drift with unknown keys`() {
        val style = MixTransitionPresets.autoSelect(
            outBpm = 124f, inBpm = 129f, // ~4 %: outside blend tolerance, inside rise
            keyFit = null, energyGap = null,
        )
        assertEquals(MixTransitionPresets.MixTransitionStyle.FADE, style)
    }

    // tempo release

    @Test
    fun `tempo release never exceeds half a percent per second`() {
        val schedule = MixTransitionPresets.tempoReleaseSchedule(
            fromSpeed = 1.06f, toSpeed = 1.0f, bpm = 124f, beats = 32,
        )
        assertTrue(schedule.isNotEmpty())
        var previousAtMs = 0L
        var previousSpeed = 1.06f
        for (step in schedule) {
            val dtSeconds = (step.atMs - previousAtMs) / 1000f
            val rate = kotlin.math.abs(previousSpeed - step.speed) / dtSeconds
            assertTrue(rate <= 0.0055f, "rate $rate/s above 0.5 %/s at ${step.atMs}ms")
            previousAtMs = step.atMs
            previousSpeed = step.speed
        }
        // Ends exactly at the track's own tempo.
        assertEquals(1.0f, schedule.last().speed, absoluteTolerance = 1e-4f)
    }

    @Test
    fun `tempo release is empty when there is nothing to release`() {
        val schedule = MixTransitionPresets.tempoReleaseSchedule(1f, 1f, 124f)
        assertTrue(schedule.isEmpty())
    }

    // loudness match

    @Test
    fun `loudness match is unity without data and -6dB for a loud target`() {
        assertEquals(1f, MixTransitionPresets.loudnessMatchGain(null, null))
        // target −14 LUFS against actual −8 LUFS -> the incoming deck is pulled down 6 dB.
        assertEquals(0.501f, MixTransitionPresets.loudnessMatchGain(-14f, -8f), absoluteTolerance = 0.01f)
    }
}
