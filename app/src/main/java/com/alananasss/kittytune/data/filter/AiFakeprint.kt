package com.alananasss.kittytune.data.filter

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The audio fingerprint music generators leave behind, after "A Fourier Explanation of AI-Music Artifacts"
 * (Afchar et al., ISMIR 2025), with the classifier of lofcz/ai-music-detector.
 *
 * The decoders of Suno, Udio & co. upsample with transposed convolutions, and those leave peaks at regular
 * frequency intervals in everything they render. The fakeprint isolates the peaks: the song's average
 * spectrum at 16 kHz between 1 and 8 kHz, minus its lower envelope. A logistic regression over its 3585 bins
 * gives the probability that a generator rendered the audio.
 *
 * Every step mirrors the reference inference (torchaudio's resampler and spectrogram, scipy's minimum
 * filter), because the weights only fit features computed exactly that way.
 *
 * The weights in res/raw/ai_music_fakeprint.bin come from lofcz/ai-music-detector:
 * MIT License, Copyright (c) 2026 Matěj Štágl. Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation files (the "Software"), to deal in the
 * Software without restriction, including without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions: The above copyright notice and this permission
 * notice shall be included in all copies or substantial portions of the Software. THE SOFTWARE IS PROVIDED
 * "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
internal object AiFakeprint {

    const val SAMPLE_RATE = 16_000
    private const val N_FFT = 8192
    private const val HOP = N_FFT / 2
    private const val BIN_MIN = 512 // 1000 Hz at 16 kHz / 8192
    private const val BIN_MAX = 4096 // 8000 Hz
    const val FEATURES = BIN_MAX - BIN_MIN + 1
    private const val HULL_SIZE = 10
    private const val MIN_DB = -45.0
    private const val MAX_DB = 5.0

    // torchaudio's default resampler: windowed sinc with a Hann window.
    private const val LOWPASS_WIDTH = 6.0
    private const val ROLLOFF = 0.99

    /** The logistic regression: sigmoid(weights · fakeprint + bias). */
    class Model(private val weights: FloatArray, private val bias: Float) {
        init {
            require(weights.size == FEATURES) { "expected $FEATURES weights, got ${weights.size}" }
        }

        fun probability(fakeprint: FloatArray): Double {
            var logit = bias.toDouble()
            for (i in weights.indices) logit += weights[i].toDouble() * fakeprint[i]
            return 1.0 / (1.0 + exp(-logit))
        }

        companion object {
            /** [FEATURES] little-endian float32 weights followed by the float32 bias. */
            fun read(input: InputStream): Model {
                val bytes = input.use { it.readBytes() }
                require(bytes.size == (FEATURES + 1) * 4) { "unexpected model size ${bytes.size}" }
                val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                val weights = FloatArray(FEATURES) { buffer.getFloat() }
                return Model(weights, buffer.getFloat())
            }
        }
    }

    /** Root mean square, to tell music from silence before judging it. */
    fun rms(samples: FloatArray): Double {
        if (samples.isEmpty()) return 0.0
        var sum = 0.0
        for (s in samples) sum += s.toDouble() * s
        return sqrt(sum / samples.size)
    }

    /** The fakeprint of mono audio that is already at [SAMPLE_RATE]; needs more than half a second. */
    fun fakeprint(audio16k: FloatArray): FloatArray {
        val meanDb = meanDbSpectrum(audio16k)
        val spectrum = DoubleArray(FEATURES) { meanDb[BIN_MIN + it] }
        val residue = DoubleArray(FEATURES)
        var maxResidue = 0.0
        for (i in 0 until FEATURES) {
            // scipy's minimum_filter1d(size = 10, mode = "nearest") looks at i - 5 .. i + 4.
            var hull = Double.MAX_VALUE
            for (j in i - HULL_SIZE / 2 until i - HULL_SIZE / 2 + HULL_SIZE) {
                hull = min(hull, spectrum[j.coerceIn(0, FEATURES - 1)])
            }
            hull = max(hull, MIN_DB)
            residue[i] = (spectrum[i] - hull).coerceIn(0.0, MAX_DB)
            maxResidue = max(maxResidue, residue[i])
        }
        val scale = maxResidue + 1e-6
        return FloatArray(FEATURES) { (residue[it] / scale).toFloat() }
    }

