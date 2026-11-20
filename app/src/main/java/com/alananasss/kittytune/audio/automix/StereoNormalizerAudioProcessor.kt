package com.alananasss.kittytune.audio.automix

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Ensures PCM audio is normalized to 2 channels (Stereo interleaved L/R):
 * - Stereo (2 ch): zero-overhead passthrough
 * - Mono (1 ch): duplicated to L and R
 * - Multichannel (3..8+ ch, e.g. 5.1 / 7.1 surround): ITU-R BS.775 downmix to Stereo L/R
 */
@UnstableApi
class StereoNormalizerAudioProcessor : BaseAudioProcessor() {

    companion object {
        private const val INV_SQRT2 = 0.70710677f
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        if (inputAudioFormat.channelCount <= 0) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        if (inputAudioFormat.channelCount == 2) {
            return inputAudioFormat
        }
        return AudioProcessor.AudioFormat(
            inputAudioFormat.sampleRate,
            2,
            inputAudioFormat.encoding
        )
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val channels = inputAudioFormat.channelCount
        if (channels == 2) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            val frameBytes = channels * 2
            val frames = remaining / frameBytes
            val buffer = replaceOutputBuffer(frames * 4)

            if (channels == 1) {
                while (inputBuffer.remaining() >= 2) {
                    val s = inputBuffer.short
                    buffer.putShort(s)
                    buffer.putShort(s)
                }
            } else {
                while (inputBuffer.remaining() >= frameBytes) {
                    val (l, r) = downmixFrame16(inputBuffer, channels)
                    buffer.putShort(l)
                    buffer.putShort(r)
                }
            }
            if (inputBuffer.hasRemaining()) {
                inputBuffer.position(inputBuffer.limit())
            }
            buffer.flip()
        } else if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
            val frameBytes = channels * 4
            val frames = remaining / frameBytes
            val buffer = replaceOutputBuffer(frames * 8)

            if (channels == 1) {
                while (inputBuffer.remaining() >= 4) {
                    val f = inputBuffer.float
                    buffer.putFloat(f)
                    buffer.putFloat(f)
                }
            } else {
                while (inputBuffer.remaining() >= frameBytes) {
                    val (l, r) = downmixFrameFloat(inputBuffer, channels)
                    buffer.putFloat(l)
                    buffer.putFloat(r)
                }
            }
            if (inputBuffer.hasRemaining()) {
                inputBuffer.position(inputBuffer.limit())
            }
            buffer.flip()
        } else {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
        }
    }

    private fun downmixFrame16(inputBuffer: ByteBuffer, channels: Int): Pair<Short, Short> {
        val fl = inputBuffer.short.toFloat()
        val fr = inputBuffer.short.toFloat()
        var left = fl
        var right = fr
        var norm = 1f

        when (channels) {
            3 -> {
                // 3.0: FL, FR, FC
                val fc = inputBuffer.short.toFloat() * INV_SQRT2
                left = fl + fc
                right = fr + fc
                norm = INV_SQRT2
            }
            4 -> {
                // Quad (4.0): FL, FR, BL, BR
                val bl = inputBuffer.short.toFloat() * INV_SQRT2
                val br = inputBuffer.short.toFloat() * INV_SQRT2
                left = fl + bl
                right = fr + br
                norm = INV_SQRT2
            }
            5 -> {
                // 5.0: FL, FR, FC, BL, BR
                val fc = inputBuffer.short.toFloat() * INV_SQRT2
                val bl = inputBuffer.short.toFloat() * INV_SQRT2
                val br = inputBuffer.short.toFloat() * INV_SQRT2
                left = fl + fc + bl
                right = fr + fc + br
                norm = INV_SQRT2
            }
            6 -> {
                // 5.1: FL, FR, FC, LFE, BL, BR
                val fc = inputBuffer.short.toFloat() * INV_SQRT2
                inputBuffer.short // skip LFE per ITU-R BS.775
                val bl = inputBuffer.short.toFloat() * INV_SQRT2
                val br = inputBuffer.short.toFloat() * INV_SQRT2
                left = fl + fc + bl
                right = fr + fc + br
                norm = INV_SQRT2
            }
            8 -> {
                // 7.1: FL, FR, FC, LFE, BL, BR, SL, SR
                val fc = inputBuffer.short.toFloat() * INV_SQRT2
                inputBuffer.short // skip LFE
                val bl = inputBuffer.short.toFloat() * INV_SQRT2
                val br = inputBuffer.short.toFloat() * INV_SQRT2
                val sl = inputBuffer.short.toFloat() * INV_SQRT2
                val sr = inputBuffer.short.toFloat() * INV_SQRT2
                left = fl + fc + bl + sl
                right = fr + fc + br + sr
                norm = 0.57735026f
            }
            else -> {
                for (ch in 2 until channels) {
                    inputBuffer.short
                }
            }
        }

        val outL = (left * norm).coerceIn(-32768f, 32767f).toInt().toShort()
        val outR = (right * norm).coerceIn(-32768f, 32767f).toInt().toShort()
        return outL to outR
    }

    private fun downmixFrameFloat(inputBuffer: ByteBuffer, channels: Int): Pair<Float, Float> {
        val fl = inputBuffer.float
        val fr = inputBuffer.float
        var left = fl
        var right = fr
        var norm = 1f

        when (channels) {
            3 -> {
                val fc = inputBuffer.float * INV_SQRT2
                left = fl + fc
                right = fr + fc
                norm = INV_SQRT2
            }
            4 -> {
                val bl = inputBuffer.float * INV_SQRT2
                val br = inputBuffer.float * INV_SQRT2
                left = fl + bl
                right = fr + br
                norm = INV_SQRT2
            }
            5 -> {
                val fc = inputBuffer.float * INV_SQRT2
                val bl = inputBuffer.float * INV_SQRT2
                val br = inputBuffer.float * INV_SQRT2
                left = fl + fc + bl
                right = fr + fc + br
                norm = INV_SQRT2
            }
            6 -> {
                val fc = inputBuffer.float * INV_SQRT2
                inputBuffer.float // skip LFE
                val bl = inputBuffer.float * INV_SQRT2
                val br = inputBuffer.float * INV_SQRT2
                left = fl + fc + bl
                right = fr + fc + br
                norm = INV_SQRT2
            }
            8 -> {
                val fc = inputBuffer.float * INV_SQRT2
                inputBuffer.float // skip LFE
                val bl = inputBuffer.float * INV_SQRT2
                val br = inputBuffer.float * INV_SQRT2
                val sl = inputBuffer.float * INV_SQRT2
                val sr = inputBuffer.float * INV_SQRT2
                left = fl + fc + bl + sl
                right = fr + fc + br + sr
                norm = 0.57735026f
            }
            else -> {
                for (ch in 2 until channels) {
                    inputBuffer.float
                }
            }
        }

        val outL = (left * norm).coerceIn(-1f, 1f)
        val outR = (right * norm).coerceIn(-1f, 1f)
        return outL to outR
    }
}
