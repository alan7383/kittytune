package com.alananasss.kittytune

import com.alananasss.kittytune.audio.haptics.HapticDynamics
import com.alananasss.kittytune.audio.haptics.HapticGesture
import com.alananasss.kittytune.audio.haptics.HapticGestureSelector
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Variety on the wrist is only worth having if each sensation means something. These tests pin
 * down what each gesture is claiming, and - just as importantly - what it refuses to claim.
 */
class HapticGestureSelectorTest {

    private fun select(
        isKick: Boolean = true,
        isDownbeat: Boolean? = false,
        intensity: Float = 0.6f,
        trend: HapticDynamics.Trend = HapticDynamics.Trend.STEADY,
        atPhraseBoundary: Boolean = false,
    ) = HapticGestureSelector.select(isKick, isDownbeat, intensity, trend, atPhraseBoundary)

    @Test
    fun `a drop needs both the climb and the landing`() {
        // Loud alone is not a drop, otherwise every heavy track would be one long impact.
        assertEquals(
            HapticGesture.IMPACT,
            select(isDownbeat = true, intensity = 0.95f, trend = HapticDynamics.Trend.RISING, atPhraseBoundary = true),
        )
        assertEquals(
            "Loud but not at a phrase line is just a strong beat",
            HapticGesture.DOWNBEAT,
            select(isDownbeat = true, intensity = 0.95f, trend = HapticDynamics.Trend.RISING),
        )
        assertEquals(
            "A phrase line while energy is falling is not a drop",
            HapticGesture.DOWNBEAT,
            select(isDownbeat = true, intensity = 0.95f, trend = HapticDynamics.Trend.FALLING, atPhraseBoundary = true),
        )
    }

    @Test
    fun `tension goes on bar lines, not on every beat`() {
        // A rise on each kick would be continuous motion - the drone this design avoids.
        assertEquals(
            HapticGesture.BUILD_UP,
            select(isDownbeat = true, trend = HapticDynamics.Trend.RISING),
        )
        assertEquals(
            "Off-beat during a climb stays an ordinary beat",
            HapticGesture.BEAT,
            select(isDownbeat = false, trend = HapticDynamics.Trend.RISING),
        )
    }

    @Test
    fun `an untrusted downbeat never claims the weight`() {
        // isDownbeat null means the classifier wasn't confident. The heaviest sensation in the
        // bar must not rest on a guess.
        assertEquals(HapticGesture.BEAT, select(isDownbeat = null, intensity = 0.9f))
        assertEquals(
            HapticGesture.BEAT,
            select(isDownbeat = null, trend = HapticDynamics.Trend.RISING),
        )
    }

    @Test
    fun `weak beats are texture, not events`() {
        assertEquals(HapticGesture.GHOST, select(intensity = 0.15f))
        assertEquals("Even on beat 1", HapticGesture.GHOST, select(isDownbeat = true, intensity = 0.15f))
    }

    @Test
    fun `a snare is always a crack`() {
        assertEquals(HapticGesture.BACKBEAT, select(isKick = false, intensity = 0.9f))
        assertEquals(
            "Even at a phrase line - the impact belongs to the kick",
            HapticGesture.BACKBEAT,
            select(isKick = false, intensity = 0.95f, atPhraseBoundary = true, trend = HapticDynamics.Trend.RISING),
        )
    }

    @Test
    fun `an ordinary beat stays an ordinary beat`() {
        assertEquals(HapticGesture.BEAT, select())
        assertEquals(HapticGesture.DOWNBEAT, select(isDownbeat = true))
    }
}
