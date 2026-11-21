package com.alananasss.kittytune.audio.automix

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Mixes PCM preview audio to stereo. Stereo input bypasses the processor. */
@UnstableApi
class StereoNormalizerAudioProcessor : BaseAudioProcessor() {
    private class Matrix(val left: FloatArray, val right: FloatArray)

    private var matrix = Matrix(floatArrayOf(), floatArrayOf())
    private var pendingFrame = ByteBuffer.allocate(0)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) return AudioProcessor.AudioFormat.NOT_SET

        val channels = inputAudioFormat.channelCount
        // AudioFormat only includes the count. For standard Android PCM layouts the channel masks
        // use FL/FR first, then center, LFE and surrounds in channel-index order.
        if (channels !in 1..8 || channels == 2) return AudioProcessor.AudioFormat.NOT_SET
        return AudioProcessor.AudioFormat(inputAudioFormat.sampleRate, 2, inputAudioFormat.encoding)
    }

    @Deprecated("Deprecated in Java")
    override fun onFlush() {
        val channels = inputAudioFormat.channelCount
        if (channels !in 1..8) return
        val left = FloatArray(channels)
        val right = FloatArray(channels)
        left[0] = 1f
        right[if (channels == 1) 0 else 1] = 1f
        when (channels) {
            3 -> { left[2] = SURROUND_GAIN; right[2] = SURROUND_GAIN }
            4 -> { left[2] = SURROUND_GAIN; right[3] = SURROUND_GAIN }
            5, 6, 8 -> {
                left[2] = SURROUND_GAIN
                right[2] = SURROUND_GAIN
                val surround = if (channels == 5) 3 else 4
                left[surround] = SURROUND_GAIN
                right[surround + 1] = SURROUND_GAIN
                if (channels == 8) {
                    left[6] = SURROUND_GAIN
                    right[7] = SURROUND_GAIN
                }
            }
            7 -> {
                left[2] = SURROUND_GAIN
                right[2] = SURROUND_GAIN
                left[4] = 0.5f
                right[4] = 0.5f
                left[5] = SURROUND_GAIN
                right[6] = SURROUND_GAIN
            }
        }
        // Leave headroom for correlated channels. The relative channel weights stay the same.
        val headroom = maxOf(1f, left.sum(), right.sum())
        for (channel in 0 until channels) {
            left[channel] /= headroom
            right[channel] /= headroom
        }
        matrix = Matrix(left, right)
        pendingFrame = ByteBuffer.allocate(inputAudioFormat.bytesPerFrame).order(ByteOrder.nativeOrder())
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val channels = matrix.left.size
        check(channels > 0)
        val floatingPoint = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val bytesPerSample = if (floatingPoint) 4 else 2
        val frameBytes = channels * bytesPerSample
        val available = inputBuffer.remaining() + pendingFrame.position()
        val output = replaceOutputBuffer(available / frameBytes * 2 * bytesPerSample)
        if (pendingFrame.position() > 0) {
            while (pendingFrame.hasRemaining() && inputBuffer.hasRemaining()) pendingFrame.put(inputBuffer.get())
            if (!pendingFrame.hasRemaining()) {
                pendingFrame.flip()
                mixFrame(pendingFrame, output, floatingPoint)
                pendingFrame.clear()
            }
        }
        while (inputBuffer.remaining() >= frameBytes) mixFrame(inputBuffer, output, floatingPoint)
        pendingFrame.put(inputBuffer)
        output.flip()
    }

    private fun mixFrame(input: ByteBuffer, output: ByteBuffer, floatingPoint: Boolean) {
        var left = 0f
        var right = 0f
        for (channel in matrix.left.indices) {
            val sample = if (floatingPoint) input.float else input.short.toFloat()
            left += sample * matrix.left[channel]
            right += sample * matrix.right[channel]
        }
        if (floatingPoint) {
            output.putFloat(left.coerceIn(-1f, 1f))
            output.putFloat(right.coerceIn(-1f, 1f))
        } else {
            output.putShort(left.coerceIn(-32768f, 32767f).roundToInt().toShort())
            output.putShort(right.coerceIn(-32768f, 32767f).roundToInt().toShort())
        }
    }

    private companion object {
        const val SURROUND_GAIN = 0.70710677f
    }
}
