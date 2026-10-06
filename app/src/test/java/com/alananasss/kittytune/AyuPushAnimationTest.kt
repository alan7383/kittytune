package com.alananasss.kittytune

import com.alananasss.kittytune.ui.navigation.AyuBack
import com.alananasss.kittytune.ui.navigation.AyuBackPhase
import com.alananasss.kittytune.ui.navigation.AyuBackState
import com.alananasss.kittytune.ui.navigation.AyuScreenRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the forward-open port against the decompiled
 * `org.telegram.ui.ActionBar.ActionBarLayout` (`vanilla-AyuGram-full-20261005.apk`):
 * with `TransitionAnimation.SPRING` (the default) one shared 0..1000 spring
 * (`setStiffness(900)`, `setDampingRatio(1)`) drives `applySpringProgress(layout, opening)`.
 *
 * Reference formulas, all from `applySpringProgress` + `drawChild`:
 * - entering: `translationX = (1 - p) * width`, `scale = lerp(0.85, 1, p)`
 * - exiting: `translationX = -p * 0.35 * width`, scale untouched (`1`)
 * - scrim under the entering screen: `(int) (getScrimAlpha(dark) * clamp(p, 0, 0.8)) / 255`
 *   with the helper multiplier always `1` here (`reset()` runs after every predictive animation)
 * - both screens clipped to the cached (device) corner radii on every API level
 */
private const val PUSH_DENSITY = 2.75f
private const val PUSH_W = 1080f
private const val PUSH_H = 2340f
private const val PUSH_CORNERS = 84f

class AyuPushAnimationTest {

    private fun pushed(phase: (AyuBackState) -> Unit = {}): AyuBackState {
        val state = AyuBackState(PUSH_DENSITY)
        state.updateSize(1080, 2340, PUSH_CORNERS)
        state.onPush(enteringId = "new", exitingId = "old")
        phase(state)
        return state
    }

    @Test
    fun pushBindsBothRoles() {
        val state = pushed()
        assertEquals(AyuBackPhase.Push, state.phase)
        assertEquals(AyuScreenRole.PushEntering, state.roleFor("new"))
        assertEquals(AyuScreenRole.PushExiting, state.roleFor("old"))
        assertEquals(AyuScreenRole.None, state.roleFor("other"))
    }

    @Test
    fun pushStartFrameMatchesApplySpringProgress() {
        val state = pushed { it.setProgressInternal(0f) }

        val entering = state.frame(AyuScreenRole.PushEntering, isDark = true)
        assertEquals("entering TX starts off-screen right", PUSH_W, entering.translationX, 1e-3f)
        assertEquals(0f, entering.translationY, 0f)
        assertEquals(0.85f, entering.scale, 1e-5f)
        assertEquals(PUSH_CORNERS / 0.85f, entering.cornerRadius, 1e-3f)
        assertEquals(1f, entering.alpha, 0f)
        assertEquals("no scrim before the spring moves", 0f, entering.scrimAlpha, 0f)
        assertTrue(entering.centerPivot)
        assertTrue(entering.clip)

        val exiting = state.frame(AyuScreenRole.PushExiting, isDark = true)
        assertEquals(0f, exiting.translationX, 0f)
        assertEquals(0f, exiting.translationY, 0f)
        assertEquals(1f, exiting.scale, 0f)
        assertEquals(PUSH_CORNERS, exiting.cornerRadius, 1e-3f)
        assertEquals(1f, exiting.alpha, 0f)
        assertEquals(0f, exiting.scrimAlpha, 0f)
        assertTrue(exiting.clip)
    }

    @Test
    fun pushMidFrameMatchesApplySpringProgress() {
        val state = pushed { it.setProgressInternal(500f) }

        val entering = state.frame(AyuScreenRole.PushEntering, isDark = true)
        assertEquals(540f, entering.translationX, 1e-3f)
        assertEquals(0.925f, entering.scale, 1e-5f)
        assertEquals(PUSH_CORNERS / 0.925f, entering.cornerRadius, 1e-3f)
        // AyuGram int math: base = (int)(0.8 * 255 * 1) = 204, (int)(204 * 0.5) = 102.
        assertEquals(102f / 255f, entering.scrimAlpha, 1e-5f)

        val exiting = state.frame(AyuScreenRole.PushExiting, isDark = true)
        assertEquals(-0.5f * 0.35f * PUSH_W, exiting.translationX, 1e-3f)
        assertEquals(1f, exiting.scale, 0f)
        assertEquals(0f, exiting.scrimAlpha, 0f)
    }

