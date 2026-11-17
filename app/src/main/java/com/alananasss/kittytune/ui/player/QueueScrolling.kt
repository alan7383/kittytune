package com.alananasss.kittytune.ui.player

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.alananasss.kittytune.domain.Track
import kotlin.math.abs

/**
 * Keeps the track being played in sight in a queue list, without ever moving it out from under the
 * touch pointer.
 *
 * Scroll only when the target is not already on screen. A row just tapped is visible by definition,
 * so nothing moves. A track that arrived because the previous one ended is usually below the fold,
 * so the list follows it smoothly.
 */
@Composable
internal fun AnchorCurrentQueueItem(
    listState: LazyListState,
    currentIndex: Int,
    currentTrackId: Long?,
    currentKey: Any? = null,
    targetIndex: Int = (currentIndex - 1).coerceAtLeast(0),
) {
    LaunchedEffect(currentIndex, currentTrackId, targetIndex) {
        if (currentIndex < 0 || targetIndex < 0) return@LaunchedEffect

        val visible = listState.layoutInfo.visibleItemsInfo
        val isVisible = if (currentKey != null) {
            visible.any { it.key == currentKey }
        } else {
            visible.any { it.index == currentIndex }
        }
        if (isVisible) return@LaunchedEffect

        val distance = visible.firstOrNull()?.index?.let { abs(targetIndex - it) }

        if (distance == null || distance > FAR_JUMP_ITEMS) {
            listState.scrollToItem(targetIndex)
        } else {
            listState.animateScrollToItem(targetIndex)
        }
    }
}

/** Beyond this many rows, anchoring stops being a movement worth watching and jumps immediately. */
private const val FAR_JUMP_ITEMS = 12

/**
 * Stable per-row keys for [tracks] supporting duplicates.
 */
internal fun queueItemKeys(tracks: List<Track>): List<String> {
    val seen = HashMap<Long, Int>(tracks.size)
    return tracks.map { track ->
        val occurrence = seen.getOrDefault(track.id, 0)
        seen[track.id] = occurrence + 1
        "${track.id}_dup$occurrence"
    }
}
