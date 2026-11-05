package com.alananasss.kittytune.ui.player

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alananasss.kittytune.R
import com.alananasss.kittytune.MainActivity
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.LocalTrack
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Runs the production queue UI and ExoPlayer on Android, using a real local WAV fixture. */
@RunWith(AndroidJUnit4::class)
class QueueIssue77EmulatorTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var vm: PlayerViewModel

    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)

    private fun await(description: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < deadline) {
            var passed = false
            main { passed = condition() }
            if (passed) return
            Thread.sleep(100)
        }
        fail("Timed out: $description")
    }

    private fun awaitPlaying(index: Int, id: Long) = await("playing occurrence $index of $id") {
        vm.currentQueueIndex == index && vm.currentTrack?.id == id &&
            MusicManager.player.currentMediaItem?.mediaId == id.toString() &&
            MusicManager.player.playbackState == Player.STATE_READY && MusicManager.player.isPlaying
    }

    private fun screenshot(name: String) {
        // Let the Compose test clock render state changes made by playback callbacks first.
        compose.waitForIdle()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(context.getExternalFilesDir(null), "issue77-$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test fun collapsedHistoryAndPlayNextDuplicatesKeepTheCorrectPlayingOccurrence() {
        val wav = File(context.cacheDir, "issue77-fixture.wav")
        val dataSize = 8_000 * 2 * 60
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(8_000).putInt(16_000)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(dataSize).array()
        wav.outputStream().use { it.write(header); it.write(ByteArray(dataSize)) }
        val tracks = List(10) { index ->
            Track(-770_000L - index, "Issue77 track $index", null, 60_000,
                User(-77, "Emulator regression", null), source = "local")
        }
        val dao = AppDatabase.getDatabase(context).downloadDao()
        runBlocking {
            tracks.forEach { track ->
                dao.insertTrack(LocalTrack(track.id, track.title!!, "Emulator regression", "", 60_000,
                    wav.absolutePath, "", source = "local"))
            }
        }
        try {
            main {
                compose.activity.setContent { MaterialTheme { } }
                vm = ViewModelProvider(compose.activity)[PlayerViewModel::class.java]
            }
            val restoring = PlayerViewModel::class.java.getDeclaredField("isRestoringSession").apply { isAccessible = true }
            await("initial session restoration") { !restoring.getBoolean(vm) }
            main {
                if (vm.shuffleEnabled) vm.toggleShuffle()
                while (vm.repeatMode != RepeatMode.NONE) vm.toggleRepeatMode()
                vm.isQueuePreserveUpcomingEnabled = false
                vm.playPlaylist(tracks, 0, autoPlay = true, respectShuffle = false)
            }
            awaitPlaying(0, tracks[0].id)
            // Exercise a real automatic ExoPlayer transition, not just a UI index assignment.
            main { MusicManager.player.seekTo(MusicManager.player.duration) }
            awaitPlaying(1, tracks[1].id)

            main { vm.playPlaylist(tracks, 7, autoPlay = true, respectShuffle = false) }
            awaitPlaying(7, tracks[7].id)
            main { compose.activity.setContent { MaterialTheme { ExpandedQueueScreen(vm, onClose = {}) } } }
            val playedLabel = context.getString(R.string.queue_played)
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
            compose.onNodeWithText(playedLabel).assertIsDisplayed()
            for (index in 4..6) compose.onNodeWithText(tracks[index].title!!).assertIsDisplayed()
            compose.onNodeWithText(tracks[0].title!!).assertDoesNotExist()
            screenshot("collapsed")

            compose.onNodeWithText(playedLabel).performClick()
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(0)
            compose.onNodeWithText(tracks[0].title!!).assertIsDisplayed()
            compose.onAllNodesWithText(playedLabel).assertCountEquals(1)
            screenshot("expanded")
            compose.onNodeWithText(playedLabel).performClick()
            compose.onNodeWithText(tracks[0].title!!).assertDoesNotExist()

            // Requeue a recent visible past track and play the NEW occurrence.
            main { vm.insertNext(listOf(tracks[4])); vm.playNext(manual = true) }
            awaitPlaying(8, tracks[4].id)
            main {
                assertEquals(tracks[4].id, vm.queue[4].id)
                assertEquals(tracks[4].id, vm.queue[8].id)
                assertEquals(11, vm.queue.size)
                vm.syncWithCurrentPlayback()
            }
            Thread.sleep(800)
            awaitPlaying(8, tracks[4].id)
            assertEquals(8, PlayerPreferences(context).getLastQueueIndex())
            main { vm.removeTrackFromQueue(4) }
            awaitPlaying(7, tracks[4].id)
            main { assertEquals(1, vm.queue.count { it.id == tracks[4].id }) }
            screenshot("duplicate-removed")
            main { vm.playNext(manual = true) }
            awaitPlaying(8, tracks[8].id)

            // Repeat with an entry hidden inside the collapsed part of history.
            main { vm.insertNext(listOf(tracks[0])); vm.playNext(manual = true) }
            awaitPlaying(9, tracks[0].id)
            main { vm.toggleShuffle(); vm.toggleShuffle() }
            awaitPlaying(9, tracks[0].id)
            main { vm.removeTrackFromQueue(0) }
            awaitPlaying(8, tracks[0].id)
            main { vm.playNext(manual = true) }
            awaitPlaying(9, tracks[9].id)
            screenshot("final")
        } finally {
            if (::vm.isInitialized) main { vm.resetPlaybackState(); compose.activity.viewModelStore.clear() }
            runBlocking { tracks.forEach { dao.deleteTrack(it.id) } }
            wav.delete()
        }
    }
}
