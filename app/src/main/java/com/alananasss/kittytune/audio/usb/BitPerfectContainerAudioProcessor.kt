package com.alananasss.kittytune.audio.usb

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

// Processor on the LIVE playback path (ExoPlayer -> DefaultAudioSink).
//
// Problem: Android 14 requires the AudioTrack format to strictly match the
// format of the applied AudioMixerAttributes. If the DAC offers bit-perfect
// only in a 32-bit container while the track is 16-bit, UsbAudioManager applies
// the 32-bit mixer (lossless container upgrade) - but DefaultAudioSink would
// create a 16-bit AudioTrack, the formats would not match, and the system
// would silently fall back to the ordinary resampling mixer.
//
// Solution: this processor reads UsbAudioManager.containerUpgradeTarget and
// converts the PCM into the target container BEFORE the sink (16 -> 32: shift
// << 16, 16 -> 24: << 8, 24 -> 32: << 8 - bit-exact, least-significant bits zero).
//
// When bit-perfect is inactive or no container upgrade is needed - plain
// passthrough without touching a single sample.
//
// IMPORTANT: it is placed at the end of the processor chain (after all DSP),
// because DSP effects already run in bypass mode while bit-perfect is active.
class BitPerfectContainerAudioProcessor : BaseAudioProcessor() {

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val target = UsbAudioManager.containerUpgradeTarget
        if (UsbAudioManager.shouldBypassPcmEffects && target != 0) {
            val targetMedia3 = UsbAudioManager.mapAudioFormatToMedia3(target)
            val upgrade = when (inputAudioFormat.encoding) {
                C.ENCODING_PCM_16BIT ->
                    targetMedia3 == C.ENCODING_PCM_24BIT || targetMedia3 == C.ENCODING_PCM_32BIT
                C.ENCODING_PCM_24BIT -> targetMedia3 == C.ENCODING_PCM_32BIT
                else -> false
            }
            if (upgrade) {
                return AudioProcessor.AudioFormat(
                    inputAudioFormat.sampleRate,
                    inputAudioFormat.channelCount,
                    targetMedia3
                )
            }
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val inEnc = inputAudioFormat.encoding
        val outEnc = outputAudioFormat.encoding

        // No conversion - byte-for-byte passthrough
        if (inEnc == outEnc) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        val srcBps = bytesPerSampleMedia3(inEnc)
        val dstBps = bytesPerSampleMedia3(outEnc)
        val frameBytes = srcBps * channels
        val aligned = remaining - (remaining % frameBytes)
        if (aligned <= 0) {
            // Partial frame: consume it BEFORE returning. An untouched inputBuffer handed
            // back to Media3 sends the AudioTrack thread into while(inputBuffer.hasRemaining())
            // forever - the sink must never return with input still pending.
            inputBuffer.position(inputBuffer.limit())
            return
        }

        val samples = aligned / srcBps
        val out = replaceOutputBuffer(samples * dstBps)
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        out.order(ByteOrder.nativeOrder())

        var i = 0
        while (i < samples) {
            when {
                inEnc == C.ENCODING_PCM_16BIT && outEnc == C.ENCODING_PCM_32BIT -> {
                    val s = inputBuffer.short.toInt()
                    out.putInt(s shl 16)
                }
                inEnc == C.ENCODING_PCM_16BIT && outEnc == C.ENCODING_PCM_24BIT -> {
                    val v = inputBuffer.short.toInt() shl 8
                    out.put((v and 0xFF).toByte())
                    out.put(((v shr 8) and 0xFF).toByte())
                    out.put(((v shr 16) and 0xFF).toByte())
                }
                inEnc == C.ENCODING_PCM_24BIT && outEnc == C.ENCODING_PCM_32BIT -> {
                    val b0 = inputBuffer.get().toInt() and 0xFF
                    val b1 = inputBuffer.get().toInt() and 0xFF
                    val b2 = inputBuffer.get().toInt() // signed most-significant byte
                    val v = b0 or (b1 shl 8) or (b2 shl 16)
                    out.putInt(v shl 8)
                }
                else -> {
                    // Unreachable: onConfigure only declares the three pairs above. Never emit
                    // bytes that do not match the declared output format (that would be noise)
                    // and never throw on the audio thread: consume the input, output nothing.
                    inputBuffer.position(inputBuffer.limit())
                    out.clear()
                    out.flip()
                    return
                }
            }
            i++
        }

        // Same contract as above: nothing may stay pending in inputBuffer when this
        // method returns, otherwise Media3 loops on hasRemaining() indefinitely.
        if (inputBuffer.hasRemaining()) {
            inputBuffer.position(inputBuffer.limit())
        }
        out.flip()
    }

    private fun bytesPerSampleMedia3(encoding: Int): Int = when (encoding) {
        C.ENCODING_PCM_16BIT -> 2
        C.ENCODING_PCM_24BIT -> 3
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
        else -> 2
    }
}
