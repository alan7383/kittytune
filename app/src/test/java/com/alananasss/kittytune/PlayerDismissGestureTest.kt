package com.alananasss.kittytune

import com.alananasss.kittytune.ui.player.shouldDismissPlayer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerDismissGestureTest {

    @Test
    fun testFastDownwardFlingDismissesEvenWithSmallDragDistance() {
        // Fast flick downward (> 800 px/s) should dismiss even if progress is only 5%
        assertTrue(shouldDismissPlayer(progress = 0.05f, velocityY = 1200f))
        assertTrue(shouldDismissPlayer(progress = 0.02f, velocityY = 850f))
    }

    @Test
    fun testSlowDragPastThresholdDismisses() {
        // Dragging past 15% distance should dismiss even with low velocity
        assertTrue(shouldDismissPlayer(progress = 0.16f, velocityY = 100f))
        assertTrue(shouldDismissPlayer(progress = 0.50f, velocityY = 0f))
    }

    @Test
    fun testMinorDragWithoutVelocitySnapsBack() {
        // Dragging only a small distance slowly should not dismiss
        assertFalse(shouldDismissPlayer(progress = 0.08f, velocityY = 200f))
        assertFalse(shouldDismissPlayer(progress = 0.12f, velocityY = 400f))
    }

    @Test
    fun testUpwardMovementDoesNotDismiss() {
        // Upward or negative velocity should not dismiss if progress < threshold
        assertFalse(shouldDismissPlayer(progress = 0.10f, velocityY = -500f))
        assertFalse(shouldDismissPlayer(progress = 0.0f, velocityY = -1000f))
    }
}
