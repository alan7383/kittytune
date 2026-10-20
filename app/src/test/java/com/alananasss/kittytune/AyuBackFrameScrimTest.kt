package com.alananasss.kittytune

import android.view.animation.Interpolator
import com.alananasss.kittytune.ui.navigation.AyuBackPhase
import com.alananasss.kittytune.ui.navigation.AyuBackState
import com.alananasss.kittytune.ui.navigation.AyuPredictiveBackGeometry
import com.alananasss.kittytune.ui.navigation.AyuScreenRole
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the scrim against the decompiled
 * `org.telegram.ui.ActionBar.ActionBarLayout.drawChild` (`vanilla-AyuGram-full-20261005.apk`).
 *
 * `drawChild` picks one of two branches for the foreground view:
 *
 *  - no predictive gesture (`!newBackTransitions()`): a FULL-screen scrim of
 *    `getScrimAlpha(dark)`, constant — `Color.argb((int) ((dark ? 0.8 : 0.2) * 255), 0, 0, 0)`.
 *    That is the forward-open spring and the back-press spring, where
 *    `springRouteBackgroundDrawable` is set and the fragment view is dropped when the transition
 *    ends. It must NOT fade.
 *  - predictive gesture (`newBackTransitions()`): `getScrimAlpha(dark)` times
 *    `clamp((width - innerTranslationX) / width, 0f, 0.8f)` with
 *    `innerTranslationX = predictiveBackAnimation.getSlideDistance()` —
 *    `interpolatedProgress * AndroidUtilities.dp(336)` — during the gesture, and
 *    `slideDistance + (width - slideDistance) * t` during the commit.
 *
 * So the dim on the screen underneath tracks the finger and fades out as the screen slides away,
 * instead of snapping to a constant value and only fading once the finger is lifted.
 *
 * The JVM tests stub Android's interpolators (`unitTests.isReturnDefaultValues`), so a linear
 * curve is injected to make the slide distance predictable: `dp(336)` = 924 and width = 1080.
 */
class AyuBackFrameScrimTest {

    private val linearGesture = Interpolator { input -> input }

    private fun state(): AyuBackState = AyuBackState(
        2.75f,
        AyuPredictiveBackGeometry(2.75f, gesture = linearGesture)
    )

    private fun gesturing(progress: Float): AyuBackState = state().apply {
        updateSize(1080, 2340, 84f)
        onGestureStart(
            touchX = 100f,
            touchY = 1000f,
            progress = progress,
            closingEntryId = "c",
            enteringEntryId = "e"
        )
    }

    @Test
    fun gestureScrimFollowsTheSlideDistance() {
        // AyuGram int math: (int)(0.8 * 255) = 204 -> (int)(204 * factor) / 255.
        val cases = mapOf(
            0.3f to 151f, // slide 277 -> factor 0.743 -> 204 * 0.743 = 151
            0.6f to 99f,  // slide 554 -> factor 0.487 -> 99
            1.0f to 29f   // slide 924 -> factor 0.144 -> 29
        )
        for ((progress, expected8Bit) in cases) {
            val state = gesturing(progress)
            assertEquals(
                "dark scrim@p=$progress",
                expected8Bit / 255f,
                state.frame(AyuScreenRole.Entering, isDark = true).scrimAlpha,
                1e-6f
            )
            // (int)(0.2 * 255) = 51 -> e.g. (int)(51 * 0.743) = 37.
            assertEquals(
                "light scrim@p=$progress",
                (51f * (expected8Bit / 204f)).toInt() / 255f,
                state.frame(AyuScreenRole.Entering, isDark = false).scrimAlpha,
                1e-6f
            )
            assertEquals(
                "the closing screen in hand draws no scrim of its own@p=$progress",
                0f,
                state.frame(AyuScreenRole.Closing, isDark = true).scrimAlpha,
                0f
            )
        }
    }

    @Test
    fun gestureScrimFadesOutAsTheGestureRuns() {
        val start = gesturing(0.3f).frame(AyuScreenRole.Entering, isDark = true).scrimAlpha
        val end = gesturing(1f).frame(AyuScreenRole.Entering, isDark = true).scrimAlpha
        assertEquals("more slide, less dim", true, start > end)
    }

    @Test
    fun gestureScrimIsZeroAtZeroProgress() {
        // `predictiveBackHasProgress` is false at exactly 0, so no branch draws a scrim.
        val state = gesturing(0f)
        assertEquals(
            0f,
            state.frame(AyuScreenRole.Entering, isDark = true).scrimAlpha,
            0f
        )
    }

