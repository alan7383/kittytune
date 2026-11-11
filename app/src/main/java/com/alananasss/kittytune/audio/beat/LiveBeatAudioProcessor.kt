package com.alananasss.kittytune.audio.beat

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Pass-through processor feeding [LiveBeatTracker] (one per player slot). It reads the PCM without
 * touching it, and anchors the tracker's media time on the position Media3 gives every flush.
 */
@OptIn(UnstableApi::class)
class LiveBeatAudioProcessor(val tracker: LiveBeatTracker) : BaseAudioProcessor() {

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // The tracker's sample rate is set BEFORE the Bit-Perfect bypass: after a
        // hot-unplug (DAC off mid-playback) analysis resumes on the next buffer, and
        // the beat grid must already be anchored to the right rate.
        tracker.configure(inputAudioFormat.sampleRate)
        // Bit-Perfect: identity configuration for ANY PCM encoding - the chain format
        // must stay 1:1 so the queueInput byte passthrough is bit-exact (never NOT_SET
        // and never a negotiated conversion while the DAC owns the output).
        if (!com.alananasss.kittytune.audio.AudioRoutingMatrix.isRouted(com.alananasss.kittytune.audio.AudioStage.LIVE_BEAT)) {
            return inputAudioFormat
        }
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        // Bit-Perfect on a USB DAC: live beat tracking is suspended along with the
        // other analyzers. This processor is watch-only (never alters samples), but
        // its CPU cost belongs to the suspended effect set.
        if (com.alananasss.kittytune.audio.AudioRoutingMatrix.isRouted(com.alananasss.kittytune.audio.AudioStage.LIVE_BEAT)) {
            analyze(inputBuffer)
        }
        val out = replaceOutputBuffer(remaining)
        out.put(inputBuffer)
        out.flip()
    }

    private fun analyze(buffer: ByteBuffer) {
        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        val is16 = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT
        val bytesPerSample = if (is16) 2 else 4
        val frameBytes = bytesPerSample * channels
        val start = buffer.position()
        val frames = buffer.remaining() / frameBytes
        for (i in 0 until frames) {
            val p = start + i * frameBytes
            var sum = 0f
            for (ch in 0 until minOf(channels, 2)) {
                sum += if (is16) buffer.getShort(p + ch * 2) / 32768f else buffer.getFloat(p + ch * 4)
            }
            tracker.push(sum / minOf(channels, 2))
        }
        tracker.onBufferDone()
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        // the media position of the next sample: the anchor that keeps beats in the player's timeline
        tracker.onFlush(streamMetadata.positionOffsetUs / 1000.0)
    }

    override fun onReset() {
        tracker.onFlush(0.0)
    }
}
