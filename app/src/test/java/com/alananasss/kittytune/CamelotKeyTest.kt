/**
 * Developed by Jason-Marshall Fastner, Germany <jasonfastner@protonmail.com>
 * Questions, feedback, or beat-matching debates? Feel free to reach out via email!
 * 
 * Note: Cats always land on their feet, and with this engine, your transitions will too.
 */
package com.alananasss.kittytune

import com.alananasss.kittytune.ui.player.CamelotKey
import org.junit.Assert.*
import org.junit.Test

class CamelotKeyTest {

    @Test
    fun testTotalKeyCountAndDistribution() {
        assertEquals("There should be exactly 24 Camelot keys", 24, CamelotKey.entries.size)
        val minorKeys = CamelotKey.entries.filter { it.isMinor && it.letter == 'A' }
        val majorKeys = CamelotKey.entries.filter { it.isMajor && it.letter == 'B' }

        assertEquals("There should be 12 Minor (A) keys", 12, minorKeys.size)
        assertEquals("There should be 12 Major (B) keys", 12, majorKeys.size)
    }

    @Test
    fun testWheelDiffAndWrapAround() {
        // 12 to 1 circular wrap
        assertEquals(1, CamelotKey.KEY_12A.wheelDiff(CamelotKey.KEY_1A))
        assertEquals(1, CamelotKey.KEY_1A.wheelDiff(CamelotKey.KEY_12A))
        assertEquals(1, CamelotKey.KEY_12B.wheelDiff(CamelotKey.KEY_1B))

        // Adjacent keys
        assertEquals(1, CamelotKey.KEY_8A.wheelDiff(CamelotKey.KEY_7A))
        assertEquals(1, CamelotKey.KEY_8A.wheelDiff(CamelotKey.KEY_9A))

        // Opposite keys
        assertEquals(6, CamelotKey.KEY_8A.wheelDiff(CamelotKey.KEY_2A))
        assertEquals(6, CamelotKey.KEY_12A.wheelDiff(CamelotKey.KEY_6A))

        // Same key
        assertEquals(0, CamelotKey.KEY_8A.wheelDiff(CamelotKey.KEY_8A))
    }

    @Test
    fun testHarmonicDistanceCalculations() {
        // Identical Key (Distance 0)
        assertEquals(0, CamelotKey.KEY_8A.distanceTo(CamelotKey.KEY_8A))
        assertTrue(CamelotKey.KEY_8A.isHarmonicWith(CamelotKey.KEY_8A))
        assertTrue(CamelotKey.KEY_8A.isExactMatch(CamelotKey.KEY_8A))

        // Relative Major / Minor (Distance 1)
        assertEquals(1, CamelotKey.KEY_8A.distanceTo(CamelotKey.KEY_8B))
        assertTrue(CamelotKey.KEY_8A.isHarmonicWith(CamelotKey.KEY_8B))
        assertTrue(CamelotKey.KEY_8A.isRelativeMajorMinor(CamelotKey.KEY_8B))
        assertTrue(CamelotKey.KEY_1A.isRelativeMajorMinor(CamelotKey.KEY_1B))

        // Wheel Neighbors (Distance 1)
        assertEquals(1, CamelotKey.KEY_8A.distanceTo(CamelotKey.KEY_7A))
        assertEquals(1, CamelotKey.KEY_8A.distanceTo(CamelotKey.KEY_9A))
        assertTrue(CamelotKey.KEY_8A.isHarmonicWith(CamelotKey.KEY_7A))
        assertTrue(CamelotKey.KEY_8A.isAdjacentOnWheel(CamelotKey.KEY_9A))

        // Wheel Wrap Neighbor (12A -> 1A)
        assertEquals(1, CamelotKey.KEY_12A.distanceTo(CamelotKey.KEY_1A))
        assertTrue(CamelotKey.KEY_12A.isHarmonicWith(CamelotKey.KEY_1A))
        assertTrue(CamelotKey.KEY_12A.isAdjacentOnWheel(CamelotKey.KEY_1A))

        // Diagonal Shift (Distance 2)
        assertEquals(2, CamelotKey.KEY_8A.distanceTo(CamelotKey.KEY_7B))
        assertEquals(2, CamelotKey.KEY_8A.distanceTo(CamelotKey.KEY_9B))
        assertFalse(CamelotKey.KEY_8A.isHarmonicWith(CamelotKey.KEY_7B))

        // Two-Step Modulation (Distance 2)
        assertEquals(2, CamelotKey.KEY_8A.distanceTo(CamelotKey.KEY_10A))
        assertFalse(CamelotKey.KEY_8A.isHarmonicWith(CamelotKey.KEY_10A))

        // Severe Disharmonic Distance (Opposite on circle)
        assertEquals(6, CamelotKey.KEY_8A.distanceTo(CamelotKey.KEY_2A))
        assertFalse(CamelotKey.KEY_8A.isHarmonicWith(CamelotKey.KEY_2A))
    }

