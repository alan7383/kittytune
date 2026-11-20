package com.alananasss.kittytune.data

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.core.content.edit
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alananasss.kittytune.data.local.PlayerPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class PlaybackAudioFocusDeviceTest {
    // Stay in the foreground for audio focus without launching the player's saved-queue restoration.
    @get:Rule val activityRule = ActivityScenarioRule(ComponentActivity::class.java)

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

    @Test fun audioFocusPausesPlaybackByDefaultAndCanBeDisabledDuringPlayback() {
        val context = instrumentation.targetContext
        val prefs = PlayerPreferences(context)
        val stored = context.getSharedPreferences("player_state", Context.MODE_PRIVATE)
        val key = PlayerPreferences.KEY_PAUSE_ON_AUDIO_FOCUS_LOSS
        val originalSetting = prefs.getPauseOnAudioFocusLoss().takeIf { stored.contains(key) }
        val audio = context.getSystemService(AudioManager::class.java)
        val competitorLostFocus = AtomicBoolean(false)
        val competitor = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            .setOnAudioFocusChangeListener { if (it == AudioManager.AUDIOFOCUS_LOSS) competitorLostFocus.set(true) }
            .build()
        val tone = File(context.cacheDir, "audio-focus-preference-test.wav")
        writeTone(tone)
        val mediaItem = MediaItem.fromUri(tone.toURI().toString())

        try {
            stored.edit { remove(key) }
            assertTrue(prefs.getPauseOnAudioFocusLoss())
            instrumentation.runOnMainSync {
                MusicManager.init(context)
                context.startService(Intent(context, PlaybackService::class.java))
                MusicManager.player.apply {
                    setMediaItem(mediaItem)
                    prepare()
                    play()
                }
            }
            await("KittyTune owns media focus by default on the phone") {
                isPlaying() && hasAudioFocus(context.packageName)
            }
            assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, audio.requestAudioFocus(competitor))
            await("KittyTune pauses when another media client takes focus") { !isPlaying() }
            audio.abandonAudioFocusRequest(competitor)

            instrumentation.runOnMainSync {
                MusicManager.playWhenReadyRequested = true
                MusicManager.player.play()
            }
            await("KittyTune owns media focus again when playback starts") {
                isPlaying() && hasAudioFocus(context.packageName)
            }
            prefs.setPauseOnAudioFocusLoss(false)
            await("Disabling the setting releases focus during playback") { !hasAudioFocus(context.packageName) }
            assertTrue(isPlaying())

            assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, audio.requestAudioFocus(competitor))
            val nextItem = mediaItem.buildUpon().setMediaId("focus-test-next-track").build()
            instrumentation.runOnMainSync { MusicManager.crossfadeToMediaItem(nextItem, 0L, 500L) }
            await("Playback advances through the crossfade with another media client holding focus") {
                var advanced = false
                instrumentation.runOnMainSync {
                    val current = MusicManager.player
                    advanced = current.currentMediaItem?.mediaId == nextItem.mediaId &&
                        current.isPlaying && current.currentPosition > 1_000 && !MusicManager.isCrossfadingOut
                }
                advanced
            }
            assertFalse("The crossfade must leave the other media client's focus alone", competitorLostFocus.get())
            audio.abandonAudioFocusRequest(competitor)

            instrumentation.runOnMainSync { MusicManager.setAndroidAutoProjected(true) }
            await("Android Auto still gets media focus when projected") { hasAudioFocus(context.packageName) }
            instrumentation.runOnMainSync { MusicManager.setAndroidAutoProjected(false) }
            await("Disconnecting Android Auto returns to mixing") { !hasAudioFocus(context.packageName) }

            prefs.setPauseOnAudioFocusLoss(true)
            await("Re-enabling the setting takes focus during playback") {
                isPlaying() && hasAudioFocus(context.packageName)
            }
        } finally {
            audio.abandonAudioFocusRequest(competitor)
            instrumentation.runOnMainSync {
                MusicManager.setAndroidAutoProjected(false)
                MusicManager.player.stop()
                MusicManager.player.clearMediaItems()
            }
            if (originalSetting == null) stored.edit { remove(key) } else prefs.setPauseOnAudioFocusLoss(originalSetting)
            tone.delete()
        }
    }

    @Test fun mutePausesPlaybackAndUnmuteHonorsTheResumeSetting() {
        val context = instrumentation.targetContext
        val prefs = PlayerPreferences(context)
        val audio = context.getSystemService(AudioManager::class.java)
        val originalVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val audibleVolume = originalVolume.coerceAtLeast(1)
        val originalPause = prefs.getPauseOnMute()
        val originalResume = prefs.getResumeOnUnmute()
        val tone = File(context.cacheDir, "pause-on-mute-test.wav")
        writeTone(tone)
        try {
            prefs.setPauseOnMute(true)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, audibleVolume, 0)
            instrumentation.runOnMainSync {
                MusicManager.init(context)
                context.startService(Intent(context, PlaybackService::class.java))
                MusicManager.player.apply {
                    setMediaItem(MediaItem.fromUri(tone.toURI().toString()))
                    prepare()
                }
            }

            for (resumeOnUnmute in listOf(false, true)) {
                prefs.setResumeOnUnmute(resumeOnUnmute)
                instrumentation.runOnMainSync { MusicManager.player.play() }
                await("The fixture is playing before mute") { isPlaying() }

                audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                await("Muting pauses the fixture in both modes") { !isPlaying() }
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, audibleVolume, 0)

                if (resumeOnUnmute) {
                    await("Playback resumes when configured") { isPlaying() }
                } else {
                    // Give the system volume broadcast time to reach the playback receiver.
                    repeat(10) {
                        Thread.sleep(100)
                        assertFalse("Playback must stay paused when volume returns", isPlaying())
                    }
                }
            }
        } finally {
            instrumentation.runOnMainSync {
                MusicManager.player.stop()
                MusicManager.player.clearMediaItems()
            }
            prefs.setPauseOnMute(originalPause)
            prefs.setResumeOnUnmute(originalResume)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
            tone.delete()
        }
    }

    private fun isPlaying(): Boolean {
        var playing = false
        instrumentation.runOnMainSync { playing = MusicManager.player.isPlaying }
        return playing
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
