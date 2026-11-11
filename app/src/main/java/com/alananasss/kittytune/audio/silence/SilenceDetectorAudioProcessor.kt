package com.alananasss.kittytune.audio.silence

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

// Pass-through analyzer that watches the decoded PCM and fires once per run of "long"
// silence, for the mini-style player's "instantly skip silence" feature.
//
// The base skip-silence behaviour is Media3's own `player.skipSilenceEnabled`, which trims
// silence while keeping the clock honest. This processor adds the instant variant: when 2 s
// of near-silence has played, it reports [isCurrentlySilent], and the player layer hops the
// playhead forward in 15 s steps until real audio is found again.
//
// Ported from the mini-style player's silence detector.
class SilenceDetectorAudioProcessor(
    private val minSilenceDurationUs: Long = 2_000_000L,
    private val silenceThreshold: Int = 256,
    private val onLongSilence: () -> Unit
) : BaseAudioProcessor() {

    private var sampleRate = 0
    private var channelCount = 0

    @Volatile
    var instantModeEnabled: Boolean = false

    @Volatile
    private var consecutiveSilentFrames: Long = 0

    @Volatile
    private var inSilence: Boolean = false

    private var notifiedThisSilence = false

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // Format fields are captured BEFORE the Bit-Perfect bypass: after a hot-unplug
        // (DAC off mid-playback) detection runs again on the very next buffer, and
        // instantModeEnabled needs sampleRate/channelCount > 0 to engage at all.
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        // Bit-Perfect: identity configuration for ANY PCM encoding - never throw while
        // the DAC owns the output, so the chain format stays 1:1 and the queueInput
        // byte passthrough is bit-exact (container-upgrade streams configure cleanly).
        if (!com.alananasss.kittytune.audio.AudioRoutingMatrix.isRouted(com.alananasss.kittytune.audio.AudioStage.SILENCE_SKIP)) {
            return inputAudioFormat
        }
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            // NOT_SET = "inactive, skip me" - the rest of the chain keeps working. Throwing here
            // used to turn a float/hi-res input into a fatal sink ConfigurationException.
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) {
            return
        }
        // Bit-Perfect on a USB DAC: silence skipping is suspended - the track plays
        // exactly as authored (a skipped "silent" lead-in would shift every beat cue
        // and break bit-exactness of the stream).
        if (!com.alananasss.kittytune.audio.AudioRoutingMatrix.isRouted(com.alananasss.kittytune.audio.AudioStage.SILENCE_SKIP)) {
            clearSilenceState()
            val out = replaceOutputBuffer(inputBuffer.remaining())
            out.put(inputBuffer)
            out.flip()
            return
        }
        if (instantModeEnabled && sampleRate > 0 && channelCount > 0) {
            detectSilence(inputBuffer)
        } else {
            clearSilenceState()
        }
        val out = replaceOutputBuffer(inputBuffer.remaining())
        out.put(inputBuffer)
        out.flip()
    }

    private fun detectSilence(inputBuffer: ByteBuffer) {
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val frameCount = inputBuffer.remaining() / 2 / channelCount
        val basePosition = inputBuffer.position()
        repeat(frameCount) { frameIndex ->
            var framePeak = 0
            repeat(channelCount) { channelIndex ->
                val sampleIndex = basePosition + (frameIndex * channelCount + channelIndex) * 2
                val sampleValue = abs(inputBuffer.getShort(sampleIndex).toInt())
                if (sampleValue > framePeak) {
                    framePeak = sampleValue
                }
            }
            if (framePeak < silenceThreshold) {
                consecutiveSilentFrames++
                val silentDurationUs = (consecutiveSilentFrames * 1_000_000L) / sampleRate
                if (silentDurationUs >= minSilenceDurationUs) {
                    inSilence = true
                    if (!notifiedThisSilence) {
                        notifiedThisSilence = true
                        onLongSilence()
                    }
                }
            } else {
                clearSilenceState()
            }
        }
    }

    private fun clearSilenceState() {
        consecutiveSilentFrames = 0
        inSilence = false
        notifiedThisSilence = false
    }

    fun resetTracking() {
        clearSilenceState()
    }

    fun isCurrentlySilent(): Boolean = inSilence
}
