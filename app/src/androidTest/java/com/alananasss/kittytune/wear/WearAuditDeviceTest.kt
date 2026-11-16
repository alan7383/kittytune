package com.alananasss.kittytune.wear

import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.os.ParcelFileDescriptor
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alananasss.kittytune.MainActivity
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.LocalTrack
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.wear.common.WearCodecs
import com.alananasss.kittytune.wear.common.WearConstants
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Audit probes: exercise production receiver and publisher against the actual Android player. */
@RunWith(AndroidJUnit4::class)
class WearAuditDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var vm: PlayerViewModel
    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private fun await(description: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < deadline) {
            var ready = false
            main { ready = condition() }
            if (ready) return
            Thread.sleep(100)
        }
        fail("Timeout: $description")
    }

    @Test fun phoneReceiverControlsActualPlaybackAndSystemVolume() {
        val wav = File(context.cacheDir, "wear-audit.wav")
        val dataSize = 8_000 * 2 * 60
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(8_000).putInt(16_000)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(dataSize).array()
        wav.outputStream().use { it.write(header); it.write(ByteArray(dataSize)) }
        val tracks = List(3) { Track(-880_000L - it, "Wear audit ${it + 1}", null, 60_000,
            User(-88, "Local silent fixture", null), source = "local") }
        val dao = AppDatabase.getDatabase(context).downloadDao()
        runBlocking { tracks.forEach { dao.insertTrack(LocalTrack(it.id, it.title!!, "Local silent fixture", "",
            60_000, wav.absolutePath, "", source = "local")) } }
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val savedVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val receiver = WearableMessageReceiverService()
        ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java).apply {
            isAccessible = true
        }.invoke(receiver, context)
        fun command(path: String, payload: ByteArray = ByteArray(0)) {
            main { runBlocking(Dispatchers.Main.immediate) { receiver.handleMessage(path, payload) } }
        }
        try {
            main {
                compose.activity.setContent { MaterialTheme { } }
                vm = ViewModelProvider(compose.activity)[PlayerViewModel::class.java]
                WearableStatePublisher.activeTargetType = WearConstants.TARGET_TYPE_PHONE
            }
            val restoring = PlayerViewModel::class.java.getDeclaredField("isRestoringSession").apply { isAccessible = true }
            await("initial restore") { !restoring.getBoolean(vm) }
            main { vm.playPlaylist(tracks, 0, autoPlay = true, respectShuffle = false) }
            await("playing first fixture") { MusicManager.player.isPlaying && vm.currentQueueIndex == 0 }
            command(WearConstants.ACTION_PAUSE)
            main { assertFalse(MusicManager.player.playWhenReady) }
            command(WearConstants.ACTION_PLAY)
            await("play command") { MusicManager.player.isPlaying }
            command(WearConstants.ACTION_PLAY_PAUSE)
            main { assertFalse(MusicManager.player.playWhenReady) }
            command(WearConstants.ACTION_PLAY)
            command(WearConstants.ACTION_NEXT)
            await("next command") { vm.currentQueueIndex == 1 && MusicManager.player.isPlaying }
            main { MusicManager.player.seekTo(0) }
            command(WearConstants.ACTION_PREVIOUS)
            await("previous command") { vm.currentQueueIndex == 0 && MusicManager.player.isPlaying }
            command(WearConstants.ACTION_SET_VOLUME, WearCodecs.encodeVolume(0.5f))
            val min = audio.getStreamMinVolume(AudioManager.STREAM_MUSIC)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val expected = (min + (max - min) * 0.5f).roundToInt()
            assertEquals(expected, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
            command(WearConstants.ACTION_VOLUME_UP)
            val increased = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            assertTrue(increased > expected)
            command(WearConstants.ACTION_VOLUME_DOWN)
            assertTrue(audio.getStreamVolume(AudioManager.STREAM_MUSIC) < increased)
        } finally {
            main {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume, 0)
                if (::vm.isInitialized) vm.resetPlaybackState()
                compose.activity.viewModelStore.clear()
            }
            runBlocking { tracks.forEach { dao.deleteTrack(it.id) } }
            wav.delete()
        }
    }

    @Test fun statePublisherMustRespectTheRealPlayerLooper() {
        main { MusicManager.init(context); WearableStatePublisher.publishCurrentState(context) }
        Thread.sleep(1200)
        val descriptor = instrumentation.uiAutomation.executeShellCommand(
            "logcat -d --pid=${android.os.Process.myPid()} -s WearableStatePublisher:V")
        val logs = ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        File(context.getExternalFilesDir(null), "wear-audit-publisher.txt").writeText(logs)
        assertFalse("Production publisher accesses ExoPlayer from the wrong thread; see wear-audit-publisher.txt",
            logs.contains("wrong thread", ignoreCase = true))
    }

    @Test fun dataLayerPeerConnectionIsAvailable() {
        val requiresPairedWatch = InstrumentationRegistry.getArguments().getString("wearPaired") == "true"
        assumeTrue("Enable the paired-device check with -e wearPaired true", requiresPairedWatch)
        val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes, 8, TimeUnit.SECONDS)
        assertTrue("No watch peer is connected", nodes.isNotEmpty())
    }
}
