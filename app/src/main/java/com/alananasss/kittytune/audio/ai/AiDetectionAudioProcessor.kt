package com.alananasss.kittytune.audio.ai

import android.util.Log
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer

/**
 * A lightweight pass-through AudioProcessor that taps into the PCM stream
 * and feeds it to [AiDetectionManager] for ArtifactNet AI music detection.
 *
 * Completely transparent — never modifies audio data.
 * Multi-stage progressive analysis:
 * - Stage 1 (~1.0s @ 176k bytes): Ultra-fast skip for obvious AI tracks (tiled inference)
 * - Stage 2 (~2.5s @ 441k bytes): Fast check if intro had lower energy
 * - Stage 3 (~4.0s @ 705k bytes): Full authentic chunk without tiling
 * - Stage 4 (~12s @ 2.1M bytes): Multi-chunk median for non-AI tracks, then releases buffer
 */
class AiDetectionAudioProcessor : BaseAudioProcessor() {

    companion object {
        private const val TAG = "AiDetectionAudioProc"
        // Stage 1: Ultra-fast early detection at ~1.0 s stereo @ 44.1 kHz (176,400 bytes)
        private const val FAST_ANALYSIS_BYTES = 176_400
        // Stage 2: Second check at ~2.5 s (441,000 bytes) if intro started slowly
        private const val MEDIUM_ANALYSIS_BYTES = 441_000
        // Stage 3: Full 4.0-second chunk (705,600 bytes)
        private const val FULL_CHUNK_BYTES = 705_600
        // Stage 4: Multi-chunk median cap at ~12 s (2,116,800 bytes)
        private const val MAX_COLLECT_BYTES = 2_116_800
    }

    private val pcmBuffer = ArrayList<ByteArray>(64)
    private var totalCollected = 0
    private var fastTriggered = false
    private var mediumTriggered = false
    private var fullChunkTriggered = false
    @Volatile private var fullAnalysisTriggered = false

    /** Called by [MusicManager] when a new track starts. */
    fun resetForNewTrack() {
        synchronized(this) {
            pcmBuffer.clear()
            pcmBuffer.trimToSize()
            totalCollected = 0
            fastTriggered = false
            mediumTriggered = false
            fullChunkTriggered = false
            fullAnalysisTriggered = false
        }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return inputAudioFormat
    }

    @Deprecated("Deprecated in supertype but required override")
    override fun onFlush() {
        // Don't clear on seek/flush — we want continuous collection
    }

    override fun onReset() {
        super.onReset()
        resetForNewTrack()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        // Pass through unmodified
        val output = replaceOutputBuffer(remaining)

        // Fast path: if analysis already finished or model is not loaded,
        // do a direct native buffer transfer without ANY heap allocation!
        if (fullAnalysisTriggered || !AiDetectionManager.isModelLoaded()) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        // Pass through and copy for analysis
        val data = ByteArray(remaining)
        inputBuffer.get(data)
        output.put(data)
        output.flip()

        var snapshotToAnalyze: ByteArray? = null
        val fmt = inputAudioFormat

        synchronized(this) {
            if (fullAnalysisTriggered) return

            val toAdd = minOf(data.size, MAX_COLLECT_BYTES - totalCollected)
            if (toAdd > 0) {
                pcmBuffer.add(data.copyOf(toAdd))
                totalCollected += toAdd
            }

            if (!fastTriggered && totalCollected >= FAST_ANALYSIS_BYTES) {
                fastTriggered = true
                snapshotToAnalyze = buildSnapshotLocked()
            } else if (!mediumTriggered && totalCollected >= MEDIUM_ANALYSIS_BYTES) {
                mediumTriggered = true
                snapshotToAnalyze = buildSnapshotLocked()
            } else if (!fullChunkTriggered && totalCollected >= FULL_CHUNK_BYTES) {
                fullChunkTriggered = true
                snapshotToAnalyze = buildSnapshotLocked()
            } else if (!fullAnalysisTriggered && totalCollected >= MAX_COLLECT_BYTES) {
                fullAnalysisTriggered = true
                snapshotToAnalyze = buildSnapshotLocked()
                pcmBuffer.clear()
                pcmBuffer.trimToSize()
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

    private fun buildSnapshotLocked(): ByteArray? {
        return try {
            val result = ByteArray(totalCollected)
            var offset = 0
            for (chunk in pcmBuffer) {
                val len = minOf(chunk.size, result.size - offset)
                if (len <= 0) break
                chunk.copyInto(result, offset, 0, len)
                offset += len
            }
            result
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "OOM while allocating snapshot ($totalCollected bytes)", e)
            pcmBuffer.clear()
            pcmBuffer.trimToSize()
            fullAnalysisTriggered = true
            null
        }
    }
}
