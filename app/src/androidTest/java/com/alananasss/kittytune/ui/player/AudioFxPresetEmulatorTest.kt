package com.alananasss.kittytune.ui.player

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alananasss.kittytune.MainActivity
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.data.local.PlayerPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioFxPresetEmulatorTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun savesRenamesTogglesAndDeletesPresetsAndTurnsEffectsOnFromTheList() {
        val activity = compose.activity
        val name = "Late night ${System.nanoTime() % 100_000}"
        val renamed = "$name (2)"
        val prefs = PlayerPreferences(activity)
        val previousActivation = prefs.getAudioFxPresetActivation()
        val previousPins = prefs.getPinnedAudioFx()
        lateinit var player: PlayerViewModel
        lateinit var previous: AudioFxPreset
        lateinit var expected: AudioFxPreset
        lateinit var beforePreset: AudioFxPreset
        var warningWasSeen = false

        compose.runOnUiThread {
            player = ViewModelProvider(activity)[PlayerViewModel::class.java]
            previous = AudioFxPreset(player.effectsState, player.equalizerState)
            warningWasSeen = player.hasSeenEarrapeWarning()
            player.setHasSeenEarrapeWarning(true)
            player.resetAllEffects()
            player.setCustomSpeed(0.9f)
            player.togglePitchEnabled(true)
            player.toggleBassBoost()
            player.setBassBoostIntensity(0.2f)
            if (!player.equalizerState.isEnabled) player.toggleEqualizer()
            player.setEqualizerBand(2, 3f)
            expected = player.currentAudioFxPreset.portable()

            activity.setContent {
                MaterialTheme { AudioControlDock(player) }
            }
        }

        try {
            // Save: the name is checked as it is typed, and the saved preset is what plays now.
            compose.onNodeWithTag("audio-fx-save-preset").performScrollTo().performClick()
            val save = compose.onNodeWithTag("audio-fx-name-confirm")
            save.assertIsNotEnabled()
            compose.onNode(hasSetTextAction()).performTextInput("  $name  ")
            save.performClick()

            val card = compose.onNodeWithTag("audio-fx-preset-$name")
            card.performScrollTo().assertIsDisplayed()
            card.assertIsSelected()
            assertEquals(expected, prefs.getAudioFxPresets()[name])
            compose.runOnUiThread { assertFalse(player.saveAudioFxPreset(name.uppercase(), expected)) }

            // Off goes back to no effects (nothing played before it), on brings the sound back exactly.
            card.performClick()
            card.assertIsNotSelected()
            compose.runOnIdle {
                assertFalse(player.effectsState.isBassBoostEnabled)
                assertFalse(player.equalizerState.isEnabled)
                assertEquals(1f, player.effectsState.speed, 0.001f)
                beforePreset = player.currentAudioFxPreset
            }
            card.performClick()
            card.assertIsSelected()
            compose.runOnIdle {
                assertEquals(expected, player.currentAudioFxPreset.portable())
                assertEquals(beforePreset, prefs.getAudioFxPresetActivation()?.previous)
                assertEquals(0.9f, player.player.playbackParameters.speed, 0.001f)
            }

            // Rename from the card's menu: it stays active and keeps what to go back to.
            card.performTouchInput { longClick() }
            compose.onNodeWithText(activity.getString(R.string.audio_fx_rename)).performClick()
            compose.onNode(hasSetTextAction()).performTextReplacement(renamed)
            compose.onNodeWithTag("audio-fx-name-confirm").performClick()
            val renamedCard = compose.onNodeWithTag("audio-fx-preset-$renamed")
            renamedCard.performScrollTo().assertIsSelected()
            compose.runOnIdle {
                assertFalse(prefs.getAudioFxPresets().containsKey(name))
                assertEquals(renamed, prefs.getAudioFxPresetActivation()?.name)
            }

            // The full list turns an effect on where it is, and taking it off the grid keeps it playing.
            compose.onNodeWithTag("audio-fx-explore").performScrollTo().performClick()
            compose.onNodeWithTag("audio-fx-row-reverb").performScrollTo().performClick()
            compose.runOnIdle {
                assertTrue(player.effectsState.isReverbEnabled)
                assertNull(player.activeAudioFxPresetName)
            }
            val wasPinned = player.isAudioFxPinned("reverb")
            compose.onNodeWithTag("audio-fx-pin-reverb").performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(!wasPinned, player.isAudioFxPinned("reverb"))
                assertTrue(player.effectsState.isReverbEnabled)
            }
            compose.onNodeWithTag("audio-fx-filter-active").performScrollTo().performClick()
            compose.onNodeWithTag("audio-fx-row-reverb").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("audio-fx-switch-reverb").performClick()
            compose.runOnIdle { assertFalse(player.effectsState.isReverbEnabled) }
            compose.runOnUiThread { activity.setContent { MaterialTheme { AudioControlDock(player) } } }

            // Delete: the sound stays as it is.
            compose.onNodeWithTag("audio-fx-preset-$renamed").performScrollTo().performTouchInput { longClick() }
            compose.onNodeWithText(activity.getString(R.string.btn_delete)).performClick()
            compose.onNodeWithText(activity.getString(R.string.audio_fx_delete_preset)).assertIsDisplayed()
            compose.onNodeWithText(activity.getString(R.string.btn_delete)).performClick()
            compose.onNodeWithTag("audio-fx-preset-$renamed").assertDoesNotExist()
            assertFalse(prefs.getAudioFxPresets().containsKey(renamed))
        } finally {
            compose.runOnUiThread {
                player.deleteAudioFxPreset(name)
                player.deleteAudioFxPreset(renamed)
                player.setHasSeenEarrapeWarning(warningWasSeen)
                player.effectsState = previous.effects
                player.equalizerState = previous.equalizer
                MusicManager.applyEffects(previous.effects)
                MusicManager.applyEqualizer(previous.equalizer)
                prefs.saveEffects(previous.effects)
                prefs.saveEqualizerState(previous.equalizer)
                prefs.saveAudioFxPresetActivation(previousActivation)
                prefs.setPinnedAudioFx(previousPins)
            }
        }
    }
}
