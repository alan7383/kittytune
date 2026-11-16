package com.alananasss.kittytune.ui.library

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alananasss.kittytune.MainActivity
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.LocalPlaylist
import com.alananasss.kittytune.data.local.LocalTrack
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.local.PlaylistTrackCrossRef
import com.alananasss.kittytune.data.local.TrackRemovalMethod
import com.alananasss.kittytune.ui.player.PlayerViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistGesturesIssue87Test {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun playlistGesturesWithSwipeRemoval() = checkPlaylistGestures(TrackRemovalMethod.SWIPE_AND_MENU)

    @Test fun playlistGesturesWithMenuRemoval() = checkPlaylistGestures(TrackRemovalMethod.MENU_ONLY)

    private fun checkPlaylistGestures(removalMethod: TrackRemovalMethod) {
        val context = compose.activity
        val dao = AppDatabase.getDatabase(context).downloadDao()
        val prefs = PlayerPreferences(context)
        val previousMethod = prefs.getTrackRemovalMethod()
        val playlistId = -87_000L
        val tracks = (1..3).map { index ->
            LocalTrack(playlistId - index, "Gesture track $index", "Test artist", "", 90_000, "", "", source = "local")
        }
        fun persistedOrder() = runBlocking { dao.getTracksForPlaylistSync(playlistId).map { it.id } }

        try {
            prefs.setTrackRemovalMethod(removalMethod)
            runBlocking {
                dao.insertPlaylist(LocalPlaylist(playlistId, "Gesture playlist", "Test artist", "", tracks.size, isUserCreated = true))
                tracks.forEachIndexed { index, track ->
                    dao.insertTrack(track)
                    dao.insertPlaylistTrackRef(PlaylistTrackCrossRef(playlistId, track.id, addedAt = index.toLong()))
                }
            }

            lateinit var vm: PlayerViewModel
            compose.runOnUiThread {
                vm = ViewModelProvider(context)[PlayerViewModel::class.java]
                context.setContent {
                    MaterialTheme { PlaylistDetailScreen(playlistId.toString(), {}, {}, vm) }
                }
            }
            val originalOrder = tracks.map { it.id }
            val first = compose.onNodeWithText(tracks.first().title)
            val second = compose.onNodeWithText(tracks[1].title)
            compose.waitUntil(15_000) { second.isDisplayed() }
            val moveLabel = context.getString(R.string.desc_move)
            val firstHandle = compose.onAllNodesWithContentDescription(moveLabel, useUnmergedTree = true)[0]

            first.performTouchInput { longClick() }
            compose.runOnIdle {
                assertTrue("A hold on the track body opens its options", vm.showMenuSheet)
                assertEquals(tracks.first().id, vm.trackForMenu?.id)
                assertEquals(playlistId, vm.menuContextPlaylistId)
                vm.showMenuSheet = false
            }
            assertEquals(originalOrder, persistedOrder())

            val displacement = second.fetchSemanticsNode().boundsInRoot.center.y -
                first.fetchSemanticsNode().boundsInRoot.center.y
            firstHandle.performTouchInput {
                down(center)
                advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
                moveBy(Offset(0f, 16.dp.toPx()))
                moveBy(Offset(0f, displacement - 16.dp.toPx()), delayMillis = 250)
                up()
            }
            val reordered = listOf(tracks[1].id, tracks[0].id, tracks[2].id)
            compose.waitUntil(8_000) { persistedOrder() == reordered }
            compose.runOnIdle { assertFalse("Dragging the handle must not open the menu", vm.showMenuSheet) }

            val optionsLabel = context.getString(R.string.btn_options)
            val firstVisibleTrack = compose.onNodeWithText(tracks[1].title)
            val rowY = firstVisibleTrack.fetchSemanticsNode().boundsInRoot.center.y
            val options = compose.onAllNodesWithContentDescription(optionsLabel, useUnmergedTree = true)
            val optionIndex = options.fetchSemanticsNodes().indexOfFirst { rowY in it.boundsInRoot.top..it.boundsInRoot.bottom }
            options[optionIndex].performTouchInput { click() }
            compose.runOnIdle {
                assertTrue(vm.showMenuSheet)
                assertEquals(tracks[1].id, vm.trackForMenu?.id)
                vm.showMenuSheet = false
            }

            firstVisibleTrack.performTouchInput {
                swipe(start = centerLeft + Offset(10.dp.toPx(), 0f), end = centerRight, durationMillis = 180)
            }
            compose.waitForIdle()
            if (removalMethod == TrackRemovalMethod.SWIPE_AND_MENU) {
                compose.waitUntil(8_000) { persistedOrder() == reordered.drop(1) }
            } else {
                assertEquals(reordered, persistedOrder())
            }
            compose.runOnIdle { assertFalse("Swiping must not open the menu", vm.showMenuSheet) }
        } finally {
            prefs.setTrackRemovalMethod(previousMethod)
            runBlocking {
                dao.deletePlaylistRefs(playlistId)
                dao.deletePlaylist(playlistId)
                tracks.forEach { dao.deleteTrack(it.id) }
            }
        }
    }
}
