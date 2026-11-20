package com.alananasss.kittytune.data

import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alananasss.kittytune.MainActivity
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.player.PlayerViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackQueueIssue139DeviceTest {
    @get:Rule val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test fun anUnavailableTrackStaysSelectedAndDoesNotAdvanceTheQueue() {
        val unavailable = Track(-139_001, "Unavailable test track", null, 180_000,
            User(1, "Test artist", null), source = "local")
        val following = unavailable.copy(id = -139_002, title = "Following track")
        lateinit var viewModel: PlayerViewModel

        activityRule.scenario.onActivity { activity ->
            viewModel = ViewModelProvider(activity)[PlayerViewModel::class.java]
            viewModel.playPlaylist(listOf(unavailable, following), startIndex = 0)
        }
        val deadline = System.currentTimeMillis() + 20_000L
        var stopped = false
        while (!stopped && System.currentTimeMillis() < deadline) {
            activityRule.scenario.onActivity {
                stopped = viewModel.currentTrack?.id == unavailable.id &&
                    !viewModel.isLoading && !MusicManager.isLoadingTrack && !MusicManager.playWhenReadyRequested
            }
            if (!stopped) Thread.sleep(100L)
        }
        assertTrue("The failed track should stay selected with playback stopped", stopped)
        activityRule.scenario.onActivity {
            assertEquals(unavailable.id, viewModel.currentTrack?.id)
            assertFalse(MusicManager.player.isPlaying)
        }
    }
}
