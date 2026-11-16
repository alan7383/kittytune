package com.alananasss.kittytune.data

import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alananasss.kittytune.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class PlaybackAudioFocusDeviceTest {
    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun playsOnAndroidAndOwnsMediaFocusDuringProjection() {
        val context = instrumentation.targetContext
        val tone = File(context.cacheDir, "audio-focus-test.wav")
        writeTone(tone)
        try {
            instrumentation.runOnMainSync {
                MusicManager.init(context)
                context.startService(Intent(context, PlaybackService::class.java))
                MusicManager.player.apply {
                    setMediaItem(
                        MediaItem.Builder()
                            .setUri(tone.toURI().toString())
                            .setMediaMetadata(MediaMetadata.Builder().setTitle("KittyTune audio test").build())
                            .build()
                    )
                    prepare()
                    play()
                }
            }
            await("Playback position advances") {
                var advancing = false
                instrumentation.runOnMainSync {
                    advancing = MusicManager.player.isPlaying && MusicManager.player.currentPosition > 500
                }
                advancing
            }
            android.util.Log.i("KittyTuneAudioTest", "Fixture is playing")
            val projectionCheckDelay = InstrumentationRegistry.getArguments().getString("holdMs")?.toLong()
            if (projectionCheckDelay != null) {
                Thread.sleep(projectionCheckDelay)
                await("KittyTune owns Android media focus during projection") { hasAudioFocus(context.packageName) }
            }
            instrumentation.runOnMainSync { MusicManager.player.pause() }
            await("KittyTune releases Android media focus on pause") { !hasAudioFocus(context.packageName) }
        } finally {
            instrumentation.runOnMainSync {
                MusicManager.player.stop()
                MusicManager.player.clearMediaItems()
            }
            tone.delete()
        }
    }

    private fun hasAudioFocus(packageName: String): Boolean {
        val descriptor = instrumentation.uiAutomation.executeShellCommand("dumpsys audio")
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { reader ->
            reader.readText().substringAfter("Audio Focus stack entries", "")
                .substringBefore("No external focus policy")
                .lineSequence().any { it.contains("pack: $packageName") && it.contains("GAIN") }
        }
    }

    private fun await(description: String, condition: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 8_000
        while (android.os.SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            Thread.sleep(100)
        }
        assertTrue(description, condition())
    }

    private fun writeTone(file: File) {
        val sampleRate = 48_000
        val bytesPerSecond = sampleRate * 2
        val dataSize = bytesPerSecond * 120
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(sampleRate).putInt(bytesPerSecond)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(dataSize).array()
        val samples = ByteBuffer.allocate(bytesPerSecond).order(ByteOrder.LITTLE_ENDIAN)
        repeat(sampleRate) { samples.putShort((sin(2 * PI * 440 * it / sampleRate) * 4_000).toInt().toShort()) }
        file.outputStream().use { output ->
            output.write(header)
            repeat(120) { output.write(samples.array()) }
        }
    }
}
