package com.alananasss.kittytune.data

/** Call under the repository's blacklist lock, including single-track edits. */
internal fun updateLikeBlacklist(
    trackIds: Collection<Long>,
    liked: Boolean,
    read: () -> Set<String>,
    write: (Set<String>) -> Unit,
) {
    if (trackIds.isEmpty()) return
    val current = read().mapNotNull { it.toLongOrNull() }.toMutableSet()
    if (liked) current.removeAll(trackIds.toSet()) else current.addAll(trackIds)
    write(current.mapTo(HashSet(current.size)) { it.toString() })
}
