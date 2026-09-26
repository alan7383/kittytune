package com.alananasss.kittytune

import com.alananasss.kittytune.ui.player.SkipAction
import com.alananasss.kittytune.ui.player.SkipDecision
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Rapid skipping is the input that previously stacked three crossfades inside 0.7 s, so the
 * rules that now sit in front of it are worth pinning down.
 */
class SkipDecisionTest {

    private fun decide(
        since: Long = 10_000L,
        dj: Boolean = false,
        match: Boolean = false,
        running: Boolean = false,
    ) = SkipDecision.decide(since, dj, match, running)

    @Test
    fun `a bouncing button is one press, not several`() {
        assertEquals(SkipAction.IGNORE, decide(since = 0L))
        assertEquals(SkipAction.IGNORE, decide(since = SkipDecision.DEBOUNCE_MS - 1))
        // Two deliberate presses must still be two.
        assertEquals(SkipAction.HARD_SKIP, decide(since = SkipDecision.DEBOUNCE_MS))
    }

    @Test
    fun `debounce wins over everything else`() {
        // Even mid-transition with DJ Flow on, a repeat inside the window changes nothing.
        assertEquals(
            SkipAction.IGNORE,
            decide(since = 10L, dj = true, match = true, running = true),
        )
    }

    @Test
    fun `DJ Flow blends instead of cutting`() {
        assertEquals(SkipAction.BLEND, decide(dj = true, match = true))
    }

    @Test
    fun `without a scored candidate there is nothing to blend into`() {
        assertEquals(SkipAction.HARD_SKIP, decide(dj = true, match = false))
    }

    @Test
    fun `pressing again during a blend gets the listener out`() {
        // The point of the second press is impatience; answering it with another blend would
        // be the engine arguing with the user.
        assertEquals(SkipAction.HARD_SKIP, decide(dj = true, match = true, running = true))
    }

    @Test
    fun `a transition already running is never blended over`() {
        // This is the guard that stops crossfades stacking.
        assertEquals(SkipAction.HARD_SKIP, decide(dj = true, match = true, running = true))
        assertEquals(SkipAction.HARD_SKIP, decide(dj = false, match = false, running = true))
    }

    @Test
    fun `with DJ Flow off a skip is a plain skip`() {
        assertEquals(SkipAction.HARD_SKIP, decide(dj = false, match = true))
    }
}