    /**
     * torch.stft(n_fft = 8192, hop = 4096, periodic Hann window, center = True with reflect padding) as power,
     * turned into dB per frame and averaged over all frames.
     */
    fun meanDbSpectrum(audio: FloatArray): DoubleArray {
        val pad = N_FFT / 2
        require(audio.size > pad) { "need more than $pad samples, got ${audio.size}" }
        val length = audio.size
        val frames = 1 + length / HOP
        val window = DoubleArray(N_FFT) { 0.5 - 0.5 * cos(2 * PI * it / N_FFT) }
        val bins = N_FFT / 2 + 1
        val sum = DoubleArray(bins)
        val re = DoubleArray(N_FFT)
        val im = DoubleArray(N_FFT)
        for (frame in 0 until frames) {
            val start = frame * HOP - pad
            for (n in 0 until N_FFT) {
                re[n] = reflected(audio, start + n) * window[n]
                im[n] = 0.0
            }
            Fft.transform(re, im)
            for (k in 0 until bins) {
                val power = re[k] * re[k] + im[k] * im[k]
                sum[k] += 10.0 * log10(power.coerceIn(1e-10, 1e6))
            }
        }
        for (k in 0 until bins) sum[k] /= frames
        return sum
    }

    private fun reflected(audio: FloatArray, index: Int): Double {
        val last = audio.size - 1
        val i = when {
            index < 0 -> -index
            index > last -> 2 * last - index
            else -> index
        }
        return audio[i].toDouble()
    }

    /** torchaudio.functional.resample with its defaults: sinc_interp_hann, lowpass width 6, rolloff 0.99. */
    fun resample(input: FloatArray, fromRate: Int): FloatArray {
        if (fromRate == SAMPLE_RATE) return input.copyOf()
        val divisor = gcd(fromRate, SAMPLE_RATE)
        val orig = fromRate / divisor
        val new = SAMPLE_RATE / divisor
        val baseFreq = min(orig, new) * ROLLOFF
        val width = ceil(LOWPASS_WIDTH * orig / baseFreq).toInt()
        val taps = 2 * width + orig

        // One kernel per output phase; outside the sinc's main lobes the Hann window is exactly zero.
        val kernels = Array(new) { phase ->
            DoubleArray(taps) { k ->
                val t = ((-phase.toDouble() / new + (k - width).toDouble() / orig) * baseFreq)
                    .coerceIn(-LOWPASS_WIDTH, LOWPASS_WIDTH)
                val window = cos(t * PI / LOWPASS_WIDTH / 2).let { it * it }
                val x = t * PI
                val sinc = if (x == 0.0) 1.0 else sin(x) / x
                sinc * window * (baseFreq / orig)
            }
        }
        val firstTap = IntArray(new) { phase -> kernels[phase].indexOfFirst { it != 0.0 }.coerceAtLeast(0) }
        val lastTap = IntArray(new) { phase -> kernels[phase].indexOfLast { it != 0.0 } }

        val length = input.size
        val outputLength = ceil(new.toDouble() * length / orig).toInt()
        val output = FloatArray(outputLength)
        val blocks = length / orig + 1
        for (block in 0 until blocks) {
            val base = block * orig - width
            for (phase in 0 until new) {
                val o = block * new + phase
                if (o >= outputLength) break
                val kernel = kernels[phase]
                var acc = 0.0
                for (k in firstTap[phase]..lastTap[phase]) {
                    val j = base + k
                    if (j in 0 until length) acc += kernel[k] * input[j]
                }
                output[o] = acc.toFloat()
            }
        }
        return output
    }

    private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

    /** In-place iterative radix-2 FFT for power-of-two sizes. */
    private object Fft {
        private var size = 0
        private lateinit var cosTable: DoubleArray
        private lateinit var sinTable: DoubleArray
        private lateinit var reversed: IntArray

        @Synchronized
        fun transform(re: DoubleArray, im: DoubleArray) {
            val n = re.size
            if (n != size) prepare(n)
            for (i in 0 until n) {
                val j = reversed[i]
                if (j > i) {
                    var t = re[i]; re[i] = re[j]; re[j] = t
                    t = im[i]; im[i] = im[j]; im[j] = t
                }
            }
            var half = 1
            while (half < n) {
                val step = n / (half * 2)
                var start = 0
                while (start < n) {
                    for (k in 0 until half) {
                        val c = cosTable[k * step]
                        val s = sinTable[k * step]
                        val a = start + k
                        val b = a + half
                        val tr = re[b] * c + im[b] * s
                        val ti = im[b] * c - re[b] * s
                        re[b] = re[a] - tr
                        im[b] = im[a] - ti
                        re[a] += tr
                        im[a] += ti
                    }
                    start += half * 2
                }
                half *= 2
            }
        }

        private fun prepare(n: Int) {
            require(n > 0 && n and (n - 1) == 0) { "FFT size must be a power of two" }
            cosTable = DoubleArray(n / 2) { cos(2 * PI * it / n) }
            sinTable = DoubleArray(n / 2) { sin(2 * PI * it / n) }
            val bits = floor(log10(n.toDouble()) / log10(2.0) + 0.5).toInt()
            reversed = IntArray(n) { Integer.reverse(it) ushr (32 - bits) }
            size = n
        }
    }
}
