package com.alananasss.kittytune

import com.alananasss.kittytune.audio.AudioMode
import com.alananasss.kittytune.audio.AudioRoutingMatrix
import com.alananasss.kittytune.audio.AudioStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRoutingMatrixTest {

    @Test fun `bit perfect routes nothing - the DAC owns the output`() {
        AudioRoutingMatrix.modeProvider = { AudioMode.BIT_PERFECT }
        AudioStage.entries.forEach { assertFalse(AudioRoutingMatrix.isRouted(it)) }
    }

    @Test fun `standard routes every stage`() {
        AudioRoutingMatrix.modeProvider = { AudioMode.STANDARD }
        AudioStage.entries.forEach { assertTrue(AudioRoutingMatrix.isRouted(it)) }
    }

    @Test fun `audiophile invariants - eq limiter normalizer and automix are locked out`() {
        AudioRoutingMatrix.modeProvider = { AudioMode.BIT_PERFECT }
        AudioRoutingMatrix.BIT_PERFECT_INVARIANTS.forEach {
            assertFalse("$it must be unrouted in Bit-Perfect", AudioRoutingMatrix.isRouted(it))
        }
        assertEquals(8, AudioRoutingMatrix.BIT_PERFECT_INVARIANTS.size)
    }

    @Test fun `matrix is total - every mode has a row`() {
        AudioMode.entries.forEach { assertNotNull(AudioRoutingMatrix.routedStages(it)) }
    }

    @Test fun `mode switches are picked up on every call`() {
        var mode = AudioMode.STANDARD
        AudioRoutingMatrix.modeProvider = { mode }
        assertTrue(AudioRoutingMatrix.isRouted(AudioStage.EQUALIZER))
        mode = AudioMode.BIT_PERFECT
        assertFalse(AudioRoutingMatrix.isRouted(AudioStage.EQUALIZER))
    }
}
