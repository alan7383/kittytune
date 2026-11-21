package com.alananasss.kittytune.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alananasss.kittytune.audio.automix.StereoNormalizerAudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class StereoPreviewDeviceTest {
    @Test
    fun surroundPreviewPlaysThroughTheStereoAudioTrack() {
        val rate = 48_000
        val frameCount = rate / 4
        val preview = StereoNormalizerAudioProcessor()
        val outputFormat = preview.configure(AudioProcessor.AudioFormat(rate, 6, C.ENCODING_PCM_16BIT))
        assertEquals(2, outputFormat.channelCount)
        preview.flush()

        val pcm = ByteBuffer.allocateDirect(frameCount * 12).order(ByteOrder.nativeOrder())
        repeat(frameCount) { frame ->
            val tone = (2_000 * sin(2.0 * PI * 440.0 * frame / rate)).toInt().toShort()
            repeat(6) { pcm.putShort(tone) }
        }
        pcm.flip()
        preview.queueInput(pcm)
        val stereo = ShortArray(frameCount * 2)
        preview.output.asShortBuffer().get(stereo)

        val playback = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(stereo.size * 2)
            .build()
        try {
            assertEquals(stereo.size, playback.write(stereo, 0, stereo.size, AudioTrack.WRITE_BLOCKING))
            playback.play()
            val deadline = SystemClock.elapsedRealtime() + 2_000L
            while (playback.playbackHeadPosition == 0 && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(10)
            }
            assertTrue("Android must consume the preview PCM", playback.playbackHeadPosition > 0)
        } finally {
            playback.release()
        }
    }
}
