package com.alananasss.kittytune.ui.player.audio

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

/**
 * Runs the format-preserving, synchronous player effects as one sink stage.
 *
 * Children stay configured even while disabled so a live toggle works without rebuilding the sink.
 * Disabled effects need neither a PCM copy nor an output buffer. The final copy is intentional:
 * output must not outlive codec-owned input or a child's reusable buffer during sink backpressure.
 * Effects used directly (for example by export) retain their original AudioProcessor behavior.
 */
class PlayerEffectsAudioProcessor(
    private val effects: Array<BypassableAudioProcessor>,
) : BaseAudioProcessor() {
    private val pendingActive = BooleanArray(effects.size)
    private val active = BooleanArray(effects.size)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        for (i in effects.indices) {
            val output = effects[i].configure(inputAudioFormat)
            // This composition is only valid for identity formats, not resamplers or buffered DSP.
            check(output == AudioProcessor.AudioFormat.NOT_SET || output == inputAudioFormat)
            pendingActive[i] = output != AudioProcessor.AudioFormat.NOT_SET
        }
        return if (pendingActive.any { it }) inputAudioFormat else AudioProcessor.AudioFormat.NOT_SET
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        var pcm = inputBuffer
        for (i in effects.indices) {
            val effect = effects[i]
            if (active[i] && effect.effectEnabled) {
                effect.queueInput(pcm)
                // All BypassableAudioProcessor effects consume one complete block and emit it
                // synchronously. Keep this invariant explicit if an effect changes in the future.
                check(!pcm.hasRemaining())
                pcm = effect.output
            }
        }
        val output = replaceOutputBuffer(pcm.remaining())
        output.put(pcm)
        output.flip()
    }

    override fun onQueueEndOfStream() {
        effects.forEach { it.queueEndOfStream() }
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        pendingActive.copyInto(active)
        effects.forEach { it.flush(streamMetadata) }
    }

    override fun onReset() {
        active.fill(false)
        pendingActive.fill(false)
        effects.forEach { it.reset() }
    }
}
