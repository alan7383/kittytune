package com.alananasss.kittytune.audio.automix

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

 // Ensures PCM audio is normalized to 2 channels (Stereo interleaved L/R).
 // If input is Mono (1 channel), each sample is duplicated to both L and R channels
 
@UnstableApi
class StereoNormalizerAudioProcessor : BaseAudioProcessor() {

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
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

        if (inputAudioFormat.channelCount == 2) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        // Mono -> Stereo
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            val frames = remaining / 2
            val buffer = replaceOutputBuffer(frames * 4)
            while (inputBuffer.remaining() >= 2) {
                val s = inputBuffer.getShort()
                buffer.putShort(s)
                buffer.putShort(s)
            }
            buffer.flip()
        } else if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) {
            val frames = remaining / 4
            val buffer = replaceOutputBuffer(frames * 8)
            while (inputBuffer.remaining() >= 4) {
                val f = inputBuffer.getFloat()
                buffer.putFloat(f)
                buffer.putFloat(f)
            }
            buffer.flip()
        } else {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
        }
    }
}
