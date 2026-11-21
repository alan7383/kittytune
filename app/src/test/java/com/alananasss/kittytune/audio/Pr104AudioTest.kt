package com.alananasss.kittytune.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.alananasss.kittytune.audio.automix.MixSweepAudioProcessor
import com.alananasss.kittytune.audio.automix.StereoNormalizerAudioProcessor
import com.alananasss.kittytune.data.audio.AutoEqRepository
import com.alananasss.kittytune.ui.player.audio.EqualizerAudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class Pr104AudioTest {
    @Test
    fun broadParametricBoostIsFittedWithHeadroom() {
        val profile = AutoEqRepository.loadFromText("Broad EQ", "Filter 1: ON PK Fc 500 Hz Gain +10 dB Q 0.5")
        val gain = EqualizerAudioProcessor.computeResponseDbAt(500f, profile.gainsDb)
        assertEquals(10f, gain, 1f)
        assertTrue(gain + profile.preampDb <= 0.05f)
    }

    @Test
    fun unsupportedAndUnrepresentableFiltersAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            AutoEqRepository.loadFromText("Unsupported", "Filter 1: ON LP Fc 100 Hz")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AutoEqRepository.loadFromText("Narrow notch", "Filter 1: ON PK Fc 670 Hz Gain -18 dB Q 28")
        }
    }

    @Test
    fun disabledFiltersKeepAnExplicitPreamp() {
        val profile = AutoEqRepository.loadFromText("Flat", "Preamp: -4 dB\nFilter 1: OFF PK Fc 500 Hz Gain +8 dB Q 1")
        assertEquals(List(16) { 0f }, profile.gainsDb)
        assertEquals(-4f, profile.preampDb, 0f)
    }

    @Test
    fun stereoIsInactiveAndMonoStillDuplicates() {
        val processor = StereoNormalizerAudioProcessor()
        assertEquals(AudioProcessor.AudioFormat.NOT_SET, processor.configure(format(2)))
        assertFalse(processor.isActive)
        processor.configure(format(1))
        processor.flush()
        processor.queueInput(pcm(9_000, -13_000))
        val output = processor.output
        assertEquals(listOf(9_000, 9_000, -13_000, -13_000), List(4) { output.short.toInt() })
    }

    @Test
    fun correlatedSurroundRetainsHeadroomInBothPcmRepresentations() {
        val processor = StereoNormalizerAudioProcessor()
        processor.configure(format(6))
        processor.flush()
        processor.queueInput(pcm(20_000, 20_000, 20_000, 20_000, 20_000, 20_000))
        val output = processor.output
        assertEquals(20_000, output.short.toInt())
        assertEquals(20_000, output.short.toInt())

        processor.configure(format(8, C.ENCODING_PCM_FLOAT))
        processor.flush()
        val input = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder())
        repeat(8) { input.putFloat(0.8f) }
        input.flip()
        processor.queueInput(input)
        val floatOutput = processor.output
        assertEquals(0.8f, floatOutput.float, 0.0001f)
        assertEquals(0.8f, floatOutput.float, 0.0001f)
    }

    @Test
    fun splitBuffersKeepTheFrameAndLfeDoesNotLeak() {
        val processor = StereoNormalizerAudioProcessor()
        processor.configure(format(6))
        processor.flush()
        processor.queueInput(pcm(0, 0, 0))
        assertEquals(0, processor.output.remaining())
        processor.queueInput(pcm(30_000, 0, 0))
        val output = processor.output
        assertEquals(0, output.short.toInt())
        assertEquals(0, output.short.toInt())
    }

    @Test
    fun bandPassReleaseMovesGraduallyToDryAndStaysBitTransparent() {
        val processor = MixSweepAudioProcessor()
        processor.configure(format(2))
        processor.flush()
        processor.setMode(MixSweepAudioProcessor.FilterMode.BAND_PASS)
        processor.setSweep(500f, 2f)
        repeat(5) { processor.queueInput(pcm(*IntArray(4_800) { 10_000 })); processor.output }
        processor.resetSweep()
        processor.queueInput(pcm(*IntArray(4_800) { 10_000 }))
        val release = processor.output
        val first = release.short.toInt()
        val last = release.getShort(release.limit() - 2).toInt()
        assertTrue("First buffer is partly filtered, first=$first last=$last", abs(first) < last && last < 10_000)
        repeat(5) { processor.queueInput(pcm(*IntArray(4_800) { 10_000 })); processor.output }
        processor.queueInput(pcm(1_234, -22_222))
        val output = processor.output
        assertEquals(1_234, output.short.toInt())
        assertEquals(-22_222, output.short.toInt())
    }

    private fun format(channels: Int, encoding: Int = C.ENCODING_PCM_16BIT) =
        AudioProcessor.AudioFormat(48_000, channels, encoding)

    private fun pcm(vararg samples: Int): ByteBuffer =
        ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder()).apply {
            samples.forEach { putShort(it.toShort()) }
            flip()
        }
}
