package com.alananasss.kittytune.ui.player

/** Queue entries are occurrences: upcoming entries move, played entries are queued again. */
internal sealed interface QueueNextAction {
    data class Move(val from: Int, val to: Int) : QueueNextAction
    data class Replay(val index: Int) : QueueNextAction
    data object None : QueueNextAction
}

internal fun queueNextAction(size: Int, current: Int, selected: Int): QueueNextAction = when {
    current !in 0 until size || selected !in 0 until size || selected == current -> QueueNextAction.None
    selected < current -> QueueNextAction.Replay(selected)
    else -> QueueNextAction.Move(selected, current + 1)
}
