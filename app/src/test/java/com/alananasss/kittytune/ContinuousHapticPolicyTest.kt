package com.alananasss.kittytune

import com.alananasss.kittytune.audio.haptics.ContinuousHapticPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The constant rumble is what a drop has to stand out from, so a phone that has been buzzing
 * steadily for thirty seconds cannot mark one. These tests pin down when it gets out of the way.
 */
class ContinuousHapticPolicyTest {

    @Test
    fun `with a beat grid, impact replaces the drone`() {
        // Discrete pulses on real beats carry more than a running level ever could.
        assertTrue(ContinuousHapticPolicy.isSuppressed(hasTrustedGrid = true, contrast = 1f))
        assertTrue(ContinuousHapticPolicy.isSuppressed(hasTrustedGrid = true, contrast = 0.9f))
    }

    @Test
    fun `without a grid the drone is all there is`() {
        // Unanalysed material has no beats to pulse on, so removing the rumble would leave
        // nothing at all.
        assertEquals(1f, ContinuousHapticPolicy.rumbleScale(hasTrustedGrid = false, contrast = 1f), 1e-6f)
        assertEquals(1f, ContinuousHapticPolicy.rumbleScale(hasTrustedGrid = false, contrast = 0f), 1e-6f)
    }

    @Test
    fun `impact at zero gives back the old behaviour`() {
        // The setting has to be able to restore what shipped before, grid or not.
        assertEquals(1f, ContinuousHapticPolicy.rumbleScale(hasTrustedGrid = true, contrast = 0f), 1e-6f)
    }

    @Test
    fun `the drone fades out as impact is turned up`() {
        val low = ContinuousHapticPolicy.rumbleScale(true, 0.25f)
        val mid = ContinuousHapticPolicy.rumbleScale(true, 0.5f)
        assertTrue("More impact, less drone: $low then $mid", low > mid)
        assertTrue(mid > 0f)
    }

    @Test
    fun `a rumble too weak to feel is switched off rather than left running`() {
        // It would still wake the motor every few tens of milliseconds and drain current for
        // something nobody can feel.
        val scale = ContinuousHapticPolicy.rumbleScale(true, 0.9f)
        assertEquals("Off, not merely quiet", 0f, scale, 1e-6f)
    }

    @Test
    fun `out-of-range settings are handled`() {
        assertEquals(1f, ContinuousHapticPolicy.rumbleScale(true, -1f), 1e-6f)
        assertEquals(0f, ContinuousHapticPolicy.rumbleScale(true, 5f), 1e-6f)
    }
}
