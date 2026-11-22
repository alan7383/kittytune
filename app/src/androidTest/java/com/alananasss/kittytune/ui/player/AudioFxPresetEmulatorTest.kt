package com.alananasss.kittytune.ui.player

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioFxPresetEmulatorTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun savesNamedPresetAndRestoresPlaybackSettingsFromAudioFx() {
        val activity = compose.activity
        val name = "Late night ${System.nanoTime()}"
        val prefs = PlayerPreferences(activity)
        val previousActivation = prefs.getAudioFxPresetActivation()
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
            player.toggleEarrape()
            player.setEarrapeIntensity(0.1f)
            player.toggleBassBoost()
            player.setBassBoostIntensity(0.2f)
            if (!player.equalizerState.isEnabled) player.toggleEqualizer()
            player.setEqualizerBand(2, 3f)
            expected = AudioFxPreset(player.effectsState, player.equalizerState)

            activity.setContent {
                MaterialTheme { AudioControlDock(player) }
            }
        }

        try {
            compose.onNodeWithText(activity.getString(R.string.audio_fx_save_preset)).performClick()
            val save = compose.onNodeWithText(activity.getString(R.string.btn_save))
            save.assertIsNotEnabled()
            compose.onNode(hasSetTextAction()).performTextInput("  $name  ")
            save.performClick()

            val presetTile = compose.onNode(hasText(name) and hasAnyAncestor(hasTestTag("audio-fx-grid")))
            presetTile.assertIsDisplayed()
            assertEquals(expected, prefs.getAudioFxPresets()[name])
            compose.runOnUiThread {
                assertFalse(player.saveAudioFxPreset(name.uppercase(), expected))
                player.resetAllEffects()
                player.setCustomSpeed(1f)
                player.togglePitchEnabled(false)
                player.toggleEqualizer()
                beforePreset = player.currentAudioFxPreset
            }

            presetTile.performClick()
            presetTile.assertIsSelected()
            compose.runOnIdle {
                assertEquals(expected.effects, player.effectsState)
                assertEquals(expected.equalizer, player.equalizerState)
                assertEquals(beforePreset, prefs.getAudioFxPresetActivation()?.previous)
                assertEquals(0.9f, player.player.playbackParameters.speed, 0.001f)
                assertEquals(0.9f, player.player.playbackParameters.pitch, 0.001f)
            }

            presetTile.performClick()
            presetTile.assertIsNotSelected()
            compose.runOnIdle {
                assertEquals(beforePreset, player.currentAudioFxPreset)
                assertEquals(1f, player.player.playbackParameters.speed, 0.001f)
                assertEquals(1f, player.player.playbackParameters.pitch, 0.001f)
                assertNull(prefs.getAudioFxPresetActivation())
            }

            presetTile.performTouchInput { longClick() }
            compose.onNodeWithText(activity.getString(R.string.audio_fx_delete_preset)).assertIsDisplayed()
            compose.onNodeWithText(activity.getString(R.string.btn_delete)).performClick()
            presetTile.assertDoesNotExist()
            assertFalse(prefs.getAudioFxPresets().containsKey(name))
        } finally {
            compose.runOnUiThread {
                player.deleteAudioFxPreset(name)
                player.setHasSeenEarrapeWarning(warningWasSeen)
                player.effectsState = previous.effects
                player.equalizerState = previous.equalizer
                MusicManager.applyEffects(previous.effects)
                MusicManager.applyEqualizer(previous.equalizer)
                prefs.saveEffects(previous.effects)
                prefs.saveEqualizerState(previous.equalizer)
                prefs.saveAudioFxPresetActivation(previousActivation)
            }
        }
    }
}
