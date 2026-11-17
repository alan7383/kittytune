package com.alananasss.kittytune.ui.library

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
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
import com.alananasss.kittytune.data.local.toTrack
import com.alananasss.kittytune.ui.common.SettingsHighlightManager
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.profile.AppearanceSettingsScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackDeletingToggleEmulatorTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun fastPlaylistScrollingAndPlaylistMenuToggleWorkEndToEnd() {
        val context = compose.activity
        val dao = AppDatabase.getDatabase(context).downloadDao()
        val prefs = PlayerPreferences(context)
        val previousMethod = prefs.getTrackRemovalMethod()
        val playlistId = -88_000L
        val tracks = (1..18).map { index ->
            LocalTrack(playlistId - index, "Fast Scroll Track $index", "Test Artist", "", 120_000, "", "", source = "local")
        }
        fun persistedIds() = runBlocking { dao.getTracksForPlaylistSync(playlistId).map { it.id } }

        try {
            prefs.setTrackRemovalMethod(TrackRemovalMethod.SWIPE_AND_MENU)
            runBlocking {
                dao.insertPlaylist(LocalPlaylist(playlistId, "Fast Scroll Playlist", "Test Artist", "", tracks.size, isUserCreated = true))
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

            compose.waitUntil(15_000) { compose.onAllNodesWithText(tracks[0].title).fetchSemanticsNodes().isNotEmpty() }

            // 1. Reproduce fast diagonal scrolling up and down the playlist while SWIPE_AND_MENU is active:
            // diagonal thumb arcs (dx = 45dp, dy = -260dp) must scroll without accidentally deleting any track.
            repeat(3) {
                compose.onRoot().performTouchInput {
                    swipe(
                        start = Offset(centerX, height * 0.78f),
                        end = Offset(centerX + 45.dp.toPx(), height * 0.25f),
                        durationMillis = 120
                    )
                }
                compose.waitForIdle()
            }
            repeat(3) {
                compose.onRoot().performTouchInput {
                    swipe(
                        start = Offset(centerX, height * 0.30f),
                        end = Offset(centerX - 45.dp.toPx(), height * 0.80f),
                        durationMillis = 120
                    )
                }
                compose.waitForIdle()
            }
            assertEquals("Fast diagonal scrolling must not delete any tracks", tracks.map { it.id }, persistedIds())

            // 2. Open playlist '...' menu -> tap 'Track removal' -> switch to 'Three-dot menu only'
            val optionsLabel = context.getString(R.string.btn_options)
            val trackRemovalMenuLabel = context.getString(R.string.menu_track_removal)
            val menuOnlyLabel = context.getString(R.string.track_removal_menu_only)
            val disabledLabel = context.getString(R.string.track_removal_disabled)
            val swipeAndMenuLabel = context.getString(R.string.track_removal_swipe_and_menu)

            fun clickTopBarOverflow() {
                val nodes = compose.onAllNodesWithContentDescription(optionsLabel, useUnmergedTree = true)
                val topIdx = nodes.fetchSemanticsNodes().withIndex().minByOrNull { it.value.boundsInRoot.top }!!.index
                nodes[topIdx].performTouchInput { click() }
            }

            clickTopBarOverflow()
            compose.waitUntil(5_000) { compose.onAllNodesWithText(trackRemovalMenuLabel).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(trackRemovalMenuLabel).performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText(menuOnlyLabel).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(menuOnlyLabel).performClick()
            compose.waitForIdle()

            assertEquals(TrackRemovalMethod.MENU_ONLY, prefs.getTrackRemovalMethod())

            // Horizontal swipe on first visible track must NOT delete when MENU_ONLY
            val visibleTrackNode = compose.onAllNodes(hasText("Fast Scroll Track ", substring = true))[0]
            visibleTrackNode.performTouchInput {
                swipe(start = centerLeft + Offset(10.dp.toPx(), 0f), end = centerRight, durationMillis = 180)
            }
            compose.waitForIdle()
            assertEquals(tracks.size, persistedIds().size)

            // 3. Open playlist '...' menu -> switch to 'Disabled'
            clickTopBarOverflow()
            compose.waitUntil(5_000) { compose.onAllNodesWithText(trackRemovalMenuLabel).fetchSemanticsNodes().isNotEmpty() }
            // Verify live subtitle in playlist '...' menu reflects MENU_ONLY
            assertTrue(compose.onAllNodesWithText(menuOnlyLabel, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithText(trackRemovalMenuLabel).performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText(disabledLabel).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(disabledLabel).performClick()
            compose.waitForIdle()

            assertEquals(TrackRemovalMethod.DISABLED, prefs.getTrackRemovalMethod())
            assertEquals(TrackRemovalMethod.MENU_ONLY, prefs.getLastActiveTrackRemovalMethod())

            // Neither swipe nor removeFromContextPlaylist may delete when DISABLED
            val visibleTrackAfterDisable = compose.onAllNodes(hasText("Fast Scroll Track ", substring = true))[0]
            visibleTrackAfterDisable.performTouchInput {
                swipe(start = centerLeft + Offset(10.dp.toPx(), 0f), end = centerRight, durationMillis = 180)
            }
            compose.runOnIdle {
                vm.removeFromContextPlaylist(playlistId, tracks.first().toTrack())
            }
            compose.waitForIdle()
            assertEquals(tracks.size, persistedIds().size)

            // 4. Verify AppearanceSettingsScreen SplitSettingsItem switch toggles DISABLED <-> MENU_ONLY
            compose.runOnUiThread {
                SettingsHighlightManager.setHighlightKey("pref_track_removal")
                context.setContent {
                    MaterialTheme {
                        AppearanceSettingsScreen(
                            onNavigateToColors = {},
                            onNavigateToBottomBarSettings = {},
                            onNavigateToAppIconSettings = {},
                            onNavigateToPlayerCustomization = {},
                            onBackClick = {}
                        )
                    }
                }
            }
            val prefTitle = context.getString(R.string.pref_track_removal_title)
            compose.waitUntil(10_000) { compose.onAllNodesWithText(prefTitle).fetchSemanticsNodes().isNotEmpty() }

            val rowTop = compose.onNodeWithText(prefTitle).fetchSemanticsNode().boundsInRoot.top
            val rowBottom = compose.onNodeWithText(prefTitle).fetchSemanticsNode().boundsInRoot.bottom
            val switches = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            val switchIdx = switches.fetchSemanticsNodes().indexOfFirst {
                it.boundsInRoot.center.y in (rowTop - 40f)..(rowBottom + 40f)
            }
            assertTrue("Expected SplitSettingsItem switch beside Track removal method", switchIdx >= 0)

            // Flip switch ON -> restores remembered MENU_ONLY
            switches[switchIdx].performClick()
            compose.waitForIdle()
            assertEquals(TrackRemovalMethod.MENU_ONLY, prefs.getTrackRemovalMethod())
            assertTrue(prefs.getTrackRemovalMethod().isRemovalAllowed)
            assertFalse(prefs.getTrackRemovalMethod().isSwipeEnabled)

            // Flip switch OFF -> returns to DISABLED
            switches[switchIdx].performClick()
            compose.waitForIdle()
            assertEquals(TrackRemovalMethod.DISABLED, prefs.getTrackRemovalMethod())

            // Tap row -> open dialog -> select SWIPE_AND_MENU
            compose.onNodeWithText(prefTitle).performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText(swipeAndMenuLabel).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(swipeAndMenuLabel).performClick()
            compose.waitForIdle()
            assertEquals(TrackRemovalMethod.SWIPE_AND_MENU, prefs.getTrackRemovalMethod())
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
