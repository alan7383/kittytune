package com.alananasss.kittytune.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.alananasss.kittytune.data.filter.AiAudioProbe
import java.nio.ByteBuffer

/**
 * Pass-through processor at the head of the chain, before any effect, that lets [AiAudioProbe] hear the
 * decoded audio of player [playerIndex] for the AI check.
 */
@OptIn(UnstableApi::class)
class AiFingerprintAudioProcessor(private val playerIndex: Int) : BaseAudioProcessor() {

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        try {
            AiAudioProbe.onPcm(playerIndex, inputBuffer, inputAudioFormat)
        } catch (_: Exception) {
            // The check must never cost the listener their audio.
        }
        val output = replaceOutputBuffer(remaining)
        output.put(inputBuffer)
        output.flip()
    }

    override fun onFlush() {
        AiAudioProbe.onFlush(playerIndex)
    }
}
