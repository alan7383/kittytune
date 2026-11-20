package com.alananasss.kittytune.audio.automix

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

// Real-time swept filter for the Mix transition presets - the piece that turns the preset
// math into sound:
//
//  - Rise drives mode [FilterMode.HIGH_PASS] with a 40 Hz -> 3 kHz exponential cutoff sweep
//    and growing resonance, the build-up before the drop;
//  - Wave drives mode [FilterMode.BAND_PASS] on both decks with center frequencies swept
//    in counter-phase, the timbre shuttle;
//  - hand-drawn Custom plan legs ([planFrameAt] frames) drive [FilterMode.LOW_PASS] -
//    the muffle the SVF preview twin ([TransitionSweepAudioProcessor]) already had — or
//    [FilterMode.HIGH_PASS], at the exact fractions the editor's curve drew;
//  - every other preset leaves it in [FilterMode.BYPASS], where it is a bit-transparent copy.
@UnstableApi
class MixSweepAudioProcessor : BaseAudioProcessor() {

    // Bypass (bit-transparent), the climbing high-pass, the muffle, the timbre shuttle
    enum class FilterMode { BYPASS, HIGH_PASS, LOW_PASS, BAND_PASS }

    private var sampleRate = 0
    private var channelCount = 0

    @Volatile private var mode: FilterMode = FilterMode.BYPASS
    @Volatile private var targetCutoffHz = 1000f
    @Volatile private var targetQ = 0.7f

    // Current (smoothed) design point the coefficients are computed from
    private var currentCutoffHz = 1000f
    private var currentQ = 0.7f

    // Wet/dry mix (1f = 100% filtered, 0f = 100% dry). Used to release BAND_PASS into BYPASS
    // without a level jump (a band-pass cannot become full-range by moving its center frequency alone).
    private var currentWet = 1f
    @Volatile private var targetWet = 1f

    // A smooth release from the filter into BYPASS is in progress (see [resetSweep])
    @Volatile private var releasing = false

    // Transposed Direct Form II state, per channel
    private var s1 = FloatArray(2)
    private var s2 = FloatArray(2)

    // Coefficients for the current design point
    private var b0 = 1f; private var b1 = 0f; private var b2 = 0f
    private var a1 = 0f; private var a2 = 0f

    fun setMode(newMode: FilterMode) {
        if (newMode == FilterMode.BYPASS) {
            resetSweep()
            return
        }
        mode = newMode
        releasing = false
        targetWet = 1f
        currentWet = 1f
    }

    // Publishes a new sweep point. Safe to call every fade step: the values are targets, the
    // filter glides towards them at its own audio-rate-quiet pace
    fun setSweep(cutoffHz: Float, q: Float) {
        releasing = false
        targetWet = 1f
        currentWet = 1f
        targetCutoffHz = cutoffHz.coerceIn(20f, 20_000f)
        targetQ = q.coerceIn(0.3f, 12f)
    }

