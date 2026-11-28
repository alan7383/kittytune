package com.alananasss.kittytune.ui.player

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alananasss.kittytune.MainActivity
import com.alananasss.kittytune.R
import com.alananasss.kittytune.audio.ai.AiDetectionManager
import com.alananasss.kittytune.data.local.AppleMusicPortraitArt
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.local.TrackSourceBadgeStyle
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.player.apple.AppleMusicPlayerScreen
import com.alananasss.kittytune.ui.profile.AppearanceSettingsScreen
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppleArtworkIssue115EmulatorTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun squareCoverIsVisibleAndArtworkFreeModeKeepsTheBadges() {
        val activity = compose.activity
        val prefs = PlayerPreferences(activity)
        val previousArtwork = prefs.getAppleMusicPortraitArt()
        val previousSource = prefs.getFullPlayerSourceIndicatorEnabled()
        val previousBadgeStyle = prefs.getTrackSourceBadgeStyle()
        val previousAiBadge = prefs.aiShowBadge
        @Suppress("UNCHECKED_CAST")
        val detection = AiDetectionManager::class.java.getDeclaredField("_result")
            .apply { isAccessible = true }
            .get(AiDetectionManager) as MutableStateFlow<AiDetectionManager.Result?>
        val previousDetection = detection.value
        val fixture = Track(
            id = 115_001L,
            title = "Issue 115 artwork",
            artworkUrl = "android.resource://android/${android.R.drawable.ic_media_play}",
            durationMs = 187_000L,
            user = User(115L, "Issue 115 artist", null),
            source = "soundcloud"
        )
        lateinit var player: PlayerViewModel
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnUiThread {
                prefs.setAppleMusicPortraitArt(AppleMusicPortraitArt.SQUARE)
                prefs.setFullPlayerSourceIndicatorEnabled(true)
                prefs.setTrackSourceBadgeStyle(TrackSourceBadgeStyle.TEXT_ONLY)
                prefs.aiShowBadge = true
                detection.value = AiDetectionManager.Result(0.92f, true, AiDetectionManager.Status.DONE)
                player = ViewModelProvider(activity)[PlayerViewModel::class.java]
                player.uiCurrentTrack = fixture
                player.uiDuration = 187_000L
                player.currentStreamSource = "soundcloud"
                activity.setContent {
                    MaterialTheme { AppleMusicPlayerScreen(player, onClose = {}) }
                }
            }
            val cover = compose.onNodeWithContentDescription(fixture.title!!, useUnmergedTree = true)
            val sourceBadge = compose.onNodeWithText("SoundCloud", useUnmergedTree = true)
            val aiBadge = compose.onNodeWithText(activity.getString(R.string.ai_badge_ai_tag, 92))
            compose.mainClock.advanceTimeBy(500)
            cover.assertIsDisplayed()
            sourceBadge.assertIsDisplayed()
            aiBadge.assertIsDisplayed()

            compose.runOnUiThread { prefs.setAppleMusicPortraitArt(AppleMusicPortraitArt.NO_ARTWORK) }
            compose.mainClock.advanceTimeBy(500)
            cover.assertDoesNotExist()
            sourceBadge.assertIsDisplayed()
            aiBadge.assertIsDisplayed().performClick()
            compose.runOnIdle { assertTrue(player.showAiDetectionSheet) }
        } finally {
            compose.runOnUiThread {
                detection.value = previousDetection
                prefs.setAppleMusicPortraitArt(previousArtwork)
                prefs.setFullPlayerSourceIndicatorEnabled(previousSource)
                prefs.setTrackSourceBadgeStyle(previousBadgeStyle)
                prefs.aiShowBadge = previousAiBadge
            }
        }
    }

    @Test fun appearanceExposesBothGyroscopePreferences() {
        val activity = compose.activity
        val prefs = PlayerPreferences(activity)
        val wasEnabled = prefs.getGyroscopeCoversEnabled()
        val wasFlat = prefs.getGyroscopeFlatCover()
        try {
            compose.runOnUiThread {
                prefs.setGyroscopeCoversEnabled(false)
                prefs.setGyroscopeFlatCover(false)
                activity.setContent {
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
            val gyroscope = activity.getString(R.string.pref_gyroscope_covers)
            val flatCover = activity.getString(R.string.pref_gyro_flat_cover)
            val settingsList = compose.onNode(
                hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
            )
            settingsList.performScrollToNode(hasText(gyroscope))
            compose.onNodeWithText(flatCover).assertDoesNotExist()
            compose.onNodeWithText(gyroscope).assertIsDisplayed().performClick()
            compose.waitUntil(5_000) { prefs.getGyroscopeCoversEnabled() }
            settingsList.performScrollToNode(hasText(flatCover))
            compose.onNodeWithText(flatCover).assertIsDisplayed().performClick()
            compose.waitUntil(5_000) { prefs.getGyroscopeFlatCover() }
        } finally {
            prefs.setGyroscopeCoversEnabled(wasEnabled)
            prefs.setGyroscopeFlatCover(wasFlat)
        }
    }
}
