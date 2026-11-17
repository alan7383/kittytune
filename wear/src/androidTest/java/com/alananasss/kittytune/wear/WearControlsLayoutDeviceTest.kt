package com.alananasss.kittytune.wear

import android.graphics.Bitmap
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.wear.compose.material3.AppScaffold
import com.alananasss.kittytune.wear.presentation.PlaybackScreen
import com.alananasss.kittytune.wear.presentation.WearPlaybackUiState
import com.alananasss.kittytune.wear.presentation.theme.KittyTuneWearTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class WearControlsLayoutDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun volumeButtonsHaveFullTargetsAndDoNotOverlapPlayback() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        var volume: Float? = null
        compose.setContent {
            KittyTuneWearTheme {
                AppScaffold {
                    PlaybackScreen(
                        uiState = WearPlaybackUiState(trackId = 1, title = "Layout test", artist = "Local fixture", volume = .5f, targetDevice = "Phone", isConnected = true),
                        onPlayPause = {}, onNext = {}, onPrevious = {},
                        onVolumeChange = { volume = it }
                    )
                }
            }
        }
        val decrease = compose.onNodeWithContentDescription(context.getString(R.string.volume_decrease, 5))
        val increase = compose.onNodeWithContentDescription(context.getString(R.string.volume_increase, 5))
        val play = compose.onNodeWithContentDescription(context.getString(R.string.action_play))
        for (button in listOf(decrease, increase, play)) {
            button.assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
        val minusBounds = decrease.fetchSemanticsNode().boundsInRoot
        val plusBounds = increase.fetchSemanticsNode().boundsInRoot
        val playBounds = play.fetchSemanticsNode().boundsInRoot
        assertTrue("Volume targets overlap", minusBounds.right <= plusBounds.left)
        assertTrue("Volume target overlaps playback", playBounds.bottom <= plusBounds.top)
        increase.performClick()
        compose.runOnIdle { assertEquals(.55f, volume ?: -1f, .001f) }
        decrease.performClick()
        compose.runOnIdle { assertEquals(.45f, volume ?: -1f, .001f) }
        instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
            File(context.getExternalFilesDir(null), "wear-layout.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
}
