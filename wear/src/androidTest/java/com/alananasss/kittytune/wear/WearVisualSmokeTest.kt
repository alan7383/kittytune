package com.alananasss.kittytune.wear

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.wear.compose.material3.AppScaffold
import com.alananasss.kittytune.wear.R
import com.alananasss.kittytune.wear.common.TargetDeviceInfo
import com.alananasss.kittytune.wear.presentation.PlaybackScreen
import com.alananasss.kittytune.wear.presentation.WearPlaybackUiState
import com.alananasss.kittytune.wear.presentation.theme.KittyTuneWearTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class WearVisualSmokeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun showsTrackControlsAndDevicePicker() {
        val phone = TargetDeviceInfo("phone", "Phone", type = "phone")
        val studio = TargetDeviceInfo("studio", "Studio Desktop")
        val laptop = TargetDeviceInfo("laptop", "Laptop")
        val picker = mutableStateOf(false)
        val state = mutableStateOf(WearPlaybackUiState(
            trackId = 10, title = "Night Drive", artist = "Test Artist",
            isPlaying = true, positionMs = 49_000, durationMs = 191_000, volume = 0.65f,
            selectedTarget = phone, availableTargets = listOf(phone, studio, laptop),
        ))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            KittyTuneWearTheme {
                AppScaffold {
                    PlaybackScreen(
                        uiState = state.value.copy(isTargetPickerVisible = picker.value),
                        onPlayPause = {}, onNext = {}, onPrevious = {},
                        onVolumeChange = { state.value = state.value.copy(volume = it) },
                        onTargetClick = { picker.value = true },
                    )
                }
            }
        }
        compose.onNodeWithText("Night Drive").assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.action_pause)).assertIsDisplayed()
        compose.onNodeWithText("65%").assertIsDisplayed()
        save("wear-audit-playing.png")
        compose.onNodeWithContentDescription(context.getString(R.string.action_volume_up))
            .performTouchInput { click() }
        compose.onNodeWithText("70%").assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.action_volume_down))
            .performTouchInput { click() }
        compose.onNodeWithText("65%").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(volume = 1f) }
        compose.onNodeWithText("100%").assertIsDisplayed()
        compose.onNodeWithText("Phone", substring = true).performTouchInput { click() }
        compose.onNodeWithText("Studio Desktop").assertIsDisplayed()
        save("wear-audit-picker.png")
    }

    private fun save(name: String) {
        if (InstrumentationRegistry.getArguments().getString("wearScreenshots") != "true") return
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
