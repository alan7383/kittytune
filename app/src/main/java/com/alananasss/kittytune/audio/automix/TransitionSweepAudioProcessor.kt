package com.alananasss.kittytune.audio.automix

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.tan

// The transition preview's sweeping filter - the audible twin of the magenta curve the
// editor draws. Where the real engine renders its sweep inside the playback chain, this one
// rides the two lightweight preview decks in `ui.player.mix.TransitionPreviewPlayer`, so the
// "Edit transition" Play button performs the filter move with actual audio: Deck A's
// high-pass sweeps up until the drop, or Deck B enters behind a low-pass that opens up.
//
// Topology is a TPT (topology-preserving transform) state-variable filter - the trapezoidally
// integrated ZDF form. It stays stable while the cutoff moves every frame, which the classic
// Chamberlin form does not, and its low/high taps share one integrator pair.
//
// The cutoff arrives as the canvas' 0..1 fraction of the log 40 Hz -> 12 kHz span
// (`filterOutFractionAt` / `filterInFractionAt` feed it directly). The `g` coefficient is
// eased per sample toward the last requested value - a frame-rate cutoff step would otherwise
// zipper. Mode switches are instant; the integrator memory carries through, so a filter that
// fades in never clicks.
@UnstableApi
class TransitionSweepAudioProcessor : BaseAudioProcessor() {

    // Which tap the processor outputs; [OFF] is a bit-exact bypass.
    enum class SweepMode { OFF, LOW_PASS, HIGH_PASS }

    // The two integrator states of one channel's SVF.
    private class Integrators {
        var ic1 = 0.0 // band accumulator
        var ic2 = 0.0 // low accumulator
    }

    private var sampleRate = 0
    private var channelCount = 0

    // Requested state, written from the preview sync loop (UI thread).
    @Volatile
    private var requestedMode: SweepMode = SweepMode.OFF

    @Volatile
    private var cutoffFraction: Float = 1f

    // Audio-thread runtime state: the eased cutoff and the per-channel integrator pairs.
    private var activeMode: SweepMode = SweepMode.OFF
    private var currentG = 0.0
    private val left = Integrators()
    private val right = Integrators()

    // Points the sweep at [mode] with [fraction] of the log 40 Hz -> 12 kHz span (0 = fully
    // closed, 1 = fully open). [OFF][SweepMode.OFF] puts the deck back on dry signal.
    fun setSweep(mode: SweepMode, fraction: Float) {
        requestedMode = mode
        cutoffFraction = fraction.coerceIn(0f, 1f)
    }

    // Back to dry, for decks the plan does not sweep.
    fun clear() {
        requestedMode = SweepMode.OFF
        cutoffFraction = 1f
    }

    private fun cutoffFractionToHz(fraction: Float): Double {
        val clamped = fraction.toDouble().coerceIn(0.0, 1.0)
        val hz = F_MIN_HZ * (F_MAX_HZ / F_MIN_HZ).pow(clamped)
        // Keep the bilinear transform well clear of Nyquist.
        return hz.coerceAtMost(sampleRate * 0.45)
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // Format fields are captured BEFORE the Bit-Perfect bypass: the DAC can be
        // unplugged mid-playback, and the dry branch of queueInput still needs a valid
        // sampleRate/channelCount to route - otherwise the processor stays configured
        // with zeros forever (no further onConfigure arrives on a hot re-route).
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        // Bit-Perfect: identity configuration for ANY PCM encoding - the chain format
        // must stay 1:1 so the queueInput byte passthrough is bit-exact (never NOT_SET
        // and never a negotiated conversion while the DAC owns the output).
        if (!com.alananasss.kittytune.audio.AudioRoutingMatrix.isRouted(com.alananasss.kittytune.audio.AudioStage.TRANSITION_SWEEP)) {
            return inputAudioFormat
        }
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount > 2) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        // Bit-Perfect on a USB DAC: bit-transparent copy - the SVF integrators stay
        // parked and the PCM bytes reach the DAC untouched.
        if (!com.alananasss.kittytune.audio.AudioRoutingMatrix.isRouted(com.alananasss.kittytune.audio.AudioStage.TRANSITION_SWEEP)) {
            val passthrough = replaceOutputBuffer(remaining)
            passthrough.put(inputBuffer)
            passthrough.flip()
            return
        }

