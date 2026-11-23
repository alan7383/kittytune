package com.alananasss.kittytune.ui.player

import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alananasss.kittytune.MainActivity
import com.alananasss.kittytune.R
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.theme.SoundTuneTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioFxExportEmulatorTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun screenshot(name: String) {
        Thread.sleep(800)
        // Through the shell: the app's own files go with it when the test APK is uninstalled.
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /data/local/tmp/$name")
            .let { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).use { out -> out.readBytes() } }
    }

    @Test
    fun theExportCardSummarisesTheSoundAndOpensTheSheet() {
        val activity = compose.activity
        lateinit var player: PlayerViewModel
        lateinit var previous: AudioFxPreset
        var hadPresets = false
        compose.runOnUiThread {
            player = ViewModelProvider(activity)[PlayerViewModel::class.java]
            previous = AudioFxPreset(player.effectsState, player.equalizerState)
            hadPresets = player.audioFxPresets.isNotEmpty()
            player.setHasSeenEarrapeWarning(true)
            player.resetAllEffects()
            player.setCustomSpeed(0.9f)
            player.togglePitchEnabled(true)
            player.toggleBassBoost(); player.setBassBoostIntensity(0.2f)
            player.toggleEarrape(); player.setEarrapeIntensity(0.1f)
            player.currentTrack = Track(id = 42, title = "Jester", artworkUrl = null, durationMs = 141_000, user = User(1, "overtonight", null))
            activity.setContent { SoundTuneTheme { AudioControlDock(player) } }
        }
        try {
            if (!hadPresets) {
                // Nothing saved: no section header taking room, the Save button still at the bottom.
                assertEquals(0, compose.onAllNodes(androidx.compose.ui.test.hasText(activity.getString(R.string.audio_fx_saved_presets))).fetchSemanticsNodes().size)
            }
            compose.onNodeWithText(activity.getString(R.string.audio_fx_save_preset)).performScrollTo()
            val card = compose.onNodeWithText(activity.getString(R.string.fx_export_title))
            card.performScrollTo()
            screenshot("fx-export-panel.png")
            card.performClick()
            compose.onNodeWithText(activity.getString(R.string.fx_export_start)).assertExists()
            screenshot("fx-export-sheet.png")
            val flow = com.alananasss.kittytune.audio.AudioFxExporter::class.java.getDeclaredField("_state").apply { isAccessible = true }
                .get(com.alananasss.kittytune.audio.AudioFxExporter) as kotlinx.coroutines.flow.MutableStateFlow<com.alananasss.kittytune.audio.AudioFxExporter.State>
            try {
                compose.runOnUiThread { flow.value = com.alananasss.kittytune.audio.AudioFxExporter.State.Running("Jester", 0.42f) }
                compose.mainClock.advanceTimeBy(1500)
                screenshot("fx-export-running.png")
                compose.runOnUiThread {
                    flow.value = com.alananasss.kittytune.audio.AudioFxExporter.State.Done(android.net.Uri.parse("content://media/1"), "overtonight - Jester (FX).m4a")
                }
                compose.mainClock.advanceTimeBy(1500)
                screenshot("fx-export-done.png")
            } finally {
                compose.runOnUiThread { flow.value = com.alananasss.kittytune.audio.AudioFxExporter.State.Idle }
            }
        } finally {
            compose.runOnUiThread {
                player.effectsState = previous.effects
                player.equalizerState = previous.equalizer
                com.alananasss.kittytune.data.MusicManager.applyEffects(previous.effects)
            }
        }
    }
}
