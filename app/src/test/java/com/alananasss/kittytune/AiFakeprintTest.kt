package com.alananasss.kittytune

import com.alananasss.kittytune.data.filter.AiFakeprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sin

/**
 * Compares the Kotlin fakeprint with lofcz/ai-music-detector's Python inference (torchaudio + scipy), run on
 * the same synthetic signal. The expected values in resources/ai_fakeprint were produced by that reference.
 */
class AiFakeprintTest {

    private val model by lazy { File("src/main/res/raw/ai_music_fakeprint.bin").inputStream().use { AiFakeprint.Model.read(it) } }

    /** Must match signal() in the reference script sample for sample. */
    private fun signal(sampleRate: Int, seconds: Int): FloatArray {
        val n = sampleRate * seconds
        var state = 12345L
        return FloatArray(n) { i ->
            val t = i.toDouble() / sampleRate
            val tone = 0.3 * sin(2 * PI * 220 * t) + 0.2 * sin(2 * PI * 440 * t + 0.5) +
                0.1 * sin(2 * PI * 1760.5 * t) + 0.05 * sin(2 * PI * 3520.25 * t) +
                0.02 * sin(2 * PI * 5000 * t) * sin(2 * PI * 0.5 * t)
            state = (state * 1103515245L + 12345L) % 2147483648L
            (tone + 0.05 * (state / 2147483648.0 - 0.5)).toFloat()
        }
    }

    private fun floats(name: String): FloatArray {
        val buffer = ByteBuffer.wrap(File("src/test/resources/ai_fakeprint/$name").readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(buffer.remaining() / 4) { buffer.getFloat() }
    }

    private fun checkAgainstReference(sampleRate: Int) {
        val (length, logit) = File("src/test/resources/ai_fakeprint/expected_$sampleRate.txt").readText().trim()
            .split(" ").let { it[0].toInt() to it[2].toDouble() }

        val resampled = AiFakeprint.resample(signal(sampleRate, 8), sampleRate)
        assertEquals(length, resampled.size)
        val expectedStart = floats("resampled_$sampleRate.bin")
        val resampleError = expectedStart.indices.maxOf { abs(expectedStart[it] - resampled[it]) }
        assertTrue("resampling differs by $resampleError", resampleError < 1e-5)

        val fakeprint = AiFakeprint.fakeprint(resampled)
        val expectedPrint = floats("fakeprint_$sampleRate.bin")
        assertEquals(AiFakeprint.FEATURES, fakeprint.size)
        val printError = fakeprint.indices.maxOf { abs(expectedPrint[it] - fakeprint[it]) }
        assertTrue("fakeprint differs by $printError", printError < 1e-3)

        val p = model.probability(fakeprint)
        val kotlinLogit = ln(p / (1 - p))
        assertTrue("logit $kotlinLogit vs $logit", abs(kotlinLogit - logit) < 0.05)
    }

    @Test
    fun `matches the Python reference for 44_1 kHz audio`() = checkAgainstReference(44100)

    @Test
    fun `matches the Python reference for 48 kHz audio`() = checkAgainstReference(48000)

    @Test
    fun `silence has no usable fakeprint signal`() {
        assertEquals(0.0, AiFakeprint.rms(FloatArray(32000)), 0.0)
    }
}
