/**
 * Developed by Jason-Marshall Fastner, Germany <jasonfastner@protonmail.com>
 * Questions, feedback, or beat-matching debates? Feel free to reach out via email!
 * 
 * Note: Cats always land on their feet, and with this engine, your transitions will too.
 */
package com.alananasss.kittytune

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.alananasss.kittytune.audio.automix.DjStemAudioProcessor
import com.alananasss.kittytune.ui.player.DjFlowState
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DjStemAndInfiniteStreamTest {

    @Test
    fun testStemProcessorDefaultValues() {
        val proc = DjStemAudioProcessor()
        assertEquals(1.0f, proc.vocalLevel, 0.001f)
        assertEquals(1.0f, proc.drumLevel, 0.001f)
        assertEquals(1.0f, proc.bassLevel, 0.001f)
        assertFalse("At 1.0 levels across stems, processor should be bypassed (disabled)", proc.isEnabled)
    }

    @Test
    fun testStemProcessorParameterClampingAndEnable() {
        val proc = DjStemAudioProcessor()

        // Mute vocal -> Instrumental
        proc.setStems(vocal = 0.0f, drum = 1.0f, bass = 1.0f)
        assertEquals(0.0f, proc.vocalLevel, 0.001f)
        assertTrue("Processor should be enabled when stems deviate from 1.0", proc.isEnabled)

        // Reset
        proc.resetStems()
        assertEquals(1.0f, proc.vocalLevel, 0.001f)
        assertFalse(proc.isEnabled)

        // Drop bass cut
        proc.triggerDropBassCut()
        assertEquals(0.0f, proc.bassLevel, 0.001f)
        assertTrue(proc.isEnabled)
    }

    @Test
    fun testStemPcmStreamProcessing() {
        val proc = DjStemAudioProcessor()
        val format = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
        val outFormat = proc.configure(format)
        proc.flush()

        assertEquals(44100, outFormat.sampleRate)
        assertEquals(2, outFormat.channelCount)

        // Cut bass to 0
        proc.triggerDropBassCut()
        assertTrue(proc.isActive)

        // Generate synthetic stereo 16-bit PCM frames
        val numSamples = 256
        val inBuffer = ByteBuffer.allocateDirect(numSamples * 2 * 2).order(ByteOrder.nativeOrder())
        for (i in 0 until numSamples) {
            val sampleVal = (10000.0 * kotlin.math.sin(i * 0.1)).toInt().toShort()
            inBuffer.putShort(sampleVal) // L
            inBuffer.putShort(sampleVal) // R
        }
        inBuffer.flip()

        proc.queueInput(inBuffer)
        val outBuffer = proc.output

        assertNotNull(outBuffer)
        assertTrue("Output buffer should have processed PCM bytes", outBuffer.remaining() > 0)
    }

    @Test
    fun testDjFlowStateInfiniteAndStemDefaults() {
        val state = DjFlowState()
        assertTrue("Infinite stream should be enabled by default", state.isInfiniteStreamEnabled)
        assertEquals("Meine Likes", state.selectedCategory)
        assertFalse(state.isFetchingInfiniteCandidates)

        assertEquals(1.0f, state.vocalStemLevel, 0.001f)
        assertEquals(1.0f, state.drumStemLevel, 0.001f)
        assertEquals(1.0f, state.bassStemLevel, 0.001f)
        assertTrue("Auto stem drop cut should be enabled by default", state.isAutoStemDropCutEnabled)
    }
}