    @Test
    fun commitScrimFadesOutWithTheSlideAndTheMultiplier() {
        // The commit captures the slide it starts from (924 here) and keeps pushing it towards
        // the full width, while `updateCommitProgress` drives the multiplier to 0.
        val state = gesturing(1f)
        state.onPop(closingEntryId = "c", enteringEntryId = "e")
        assertEquals(AyuBackPhase.Commit, state.phase)
        state.setProgressInternal(0.5f)
        // inner = 924 + (1080 - 924) * 0.5 = 1002 -> factor 0.072, multiplier 0.5
        // -> base = (int)(204 * 0.5) = 102 -> (int)(102 * 0.072) = 7.
        assertEquals(
            7f / 255f,
            state.frame(AyuScreenRole.Entering, isDark = true).scrimAlpha,
            1e-6f
        )
        state.setProgressInternal(1f)
        assertEquals(0f, state.frame(AyuScreenRole.Entering, isDark = true).scrimAlpha, 0f)
    }

    @Test
    fun cancelScrimFollowsProgressOverCancelFrom() {
        val state = gesturing(0.6f)
        state.onCancel()
        assertEquals(AyuBackPhase.Cancel, state.phase)
        state.setProgressInternal(0.3f)
        // multiplier = 0.3 / 0.6 = 0.5, slide = 0.3 * 924 = 277 -> factor 0.743
        // -> base = 102 -> (int)(102 * 0.743) = 75.
        assertEquals(
            75f / 255f,
            state.frame(AyuScreenRole.Entering, isDark = true).scrimAlpha,
            1e-6f
        )
    }

    @Test
    fun springScrimFadesOutOnTheRevealedScreen() {
        // A back brings the revealed screen to the front (`bringChildToFront(containerView)`), so
        // `drawChild`'s full-screen `getScrimAlpha(dark)` on the closing screen never shows.
        // The dim you actually see is the second scrim, drawn on the revealed screen:
        // (int)(102 * clamp((width - innerTranslationX) / width, 0, 0.8)) / 255, with
        // innerTranslationX = width * p. Not themed: the same faint veil on light and dark.
        val state = state().apply {
            updateSize(1080, 2340, 84f)
            onPop(closingEntryId = "c", enteringEntryId = "e")
        }
        val cases = mapOf(
            0f to 81f,   // factor clamp(1, 0, 0.8) = 0.8 -> 102 * 0.8 = 81   (32%)
            250f to 76f, // p 0.25 -> innerTX 270 -> factor 0.75 -> 76
            500f to 51f, // p 0.5  -> innerTX 540 -> factor 0.5  -> 51
            750f to 25f, // p 0.75 -> innerTX 810 -> factor 0.25 -> 25
            1000f to 0f
        )
        for ((progress, expected8Bit) in cases) {
            state.setProgressInternal(progress)
            assertEquals(
                "revealed scrim@p=$progress",
                expected8Bit / 255f,
                state.frame(AyuScreenRole.Entering, isDark = true).scrimAlpha,
                1e-6f
            )
            assertEquals(
                "revealed scrim, light theme@p=$progress",
                expected8Bit / 255f,
                state.frame(AyuScreenRole.Entering, isDark = false).scrimAlpha,
                1e-6f
            )
            // The closing screen's own scrim is painted over by the revealed screen.
            assertEquals(
                "closing scrim@p=$progress",
                0f,
                state.frame(AyuScreenRole.Closing, isDark = true).scrimAlpha,
                0f
            )
        }
    }

    @Test
    fun springScrimFadesMonotonicallyToZero() {
        val state = state().apply {
            updateSize(1080, 2340, 84f)
            onPop(closingEntryId = "c", enteringEntryId = "e")
        }
        var previous = Float.MAX_VALUE
        for (p in 0..1000 step 50) {
            state.setProgressInternal(p.toFloat())
            val alpha = state.frame(AyuScreenRole.Entering, isDark = true).scrimAlpha
            assertEquals("dim only ever fades", true, alpha <= previous)
            previous = alpha
        }
        assertEquals(0f, previous, 0f)
    }

    @Test
    fun pushScrimDimsTheScreenUnderneath() {
        val state = state().apply {
            updateSize(1080, 2340, 84f)
            onPush(enteringId = "n", exitingId = "o")
        }
        for (p in floatArrayOf(0f, 500f, 1000f)) {
            state.setProgressInternal(p)
            // The entering foreground screen has no scrim over it.
            assertEquals(
                0f,
                state.frame(AyuScreenRole.PushEntering, isDark = true).scrimAlpha,
                0f
            )
            // The exiting screen underneath is dimmed smoothly: base * clamp(q, 0, 0.8).
            val q = (p / 1000f).coerceIn(0f, 1f)
            val expectedFactor = q.coerceIn(0f, 0.8f)
            assertEquals(
                "dark scrim on exiting@p=$p",
                0.8f * expectedFactor,
                state.frame(AyuScreenRole.PushExiting, isDark = true).scrimAlpha,
                1e-6f
            )
            assertEquals(
                "light scrim on exiting@p=$p",
                0.2f * expectedFactor,
                state.frame(AyuScreenRole.PushExiting, isDark = false).scrimAlpha,
                1e-6f
            )
        }
    }
}
