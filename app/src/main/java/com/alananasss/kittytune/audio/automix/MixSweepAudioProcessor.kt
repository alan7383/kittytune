package com.alananasss.kittytune.audio.automix

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

// Real-time swept filter for the Mix transition presets - the piece that turns the preset
// math into sound:
//
//  - Rise drives mode [FilterMode.HIGH_PASS] with a 40 Hz ->3 kHz exponential cutoff sweep
//    and growing resonance, the build-up before the drop;
//  - Wave drives mode [FilterMode.BAND_PASS] on both decks with center frequencies swept
//    in counter-phase, the timbre shuttle;
//  - hand-drawn Custom plan legs ([planFrameAt] frames) drive [FilterMode.LOW_PASS] -
//    the muffle the SVF preview twin ([TransitionSweepAudioProcessor]) already had — or
//    [FilterMode.HIGH_PASS], at the exact fractions the editor's curve drew;
//  - every other preset leaves it in [FilterMode.BYPASS], where it is a bit-transparent copy.
//
//  Sweeping a biquad without clicks
//
// Recomputing coefficients every buffer is safe only if the cutoff cannot jump: a step in
// the cutoff of an IIR filter whose delay lines still hold the old phase response is a step
// discontinuity in the output. So the processor owns its own smoothing: whatever cutoff the
// fade loop publishes is approached by up to [MAX_CUTOFF_OCTAVES_PER_SECOND] octaves of
// travel per second, split evenly across the buffers that arrive in between. The fade loop
// publishes a new target every ~15 ms; the filter glides between them at ≤ 6 oct/s, which is
// faster than any musical sweep in the presets and slow enough to be seamless.
@UnstableApi
class MixSweepAudioProcessor : BaseAudioProcessor() {

    // Bypass (bit-transparent), the climbing high-pass, the muffle, the timbre shuttle
    enum class FilterMode { BYPASS, HIGH_PASS, LOW_PASS, BAND_PASS }

    private var sampleRate = 0
    private var channelCount = 0

    private data class Target(
        val mode: FilterMode = FilterMode.BYPASS,
        val cutoffHz: Float = 1000f,
        val q: Float = 0.7f,
        val releasing: Boolean = false,
    )

    private val target = AtomicReference(Target())
    private var currentCutoffHz = 1000f
    private var currentQ = 0.7f
    private var currentWet = 0f

    // Transposed Direct Form II state, per channel
    private var s1 = FloatArray(2)
    private var s2 = FloatArray(2)

    // Coefficients for the current design point
    private var b0 = 1f; private var b1 = 0f; private var b2 = 0f
    private var a1 = 0f; private var a2 = 0f

    fun setMode(newMode: FilterMode) {
        if (newMode == FilterMode.BYPASS) resetSweep()
        else target.updateAndGet { it.copy(mode = newMode, releasing = false) }
    }

    fun setSweep(cutoffHz: Float, q: Float) {
        if (!cutoffHz.isFinite() || !q.isFinite()) return
        target.updateAndGet {
            it.copy(cutoffHz = cutoffHz.coerceIn(20f, 20_000f), q = q.coerceIn(0.3f, 12f), releasing = false)
        }
    }

    /** A wet-to-dry ramp also works for a band-pass, which cannot glide to a flat response. */
    fun resetSweep() {
        target.updateAndGet { if (it.mode == FilterMode.BYPASS || it.releasing) it else it.copy(releasing = true) }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // State is initialized BEFORE the Bit-Perfect early return: sampleRate/channelCount
        // left at 0 (or s1/s2 null) hit a division by channelCount in queueInput the moment
        // a DAC disconnects mid-stream and the buffers still flow through. The BP branch
        // still returns identity for ANY PCM encoding - chain format stays 1:1
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount.coerceAtLeast(1)
        s1 = FloatArray(channelCount)
        s2 = FloatArray(channelCount)
        recompute(target.get().mode)

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
        // A flush arrives on seeks and stream changes - including the phase-lock
        // correction seeks the automix fade loop performs mid-transition. Zeroing the
        // delay lines while the sweep is ENGAGED is a step discontinuity in a running
        // IIR filter: an audible click. The state is kept while the filter works; a
        // bypassed filter keeps the old always-clean-restart contract
        if (target.get().mode == FilterMode.BYPASS) {
            currentWet = 0f
            s1.fill(0f)
            s2.fill(0f)
        }
    }

