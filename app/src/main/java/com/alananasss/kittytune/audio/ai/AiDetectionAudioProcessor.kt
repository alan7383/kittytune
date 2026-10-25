package com.alananasss.kittytune.audio.ai

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import com.alananasss.kittytune.data.local.PlayerPreferences
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A lightweight pass-through AudioProcessor that taps into the PCM stream
 * and feeds it to [AiDetectionManager] for ArtifactNet AI music detection.
 *
 * Completely transparent — never modifies audio data.
 * Zero-allocation during playback via pre-allocated PCM buffer.
 * Progressive sampling checkpoints:
 * - 0s, 4s, 8s (early transition coverage)
 * - 20s, 40s, 60s (spaced late-onset monitoring)
 */
class AiDetectionAudioProcessor(
    private var preferences: PlayerPreferences? = null
) : BaseAudioProcessor() {

    companion object {
        private const val TAG = "AiDetectionAudioProc"
        // Maximum collection buffer: 4.0 seconds @ 48 kHz stereo 16-bit = 768,000 bytes
        private const val MAX_BUFFER_BYTES = 800_000
        // Maximum mono float collection buffer: 4.0 seconds @ 48 kHz mono = 192,000 floats
        private const val MAX_MONO_FLOATS = 200_000

        val DEFAULT_CHECKPOINTS_4S = doubleArrayOf(0.0, 4.0, 8.0, 20.0, 40.0, 60.0)
        val DEFAULT_CHECKPOINTS_1S = doubleArrayOf(0.0, 1.0, 2.0, 6.0, 12.0, 20.0)
        const val MAX_CHECKPOINTS = 6
    }

    private val preallocatedBuffer = ByteArray(MAX_BUFFER_BYTES)
    private val preallocatedFloatMono = FloatArray(MAX_MONO_FLOATS)
    private var totalCollected = 0
    private var totalFloatsCollected = 0
    private var currentPositionSec = 0.0
    private var checkpointCount = 0
    private var nextCheckpointStartSec = 0.0
    @Volatile private var isAiConfirmed = false
    @Volatile private var allCheckpointsDone = false
    @Volatile private var cachedTargetSeconds = 4

    fun setPreferences(prefs: PlayerPreferences) {
        this.preferences = prefs
        this.cachedTargetSeconds = prefs.aiDetectionWindow.seconds
    }

    /** Called by [com.alananasss.kittytune.data.MusicManager] when a new track starts. */
    fun resetForNewTrack() {
        synchronized(this) {
            totalCollected = 0
            totalFloatsCollected = 0
            currentPositionSec = 0.0
            checkpointCount = 0
            nextCheckpointStartSec = 0.0
            isAiConfirmed = false
            allCheckpointsDone = false
            cachedTargetSeconds = preferences?.aiDetectionWindow?.seconds
                ?: AiDetectionManager.getEffectiveWindowSeconds()
        }
    }

    /** Called on seek to adjust playback position and schedule an immediate sample at the new position. */
    fun notifySeek(seekMs: Long) {
        synchronized(this) {
            totalCollected = 0
            totalFloatsCollected = 0
            currentPositionSec = seekMs.coerceAtLeast(0) / 1000.0
            if (!isAiConfirmed && !AiDetectionManager.isAiConfirmed() && !AiDetectionManager.isVerdictFinal() && !AiDetectionManager.isAnalyzingUpfront()) {
                allCheckpointsDone = false
                checkpointCount = 0
                nextCheckpointStartSec = currentPositionSec
                AiDetectionManager.notifySeek(currentPositionSec)
            }
        }
    }

    internal fun getCurrentPositionSec(): Double = synchronized(this) { currentPositionSec }
    internal fun isAllCheckpointsDone(): Boolean = synchronized(this) { allCheckpointsDone }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    @Deprecated("Deprecated in supertype but required override")
    override fun onFlush() {
        val isFinal = AiDetectionManager.isVerdictFinal()
        val isAi = isAiConfirmed || AiDetectionManager.isAiConfirmed() || AiDetectionManager.result.value?.isAi == true
        synchronized(this) {
            totalCollected = 0
            totalFloatsCollected = 0
            if (isAi) {
                isAiConfirmed = true
            }
            if (isFinal) {
                allCheckpointsDone = true
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

        val output = replaceOutputBuffer(remaining)

        // Fast path: if all checkpoints completed, final verdict reached, or upfront multi-point analysis is running
        if (allCheckpointsDone || AiDetectionManager.isVerdictFinal() || isAiConfirmed || AiDetectionManager.isAiConfirmed() || AiDetectionManager.isAnalyzingUpfront()) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        val targetSeconds = cachedTargetSeconds
        val fmt = inputAudioFormat
        val isFloat = fmt.encoding == C.ENCODING_PCM_FLOAT
        val channels = fmt.channelCount.coerceAtLeast(1)
        val inputBytesPerSample = if (isFloat) 4 else 2
        val pcm16BytesPerSec = fmt.sampleRate * channels * 2
        val inputBytesPerSec = fmt.sampleRate * channels * inputBytesPerSample
        if (pcm16BytesPerSec <= 0 || inputBytesPerSec <= 0) {
            output.put(inputBuffer)
            output.flip()
            return
        }
        val targetBytes = (targetSeconds * pcm16BytesPerSec).coerceIn(176_400, preallocatedBuffer.size)
        val targetMonoFloats = (targetSeconds * fmt.sampleRate).coerceIn(44_100, preallocatedFloatMono.size)
        val chunkSec = remaining.toDouble() / inputBytesPerSec

        if (!AiDetectionManager.isModelLoaded()) {
            AiDetectionManager.ensureLoaded()
            synchronized(this) {
                currentPositionSec += chunkSec
            }
            output.put(inputBuffer)
            output.flip()
            return
        }

        var snapshotToAnalyze: ByteArray? = null
        var floatSnapshotToAnalyze: FloatArray? = null

        synchronized(this) {
            if (allCheckpointsDone || AiDetectionManager.isVerdictFinal() || isAiConfirmed || AiDetectionManager.isAiConfirmed()) {
                allCheckpointsDone = true
                output.put(inputBuffer)
                output.flip()
                return
            }
            if (AiDetectionManager.isAnalyzingUpfront()) {
                currentPositionSec += chunkSec
                output.put(inputBuffer)
                output.flip()
                return
            }

            // Waiting for next checkpoint window
            if (currentPositionSec < nextCheckpointStartSec && totalCollected == 0 && totalFloatsCollected == 0) {
                currentPositionSec += chunkSec
                output.put(inputBuffer)
                output.flip()
                return
            }

            if (isFloat) {
                val srcDup = inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                output.put(inputBuffer)
                output.flip()

                val availFrames = remaining / (4 * channels)
                val neededFrames = targetMonoFloats - totalFloatsCollected
                val toCollectFrames = minOf(availFrames, neededFrames)
                val invCh = 1f / channels
                for (f in 0 until toCollectFrames) {
                    var sum = 0f
                    for (c in 0 until channels) {
                        sum += srcDup.float
                    }
                    preallocatedFloatMono[totalFloatsCollected++] = sum * invCh
                }

                currentPositionSec += chunkSec

                if (totalFloatsCollected >= targetMonoFloats) {
                    floatSnapshotToAnalyze = preallocatedFloatMono.copyOf(totalFloatsCollected)
                    totalFloatsCollected = 0
                    advanceCheckpointSchedule(targetSeconds)
                }
            } else {
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

                currentPositionSec += chunkSec

                if (totalCollected >= targetBytes) {
                    snapshotToAnalyze = preallocatedBuffer.copyOf(totalCollected)
                    totalCollected = 0
                    advanceCheckpointSchedule(targetSeconds)
                }
            }
        }

        floatSnapshotToAnalyze?.let { monoFloats ->
            AiDetectionManager.analyzeFloatAsync(
                monoSamples = monoFloats,
                sampleRate = fmt.sampleRate
            )
        }

        snapshotToAnalyze?.let { snapshot ->
            AiDetectionManager.analyzeAsync(
                pcmBytes = snapshot,
                sampleRate = fmt.sampleRate,
                channelCount = fmt.channelCount
            )
        }
    }

    private fun advanceCheckpointSchedule(targetSeconds: Int) {
        checkpointCount++
        if (checkpointCount >= MAX_CHECKPOINTS || isAiConfirmed || AiDetectionManager.isAiConfirmed()) {
            allCheckpointsDone = true
        } else {
            val schedule = if (targetSeconds == 1) DEFAULT_CHECKPOINTS_1S else DEFAULT_CHECKPOINTS_4S
            val scheduledSec = schedule.getOrNull(checkpointCount)
            if (scheduledSec != null) {
                nextCheckpointStartSec = maxOf(scheduledSec, currentPositionSec)
            } else {
                allCheckpointsDone = true
            }
        }
    }
}
