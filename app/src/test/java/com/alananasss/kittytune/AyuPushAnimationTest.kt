package com.alananasss.kittytune

import com.alananasss.kittytune.ui.navigation.AyuBack
import com.alananasss.kittytune.ui.navigation.AyuBackPhase
import com.alananasss.kittytune.ui.navigation.AyuBackState
import com.alananasss.kittytune.ui.navigation.AyuScreenRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        // Entering screen has no scrim overlay over it.
        assertEquals(0f, entering.scrimAlpha, 0f)
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
        assertEquals(0f, entering.scrimAlpha, 0f)

        val exiting = state.frame(AyuScreenRole.PushExiting, isDark = true)
        assertEquals(-0.5f * 0.35f * PUSH_W, exiting.translationX, 1e-3f)
        assertEquals(1f, exiting.scale, 0f)
        // At p=500, q=0.5, factor=0.5 -> 0.8 * 0.5 = 0.4
        assertEquals(0.8f * 0.5f, exiting.scrimAlpha, 1e-6f)
    }

    @Test
    fun pushEndFrameMatchesApplySpringProgress() {
        val state = pushed { it.setProgressInternal(1000f) }

        val enteringDark = state.frame(AyuScreenRole.PushEntering, isDark = true)
        assertEquals(0f, enteringDark.translationX, 1e-3f)
        assertEquals(1f, enteringDark.scale, 1e-5f)
        assertEquals(PUSH_CORNERS, enteringDark.cornerRadius, 1e-3f)
        assertEquals(0f, enteringDark.scrimAlpha, 0f)

        val enteringLight = state.frame(AyuScreenRole.PushEntering, isDark = false)
        assertEquals(0f, enteringLight.scrimAlpha, 0f)

        val exitingDark = state.frame(AyuScreenRole.PushExiting, isDark = true)
        assertEquals(-0.35f * PUSH_W, exitingDark.translationX, 1e-3f)
        // At p=1000, q=1.0, clamp(1.0, 0, 0.8) = 0.8 -> 0.8 * 0.8 = 0.64
        assertEquals(0.8f * 0.8f, exitingDark.scrimAlpha, 1e-6f)
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
            0f,
            state.frame(AyuScreenRole.PushEntering, isDark = true).scrimAlpha,
            0f
        )
        assertEquals(
            0.8f * 0.5f,
            state.frame(AyuScreenRole.PushExiting, isDark = true).scrimAlpha,
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
    fun popDuringPushSnapsToCloseSpring() {
        // AyuGram snaps the open shut (`onAnimationEndCheck` cancels it) and then runs a full
        // close — never a cut to the previous screen.
        val state = pushed { it.setProgressInternal(500f) }
        state.onPop(closingEntryId = "new", enteringEntryId = "old")
        assertEquals(AyuBackPhase.Spring, state.phase)
        assertEquals(AyuScreenRole.Closing, state.roleFor("new"))
        assertEquals(AyuScreenRole.Entering, state.roleFor("old"))
        // Snapped: the spring restarts from zero (full-screen closing screen).
        state.setProgressInternal(0f)
        val closing = state.frame(AyuScreenRole.Closing, isDark = true)
        assertEquals(0f, closing.translationX, 0f)
        assertEquals(1f, closing.scale, 1e-5f)
        val entering = state.frame(AyuScreenRole.Entering, isDark = true)
        assertEquals(-0.35f * PUSH_W, entering.translationX, 1e-3f)
    }

    @Test
    fun gestureStartIsRejectedWhilePushRuns() {
        val state = pushed { it.setProgressInternal(500f) }
        state.onGestureStart(100f, 1000f, 0.5f, "new", "old")
        assertEquals(AyuBackPhase.Push, state.phase)
        assertEquals(AyuScreenRole.PushEntering, state.roleFor("new"))
    }

    @Test
    fun gestureStartIsRejectedWhileCommitRuns() {
        val state = AyuBackState(PUSH_DENSITY)
        state.updateSize(1080, 2340, PUSH_CORNERS)
        state.onGestureStart(100f, 1000f, 1f, "a", "b")
        state.onPop(closingEntryId = "a", enteringEntryId = "b")
        assertEquals(AyuBackPhase.Commit, state.phase)
        state.onGestureStart(100f, 1000f, 0.5f, "a", "b")
        assertEquals(AyuBackPhase.Commit, state.phase)
    }

    @Test
    fun popDuringCommitSnapsToSpring() {
        // Mirrors `backAnimator.end()`: the running animation snaps, then the pop is honored
        // as a back-press spring with the new ids.
        val state = AyuBackState(PUSH_DENSITY)
        state.updateSize(1080, 2340, PUSH_CORNERS)
        state.onGestureStart(100f, 1000f, 1f, "a", "b")
        state.onPop(closingEntryId = "a", enteringEntryId = "b")
        state.onPop(closingEntryId = "b", enteringEntryId = "c")
        assertEquals(AyuBackPhase.Spring, state.phase)
        assertEquals(AyuScreenRole.Closing, state.roleFor("b"))
        assertEquals(AyuScreenRole.Entering, state.roleFor("c"))
    }

    @Test
    fun popDuringSpringRestartsTheSpring() {
        val state = AyuBackState(PUSH_DENSITY)
        state.updateSize(1080, 2340, PUSH_CORNERS)
        state.onPop(closingEntryId = "a", enteringEntryId = "b")
        val epoch = state.animEpoch
        state.setProgressInternal(500f)
        state.onPop(closingEntryId = "b", enteringEntryId = "c")
        assertEquals(AyuBackPhase.Spring, state.phase)
        assertEquals("retarget must restart the spring clock", epoch + 1, state.animEpoch)
        assertEquals(AyuScreenRole.Closing, state.roleFor("b"))
    }

    @Test
    fun gestureStartsOnlyWhenIdleAndSynced() {
        val state = AyuBackState(PUSH_DENSITY)
        state.updateSize(1080, 2340, PUSH_CORNERS)
        state.syncedStackIds = listOf("a")
        assertTrue(state.shouldStartGesture(listOf("a")))
        // A committed push the observer hasn't absorbed yet: reject, like AyuGram's
        // synchronously-set transition flag.
        assertFalse(state.shouldStartGesture(listOf("a", "b")))
        state.onPush(enteringId = "b", exitingId = "a")
        assertFalse(state.shouldStartGesture(listOf("a", "b")))
    }

    @Test
    fun vetoCoversCloseAndUnabsorbedPop() {
        val state = AyuBackState(PUSH_DENSITY)
        state.updateSize(1080, 2340, PUSH_CORNERS)
        state.syncedStackIds = listOf("a")
        assertFalse(state.shouldVetoBack(listOf("a")))
        // Forward push does NOT veto back (in AyuGram, onBackPressed cancels the open and closes)
        assertFalse("unabsorbed push must not veto", state.shouldVetoBack(listOf("a", "b")))
        state.onPush(enteringId = "b", exitingId = "a")
        assertFalse("open push animation must not veto back", state.shouldVetoBack(listOf("a", "b")))

        // Close spring animation DOES veto back (mirrors closeLastFragment returning if onCloseAnimationEndRunnable != null)
        state.onPop(closingEntryId = "b", enteringEntryId = "a")
        assertEquals(AyuBackPhase.Spring, state.phase)
        assertTrue("running close spring must veto back", state.shouldVetoBack(listOf("a")))

        // Unabsorbed pop also vetoes back to prevent double-pop
        state.onFinished()
        state.syncedStackIds = listOf("a", "b")
        assertTrue("unabsorbed pop must veto back", state.shouldVetoBack(listOf("a")))

        // Absorbed and idle: no veto
        state.syncedStackIds = listOf("a")
        assertFalse(state.shouldVetoBack(listOf("a")))
    }

    @Test
    fun hiddenEntryKeepsPoppedDestinationInvisible() {
        val state = AyuBackState(PUSH_DENSITY)
        state.updateSize(1080, 2340, PUSH_CORNERS)
        state.onPush(enteringId = "b", exitingId = "a")
        state.onPop(closingEntryId = "b", enteringEntryId = "a")
        assertTrue(state.isHidden("b"))

        // While spring runs, closing frame is active
        val springFrame = state.frame(AyuScreenRole.Closing, isDark = true, entryId = "b")
        assertEquals(1f, springFrame.alpha, 0f)

        // When spring finishes and phase becomes Idle, popped entry stays Hidden
        state.onFinished()
        val idleFrame = state.frame(AyuScreenRole.None, isDark = true, entryId = "b")
        assertEquals(0f, idleFrame.alpha, 0f)

        // Even if a new push begins and clears closingEntryId, old popped entry still returns Hidden
        state.onPush(enteringId = "c", exitingId = "a")
        val duringNewPush = state.frame(AyuScreenRole.None, isDark = true, entryId = "b")
        assertEquals(0f, duringNewPush.alpha, 0f)

        // When entry is disposed, it is no longer hidden
        state.onEntryDisposed("b")
        assertFalse(state.isHidden("b"))
    }

    @Test
    fun cancelCanBeCommittedByLatePop() {
        val state = AyuBackState(PUSH_DENSITY)
        state.updateSize(1080, 2340, PUSH_CORNERS)
        state.onGestureStart(100f, 1000f, 0.8f, "b", "a")
        state.onCancel()
        assertEquals(AyuBackPhase.Cancel, state.phase)

        // Late pop commits instead of snapping to 0 spring
        state.onPop(closingEntryId = "b", enteringEntryId = "a")
        assertEquals(AyuBackPhase.Commit, state.phase)
        assertEquals(AyuScreenRole.Closing, state.roleFor("b"))
        assertEquals(AyuScreenRole.Entering, state.roleFor("a"))
    }
    @Test
    fun pushDuringPushRestartsTheSpring() {
        val state = pushed { it.setProgressInternal(500f) }
        val epoch = state.animEpoch
        state.onPush(enteringId = "c", exitingId = "b")
        assertEquals(AyuBackPhase.Push, state.phase)
        assertEquals("retarget must restart the spring clock", epoch + 1, state.animEpoch)
        assertEquals(AyuScreenRole.PushEntering, state.roleFor("c"))
    }

    /**
     * Regression: rapid push/back cycles left the screen stuck on the destination that had just
     * been popped, until it vanished on its own.
     *
     * `AnimatedContent` keeps a popped destination composed for the whole length of its own exit
     * transition, and Navigation draws that content ABOVE the screen revealed underneath (a pop
     * lowers its target's zIndex). So a destination that is off the back stack and is not the one
     * a close animation is dismissing must stay invisible, or it lingers on top for the full
     * 400ms of the transition.
     */
    @Test
    fun offStackDestinationStaysHidden() {
        val state = AyuBackState(PUSH_DENSITY)
        state.updateSize(1080, 2340, PUSH_CORNERS)
        state.onPush(enteringId = "b", exitingId = "a")
        state.setProgressInternal(500f)

        // Back before the navigation was absorbed: the state machine still owns "b" as the entering
        // screen of a finished open, so no role and no close animation are bound to it.
        val orphan = state.frame(AyuScreenRole.None, isDark = true, entryId = "b", onStack = false)
        assertEquals("popped destination must not cover the revealed screen", 0f, orphan.alpha, 0f)

        // The screen a forward open is sliding over is legitimately off-stack (popUpTo + push):
        // it must stay visible, it IS the animation.
        val exiting = state.frame(AyuScreenRole.PushExiting, isDark = true, entryId = "a", onStack = false)
        assertEquals(1f, exiting.alpha, 0f)
        assertEquals(-0.5f * 0.35f * PUSH_W, exiting.translationX, 1e-3f)
    }

    @Test
    fun offStackClosingScreenAnimatesThenHides() {
        val state = AyuBackState(PUSH_DENSITY)
        state.updateSize(1080, 2340, PUSH_CORNERS)
        state.onPop(closingEntryId = "b", enteringEntryId = "a")

        // Mid close: the dismissed destination is off the stack but owns the animation.
        state.setProgressInternal(500f)
        val closing = state.frame(AyuScreenRole.Closing, isDark = true, entryId = "b", onStack = false)
        assertEquals(1f, closing.alpha, 0f)
        assertEquals(0.5f * PUSH_W, closing.translationX, 1e-3f)

        // Once the animation is over it must be gone, not flash back on top.
        state.onFinished()
        val idle = state.frame(AyuScreenRole.Closing, isDark = true, entryId = "b", onStack = false)
        assertEquals(0f, idle.alpha, 0f)
    }

    @Test
    fun onStackIsUnknownBeforeTheFirstSync() {
        val state = AyuBackState(PUSH_DENSITY)
        assertTrue("nothing may be hidden before the stack was ever read", state.isOnStack("a"))
        state.syncedStackIds = listOf("a")
        assertFalse(state.isOnStack("b"))
        assertTrue(state.isOnStack("a"))
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
