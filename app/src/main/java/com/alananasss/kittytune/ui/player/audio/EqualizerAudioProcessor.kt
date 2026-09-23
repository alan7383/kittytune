package com.alananasss.kittytune.ui.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.*

/**
 * High-performance 16-band graphic equalizer & preamp audio processor for ExoPlayer / Media3.
 *
 * Implements 16 peaking biquad filters using Robert Bristow-Johnson (RBJ) Audio EQ Cookbook formulas
 * in Transposed Direct Form II with per-channel state arrays, lock-free parameter publishing,
 * denormal protection, and smooth hyperbolic tangent soft limiting to eliminate digital clipping.
 */
class EqualizerAudioProcessor : BaseAudioProcessor() {

    companion object {
        const val NUM_BANDS = 16
        val BAND_FREQUENCIES = floatArrayOf(
            32f, 64f, 125f, 200f, 315f, 500f, 800f, 1250f,
            2000f, 3150f, 5000f, 8000f, 10000f, 12500f, 16000f, 20000f
        )
        private const val DEFAULT_Q = 1.8f
        private const val SOFT_LIMIT_THRESHOLD = 28000f
        private const val MAX_PCM_VAL = 32767f
        private const val DENORMAL_THRESHOLD = 1e-15f
    }

    /**
     * Immutable snapshot of filter coefficients and runtime flags for lock-free thread safety.
     */
    private class FilterConfig(
        val isEnabled: Boolean,
        val preampLinear: Float,
        val hasPreamp: Boolean,
        val hasAnyActiveBand: Boolean,
        val isBandActive: BooleanArray,
        val b0: FloatArray,
        val b1: FloatArray,
        val b2: FloatArray,
        val a1: FloatArray,
        val a2: FloatArray
    )

    @Volatile
    private var filterConfig: FilterConfig = FilterConfig(
        isEnabled = false,
        preampLinear = 1f,
        hasPreamp = false,
        hasAnyActiveBand = false,
        isBandActive = BooleanArray(NUM_BANDS),
        b0 = FloatArray(NUM_BANDS),
        b1 = FloatArray(NUM_BANDS),
        b2 = FloatArray(NUM_BANDS),
        a1 = FloatArray(NUM_BANDS),
        a2 = FloatArray(NUM_BANDS)
    )

    // Current settings cached for recalculation on sample rate changes
    private var cachedEnabled = false
    private var cachedPreampDb = 0f
    private val cachedGainsDb = FloatArray(NUM_BANDS)

    // Per-channel Transposed Direct Form II states: s1[channel][band], s2[channel][band]
    private var channelCount = 2
    private var s1 = Array(2) { FloatArray(NUM_BANDS) }
    private var s2 = Array(2) { FloatArray(NUM_BANDS) }

    fun setParameters(enabled: Boolean, preampDb: Float, gainsDb: FloatArray) {
        val clampedPreamp = preampDb.coerceIn(-12f, 12f)
        val clampedGains = FloatArray(NUM_BANDS) { i ->
            if (i < gainsDb.size) gainsDb[i].coerceIn(-12f, 12f) else 0f
        }

        synchronized(this) {
            val enabledChanged = cachedEnabled != enabled
            cachedEnabled = enabled
            cachedPreampDb = clampedPreamp
            System.arraycopy(clampedGains, 0, cachedGainsDb, 0, NUM_BANDS)

            val sampleRate = if (inputAudioFormat.sampleRate > 0) inputAudioFormat.sampleRate else 44100
            rebuildFilterConfig(sampleRate)

            if (!enabled && enabledChanged) {
                resetStates()
            }
        }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }

        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        synchronized(this) {
            channelCount = channels
            s1 = Array(channels) { FloatArray(NUM_BANDS) }
            s2 = Array(channels) { FloatArray(NUM_BANDS) }
            rebuildFilterConfig(inputAudioFormat.sampleRate)
        }
        return inputAudioFormat
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onFlush() {
        resetStates()
    }

    override fun onReset() {
        resetStates()
    }

    private fun resetStates() {
        for (ch in 0 until channelCount) {
            if (ch < s1.size) s1[ch].fill(0f)
            if (ch < s2.size) s2[ch].fill(0f)
        }
    }

