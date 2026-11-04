package com.alananasss.kittytune

import com.alananasss.kittytune.data.PlaybackRecoveryBudget
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRecoveryBudgetTest {
    @Test
    fun `repeated successful resolutions followed by decoder failures cannot loop forever`() {
        val budget = PlaybackRecoveryBudget()
        // Resolving a new URL or reaching STATE_READY does not start a new user request.
        assertTrue(budget.tryRecover(854420383L))
        assertTrue(budget.tryRecover(854420383L))
        repeat(100) { assertFalse(budget.tryRecover(854420383L)) }
    }

    @Test
    fun `a new selection or a different track can recover independently`() {
        val budget = PlaybackRecoveryBudget(maxRetries = 1)
        assertTrue(budget.tryRecover(1L))
        assertFalse(budget.tryRecover(1L))
        assertTrue(budget.tryRecover(2L))
        assertFalse(budget.tryRecover(2L))
        budget.reset()
        assertTrue(budget.tryRecover(2L))
    }
}
