package com.alananasss.kittytune.ui.player

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alananasss.kittytune.MainActivity
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.LikeRepository
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.LocalTrack
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.profile.AudioPlaybackSettingsScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Production queue menus, persisted settings and real local-file playback (issue #78). */
@RunWith(AndroidJUnit4::class)
class QueueIssue78EmulatorTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var vm: PlayerViewModel
    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)

    private fun await(description: String, condition: () -> Boolean) {
        compose.waitUntil(description, 20_000) {
            var passed = false
            main { passed = condition() }
            passed
        }
    }

    private fun awaitPlaying(index: Int, id: Long) = await("playing $id at $index") {
        vm.currentQueueIndex == index && vm.currentTrack?.id == id &&
            MusicManager.player.playbackState == Player.STATE_READY && MusicManager.player.isPlaying
    }

    private fun menuNext(index: Int) {
        main { vm.showTrackOptions(vm.queue[index], fromPlayer = true, queueIndex = index) }
        compose.onNodeWithText(context.getString(R.string.menu_play_next)).performClick()
        compose.waitForIdle()
    }

    @Test fun queueMenusAndJumpSettingWorkTogetherWithoutLosingOccurrences() {
        val wav = File(context.cacheDir, "issue78-fixture.wav")
        val dataSize = 8_000 * 2 * 120
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(8_000).putInt(16_000)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(dataSize).array()
        wav.outputStream().use { it.write(header); it.write(ByteArray(dataSize)) }
        val tracks = List(8) { index -> Track(780_000L + index, "Issue78 track $index", null, 120_000,
            User(-78, "Regression", null), source = "local") }
        val dao = AppDatabase.getDatabase(context).downloadDao()
        val prefs = PlayerPreferences(context)
        val previousPreserve = prefs.getQueuePreserveUpcomingOnJump()
        var settings by mutableStateOf(false)
        runBlocking {
            tracks.forEach { dao.insertTrack(LocalTrack(it.id, it.title!!, "Regression", "", 120_000,
                wav.absolutePath, "", source = "local")) }
        }
        try {
            main {
                compose.activity.setContent { MaterialTheme { } }
                vm = ViewModelProvider(compose.activity)[PlayerViewModel::class.java]
            }
            val restoring = PlayerViewModel::class.java.getDeclaredField("isRestoringSession").apply { isAccessible = true }
            await("restored session") { !restoring.getBoolean(vm) }
            main {
                if (vm.shuffleEnabled) vm.toggleShuffle()
                while (vm.repeatMode != RepeatMode.NONE) vm.toggleRepeatMode()
                vm.playPlaylist(tracks, 2, autoPlay = true, respectShuffle = false)
                compose.activity.setContent {
                    MaterialTheme {
                        if (settings) AudioPlaybackSettingsScreen({}, vm)
                        else {
                            ExpandedQueueScreen(vm, {})
                            if (vm.showMenuSheet) MenuSheetContent(vm)
                        }
                    }
                }
            }
            awaitPlaying(2, tracks[2].id)
            menuNext(7)
            compose.runOnIdle {
                assertEquals(8, vm.queue.size)
                assertEquals(tracks[7].id, vm.queue[3].id)
                assertEquals(1, vm.queue.count { it.id == tracks[7].id })
            }
            awaitPlaying(2, tracks[2].id)
            menuNext(0)
            compose.runOnIdle {
                assertEquals(9, vm.queue.size)
                assertEquals(tracks[0].id, vm.queue[0].id)
                assertEquals(tracks[0].id, vm.queue[3].id)
                assertEquals(2, vm.queue.count { it.id == tracks[0].id })
            }
            // Moving the replayed occurrence must leave the historical occurrence where it was.
            menuNext(4)
            compose.runOnIdle { assertEquals(tracks[7].id, vm.queue[3].id) }
            menuNext(4)
            compose.runOnIdle {
                assertEquals(tracks[0].id, vm.queue[0].id)
                assertEquals(tracks[0].id, vm.queue[3].id)
                assertEquals(9, vm.queue.size)
            }
            main { vm.playNext(manual = true) }
            awaitPlaying(3, tracks[0].id)
            main { vm.syncWithCurrentPlayback(); vm.toggleShuffle(); vm.toggleShuffle() }
            awaitPlaying(3, tracks[0].id)
            compose.onNodeWithText(tracks[1].title!!).performTouchInput { longClick() }
            compose.onNodeWithText(context.getString(R.string.player_like_action)).performClick()
            compose.onNodeWithText(context.getString(R.string.action_unlike)).assertExists()
            compose.runOnIdle { assertTrue(LikeRepository.isTrackLiked(tracks[1].id)) }
            compose.onNodeWithText(context.getString(R.string.action_unlike)).performClick()
            compose.runOnIdle { assertFalse(LikeRepository.isTrackLiked(tracks[1].id)) }
            main { vm.showMenuSheet = false; settings = true; vm.toggleQueuePreserveUpcoming(true) }
            // The visible setting changes both the live behavior and the persisted preference.
            compose.onNodeWithText(context.getString(R.string.pref_queue_preserve_upcoming)).performClick()
            compose.runOnIdle {
                assertFalse(vm.isQueuePreserveUpcomingEnabled)
                assertFalse(prefs.getQueuePreserveUpcomingOnJump())
                settings = false
            }
            val before = vm.queue.map { it.id }
            main { vm.skipToQueueItem(6) }
            awaitPlaying(6, before[6])
            compose.runOnIdle { assertEquals(before, vm.queue.map { it.id }) }
            main { vm.toggleQueuePreserveUpcoming(true); vm.skipToQueueItem(8) }
            awaitPlaying(7, before[8])
            compose.runOnIdle { assertEquals(before[7], vm.queue[8].id) }
            // The disabled setting must be respected even when shuffle is active.
            main { vm.toggleShuffle(); vm.toggleQueuePreserveUpcoming(false) }
            val shuffled = vm.queue.map { it.id }
            val target = vm.queue.lastIndex
            main { vm.skipToQueueItem(target) }
            awaitPlaying(target, shuffled[target])
            compose.runOnIdle { assertEquals(shuffled, vm.queue.map { it.id }) }
        } finally {
            if (::vm.isInitialized) main {
                vm.toggleQueuePreserveUpcoming(previousPreserve)
                vm.resetPlaybackState()
                compose.activity.viewModelStore.clear()
            }
            tracks.forEach { LikeRepository.removeLike(it.id) }
            runBlocking { tracks.forEach { dao.deleteTrack(it.id) } }
            wav.delete()
        }
    }
}
