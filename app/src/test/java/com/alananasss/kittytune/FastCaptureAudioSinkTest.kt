package com.alananasss.kittytune

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import com.alananasss.kittytune.audio.automix.FastCaptureAudioSink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch

class FastCaptureAudioSinkTest {

    @Test
    fun testFastCaptureSinkReceivesPcm() {
        val latch = CountDownLatch(1)
        val sink = FastCaptureAudioSink(
            targetDurationUs = 1_000_000L, // 1 second
            onComplete = { latch.countDown() }
        )

        val format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_RAW)
            .setSampleRate(44100)
            .setChannelCount(2)
            .setPcmEncoding(C.ENCODING_PCM_16BIT)
            .build()

        sink.configure(format, 0, null)
        assertTrue(sink.supportsFormat(format))

        // Create 0.5s of 44.1kHz 16-bit stereo PCM (44100 * 2 channels * 2 bytes = 176400 bytes per sec)
        // 0.5s = 88200 bytes = 22050 frames
        val numFrames = 22050
        val buffer = ByteBuffer.allocateDirect(numFrames * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until numFrames) {
            val sampleVal = (Math.sin(2.0 * Math.PI * 440.0 * i / 44100.0) * 16384).toInt().toShort()
            buffer.putShort(sampleVal) // Left
            buffer.putShort(sampleVal) // Right
        }
        buffer.flip()

        // Send first chunk (0.5s)
        val consumed1 = sink.handleBuffer(buffer, 0L, 1)
        assertTrue(consumed1)
        assertEquals(1, latch.count) // Not yet target duration (1s)

        // Send second chunk (0.5s) -> reaches 1.0s target
        buffer.rewind()
        val consumed2 = sink.handleBuffer(buffer, 500_000L, 1)
        assertTrue(consumed2)
        assertEquals(0, latch.count) // Now complete!

        val pcm = sink.getPcm()
        assertTrue(pcm != null)
        assertEquals(44100, pcm!!.sampleRate)
        assertEquals(44100, pcm.samples.size) // 1 second of frames!
    }
}