    private fun rebuildFilterConfig(sampleRate: Int) {
        val fs = if (sampleRate > 0) sampleRate.toFloat() else 44100f
        val nyquist = fs * 0.5f

        val b0 = FloatArray(NUM_BANDS)
        val b1 = FloatArray(NUM_BANDS)
        val b2 = FloatArray(NUM_BANDS)
        val a1 = FloatArray(NUM_BANDS)
        val a2 = FloatArray(NUM_BANDS)
        val isBandActive = BooleanArray(NUM_BANDS)
        var anyActive = false

        for (i in 0 until NUM_BANDS) {
            val gain = cachedGainsDb[i]
            if (abs(gain) < 0.05f) {
                isBandActive[i] = false
                continue
            }

            val f0 = BAND_FREQUENCIES[i]
            // Skip frequencies at or exceeding Nyquist
            if (f0 >= nyquist - 100f || f0 <= 0f) {
                isBandActive[i] = false
                continue
            }

            isBandActive[i] = true
            anyActive = true

            // Peaking EQ RBJ Biquad coefficients:
            val aVal = 10.0.pow(gain.toDouble() / 40.0)
            val w0 = 2.0 * PI * f0.toDouble() / fs.toDouble()
            val sinW0 = sin(w0)
            val cosW0 = cos(w0)
            val alpha = sinW0 / (2.0 * DEFAULT_Q.toDouble())

            val a0 = 1.0 + alpha / aVal
            b0[i] = ((1.0 + alpha * aVal) / a0).toFloat()
            b1[i] = ((-2.0 * cosW0) / a0).toFloat()
            b2[i] = ((1.0 - alpha * aVal) / a0).toFloat()
            a1[i] = ((-2.0 * cosW0) / a0).toFloat()
            a2[i] = ((1.0 - alpha / aVal) / a0).toFloat()
        }

        val hasPreamp = abs(cachedPreampDb) > 0.05f
        val preampLinear = 10.0.pow(cachedPreampDb.toDouble() / 20.0).toFloat()

        filterConfig = FilterConfig(
            isEnabled = cachedEnabled,
            preampLinear = preampLinear,
            hasPreamp = hasPreamp,
            hasAnyActiveBand = anyActive,
            isBandActive = isBandActive,
            b0 = b0,
            b1 = b1,
            b2 = b2,
            a1 = a1,
            a2 = a2
        )
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val config = filterConfig

        // Zero-overhead bypass when disabled or when flat with 0 dB preamp
        if (!config.isEnabled || (!config.hasAnyActiveBand && !config.hasPreamp)) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)
        val channels = channelCount
        val localS1 = s1
        val localS2 = s2
        val b0 = config.b0
        val b1 = config.b1
        val b2 = config.b2
        val a1 = config.a1
        val a2 = config.a2
        val isBandActive = config.isBandActive
        val hasActiveBands = config.hasAnyActiveBand
        val preamp = config.preampLinear

        var channelIndex = 0
        while (inputBuffer.hasRemaining()) {
            val sample = inputBuffer.getShort().toFloat()
            val ch = channelIndex

            // Apply Preamp gain
            var x = sample * preamp

            // Apply 16 Biquad Peaking Filters in Transposed Direct Form II
            if (hasActiveBands && ch < localS1.size) {
                val chS1 = localS1[ch]
                val chS2 = localS2[ch]

                for (b in 0 until NUM_BANDS) {
                    if (!isBandActive[b]) continue

                    val nb0 = b0[b]
                    val nb1 = b1[b]
                    val nb2 = b2[b]
                    val na1 = a1[b]
                    val na2 = a2[b]

                    val s1Val = chS1[b]
                    val s2Val = chS2[b]

                    val y = nb0 * x + s1Val
                    var nextS1 = nb1 * x - na1 * y + s2Val
                    var nextS2 = nb2 * x - na2 * y

                    // Flush subnormal values to zero (denormal prevention)
                    if (abs(nextS1) < DENORMAL_THRESHOLD) nextS1 = 0f
                    if (abs(nextS2) < DENORMAL_THRESHOLD) nextS2 = 0f

                    chS1[b] = nextS1
                    chS2[b] = nextS2
                    x = y
                }
            }

            // High-fidelity soft saturation limiter (smooth hyperbolic tangent knee)
            buffer.putShort(softLimit(x))

            channelIndex = (channelIndex + 1) % channels
        }

        buffer.flip()
    }

    /**
     * C-infinity continuous hyperbolic tangent soft-limiter:
     * Transparent linear pass-through up to [SOFT_LIMIT_THRESHOLD] (28000, ~ -1.36 dBFS).
     * Smooth analog-like saturation approaching [MAX_PCM_VAL] (32767) to eliminate digital clipping.
     */
    private fun softLimit(sample: Float): Short {
        val absSample = abs(sample)
        if (absSample <= SOFT_LIMIT_THRESHOLD) {
            return sample.toInt().toShort()
        }

        val sign = if (sample >= 0f) 1f else -1f
        val excess = (absSample - SOFT_LIMIT_THRESHOLD) / (MAX_PCM_VAL - SOFT_LIMIT_THRESHOLD)
        val compressed = SOFT_LIMIT_THRESHOLD + (MAX_PCM_VAL - SOFT_LIMIT_THRESHOLD) * tanh(excess.toDouble()).toFloat()
        return (sign * compressed).coerceIn(-32768f, 32767f).toInt().toShort()
    }
}
