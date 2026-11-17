/**
 * Developed by Jason-Marshall Fastner, Germany <jasonfastner@protonmail.com>
 * Questions, feedback, or beat-matching debates? Feel free to reach out via email!
 *
 * Note: Cats always land on their feet, and with this engine, your transitions will too.
 */
package com.alananasss.kittytune.ui.player

/**
 * What a "next" press should actually do.
 *
 * Pulled out of the ViewModel so it can be tested without a player, a context or a device.
 * The rules are small but easy to get subtly wrong, and rapid skipping is exactly the input
 * that produced stacked crossfades before.
 */
enum class SkipAction {
    /** Too close to the previous press — a bouncing button, not a second intention. */
    IGNORE,

    /** DJ Flow is running and idle: blend into the next track instead of cutting. */
    BLEND,

    /** Cut to the next track now, cancelling any blend already in flight. */
    HARD_SKIP,
}

object SkipDecision {

    /**
     * Length of the window in which a second press counts as the same one.
     *
     * Long enough to swallow a bouncing headphone button and an impatient double tap, short
     * enough that two deliberate skips still register as two.
     */
    const val DEBOUNCE_MS = 350L

    /**
     * @param sinceLastPressMs time since the previous accepted press.
     * @param djActive DJ Flow is switched on.
     * @param hasNextMatch the engine has a scored candidate to blend into.
     * @param transitionRunning a crossfade is in flight, from either the engine or a previous press.
     */
    fun decide(
        sinceLastPressMs: Long,
        djActive: Boolean,
        hasNextMatch: Boolean,
        transitionRunning: Boolean,
    ): SkipAction = when {
        sinceLastPressMs < DEBOUNCE_MS -> SkipAction.IGNORE

        // A blend is already running and the listener pressed again: they want out now, not a
        // smoother version of the thing they are trying to leave.
        transitionRunning -> SkipAction.HARD_SKIP

        // Cutting is the one thing DJ Flow exists to avoid - but only when there is something
        // scored to cut to. Without a candidate a blend would have nowhere to go.
        djActive && hasNextMatch -> SkipAction.BLEND

        else -> SkipAction.HARD_SKIP
    }
}
