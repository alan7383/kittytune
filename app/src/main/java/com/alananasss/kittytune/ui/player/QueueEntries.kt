package com.alananasss.kittytune.ui.player

import com.alananasss.kittytune.domain.Track

internal sealed interface QueueListItem {
    data class PastHeader(val count: Int, val isExpanded: Boolean) : QueueListItem
    data class UpNextHeader(val count: Int) : QueueListItem
    data class TrackItem(val queueIndex: Int, val track: Track) : QueueListItem
}

internal const val RECENT_QUEUE_TRACKS = 3

/** The queue prefix is one section, including skipped and restored entries. */
internal fun queueListItems(queue: List<Track>, currentIndex: Int, pastExpanded: Boolean): List<QueueListItem> = buildList {
    val pastCount = currentIndex.coerceIn(0, queue.size)
    if (pastCount > 0) add(QueueListItem.PastHeader(pastCount, pastExpanded))
    val firstVisible = if (pastExpanded) 0 else (pastCount - RECENT_QUEUE_TRACKS).coerceAtLeast(0)
    for (index in firstVisible until queue.size) {
        if (index == currentIndex + 1 && index > 0) {
            add(QueueListItem.UpNextHeader(queue.size - index))
        }
        add(QueueListItem.TrackItem(index, queue[index]))
    }
}

/** Preserve the selected occurrence when filtering a playlist, even with repeated ids. */
internal fun filteredQueueIndex(queue: List<Track>, index: Int, keep: (Track) -> Boolean): Int {
    val selected = queue.getOrNull(index) ?: return -1
    if (!keep(selected)) return -1
    return queue.take(index).count(keep)
}

/** A saved or live position wins over a lookup by song id. */
internal fun resolveQueueIndex(queue: List<Track>, trackId: Long?, preferredIndex: Int): Int {
    if (trackId == null) return -1
    if (queue.getOrNull(preferredIndex)?.id == trackId) return preferredIndex
    return queue.indexOfFirst { it.id == trackId }
}

/** Match an entry across the live and original order without deleting every copy of a song. */
internal fun matchingQueueIndex(source: List<Track>, index: Int, target: List<Track>): Int {
    val track = source.getOrNull(index) ?: return -1
    val referenceMatches = target.indices.filter { target[it] === track }
    if (referenceMatches.size == 1) return referenceMatches.single()
    val occurrence = source.take(index).count { it.id == track.id }
    return target.indices.filter { target[it].id == track.id }.getOrNull(occurrence) ?: -1
}