    // Fully bypasses the filter - as a SMOOTH release when audio is flowing through it.
    //
    // - HPF glides down to 20 Hz (acoustically transparent)
    // - LPF glides up to 20 kHz (acoustically transparent)
    // - BPF cannot become transparent by moving its cutoff to 16 kHz (it would still strip all
    //   bass and mids, causing a level jump when switching to BYPASS). Instead, BPF relaxes Q
    //   toward 0.3 while smoothly crossfading wet -> dry (1f -> 0f) sample-by-sample.
    fun resetSweep() {
        if (mode == FilterMode.BYPASS) {
            targetCutoffHz = 1000f
            targetQ = 0.7f
            currentCutoffHz = 1000f
            currentQ = 0.7f
            currentWet = 1f
            targetWet = 1f
            s1.fill(0f)
            s2.fill(0f)
            return
        }
        releasing = true
        targetQ = 0.3f
        when (mode) {
            FilterMode.HIGH_PASS -> {
                targetCutoffHz = 20f
                targetWet = 1f
            }
            FilterMode.LOW_PASS -> {
                targetCutoffHz = 20_000f
                targetWet = 1f
            }
            FilterMode.BAND_PASS -> {
                // Keep center stable near the midband and ramp wet mix down to 0 (full dry)
                targetCutoffHz = currentCutoffHz.coerceIn(250f, 2_500f)
                targetWet = 0f
            }
            FilterMode.BYPASS -> Unit
        }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount.coerceAtLeast(1)
        s1 = FloatArray(channelCount)
        s2 = FloatArray(channelCount)
        recompute()

        if (!com.alananasss.kittytune.audio.AudioRoutingMatrix.isRouted(com.alananasss.kittytune.audio.AudioStage.MIX_SWEEP)) {
            return inputAudioFormat
        }
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    @Deprecated("Deprecated in Java")
    override fun onFlush() {
        if (mode == FilterMode.BYPASS) {
            s1.fill(0f)
            s2.fill(0f)
        }
    }

    override fun onReset() {
        onFlush()
        sampleRate = 0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()

        if (!com.alananasss.kittytune.audio.AudioRoutingMatrix.isRouted(com.alananasss.kittytune.audio.AudioStage.MIX_SWEEP)) {
            val passthrough = replaceOutputBuffer(remaining)
            passthrough.put(inputBuffer)
            passthrough.flip()
            return
        }

        val output = replaceOutputBuffer(remaining)

        if (remaining == 0) {
            output.flip()
            return
        }

        if (mode == FilterMode.BYPASS) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        val frames = remaining / (2 * channelCount)
        if (frames <= 0 || sampleRate <= 0) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        val bufferSeconds = frames.toFloat() / sampleRate
        val cutoffMove = MAX_CUTOFF_OCTAVES_PER_SECOND * bufferSeconds
        val cutoffOctaves = ln(targetCutoffHz / currentCutoffHz) / LN2
        val stepOctaves = cutoffOctaves.coerceIn(-cutoffMove, cutoffMove)
        currentCutoffHz = (currentCutoffHz * 2f.pow(stepOctaves)).coerceIn(20f, 20_000f)
        currentQ += (targetQ - currentQ).coerceIn(-Q_MOVE_PER_BUFFER, Q_MOVE_PER_BUFFER)
        recompute()

        val wetStart = currentWet
        val maxWetDelta = WET_RELEASE_PER_SECOND * bufferSeconds
        val wetEnd = (wetStart + (targetWet - wetStart).coerceIn(-maxWetDelta, maxWetDelta)).coerceIn(0f, 1f)
        currentWet = wetEnd
        val totalSamples = frames * channelCount
        val wetStep = if (totalSamples > 0) (wetEnd - wetStart) / totalSamples else 0f
        var sampleWet = wetStart

        val finishRelease = releasing && when (mode) {
            FilterMode.BAND_PASS -> wetEnd <= 0.01f
            else -> abs(cutoffOctaves) < 1f / 3f
        }

        var ch = 0
        var s1v = s1[0]
        var s2v = s2[0]
        while (inputBuffer.remaining() >= 2) {
            val x = inputBuffer.short.toFloat()
            val y = b0 * x + s1v
            s1v = b1 * x - a1 * y + s2v
            s2v = b2 * x - a2 * y

            val mixed = if (sampleWet >= 0.999f) {
                y
            } else {
                x + sampleWet * (y - x)
            }
            sampleWet = (sampleWet + wetStep).coerceIn(0f, 1f)

            output.putShort(mixed.coerceIn(-32768f, 32767f).toInt().toShort())

            s1[ch] = s1v
            s2[ch] = s2v
            ch++
            if (ch == channelCount) ch = 0
            s1v = s1[ch]
            s2v = s2[ch]
        }

        if (inputBuffer.hasRemaining()) {
            output.put(inputBuffer)
        }

        if (finishRelease) {
            releasing = false
            mode = FilterMode.BYPASS
            targetCutoffHz = 1000f
            targetQ = 0.7f
            currentCutoffHz = 1000f
            currentQ = 0.7f
            currentWet = 1f
            targetWet = 1f
            s1.fill(0f)
            s2.fill(0f)
        }
        output.flip()
    }

    private fun recompute() {
        if (sampleRate <= 0) return
        val coeffs = when (mode) {
            FilterMode.HIGH_PASS -> rbjHighPass(sampleRate, currentCutoffHz, currentQ)
            FilterMode.LOW_PASS -> rbjLowPass(sampleRate, currentCutoffHz, currentQ)
            FilterMode.BAND_PASS -> rbjBandPass(sampleRate, currentCutoffHz, currentQ)
            FilterMode.BYPASS -> return
        }
        b0 = coeffs[0]; b1 = coeffs[1]; b2 = coeffs[2]
        a1 = coeffs[3]; a2 = coeffs[4]
    }

    companion object {
        private val LN2 = ln(2f)

        // Sweep glide speed: faster than any preset sweep, slow enough to be click-free.
        private const val MAX_CUTOFF_OCTAVES_PER_SECOND = 6f

        // Wet->dry crossfade speed during BAND_PASS release (~200 ms full fade to dry).
        private const val WET_RELEASE_PER_SECOND = 5f

        // Q changes at most this much per buffer; 0.05 per ~3 ms buffer ≈ 16 Q/s.
        private const val Q_MOVE_PER_BUFFER = 0.05f

        // RBJ Audio EQ Cookbook high-pass, normalized so b0..b2/a1..a2 are Direct-Form ready.
        fun rbjHighPass(sampleRate: Int, cutoffHz: Float, q: Float): FloatArray {
            val fs = sampleRate.coerceAtLeast(1).toFloat()
            val w0 = 2.0 * PI * cutoffHz.toDouble().coerceIn(10.0, fs * 0.45) / fs
            val cosW = cos(w0).toFloat()
            val sinW = sin(w0).toFloat()
            val alpha = sinW / (2f * q)
            val a0 = 1f + alpha
            val b0 = ((1f + cosW) / 2f) / a0
            val b1 = (-(1f + cosW)) / a0
            val b2 = ((1f + cosW) / 2f) / a0
            val a1 = (-2f * cosW) / a0
            val a2 = (1f - alpha) / a0
            return floatArrayOf(b0, b1, b2, a1, a2)
        }

        // RBJ Audio EQ Cookbook low-pass.
        fun rbjLowPass(sampleRate: Int, cutoffHz: Float, q: Float): FloatArray {
            val fs = sampleRate.coerceAtLeast(1).toFloat()
            val w0 = 2.0 * PI * cutoffHz.toDouble().coerceIn(10.0, fs * 0.45) / fs
            val cosW = cos(w0).toFloat()
            val sinW = sin(w0).toFloat()
            val alpha = sinW / (2f * q)
            val a0 = 1f + alpha
            val b0 = ((1f - cosW) / 2f) / a0
            val b1 = (1f - cosW) / a0
            val b2 = ((1f - cosW) / 2f) / a0
            val a1 = (-2f * cosW) / a0
            val a2 = (1f - alpha) / a0
            return floatArrayOf(b0, b1, b2, a1, a2)
        }

        // RBJ band-pass with constant 0 dB peak gain (b0 = alpha).
        fun rbjBandPass(sampleRate: Int, centerHz: Float, q: Float): FloatArray {
            val fs = sampleRate.coerceAtLeast(1).toFloat()
            val w0 = 2.0 * PI * centerHz.toDouble().coerceIn(10.0, fs * 0.45) / fs
            val cosW = cos(w0).toFloat()
            val sinW = sin(w0).toFloat()
            val alpha = sinW / (2f * q)
            val a0 = 1f + alpha
            val b0 = alpha / a0
            val b1 = 0f
            val b2 = (-alpha) / a0
            val a1 = (-2f * cosW) / a0
            val a2 = (1f - alpha) / a0
            return floatArrayOf(b0, b1, b2, a1, a2)
        }
    }
}