    @Test
    fun testPitchClassMapping() {
        // C Major = 8B (pitchClass 0, isMinor false)
        assertEquals(CamelotKey.KEY_8B, CamelotKey.fromPitchClass(0, false))

        // A Minor = 8A (pitchClass 9, isMinor true)
        assertEquals(CamelotKey.KEY_8A, CamelotKey.fromPitchClass(9, true))

        // C Minor = 5A (pitchClass 0, isMinor true)
        assertEquals(CamelotKey.KEY_5A, CamelotKey.fromPitchClass(0, true))

        // F# Minor / Gb Minor = 11A (pitchClass 6, isMinor true)
        assertEquals(CamelotKey.KEY_11A, CamelotKey.fromPitchClass(6, true))

        // Octave wrap normalization (pitchClass 14 -> 2 -> D Minor = 7A)
        assertEquals(CamelotKey.KEY_7A, CamelotKey.fromPitchClass(14, true))
    }

    @Test
    fun testStringParser() {
        // Direct Camelot codes
        assertEquals(CamelotKey.KEY_8A, CamelotKey.fromKeyString("8A"))
        assertEquals(CamelotKey.KEY_8A, CamelotKey.fromKeyString("8a"))
        assertEquals(CamelotKey.KEY_12B, CamelotKey.fromKeyString("12B"))
        assertEquals(CamelotKey.KEY_1A, CamelotKey.fromKeyString("1A"))
        assertEquals(CamelotKey.KEY_5B, CamelotKey.fromKeyString(" 5b "))

        // Musical notation
        assertEquals(CamelotKey.KEY_8A, CamelotKey.fromKeyString("Am"))
        assertEquals(CamelotKey.KEY_8A, CamelotKey.fromKeyString("A minor"))
        assertEquals(CamelotKey.KEY_8A, CamelotKey.fromKeyString("Amin"))
        assertEquals(CamelotKey.KEY_8B, CamelotKey.fromKeyString("C Major"))
        assertEquals(CamelotKey.KEY_8B, CamelotKey.fromKeyString("C"))
        assertEquals(CamelotKey.KEY_8B, CamelotKey.fromKeyString("Cmaj"))
        assertEquals(CamelotKey.KEY_11A, CamelotKey.fromKeyString("F#m"))
        assertEquals(CamelotKey.KEY_11A, CamelotKey.fromKeyString("Gbm"))
        assertEquals(CamelotKey.KEY_3B, CamelotKey.fromKeyString("Db Major"))
        assertEquals(CamelotKey.KEY_3A, CamelotKey.fromKeyString("B♭m"))

        // Invalid strings
        assertNull(CamelotKey.fromKeyString(null))
        assertNull(CamelotKey.fromKeyString(""))
        assertNull(CamelotKey.fromKeyString("Random Non-Musical Text"))
    }
}