    @Test
    fun pushEndFrameMatchesApplySpringProgress() {
        val state = pushed { it.setProgressInternal(1000f) }

        val enteringDark = state.frame(AyuScreenRole.PushEntering, isDark = true)
        assertEquals(0f, enteringDark.translationX, 1e-3f)
        assertEquals(1f, enteringDark.scale, 1e-5f)
        assertEquals(PUSH_CORNERS, enteringDark.cornerRadius, 1e-3f)
        // (int)(204 * 0.8) = (int)163.2 = 163.
        assertEquals(163f / 255f, enteringDark.scrimAlpha, 1e-5f)

        val enteringLight = state.frame(AyuScreenRole.PushEntering, isDark = false)
        // base = (int)(0.2 * 255) = 51, (int)(51 * 0.8) = (int)40.8 = 40.
        assertEquals(40f / 255f, enteringLight.scrimAlpha, 1e-5f)

        val exiting = state.frame(AyuScreenRole.PushExiting, isDark = true)
        assertEquals(-0.35f * PUSH_W, exiting.translationX, 1e-3f)
    }

    @Test
    fun pushScrimIgnoresStaleCommitMultiplier() {
        val state = AyuBackState(PUSH_DENSITY)
        state.updateSize(1080, 2340, PUSH_CORNERS)
        // Run a full gesture commit first: it leaves the helper scrim multiplier at 0,
        // while AyuGram's `reset()` puts it back to 1 before any push.
        state.onGestureStart(0f, 1000f, 1f, "a", "b")
        state.onPop(closingEntryId = "a", enteringEntryId = "b")
        state.setProgressInternal(1f)
        state.frame(AyuScreenRole.Closing, isDark = true)
        state.onFinished()

        state.onPush(enteringId = "new", exitingId = "old")
        state.setProgressInternal(500f)
        assertEquals(
            102f / 255f,
            state.frame(AyuScreenRole.PushEntering, isDark = true).scrimAlpha,
            1e-5f
        )
    }

    @Test
    fun finishedPushIsIdentity() {
        val state = pushed { it.setProgressInternal(1000f) }
        state.onFinished()
        assertEquals(AyuBackPhase.Idle, state.phase)
        val entering = state.frame(AyuScreenRole.PushEntering, isDark = true)
        assertEquals(0f, entering.translationX, 0f)
        assertEquals(1f, entering.scale, 0f)
        assertEquals(0f, entering.scrimAlpha, 0f)
        val exiting = state.frame(AyuScreenRole.PushExiting, isDark = true)
        assertEquals(0f, exiting.translationX, 0f)
        assertEquals(1f, exiting.scale, 0f)
    }

    @Test
    fun popDuringPushFallsBackToSpring() {
        val state = pushed { it.setProgressInternal(500f) }
        state.onPop(closingEntryId = "new", enteringEntryId = "old")
        assertEquals(AyuBackPhase.Spring, state.phase)
        assertEquals(AyuScreenRole.Closing, state.roleFor("new"))
    }

    @Test
    fun dpf2HasNoCeil() {
        assertEquals(33f, AyuBack.dpf2(PUSH_DENSITY, 12f), 0f)
        assertEquals(0f, AyuBack.dpf2(PUSH_DENSITY, 0f), 0f)
    }

    @Test
    fun rtlLeftFactorMatchesDrawChild() {
        // clamp01(abs((int) innerTX) / dpf2(12)) with dpf2 = 33 at this density.
        assertEquals(1f, AyuBack.pushRtlLeftFactor(PUSH_DENSITY, 540f), 0f)
        assertEquals(0f, AyuBack.pushRtlLeftFactor(PUSH_DENSITY, 0f), 0f)
        assertEquals(16f / 33f, AyuBack.pushRtlLeftFactor(PUSH_DENSITY, 16.9f), 1e-6f)
    }
}
