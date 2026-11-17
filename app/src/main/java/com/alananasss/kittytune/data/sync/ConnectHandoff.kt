package com.alananasss.kittytune.data.sync

/** Prepare the destination silently before stopping the source. Both renderers acknowledge commands. */
internal object ConnectHandoff {
    suspend fun move(source: String?, target: String?, initial: PlaybackSnapshot,
        readSource: suspend () -> PlaybackSnapshot?,
        execute: suspend (String?, String, PlaybackSnapshot?, Long) -> Unit) {
        if (source == target) return
        val track = initial.queue.getOrNull(initial.currentIndex) ?: error("Queue is empty")
        require(track.source != "local") { "Local files are unavailable on another device" }
        execute(target, "transfer", initial.copy(isPlaying = false), 0)
        val latest = readSource() ?: error("Source is unavailable")
        check(latest.queue.getOrNull(latest.currentIndex)?.id == track.id) { "Track changed; choose the device again" }
        // Respect a pause made while the destination was preparing.
        execute(source, "pause", null, 0)
        val stopped = readSource() ?: latest
        try {
            execute(target, "seek", null, stopped.positionMs)
            if (latest.isPlaying) execute(target, "play", null, 0)
        } catch (failure: Exception) {
            // Resume only after confirming that the destination is silent. A lost ACK is ambiguous.
            if (runCatching { execute(target, "pause", null, 0) }.isSuccess && latest.isPlaying) {
                runCatching { execute(source, "play", null, 0) }
            }
            throw failure
        }
    }
}
