package com.alananasss.kittytune

import com.alananasss.kittytune.audio.haptics.HapticDynamics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A drop only feels like an impact if the phone was not already buzzing flat out for the thirty
 * seconds before it. Driving the motor from absolute loudness loses exactly that, which is the
 * thing these tests are here to prevent regressing.
 */
class HapticDynamicsTest {

    /** Feeds a steady level for a while, as a build-up or a sustained section would. */
    private fun HapticDynamics.settle(level: Float, seconds: Int, stepMs: Long = 400L) {
        repeat((seconds * 1000 / stepMs).toInt()) { shape(level, stepMs) }
    }

    @Test
    fun `a drop after a build-up still has somewhere to go`() {
        val d = HapticDynamics(contrast = 1f)
        d.settle(level = 0.7f, seconds = 20)

        // Sustained 0.7 has become the new normal, so another 0.7 beat is not an event.
        val duringBuildUp = d.shape(0.7f, 400L)
        // The drop jumps clear of it.
        val atDrop = d.shape(1.0f, 400L)

        // What matters is the ratio, not an absolute ceiling on the build-up. A hardtekk
        // build-up *is* heavy, and an earlier version of this test demanded it stay under 0.35 -
        // which is the same thing as demanding a heavy track feel thin. The floor is there
        // precisely so it does not, so the drop earns its impact by rising above, not by being
        // the only thing that moves the motor.
        assertTrue("Drop hits full, got $atDrop", atDrop > 0.9f)
        assertTrue(
            "Drop is clearly stronger than the build-up: $atDrop vs $duringBuildUp",
            atDrop / duringBuildUp > 1.8f,
        )
    }

    @Test
    fun `a relentlessly heavy track still feels heavy`() {
        // Hardtekk has no quiet passages: the bass never lets up, so contrast alone would judge
        // every beat unremarkable and keep a genuinely punishing track feeling thin.
        val d = HapticDynamics(contrast = 1f)
        d.settle(level = 0.95f, seconds = 30)

        val steadyHeavy = d.shape(0.95f, 400L)
        assertTrue("A constant heavy bass must still drive the motor, got $steadyHeavy", steadyHeavy > 0.55f)

        // And a drop still has room above that.
        val drop = d.shape(1.0f, 400L)
        assertTrue("The drop stays on top, got $drop vs $steadyHeavy", drop > steadyHeavy)
    }

    @Test
    fun `the floor follows loudness, so quiet stays quiet`() {
        // The floor must not lift everything - it is proportional to the level, not a constant.
        val d = HapticDynamics(contrast = 1f)
        d.settle(level = 0.2f, seconds = 30)
        val quiet = d.shape(0.2f, 400L)
        assertTrue("A quiet passage stays restrained, got $quiet", quiet < 0.3f)
    }

    @Test
    fun `without contrast it behaves exactly as before`() {
        // The setting has to be able to give people back the old behaviour.
        val d = HapticDynamics(contrast = 0f)
        d.settle(level = 0.7f, seconds = 20)
        assertEquals(0.7f, d.shape(0.7f, 400L), 0.001f)
        assertEquals(1.0f, d.shape(1.0f, 400L), 0.001f)
    }

    @Test
    fun `loudness alone does not earn amplitude`() {
        // The whole point: two tracks at different absolute levels should feel similar, because
        // what is felt is the shape of the music, not how loud it was mastered.
        val quiet = HapticDynamics(contrast = 1f).apply { settle(0.3f, 20) }
        val loud = HapticDynamics(contrast = 1f).apply { settle(0.8f, 20) }

        // Each gets a beat 0.2 above its own baseline.
        val quietHit = quiet.shape(0.5f, 400L)
        val loudHit = loud.shape(1.0f, 400L)
        assertTrue(
            "Comparable response to a comparable rise: $quietHit vs $loudHit",
            kotlin.math.abs(quietHit - loudHit) < 0.35f,
        )
    }

    @Test
    fun `sensitivity returns during a breakdown`() {
        val d = HapticDynamics(contrast = 1f)
        d.settle(level = 0.9f, seconds = 20)
        // The track drops out.
        d.settle(level = 0.05f, seconds = 6)
        // The first beat back should register rather than be judged against the old loud part.
        val backIn = d.shape(0.6f, 400L)
        assertTrue("Beat after a breakdown is felt, got $backIn", backIn > 0.4f)
    }

    @Test
    fun `a beat that survived detection is never silenced`() {
        val d = HapticDynamics(contrast = 1f)
        d.settle(level = 0.9f, seconds = 20)
        // Even a beat far below the baseline still gets something - the detector already decided
        // there is a beat here; contrast shapes it, it does not veto it.
        val weak = d.shape(0.2f, 400L)
        assertTrue("Still perceptible, got $weak", weak >= HapticDynamics.MIN_AUDIBLE)
    }

    @Test
    fun `the first beat of a track is not judged against nothing`() {
        val d = HapticDynamics(contrast = 1f)
        assertEquals("No history yet, so pass it through", 0.8f, d.shape(0.8f, 0L), 0.001f)
    }

    @Test
    fun `a track change clears the previous track's dynamics`() {
        val d = HapticDynamics(contrast = 1f)
        d.settle(level = 0.9f, seconds = 20)
        assertTrue("Baseline has risen", d.currentBaseline() > 0.5f)

        d.reset()
        assertEquals("A new track starts fresh", 0.6f, d.shape(0.6f, 400L), 0.001f)
    }

    @Test
    fun `output is always a usable amplitude`() {
        val d = HapticDynamics(contrast = 1f)
        for (level in listOf(-1f, 0f, 0.5f, 1f, 5f)) {
            val out = d.shape(level, 400L)
            assertTrue("$level -> $out is in range", out in 0f..1f)
        }
    }
}
