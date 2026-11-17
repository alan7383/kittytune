package com.alananasss.kittytune.audio.usb

import android.media.AudioFormat
import java.nio.ByteBuffer

// Lossless PCM container converters for Bit-Perfect.
//
// On Android 14, USB DACs expose their bit-perfect mixers, as a rule, only in the
// 32-bit (S32_LE) or 24-bit packed (S24_3LE) container, even at 44.1/48 kHz.
// To keep a 16-bit track bit-exact inside a 32-bit container, samples are
// shifted left with the least-significant bits zero-filled (sample << 16) - a
// mathematically reversible operation (lossless container upgrade).
//
// All methods operate in little-endian (Android's native PCM byte order)
// and on whole frames only (channelCount * bytesPerSample).
object PcmContainerConverter {

    fun bytesPerSample(encoding: Int): Int = when (encoding) {
        AudioFormat.ENCODING_PCM_16BIT -> 2
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
        AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 4
        else -> 2
    }

    fun bitDepth(encoding: Int): Int = when (encoding) {
        AudioFormat.ENCODING_PCM_16BIT -> 16
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
        AudioFormat.ENCODING_PCM_32BIT -> 32
        AudioFormat.ENCODING_PCM_FLOAT -> 32
        else -> 0
    }

    fun isIntegerEncoding(encoding: Int): Boolean = when (encoding) {
        AudioFormat.ENCODING_PCM_16BIT,
        AudioFormat.ENCODING_PCM_24BIT_PACKED,
        AudioFormat.ENCODING_PCM_32BIT -> true
        else -> false
    }

    // Whether srcEncoding -> dstEncoding is a lossless container upgrade.
    fun isLosslessUpgrade(srcEncoding: Int, dstEncoding: Int): Boolean {
        if (!isIntegerEncoding(srcEncoding) || !isIntegerEncoding(dstEncoding)) return false
        if (srcEncoding == dstEncoding) return false
        return bitDepth(dstEncoding) > bitDepth(srcEncoding)
    }

    // Truncates the data size down to a whole number of frames.
    // MediaCodec audio decoders always emit whole frames, but the extra guard
    // against channel phase misalignment (crackling) costs nothing.
    fun alignToFrame(sizeBytes: Int, channelCount: Int, encoding: Int): Int {
        val frameBytes = bytesPerSample(encoding) * channelCount.coerceAtLeast(1)
        if (frameBytes <= 0) return sizeBytes
        return sizeBytes - (sizeBytes % frameBytes)
    }

    // Expands an integer PCM container losslessly:
    // 16 -> 32 (<< 16), 16 -> 24 (<< 8), 24 -> 32 (<< 8).
    //
    // Buffers must be LITTLE_ENDIAN (the order is set internally).
    // dst must have enough capacity: at least aligned * dstBps / srcBps bytes.
    //
    // @return the number of bytes written into dst.
    fun expandLossless(src: ByteBuffer, dst: ByteBuffer, srcEncoding: Int, dstEncoding: Int, channelCount: Int): Int {
        if (!isLosslessUpgrade(srcEncoding, dstEncoding)) return 0
        src.order(java.nio.ByteOrder.LITTLE_ENDIAN)
        dst.order(java.nio.ByteOrder.LITTLE_ENDIAN)

        val srcBps = bytesPerSample(srcEncoding)
        val dstBps = bytesPerSample(dstEncoding)
        val frameBytes = srcBps * channelCount.coerceAtLeast(1)
        val srcRemaining = src.remaining()
        val wholeBytes = srcRemaining - (srcRemaining % frameBytes)
        if (wholeBytes <= 0) return 0

        val frames = wholeBytes / frameBytes
        var written = 0

        when (srcEncoding) {
            AudioFormat.ENCODING_PCM_16BIT -> when (dstEncoding) {
                AudioFormat.ENCODING_PCM_32BIT -> {
                    repeat(frames * channelCount) {
                        val s = src.short.toInt()
                        dst.putInt(s shl 16)
                        written += 4
                    }
                }
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                    repeat(frames * channelCount) {
                        val v = src.short.toInt() shl 8
                        dst.put((v and 0xFF).toByte())
                        dst.put(((v shr 8) and 0xFF).toByte())
                        dst.put(((v shr 16) and 0xFF).toByte())
                        written += 3
                    }
                }
            }
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> if (dstEncoding == AudioFormat.ENCODING_PCM_32BIT) {
                repeat(frames * channelCount) {
                    val b0 = src.get().toInt() and 0xFF
                    val b1 = src.get().toInt() and 0xFF
                    val b2 = src.get().toInt() // signed most-significant byte - gives sign extension
                    val v = b0 or (b1 shl 8) or (b2 shl 16)
                    dst.putInt(v shl 8)
                    written += 4
                }
            }
        }
        return written
    }

    // Fallback Float -> 32-bit int conversion (NOT bit-exact!).
    // Used when the decoder can only output float while the DAC requires an
    // int container: the audio still plays, but the engine must flag bit-perfect
    // as invalid.
    fun expandFloatTo32(src: ByteBuffer, dst: ByteBuffer, channelCount: Int): Int {
        src.order(java.nio.ByteOrder.LITTLE_ENDIAN)
        dst.order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val frameBytes = 4 * channelCount.coerceAtLeast(1)
        val srcRemaining = src.remaining()
        val wholeBytes = srcRemaining - (srcRemaining % frameBytes)
        if (wholeBytes <= 0) return 0

        val samples = wholeBytes / 4
        var written = 0
        repeat(samples) {
            val f = src.float
            // [-1.0 .. 1.0) -> int32, guarded against overflow
            val v = if (f <= -1.0f) Int.MIN_VALUE
            else if (f >= 1.0f) Int.MAX_VALUE
            else (f * 2147483648.0f).toInt()
            dst.putInt(v)
            written += 4
        }
        return written
    }
}
