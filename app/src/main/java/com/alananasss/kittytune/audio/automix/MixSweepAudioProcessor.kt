package com.alananasss.kittytune.audio.automix

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
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

    @Volatile private var mode: FilterMode = FilterMode.BYPASS
    @Volatile private var targetCutoffHz = 1000f
    @Volatile private var targetQ = 0.7f

    // Current (smoothed) design point the coefficients are computed from
    private var currentCutoffHz = 1000f
    private var currentQ = 0.7f

    // A smooth release from the filter into BYPASS is in progress (see [resetSweep])
    @Volatile private var releasing = false

    // Transposed Direct Form II state, per channel
    private var s1 = FloatArray(2)
    private var s2 = FloatArray(2)

    // Coefficients for the current design point
    private var b0 = 1f; private var b1 = 0f; private var b2 = 0f
    private var a1 = 0f; private var a2 = 0f

    fun setMode(newMode: FilterMode) {
        mode = newMode
        releasing = false // the new transition owns the filter - release cancelled
    }

    // Publishes a new sweep point. Safe to call every fade step: the values are targets, the
    // filter glides towards them at its own audio-rate-quiet pace
    fun setSweep(cutoffHz: Float, q: Float) {
        releasing = false // the new transition owns the filter — release cancelled
        targetCutoffHz = cutoffHz.coerceIn(20f, 20_000f)
        targetQ = q.coerceIn(0.3f, 12f)
    }

    // Fully bypasses the filter - as a SMOOTH release when audio is flowing through it.
    //
    // Audit (hiss/clicks): resetSweep() used to switch the mode to BYPASS instantly and
    // zero the delay lines - one buffer, one discontinuity. A WAVE transition ended on a deck
    // that kept playing the rest of the track through a band-pass at ~465 Hz: an instant
    // "dry" switch = an audible click/pop at the end of every wave transition.
    // Now the processor first GLIDES the sweep to its transparency point with its own
    // glissando (HPF -> 20 Hz, BPF -> up + Q to minimum), and only once it reaches that point
    // does it switch to BYPASS with cleared state. The instant path remains for an already-off
    // filter (the common case - memcpy fast path and non-Rise/Wave preset transitions).
    fun resetSweep() {
        if (mode == FilterMode.BYPASS) {
            targetCutoffHz = 1000f
            targetQ = 0.7f
            currentCutoffHz = 1000f
            currentQ = 0.7f
            s1.fill(0f)
            s2.fill(0f)
            return
        }
        // Smooth release: the target is the transparency point of the current filter type; once
        // the glissando arrives (≤ ~1/3 octave away), queueInput switches the mode to BYPASS
        releasing = true
        targetQ = 0.3f
        targetCutoffHz = when (mode) {
            FilterMode.HIGH_PASS -> 20f // an HPF at 20 Hz is acoustically transparent
            FilterMode.LOW_PASS -> 20_000f // an LPF at 20 kHz is acoustically transparent
            else -> 16_000f // BPF: center pushed up, Q to minimum — the coloration melts away
        }
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
        // A flush arrives on seeks and stream changes - including the phase-lock
        // correction seeks the automix fade loop performs mid-transition. Zeroing the
        // delay lines while the sweep is ENGAGED is a step discontinuity in a running
        // IIR filter: an audible click. The state is kept while the filter works; a
        // bypassed filter keeps the old always-clean-restart contract
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

        // Bit-Perfect on a USB DAC: this processor must be a bit-transparent copy —
        // no filter, no gain, not a single sample touched. Checked per buffer (never per
        // sample) so the audio thread stays allocation-free
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

        // BYPASS fast path: bit-identical copy, zero DSP. Every preset except Rise/Wave keeps
        // the processor here for the whole track (a hand-drawn Custom leg engages LOW/HIGH_PASS
        // only while its fraction is live), so the common case costs a memcpy
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

        // Glide the design point across this buffer: at most MAX_CUTOFF_OCTAVES_PER_SECOND
        // octaves of travel per second of audio, and Q moves in parallel. Frames/bytesPerFrame
        // gives the buffer duration; the fraction of the whole glide we can afford in one
        // buffer is duration × octavesPerSecond ÷ remaining octaves
        val bufferSeconds = frames.toFloat() / sampleRate
        val cutoffMove = MAX_CUTOFF_OCTAVES_PER_SECOND * bufferSeconds
        val cutoffOctaves = kotlin.math.ln(targetCutoffHz / currentCutoffHz) / LN2
        val stepOctaves = cutoffOctaves.coerceIn(-cutoffMove, cutoffMove)
        currentCutoffHz = (currentCutoffHz * 2f.pow(stepOctaves)).coerceIn(20f, 20_000f)
        currentQ += (targetQ - currentQ).coerceIn(-Q_MOVE_PER_BUFFER, Q_MOVE_PER_BUFFER)
        recompute()
        val finishRelease = releasing && kotlin.math.abs(cutoffOctaves) < 1f / 3f

        var ch = 0
        var s1v = s1[0]
        var s2v = s2[0]
        while (inputBuffer.remaining() >= 2) {
            val x = inputBuffer.short.toFloat()
            // Transposed Direct Form II: y = b0·x + s1; s1 = b1·x − a1·y + s2; s2 = b2·x − a2·y
            val y = b0 * x + s1v
            s1v = b1 * x - a1 * y + s2v
            s2v = b2 * x - a2 * y
            output.putShort(y.coerceIn(-32768f, 32767f).toInt().toShort())
            // Park the register pair of the channel just processed, load the next channel's.
            // Writing back every sample keeps the mapping between registers and channel slots
            // trivially correct at any channel count (1, 2, ...)
            s1[ch] = s1v
            s2[ch] = s2v
            ch++
            if (ch == channelCount) ch = 0
            s1v = s1[ch]
            s2v = s2[ch]
        }
        // A non-frame-aligned remainder cannot exist in PCM16 from ExoPlayer, but a lost byte
        // here would be a one-sample desync between decks - copy it through instead of
        // dropping it, whatever it is
        if (inputBuffer.hasRemaining()) {
            output.put(inputBuffer)
        }

        // End of the smooth release - AFTER the buffer has been processed: this buffer went
        // through an almost transparent filter, the next one takes the BYPASS fast path. The
        // jump between "less than 1/3 octave from the transparency point" and the dry signal is inaudible
        if (finishRelease) {
            releasing = false
            mode = FilterMode.BYPASS
            targetCutoffHz = 1000f
            targetQ = 0.7f
            currentCutoffHz = 1000f
            currentQ = 0.7f
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
        private val LN2 = kotlin.math.ln(2f)

        // Sweep glide speed: faster than any preset sweep, slow enough to be click-free.
        private const val MAX_CUTOFF_OCTAVES_PER_SECOND = 6f

        // Q changes at most this much per buffer; 0.05 per ~3 ms buffer ≈ 16 Q/s.
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
