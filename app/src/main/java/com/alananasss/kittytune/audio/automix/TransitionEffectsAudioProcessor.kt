package com.alananasss.kittytune.audio.automix

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

// The Spotify-Mix Effects control's voice - the "dramatic exit" the white curve draws.
//
// One instance rides each deck's sink chain and plays a **damped feedback delay**: a short
// fixed tail for the reverb modes, a beat-timed ½/¾ repeat for the delay and echo modes.
// The fade loop (and the editor's preview player) publish the current wet level every
// frame through [setTail] - the value comes straight from [effectWetAt], the same envelope
// the waveform canvas draws, so what you see is what you hear.
//
//  Design notes
//
//  - Dry when idle. With [setTail] never called (or `wet = 0`), the processor is a
//    bit-identical copy - a memcpy fast path, exactly like [AutomixDuckAudioProcessor].
//  - No clicks. The wet level, delay length and feedback are *targets*: each is eased
//    per sample (≈15 ms) inside the audio thread, so a frame step never steps the tail.
//  - Damping in the feedback path (a one-pole low-pass at ~3.5 kHz) turns each repeat
//    a little darker - the tail fades like a room, not like a metronome.
//  - Peak-safe. The wet contribution is scaled (≤ [WET_CEILING]) and the output is
//    clamped; the safety limiter further downstream catches the rest on the main decks.
@UnstableApi
class TransitionEffectsAudioProcessor : BaseAudioProcessor() {

    private var sampleRate = 0
    private var channelCount = 0

    // Per-frame published targets (the fade loop writes these) 

    @Volatile private var targetWet = 0f
    @Volatile private var targetDelayMs = 0f
    @Volatile private var targetFeedback = 0f

    // Eased audio-thread state

    private var wet = 0f
    private var feedback = 0f
    private var delayFrames = 0

    // Circular per-channel buffer, `bufferFrames * channelCount` floats.
    private var buffer = FloatArray(0)
    private var bufferFrames = 0
    private var writeFrame = 0

    // The one-pole damper inside the feedback path, per channel.
    private var damp = FloatArray(0)

    // The largest delay (ms) the buffer can hold - 1.5 s covers a ¾-beat at any dance tempo.
    private val maxDelayMs = 1_500f

    // Publishes the frame's tail: [wetLevel] 0..1 (0 = dry), the repeat spacing in [delayMs]
    // and the [feedbackAmount] 0..0.6. Safe to call every fade step - these are targets.
    fun setTail(wetLevel: Float, delayMs: Float, feedbackAmount: Float) {
        targetWet = (wetLevel.coerceIn(0f, 1f) * WET_CEILING)
        targetDelayMs = delayMs.coerceIn(20f, maxDelayMs)
        targetFeedback = feedbackAmount.coerceIn(0f, 0.6f)
    }

    // Dries the tail out - the transition ended or this deck never carried one.
    fun resetTail() {
        targetWet = 0f
        targetFeedback = 0f
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount.coerceAtLeast(1)
        bufferFrames = ((maxDelayMs / 1000f) * sampleRate).toInt().coerceAtLeast(1)
        buffer = FloatArray(bufferFrames * channelCount)
        damp = FloatArray(channelCount)
        delayFrames = (targetDelayMs / 1000f * sampleRate).toInt().coerceIn(1, bufferFrames)

        if (!com.alananasss.kittytune.audio.AudioRoutingMatrix.isRouted(com.alananasss.kittytune.audio.AudioStage.TRANSITION_EFFECTS)) {
            return inputAudioFormat
        }
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    @Deprecated("Deprecated in Java")
    override fun onFlush() {
        // A flush arrives on seeks - including the loop region's seek-backs. The tail is
        // kept while it rings (cutting it is the click this class exists to avoid); a dry
        // processor keeps the always-clean-restart contract.
        if (targetWet <= 0f && wet <= 0.001f) {
            buffer.fill(0f)
            damp.fill(0f)
        }
    }

    override fun onReset() {
        onFlush()
        sampleRate = 0
        wet = 0f
        feedback = 0f
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()

        // Bit-Perfect on a USB DAC: a bit-transparent copy, no tail, not one sample touched.
        if (!com.alananasss.kittytune.audio.AudioRoutingMatrix.isRouted(com.alananasss.kittytune.audio.AudioStage.TRANSITION_EFFECTS)) {
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

        // Dry fast path: bit-identical copy - the common case for every deck that is not
        // mid-transition-with-effects.
        if (targetWet <= 0f && wet <= 0.001f) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        val frames = remaining / (2 * channelCount)
        if (frames <= 0 || sampleRate <= 0 || bufferFrames <= 0) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        // Per-buffer delay glide: step the delay line length towards its target no faster
        // than the buffer's own duration, so the repeats speed up like a tape, not a click.
        val targetFrames = (targetDelayMs / 1000f * sampleRate).toInt().coerceIn(1, bufferFrames)
        val maxStep = frames
        delayFrames += (targetFrames - delayFrames).coerceIn(-maxStep, maxStep)

        // Per-sample ease constants (≈15 ms to full travel).
        val perSampleEase = (1f / (EASE_MS / 1000f * sampleRate)).coerceIn(0f, 1f)

        var ch = 0
        var wetV = wet
        var fbV = feedback
        while (inputBuffer.remaining() >= 2) {
            val x = inputBuffer.short.toFloat()

            // Ease the frame targets - a published step becomes a smooth ramp.
            wetV += (targetWet - wetV) * perSampleEase
            fbV += (targetFeedback - fbV) * perSampleEase

            val writeIdx = writeFrame * channelCount + ch
            val readFrame = (writeFrame - delayFrames + bufferFrames) % bufferFrames
            val readIdx = readFrame * channelCount + ch

            // The delayed sample, darkened by the one-pole damper (per channel).
            val raw = buffer[readIdx]
            val damped = DAMP_COEFF * raw + (1f - DAMP_COEFF) * damp[ch]
            damp[ch] = damped

            // Write the fed-back signal; read the tail into the mix.
            buffer[writeIdx] = x + fbV * damped
            val y = x + wetV * damped

            output.putShort(y.coerceIn(-32768f, 32767f).toInt().toShort())

            ch++
            if (ch == channelCount) {
                ch = 0
                writeFrame = (writeFrame + 1) % bufferFrames
            }
        }
        wet = wetV
        feedback = fbV

        if (inputBuffer.hasRemaining()) {
            output.put(inputBuffer)
        }
        output.flip()
    }

    companion object {
        // The tail's wet contribution ceiling - keeps the summed decks off the rails.
        private const val WET_CEILING = 0.55f

        // How fast the eased targets travel, in milliseconds.
        private const val EASE_MS = 15f

        // The feedback damper's one-pole coefficient (≈3.5 kHz at 48 kHz).
        private const val DAMP_COEFF = 0.55f
    }
}
