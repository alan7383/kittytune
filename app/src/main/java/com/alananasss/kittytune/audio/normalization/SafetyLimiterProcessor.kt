package com.alananasss.kittytune.audio.normalization

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Last in the effect chain while normalization is on: a lookahead limiter at -1 dBFS for what the EQ
 * and the effects add on top of a normalized track. It sets no level of its own — that was decided at
 * the start of the chain by [TrackNormalizerProcessor] — and a peak is caught on its first sample.
 *
 * Switched off it still delays the audio by its 5 ms lookahead, so turning normalization on or off
 * never makes the audio jump.
 */
@UnstableApi
class SafetyLimiterProcessor : BaseAudioProcessor() {

    @Volatile private var enabled = false

    private var handle = 0L
    private var handleChannels = 0
    private var handleSampleRate = 0

    init {
        try {
            System.loadLibrary("kittytune_audio_dsp")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("SafetyLimiterProcessor", "Failed to load kittytune_audio_dsp library", e)
        }
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    // Current engage state, so a temporary force-enable (Slam's seam hold) can restore it.
    // Named so it does not shadow BaseAudioProcessor's own isActive() from the Media3 chain.
    fun isLimiterEngaged(): Boolean = enabled

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        handleChannels = inputAudioFormat.channelCount
        handleSampleRate = inputAudioFormat.sampleRate
        // Bit-Perfect: identity configuration for ANY PCM encoding - the chain format
        // must stay 1:1 so the queueInput byte passthrough is bit-exact (never NOT_SET
        // and never a negotiated conversion while the DAC owns the output).
        if (!com.alananasss.kittytune.audio.AudioRoutingMatrix.isRouted(com.alananasss.kittytune.audio.AudioStage.SAFETY_LIMITER)) {
            return inputAudioFormat
        }
        return if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            inputAudioFormat
        } else {
            AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun onFlush() {
        val format = inputAudioFormat
        if (format.encoding != C.ENCODING_PCM_16BIT || format.channelCount <= 0) {
            destroyNative()
            return
        }
        if (handle == 0L || format.channelCount != handleChannels || format.sampleRate != handleSampleRate) {
            destroyNative()
            handle = nativeCreate(format.channelCount, format.sampleRate)
            handleChannels = format.channelCount
            handleSampleRate = format.sampleRate
        } else {
            nativeFlush(handle)
        }
    }

    override fun onReset() = destroyNative()

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        // Bit-Perfect on a USB DAC: the safety limiter is suspended - no gain riding,
        // the PCM bytes reach the DAC exactly as decoded.
        if (!com.alananasss.kittytune.audio.AudioRoutingMatrix.isRouted(com.alananasss.kittytune.audio.AudioStage.SAFETY_LIMITER)) {
            val passthrough = replaceOutputBuffer(remaining)
            passthrough.put(inputBuffer)
            passthrough.flip()
            return
        }
        val frameSize = inputAudioFormat.bytesPerFrame
        if (frameSize <= 0) {
            // Degenerate format (bytesPerFrame unset/negative, e.g. a non-linear-PCM
            // upstream mishap): dividing would throw ArithmeticException on the audio
            // thread - pass the bytes through untouched instead.
            val output = replaceOutputBuffer(remaining)
            output.put(inputBuffer)
            output.flip()
            return
        }
        val frames = remaining / frameSize
        val output = replaceOutputBuffer(remaining)
        if (handle == 0L || frames == 0 || !inputBuffer.isDirect || !output.isDirect) {
            output.put(inputBuffer)
            output.flip()
            return
        }
        nativeProcess(handle, inputBuffer, output, frames, inputBuffer.position(), enabled)
        inputBuffer.position(inputBuffer.limit())
        output.position(frames * frameSize)
        output.flip()
    }

    private fun destroyNative() {
        if (handle != 0L) {
            nativeDestroy(handle)
            handle = 0L
        }
    }

    private companion object {
        @JvmStatic external fun nativeCreate(channels: Int, sampleRate: Int): Long
        @JvmStatic external fun nativeDestroy(handle: Long)
        @JvmStatic external fun nativeFlush(handle: Long)
        @JvmStatic external fun nativeProcess(
            handle: Long,
            input: ByteBuffer,
            output: ByteBuffer,
            frames: Int,
            inOffset: Int,
            active: Boolean,
        )
    }
}
