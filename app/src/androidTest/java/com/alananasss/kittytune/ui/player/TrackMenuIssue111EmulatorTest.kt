package com.alananasss.kittytune.ui.player

import android.os.ParcelFileDescriptor
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alananasss.kittytune.MainActivity
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.LocalTrack
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.common.KittyModalBottomSheet
import com.alananasss.kittytune.ui.home.HomeViewModel
import com.alananasss.kittytune.ui.home.SearchFilter
import com.alananasss.kittytune.ui.home.SearchResultsList
import com.alananasss.kittytune.ui.library.PlaylistDetailScreen
import com.alananasss.kittytune.utils.NetworkUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class TrackMenuIssue111EmulatorTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun downloadsPlaybackKeepsTrackMenusUsableAcrossNetworkChanges() {
        val activity = compose.activity
        val dao = AppDatabase.getDatabase(activity).downloadDao()
        val wav = File(activity.cacheDir, "issue111-fixture.wav")
        val downloaded = LocalTrack(
            1_110_001L, "Issue 111 saved track", "Saved artist", "", 90_000,
            wav.absolutePath, "", ownerId = 1_110_010L
        )
        val searchTitle = "Issue 111 search result"
        val searchResult = Track(
            1_110_002L, searchTitle, null, 90_000,
            User(1_110_020L, "Search artist", null), source = "soundcloud"
        )
        val wifiWasEnabled = shell("settings get global wifi_on") == "1"
        val mobileDataWasEnabled = shell("settings get global mobile_data") == "1"
        lateinit var player: PlayerViewModel
        var showSearch by mutableStateOf(false)

        try {
            writeWav(wav)
            runBlocking { dao.insertTrack(downloaded) }
            compose.waitUntil("validated network", 20_000) { NetworkUtils.hasValidatedInternet(activity) }
            compose.runOnUiThread {
                val models = ViewModelProvider(activity)
                player = models[PlayerViewModel::class.java]
                val home = models[HomeViewModel::class.java]
                home.searchResultsTracks.add(searchResult)
                activity.setContent {
                    MaterialTheme {
                        if (showSearch) {
                            SearchResultsList(home, player, {}, SearchFilter.TRACKS)
                        } else {
                            PlaylistDetailScreen("downloads", {}, {}, player)
                        }
                        if (player.showMenuSheet) {
                            KittyModalBottomSheet(onDismissRequest = { player.showMenuSheet = false }) {
                                MenuSheetContent(player)
                            }
                        }
                        if (player.showDetailsSheet) {
                            player.selectedTrackForSheet?.let { track ->
                                KittyModalBottomSheet(onDismissRequest = { player.showDetailsSheet = false }) {
                                    DetailsSheetContent(track, { player.showDetailsSheet = false }, {}, player)
                                }
                            }
                        }
                    }
                }
            }

            compose.waitUntil(15_000) { compose.onAllNodesWithText(downloaded.title).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(downloaded.title).performClick()
            compose.waitUntil("download playing", 20_000) {
                compose.runOnUiThread {
                    player.currentContext?.navigationId == "downloads" && player.currentTrack?.id == downloaded.id &&
                        MusicManager.player.playbackState == Player.STATE_READY && MusicManager.player.isPlaying
                }
            }
            longPress(downloaded.title)
            menuTile(R.string.menu_go_artist).performClick()
            compose.runOnIdle {
                assertEquals("profile:${downloaded.ownerId}", player.navigateToPlaylistId)
                assertFalse(player.showMenuSheet)
            }
            longPress(downloaded.title)
            menuTile(R.string.menu_details).performClick()
            compose.runOnIdle {
                assertTrue(player.showDetailsSheet)
                assertFalse(player.isLocalDetailsMode)
                assertEquals(downloaded.id, player.selectedTrackForSheet?.id)
                player.showDetailsSheet = false
                showSearch = true
            }

            longPress(searchTitle)
            menuTile(R.string.menu_details).assertIsDisplayed()
            menuTile(R.string.menu_go_artist).assertIsDisplayed()
            shell("svc wifi disable")
            shell("svc data disable")
            compose.waitUntil("network disconnected", 20_000) { !NetworkUtils.hasValidatedInternet(activity) }
            menuTile(R.string.menu_go_artist).performClick()
            compose.onNodeWithText(activity.getString(R.string.home_offline_title)).assertIsDisplayed()
            compose.runOnIdle { assertTrue(player.showMenuSheet) }
            compose.onNodeWithText(activity.getString(R.string.btn_ok)).performClick()
            menuTile(R.string.menu_details).performClick()
            compose.onNodeWithText(activity.getString(R.string.home_offline_title)).assertIsDisplayed()
            compose.onNodeWithText(activity.getString(R.string.btn_ok)).performClick()

            compose.runOnIdle { player.showMenuSheet = false; showSearch = false }
            longPress(downloaded.title)
            menuTile(R.string.menu_details).performClick()
            compose.onNodeWithText(activity.getString(R.string.detail_file_info)).assertIsDisplayed()
            compose.runOnIdle {
                assertTrue(player.isLocalDetailsMode)
                assertEquals(wav.absolutePath, player.localFilePathForDetails)
                player.showDetailsSheet = false
                showSearch = true
            }

            longPress(searchTitle)
            restoreNetwork(wifiWasEnabled, mobileDataWasEnabled)
            compose.waitUntil("network reconnected", 30_000) { NetworkUtils.hasValidatedInternet(activity) }
            menuTile(R.string.menu_go_artist).performClick()
            compose.runOnIdle {
                assertEquals("profile:1110020", player.navigateToPlaylistId)
                assertFalse(player.showMenuSheet)
                assertEquals("downloads", player.currentContext?.navigationId)
            }
        } finally {
            restoreNetwork(wifiWasEnabled, mobileDataWasEnabled)
            compose.runOnUiThread { activity.viewModelStore.clear() }
            runBlocking { dao.deleteTrack(downloaded.id) }
            wav.delete()
        }
    }

    private fun menuTile(label: Int) = compose.activity.getString(label).let { text ->
        compose.onNodeWithTag("track-options-grid").performScrollToNode(hasText(text))
        compose.onNodeWithText(text)
    }

    private fun longPress(title: String) {
        compose.onNodeWithText(title).performTouchInput { longClick() }
    }

    private fun restoreNetwork(wifi: Boolean, mobileData: Boolean) {
        shell("svc wifi ${if (wifi) "enable" else "disable"}")
        shell("svc data ${if (mobileData) "enable" else "disable"}")
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText().trim() }

    private fun writeWav(file: File) {
        val dataSize = 8_000 * 2 * 90
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(8_000).putInt(16_000)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(dataSize).array()
        file.outputStream().use { it.write(header); it.write(ByteArray(dataSize)) }
    }
}