    override fun onReset() {
        target.set(Target())
        onFlush()
        sampleRate = 0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val requested = target.get()
        val remaining = inputBuffer.remaining()
        val output = replaceOutputBuffer(remaining)
        if (requested.mode == FilterMode.BYPASS || sampleRate <= 0 || channelCount <= 0 ||
            inputAudioFormat.encoding != C.ENCODING_PCM_16BIT ||
            !com.alananasss.kittytune.audio.AudioRoutingMatrix.isRouted(com.alananasss.kittytune.audio.AudioStage.MIX_SWEEP)
        ) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        val frames = remaining / (2 * channelCount)
        if (frames == 0) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        val seconds = frames.toFloat() / sampleRate
        val maxCutoffStep = MAX_CUTOFF_OCTAVES_PER_SECOND * seconds
        val cutoffStep = (ln(requested.cutoffHz / currentCutoffHz) / LN2).coerceIn(-maxCutoffStep, maxCutoffStep)
        currentCutoffHz = (currentCutoffHz * 2f.pow(cutoffStep)).coerceIn(20f, 20_000f)
        currentQ += (requested.q - currentQ).coerceIn(-Q_MOVE_PER_BUFFER, Q_MOVE_PER_BUFFER)
        recompute(requested.mode)

        val wantedWet = if (requested.releasing) 0f else 1f
        val wetStep = WET_RELEASE_PER_SECOND / sampleRate
        var wet = currentWet
        repeat(frames) {
            for (channel in 0 until channelCount) {
                val dry = inputBuffer.short.toFloat()
                val filtered = b0 * dry + s1[channel]
                s1[channel] = b1 * dry - a1 * filtered + s2[channel]
                s2[channel] = b2 * dry - a2 * filtered
                val sample = dry + wet * (filtered - dry)
                output.putShort(sample.coerceIn(-32768f, 32767f).toInt().toShort())
            }
            wet = if (requested.releasing) (wet - wetStep).coerceAtLeast(0f)
                else (wet + wetStep).coerceAtMost(wantedWet)
        }
        currentWet = wet
        if (inputBuffer.hasRemaining()) output.put(inputBuffer)
        if (requested.releasing && wet == 0f && target.compareAndSet(requested, Target())) {
            currentCutoffHz = 1000f
            currentQ = 0.7f
            s1.fill(0f)
            s2.fill(0f)
        }
        output.flip()
    }

    private fun recompute(mode: FilterMode) {
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
        private val LN2 = kotlin.math.ln(2f)

        // Sweep glide speed: faster than any preset sweep, slow enough to be click-free.
        private const val MAX_CUTOFF_OCTAVES_PER_SECOND = 6f

        // Q changes at most this much per buffer; 0.05 per ~3 ms buffer ≈ 16 Q/s.
        private const val WET_RELEASE_PER_SECOND = 5f

        private const val Q_MOVE_PER_BUFFER = 0.05f

        // RBJ Audio EQ Cookbook high-pass, normalized so b0..b2/a1..a2 are Direct-Form ready.
        // Returns [b0, b1, b2, a1, a2]
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

        // RBJ Audio EQ Cookbook low-pass - the muffle leg of a hand-drawn Custom plan: as
        // the cutoff falls toward 20 Hz the deck sinks into the floor, exactly the shape
        // the preview's SVF low tap renders. Returns [b0, b1, b2, a1, a2].
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

        // RBJ band-pass with constant 0 dB peak gain (b0 = alpha): the peak stays at unity for
        // every Q, so a narrow resonant sweep gets thinner, not louder. (The "constant skirt
        // gain" variant would use b0 = Q·alpha instead.)
        // Returns [b0, b1, b2, a1, a2]
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