        // Fast path: no sweep asked for and nothing ringing - copy through untouched and
        // drain the integrator memory so a later ramp-up starts clean.
        val requested = requestedMode
        if (requested == SweepMode.OFF && activeMode == SweepMode.OFF) {
            val outputBuffer = replaceOutputBuffer(remaining)
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            currentG = 0.0
            left.ic1 = 0.0; left.ic2 = 0.0
            right.ic1 = 0.0; right.ic2 = 0.0
            return
        }
        activeMode = requested

        val targetG = if (sampleRate > 0) {
            tan(PI * cutoffFractionToHz(cutoffFraction) / sampleRate)
        } else 0.0
        val easePerSample =
            if (sampleRate > 0) (1000.0 / (G_EASE_MS * sampleRate)).coerceIn(0.0, 1.0) else 1.0
        val highPass = requested == SweepMode.HIGH_PASS

        val outputBuffer = replaceOutputBuffer(remaining)
        val sampleCount = remaining / 2
        when (channelCount) {
            1 -> repeat(sampleCount) {
                currentG += (targetG - currentG) * easePerSample
                val input = inputBuffer.short.toDouble() / 32768.0
                val out = sweepSample(input, left, highPass)
                outputBuffer.putShort((out * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort())
            }
            2 -> repeat(sampleCount / 2) {
                currentG += (targetG - currentG) * easePerSample
                val inL = inputBuffer.short.toDouble() / 32768.0
                val inR = inputBuffer.short.toDouble() / 32768.0
                val outL = sweepSample(inL, left, highPass)
                val outR = sweepSample(inR, right, highPass)
                outputBuffer.putShort((outL * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort())
                outputBuffer.putShort((outR * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort())
            }
            else -> {
                outputBuffer.put(inputBuffer)
            }
        }

        // Absorb a trailing partial frame (an odd byte on mono, a half frame on stereo)
        // instead of leaving it behind: Media3's audio thread loops on
        // while(inputBuffer.hasRemaining()) - an unread tail would spin it forever.
        if (inputBuffer.hasRemaining()) {
            inputBuffer.position(inputBuffer.limit())
        }

        outputBuffer.flip()
    }

    // One TPT SVF sample. `ic1` is the band integrator, `ic2` the low one; the taps come
    // straight out of the trapezoidal step (new = 2·step − old), so low and high share the
    // same state and the recurrence stays stable for any eased `g`.
    private fun sweepSample(input: Double, state: Integrators, highPass: Boolean): Double {
        val g = currentG
        val a1 = 1.0 / (1.0 + g * (g + K_RESONANCE))
        val a2 = g * a1
        val a3 = g * a2
        val v3 = input - state.ic2
        val v1 = a1 * state.ic1 + a2 * v3
        val v2 = state.ic2 + a2 * state.ic1 + a3 * v3
        state.ic1 = 2.0 * v1 - state.ic1
        state.ic2 = 2.0 * v2 - state.ic2
        return if (highPass) v3 - K_RESONANCE * v1 else v2
    }

    @Deprecated("Deprecated in Java")
    override fun onFlush() {
        // Flush arrives on seeks - including phase-lock correction seeks made while the
        // sweep is ringing. Zeroing the TPT integrators then is a step discontinuity in
        // a running filter = a click. Only an idle sweep keeps the clean-restart contract.
        if (activeMode == SweepMode.OFF && requestedMode == SweepMode.OFF) {
            currentG = 0.0
            left.ic1 = 0.0; left.ic2 = 0.0
            right.ic1 = 0.0; right.ic2 = 0.0
        }
    }

    override fun onReset() {
        onFlush()
        sampleRate = 0
        channelCount = 0
        activeMode = SweepMode.OFF
    }

    companion object {
        // The sweep's log-span floor - the spec's full-band 20 Hz bottom.
        private const val F_MIN_HZ = 20.0

        // The sweep's log-span ceiling. The spec asks for the full audio band up to 20 kHz;
        // the bilinear SVF is kept well clear of Nyquist (the per-call clamp below holds it
        // under 0.45 × sample rate), and 16 kHz is already "fully open" for every listener -
        // the same ceiling the canvas' filter geometry assumes.
        private const val F_MAX_HZ = 16_000.0

        // Butterworth Q (1/√2) - flat passband, no sweep-induced resonance ring.
        private const val K_RESONANCE = 1.41421356

        // Time constant of the per-sample cutoff ease, so frame-rate steps never zipper.
        private const val G_EASE_MS = 8.0
    }
}
