package com.alananasss.kittytune.wear

import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.os.ParcelFileDescriptor
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.test.ext.junit.rules.ActivityScenarioRule
import org.junit.Before
import androidx.lifecycle.Lifecycle
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
import com.alananasss.kittytune.wear.common.WearCommandResult
import com.alananasss.kittytune.wear.common.TargetSelectionPayload
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
    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)
    private lateinit var activity: MainActivity
    @Before fun prepareActivity() {
        activityRule.scenario.onActivity {
            activity = it
            it.setContent { MaterialTheme { } }
        }
    }
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
                activity.setContent { MaterialTheme { } }
                vm = ViewModelProvider(activity)[PlayerViewModel::class.java]
                WearableStatePublisher.activeTargetType = WearConstants.TARGET_TYPE_PHONE
            }
            val restoring = PlayerViewModel::class.java.getDeclaredField("isRestoringSession").apply { isAccessible = true }
            await("initial restore") { !restoring.getBoolean(vm) }
            main { vm.playPlaylist(tracks, 0, autoPlay = true, respectShuffle = false) }
            await("playing first fixture") { MusicManager.player.isPlaying && vm.currentQueueIndex == 0 }
            activityRule.scenario.moveToState(Lifecycle.State.CREATED)
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
                activity.viewModelStore.clear()
            }
            runBlocking { tracks.forEach { dao.deleteTrack(it.id) } }
            wav.delete()
        }
    }

    @Test fun pairedWatchControlsBackgroundPhone() {
        assumeTrue("Enable the paired-device check with -e wearPaired true",
            InstrumentationRegistry.getArguments().getString("wearPaired") == "true")
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
                activity.setContent { MaterialTheme { } }
                vm = ViewModelProvider(activity)[PlayerViewModel::class.java]
                WearableStatePublisher.activeTargetType = WearConstants.TARGET_TYPE_PHONE
            }
            val restoring = PlayerViewModel::class.java.getDeclaredField("isRestoringSession").apply { isAccessible = true }
            await("initial restore") { !restoring.getBoolean(vm) }
            main { vm.playPlaylist(tracks, 0, autoPlay = true, respectShuffle = false) }
            await("playing first fixture") { MusicManager.player.isPlaying && vm.currentQueueIndex == 0 }
            activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            val ready = File(context.getExternalFilesDir(null), "wear-e2e-ready.txt")
            val initialIndex = (audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * .5f).roundToInt()
            main {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, initialIndex, 0)
                WearableStatePublisher.publishCurrentState(context)
            }
            ready.writeText("READY")
            await("watch pause") { !MusicManager.player.playWhenReady }
            await("watch play") { MusicManager.player.isPlaying }
            await("watch next") { vm.currentQueueIndex == 1 && MusicManager.player.isPlaying }
            await("watch previous") { vm.currentQueueIndex == 0 && MusicManager.player.isPlaying }
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val high = (max * .8f).roundToInt()
            await("watch volume up") { audio.getStreamVolume(AudioManager.STREAM_MUSIC) == high }
            await("watch volume down") { audio.getStreamVolume(AudioManager.STREAM_MUSIC) < high }
            await("watch final pause") { !MusicManager.player.playWhenReady }
            ready.writeText("PASSED: pause, play, next, previous, volume up/down on background phone")

        } finally {
            main {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume, 0)
                if (::vm.isInitialized) vm.resetPlaybackState()
                activity.viewModelStore.clear()
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

    @Test fun applicationAcknowledgementReportsAppliedVolumeAndRejectedTarget() {
        val receiver = WearableMessageReceiverService()
        ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java).apply {
            isAccessible = true
        }.invoke(receiver, context)
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val original = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        main { WearableStatePublisher.resetTargetForTesting() }
        try {
            val accepted = WearCommandResult.decode(Tasks.await(receiver.onRequest(
                "test-node", WearConstants.ACTION_SET_VOLUME, WearCodecs.encodeVolume(.5f)
            ), 10, TimeUnit.SECONDS))
            assertNull(accepted.error)
            assertNotNull(accepted.state)
            assertEquals(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() /
                audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC), accepted.state!!.volume, .001f)
            val rejected = WearCommandResult.decode(Tasks.await(receiver.onRequest(
                "test-node", WearConstants.ACTION_TARGET_SELECT,
                WearCodecs.encodeTarget(TargetSelectionPayload.desktop("missing-peer", "Missing"))
            ), 10, TimeUnit.SECONDS))
            assertNotNull("Unknown desktops must be rejected by the phone", rejected.error)
            assertEquals(WearConstants.TARGET_TYPE_PHONE, WearableStatePublisher.activeTargetType)
        } finally {
            main { audio.setStreamVolume(AudioManager.STREAM_MUSIC, original, 0) }
        }
    }

    @Test fun dataLayerPeerConnectionIsAvailable() {
        assumeTrue("Enable the paired-device check with -e wearPaired true",
            InstrumentationRegistry.getArguments().getString("wearPaired") == "true")
        val report = File(context.getExternalFilesDir(null), "wear-audit-phone-nodes.txt")
        try {
            val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes, 8, TimeUnit.SECONDS)
            report.writeText("Connected wearable nodes: ${nodes.map { it.displayName + ':' + it.id }}")
            assertTrue("No watch peer is connected; end-to-end Data Layer testing is unavailable", nodes.isNotEmpty())
        } catch (e: Exception) {
            report.writeText("Data Layer unavailable: ${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }
}
