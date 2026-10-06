package com.alananasss.kittytune

import com.alananasss.kittytune.ui.navigation.AyuBackPhase
import com.alananasss.kittytune.ui.navigation.AyuBackState
import com.alananasss.kittytune.ui.navigation.AyuScreenRole
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the predictive-back scrim against the decompiled
 * `org.telegram.ui.ActionBar.ActionBarLayout.drawChild` (`vanilla-AyuGram-full-20261005.apk`):
 * while `newBackTransitions` holds (gesture, cancel, commit) the foreground screen draws a
 * FULL-screen scrim of `getScrimAlpha(dark)` — the `(width - innerTX) / width` factor belongs
 * to the spring-path branch only. So the screen behind stays dimmed at 0.8 (dark) / 0.2 (light)
 * for the whole gesture instead of fading back to fully visible as the slide grows.
 */
class AyuBackFrameScrimTest {

    private fun gesturing(progress: Float): AyuBackState {
        val state = AyuBackState(2.75f)
        state.updateSize(1080, 2340, 84f)
        state.onGestureStart(
            touchX = 100f,
            touchY = 1000f,
            progress = progress,
            closingEntryId = "c",
            enteringEntryId = "e"
        )
        return state
    }

    @Test
    fun gestureScrimStaysFullWhateverTheSlide() {
        for (progress in floatArrayOf(0.1f, 0.5f, 0.9f, 1f)) {
            val state = gesturing(progress)
            // AyuGram int math: (int)(0.8 * 255 * 1) = 204 -> 204/255.
            assertEquals(
                "dark scrim@p=$progress",
                204f / 255f,
                state.frame(AyuScreenRole.Closing, isDark = true).scrimAlpha,
                1e-6f
            )
            // (int)(0.2 * 255) = 51 -> 51/255.
            assertEquals(
                "light scrim@p=$progress",
                51f / 255f,
                state.frame(AyuScreenRole.Closing, isDark = false).scrimAlpha,
                1e-6f
            )
            assertEquals(
                0f,
                state.frame(AyuScreenRole.Entering, isDark = true).scrimAlpha,
                0f
            )
        }
    }

    @Test
    fun gestureScrimIsZeroAtZeroProgress() {
        // `predictiveBackHasProgress` is false at exactly 0, so no branch draws a scrim.
        val state = AyuBackState(2.75f)
        state.updateSize(1080, 2340, 84f)
        state.onGestureStart(100f, 1000f, 0f, "c", "e")
        assertEquals(
            0f,
            state.frame(AyuScreenRole.Closing, isDark = true).scrimAlpha,
            0f
        )
    }

    @Test
    fun commitScrimFadesWithOneMinusT() {
        val state = gesturing(1f)
        state.onPop(closingEntryId = "c", enteringEntryId = "e")
        state.setProgressInternal(0.5f)
        // (int)(0.8 * 255 * 0.5) = (int)102.0 = 102.
        assertEquals(
            102f / 255f,
            state.frame(AyuScreenRole.Closing, isDark = true).scrimAlpha,
            1e-6f
        )
    }

    @Test
    fun cancelScrimFollowsProgressOverCancelFrom() {
        val state = gesturing(0.6f)
        state.onCancel()
        state.setProgressInternal(0.3f)
        // multiplier = 0.3 / 0.6 = 0.5 -> (int)(0.8 * 255 * 0.5) = 102.
        assertEquals(
            102f / 255f,
            state.frame(AyuScreenRole.Closing, isDark = true).scrimAlpha,
            1e-6f
        )
    }
}
