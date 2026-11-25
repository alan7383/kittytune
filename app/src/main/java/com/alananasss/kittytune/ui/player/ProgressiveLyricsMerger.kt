package com.alananasss.kittytune.ui.player

import com.alananasss.kittytune.data.LyricsMatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** One instance per search. State callbacks run on the caller's UI dispatcher. */
internal class ProgressiveLyricsMerger(private val worker: CoroutineDispatcher = Dispatchers.Default) {
    private val mutex = Mutex()

    suspend fun merge(
        incoming: List<UnifiedLyricResult>,
        target: LyricsMatcher.Target,
        current: () -> List<UnifiedLyricResult>,
        activeKey: () -> String?,
        isCurrent: () -> Boolean,
        publish: (List<UnifiedLyricResult>) -> Unit,
    ) = mutex.withLock {
        if (!isCurrent()) return@withLock
        val snapshot = current()
        var selected: String?
        var merged: List<UnifiedLyricResult>
        do {
            selected = activeKey()
            merged = withContext(worker) {
                ManualLyricsRanking.merge(snapshot, incoming, target, selected)
            }
            if (!isCurrent()) return@withLock
        } while (selected != activeKey())
        publish(merged)
    }
}
