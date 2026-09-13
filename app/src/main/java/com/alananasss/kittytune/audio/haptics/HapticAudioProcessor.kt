package com.alananasss.kittytune.audio.haptics

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * HapticAudioProcessor: Non-blocking pass-through AudioProcessor that extracts
 * real-time crossover band energies (sub-bass, bass, mid, high) from the PCM stream
 * and feeds them to PlayerHapticManager.
 */
@OptIn(UnstableApi::class)
class HapticAudioProcessor(context: Context) : BaseAudioProcessor() {

    private val hapticManager = PlayerHapticManager.getInstance(context)

    // Running 1st-order IIR DSP crossover filter states
    private var subBassFilterState = 0f
    private var bassFilterState = 0f
    private var midFilterState = 0f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (hapticManager.isHapticsEnabled) {
            analyzePcmForHaptics(inputBuffer)
        }

        val outBuffer = replaceOutputBuffer(remaining)
        outBuffer.put(inputBuffer)
        outBuffer.flip()
    }

    private fun analyzePcmForHaptics(buffer: ByteBuffer) {
        val isStereo = inputAudioFormat.channelCount == 2
        val is16Bit = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT
        val bytesPerSample = if (is16Bit) 2 else 4
        val bytesPerFrame = bytesPerSample * inputAudioFormat.channelCount
        if (bytesPerFrame == 0) return

        val frameCount = buffer.remaining() / bytesPerFrame
        if (frameCount == 0) return

        var sumSubBass = 0f
        var sumBass = 0f
        var sumMid = 0f
        var sumHigh = 0f
        var count = 0

        val step = 8 // Downsample to save CPU while keeping high temporal precision
        val startPos = buffer.position()
        val limit = buffer.limit()

        for (i in 0 until frameCount step step) {
            val bytePos = startPos + i * bytesPerFrame
            if (bytePos + bytesPerFrame > limit) break

            val sampleVal = if (is16Bit) {
                val left = buffer.getShort(bytePos).toFloat()
                val right = if (isStereo) buffer.getShort(bytePos + 2).toFloat() else left
                (left + right) / (2f * 32768f)
            } else {
                val left = buffer.getFloat(bytePos)
                val right = if (isStereo) buffer.getFloat(bytePos + 4) else left
                (left + right) / 2f
            }

            // Running IIR crossover filters
            subBassFilterState = 0.007f * sampleVal + 0.993f * subBassFilterState
            bassFilterState = 0.028f * sampleVal + 0.972f * bassFilterState
            midFilterState = 0.42f * sampleVal + 0.58f * midFilterState

            val subBassSample = subBassFilterState
            val bassSample = bassFilterState - subBassFilterState
            val midSample = midFilterState - bassFilterState
            val highSample = sampleVal - midFilterState

            sumSubBass += abs(subBassSample)
            sumBass += abs(bassSample)
            sumMid += abs(midSample)
            sumHigh += abs(highSample)
            count++
        }

        if (count > 0) {
            val subBassEnergy = ((sumSubBass / count) * 4f).coerceIn(0f, 1f)
            val bassEnergy = ((sumBass / count) * 4f).coerceIn(0f, 1f)
            val midEnergy = ((sumMid / count) * 4f).coerceIn(0f, 1f)
            val highEnergy = ((sumHigh / count) * 4f).coerceIn(0f, 1f)

            hapticManager.processPcmHaptics(subBassEnergy, bassEnergy, midEnergy, highEnergy)
        }
    }

    override fun onFlush() {
        subBassFilterState = 0f
        bassFilterState = 0f
        midFilterState = 0f
        hapticManager.stopAllHaptics()
    }

    override fun onReset() {
        subBassFilterState = 0f
        bassFilterState = 0f
        midFilterState = 0f
        hapticManager.stopAllHaptics()
    }
}
