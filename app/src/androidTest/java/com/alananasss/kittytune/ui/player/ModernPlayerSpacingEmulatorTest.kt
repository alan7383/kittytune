package com.alananasss.kittytune.ui.player

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alananasss.kittytune.MainActivity
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.local.PlayerProgressMode
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModernPlayerSpacingEmulatorTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun progressStaysNearTrackDetailsOnATallScreen() =
        checkLayout(PlayerProgressMode.CLASSIC_BAR, compact = false)

    @Test fun waveformAndControlsFitOnACompactScreen() =
        checkLayout(PlayerProgressMode.HYBRID_WAVEFORM, compact = true)

    private fun checkLayout(mode: PlayerProgressMode, compact: Boolean) {
        compose.mainClock.autoAdvance = false
        val activity = compose.activity
        val prefs = PlayerPreferences(activity)
        val previousMode = prefs.getPlayerProgressMode()
        try {
            prefs.setPlayerProgressMode(mode)
            compose.runOnUiThread {
                val vm = ViewModelProvider(activity)[PlayerViewModel::class.java]
                val track = Track(-88_001L, "Spacing fixture", null, 187_000L, User(-88L, "Spacing artist", null), source = "local")
                vm.uiCurrentTrack = track
                vm.uiDuration = 187_000L
                vm.uiQueueState = listOf(track)
                vm.uiCurrentQueueIndex = 0
                activity.setContent {
                    MaterialTheme {
                        Box(if (compact) Modifier.fillMaxWidth().height(650.dp) else Modifier) {
                            NewPlayerScreen(vm, onClose = {}, forceSoundCloud = false)
                        }
                    }
                }
            }

            val artist = compose.onNodeWithText("Spacing artist")
            val duration = compose.onNodeWithText("03:07", substring = true)
            compose.mainClock.advanceTimeBy(500)
            artist.assertIsDisplayed()
            duration.assertIsDisplayed()
            val controls = compose.onNode(
                hasClickAction() and
                    (hasText(activity.getString(R.string.action_play)) or hasText(activity.getString(R.string.action_pause))),
            )
            controls.assertIsDisplayed()

            val artistBounds = artist.getUnclippedBoundsInRoot()
            val durationBounds = duration.getUnclippedBoundsInRoot()
            val gap = durationBounds.top - artistBounds.bottom
            assertTrue("The progress section must follow the track details (gap: $gap)", gap >= 0.dp)
            if (mode == PlayerProgressMode.CLASSIC_BAR) {
                assertTrue("The slider and its labels should stay close to the track details (gap: $gap)", gap <= 96.dp)
            }
        } finally {
            prefs.setPlayerProgressMode(previousMode)
        }
    }
}
