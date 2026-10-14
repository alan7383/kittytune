package com.alananasss.kittytune

import com.alananasss.kittytune.audio.automix.KeyHarmony
import com.alananasss.kittytune.audio.automix.KeyHarmony.Fit
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyHarmonyTest {

    @Test
    fun `the same key and its relative are the same notes`() {
        assertEquals(Fit.SAME, KeyHarmony.between("8A", "8A"))
        assertEquals(Fit.SAME, KeyHarmony.between("8A", "8B"))
    }

    @Test
    fun `a step round the wheel in the same mode is a neighbour, across the twelve too`() {
        assertEquals(Fit.NEIGHBOUR, KeyHarmony.between("8A", "9A"))
        assertEquals(Fit.NEIGHBOUR, KeyHarmony.between("12B", "1B"))
    }

    @Test
    fun `two steps up is a lift and unrelated keys clash`() {
        assertEquals(Fit.LIFT, KeyHarmony.between("5A", "7A"))
        assertEquals(Fit.CLASH, KeyHarmony.between("1A", "6A"))
        assertEquals(Fit.CLASH, KeyHarmony.between("8A", "9B"))
    }

    @Test
    fun `a key that could not be told is unknown, not a clash`() {
        assertEquals(Fit.UNKNOWN, KeyHarmony.between(null, "8A"))
        assertEquals(Fit.UNKNOWN, KeyHarmony.between("8A", "nonsense"))
    }
}
