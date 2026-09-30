package com.alananasss.kittytune.audio.ai

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import com.alananasss.kittytune.data.local.AiDetectionWindow
import com.alananasss.kittytune.data.local.PlayerPreferences
import java.nio.ByteBuffer

/**
 * A lightweight pass-through AudioProcessor that taps into the PCM stream
 * and feeds it to [AiDetectionManager] for ArtifactNet AI music detection.
 *
 * Completely transparent — never modifies audio data.
 * Zero-allocation during playback via pre-allocated PCM buffer.
 * Configurable analysis window:
 * - 4 seconds (Accurate, Recommended): Full native 4.0s ArtifactNet segment without tiling
 * - 1 second (Ultra-fast): ~1.0s segment tiled with loop crossfading for immediate skips
 */
class AiDetectionAudioProcessor(
    private var preferences: PlayerPreferences? = null
) : BaseAudioProcessor() {

    companion object {
        private const val TAG = "AiDetectionAudioProc"
        // Maximum collection buffer: 4.0 seconds @ 48 kHz stereo 16-bit = 768,000 bytes
        private const val MAX_BUFFER_BYTES = 800_000
    }

    private val preallocatedBuffer = ByteArray(MAX_BUFFER_BYTES)
    private var totalCollected = 0
    @Volatile private var analysisTriggered = false

    fun setPreferences(prefs: PlayerPreferences) {
        this.preferences = prefs
    }

    /** Called by [com.alananasss.kittytune.data.MusicManager] when a new track starts. */
    fun resetForNewTrack() {
        synchronized(this) {
            totalCollected = 0
            analysisTriggered = false
        }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    @Deprecated("Deprecated in supertype but required override")
    override fun onFlush() {
        // Reset collection on seek/flush so pre-seek and post-seek audio are not concatenated.
        // If analysis has already completed for this track, remain in fast pass-through mode.
        val isDone = AiDetectionManager.result.value?.status == AiDetectionManager.Status.DONE
        synchronized(this) {
            if (!isDone) {
                totalCollected = 0
                analysisTriggered = false
            }
        }
    }

    override fun onReset() {
        super.onReset()
        resetForNewTrack()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        // Always pass audio to output buffer
        val output = replaceOutputBuffer(remaining)

        // Fast path: if analysis already finished or model is not loaded,
        // direct native buffer transfer with zero heap allocation!
        if (analysisTriggered || !AiDetectionManager.isModelLoaded()) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        val window = preferences?.aiDetectionWindow ?: AiDetectionWindow.ACCURATE
        val targetSeconds = window.seconds // 1 or 4
        val fmt = inputAudioFormat
        val bytesPerSec = fmt.sampleRate * fmt.channelCount * 2 // 16-bit PCM = 2 bytes per sample
        val targetBytes = (targetSeconds * bytesPerSec).coerceIn(176_400, preallocatedBuffer.size)

        var snapshotToAnalyze: ByteArray? = null

        synchronized(this) {
            if (analysisTriggered) {
                output.put(inputBuffer)
                output.flip()
                return
            }

            val toCollect = minOf(remaining, targetBytes - totalCollected)
            if (toCollect > 0) {
                inputBuffer.get(preallocatedBuffer, totalCollected, toCollect)
                totalCollected += toCollect
                output.put(preallocatedBuffer, totalCollected - toCollect, toCollect)
            }
            if (inputBuffer.hasRemaining()) {
                output.put(inputBuffer)
            }
            output.flip()

            if (totalCollected >= targetBytes) {
                analysisTriggered = true
                snapshotToAnalyze = preallocatedBuffer.copyOf(totalCollected)
            }
        }

        snapshotToAnalyze?.let { snapshot ->
            AiDetectionManager.analyzeAsync(
                pcmBytes = snapshot,
                sampleRate = fmt.sampleRate,
                channelCount = fmt.channelCount
            )
        }
    }
}
