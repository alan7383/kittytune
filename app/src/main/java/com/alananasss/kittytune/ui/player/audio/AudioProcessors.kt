package com.alananasss.kittytune.ui.player.audio

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// --- 1. 8D AUDIO (Auto-Pan) ---
class EightDAudioProcessor : BaseAudioProcessor() {
    private var enabled = false
    private var time: Double = 0.0

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) time = 0.0
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return inputAudioFormat // no format change
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        // if disabled, just pass through
        if (!enabled) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        // make sure we have room
        val buffer = replaceOutputBuffer(remaining)

        while (inputBuffer.hasRemaining()) {
            // stereo required for 8d effect
            if (inputAudioFormat.channelCount == 2) {
                // read 16-bit pcm
                val left = inputBuffer.getShort().toFloat()
                val right = inputBuffer.getShort().toFloat()

                // simple oscillator logic
                time += 0.00001 // rotation speed
                val pan = sin(time) // -1.0 to 1.0

                // volume modulation
                val leftVol = (1.0 - pan) / 2.0
                val rightVol = (1.0 + pan) / 2.0

                val newLeft = (left * leftVol).toInt().toShort()
                val newRight = (right * rightVol).toInt().toShort()

                buffer.putShort(newLeft)
                buffer.putShort(newRight)
            } else {
                // mono pass-through
                buffer.putShort(inputBuffer.getShort())
            }
        }
        buffer.flip()
    }
}

// --- 2. MULTI-FX (Bass Boost + Muffled) ---
class FxAudioProcessor : BaseAudioProcessor() {

    private var isMuffled = false
    private var isBassBoost = false

    // filter state variables
    private var x1 = 0f; private var x2 = 0f
    private var y1 = 0f; private var y2 = 0f
    private var b0 = 0f; private var b1 = 0f; private var b2 = 0f
    private var a1 = 0f; private var a2 = 0f

    fun setEffects(muffled: Boolean, bassBoost: Boolean) {
        if (this.isMuffled != muffled || this.isBassBoost != bassBoost) {
            this.isMuffled = muffled
            this.isBassBoost = bassBoost
            calculateCoefficients()
        }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        calculateCoefficients()
        return inputAudioFormat
    }

    private fun calculateCoefficients() {
        val fs = inputAudioFormat.sampleRate.toFloat().coerceAtLeast(44100f)

        if (isMuffled) {
            // --- LOW PASS FILTER (Muffled) ---
            val f0 = 800f // cutoff freq
            val q = 0.707f
            val w0 = (2.0 * PI * f0 / fs).toFloat()
            val alpha = (sin(w0) / (2.0 * q)).toFloat()
            val cosW0 = cos(w0).toFloat()

            val a0 = 1f + alpha
            b0 = ((1f - cosW0) / 2f) / a0
            b1 = (1f - cosW0) / a0
            b2 = ((1f - cosW0) / 2f) / a0
            a1 = (-2f * cosW0) / a0
            a2 = (1f - alpha) / a0

        } else if (isBassBoost) {
            // --- LOW SHELF FILTER (Bass Boost) ---
            val f0 = 100f // boost freq
            val gain = 10f // gain in dB
            val S = 1f
            val A = Math.pow(10.0, gain / 40.0).toFloat()
            val w0 = (2.0 * PI * f0 / fs).toFloat()
            val sinW0 = sin(w0).toFloat()
            val cosW0 = cos(w0).toFloat()
            val alpha = sinW0 / 2f * Math.sqrt(((A + 1f / A) * (1f / S - 1f) + 2f).toDouble()).toFloat()
            val beta = 2f * Math.sqrt(A.toDouble()).toFloat() * alpha

            val a0 = (A + 1f) + (A - 1f) * cosW0 + beta
            b0 = (A * ((A + 1f) - (A - 1f) * cosW0 + beta)) / a0
            b1 = (2f * A * ((A - 1f) - (A + 1f) * cosW0)) / a0
            b2 = (A * ((A + 1f) - (A - 1f) * cosW0 - beta)) / a0
            a1 = (-2f * ((A - 1f) + (A + 1f) * cosW0)) / a0
            a2 = ((A + 1f) + (A - 1f) * cosW0 - beta) / a0
        } else {
            resetState()
        }
    }

    private fun resetState() {
        x1 = 0f; x2 = 0f; y1 = 0f; y2 = 0f
        b0 = 1f; b1 = 0f; b2 = 0f; a1 = 0f; a2 = 0f
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        // pass-through if no effects active
        if (!isMuffled && !isBassBoost) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)

        while (inputBuffer.hasRemaining()) {
            val x = inputBuffer.getShort().toFloat()

            // biquad filter implementation
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2

            // update state
            x2 = x1
            x1 = x
            y2 = y1
            y1 = y

            // hard clipping to avoid overflow
            val out = y.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
            buffer.putShort(out)
        }
        buffer.flip()
    }
}
// --- 3. REVERB (Simple Delay Line) ---
class ReverbAudioProcessor : BaseAudioProcessor() {
    private var enabled = false
    private var buffer: ShortArray = ShortArray(0)
    private var cursor = 0
    // params: 150ms delay, 0.5 decay
    private val delayMs = 150
    private val decay = 0.5f

    fun setEnabled(enabled: Boolean) {
        if (this.enabled != enabled) {
            this.enabled = enabled
            // clear buffer on disable
            if (!enabled) buffer = ShortArray(0)
        }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // calculate buffer size based on sample rate
        val bufferSize = (inputAudioFormat.sampleRate * (delayMs / 1000.0) * inputAudioFormat.channelCount).toInt()
        buffer = ShortArray(bufferSize)
        cursor = 0
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!enabled) {
            val outputBuffer = replaceOutputBuffer(remaining)
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val outputBuffer = replaceOutputBuffer(remaining)

        while (inputBuffer.hasRemaining()) {
            val inputSample = inputBuffer.getShort()

            // get the past sample
            val delayedSample = buffer[cursor]

            // mix: input + (echo * decay)
            // clamp to avoid crackling
            val outputSample = (inputSample + delayedSample * decay).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()

            // write output
            outputBuffer.putShort(outputSample)

            // feed back into delay line
            buffer[cursor] = outputSample

            cursor++
            if (cursor >= buffer.size) cursor = 0
        }
        outputBuffer.flip()
    }
}