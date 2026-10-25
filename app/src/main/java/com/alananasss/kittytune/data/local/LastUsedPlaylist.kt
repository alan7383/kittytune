package com.alananasss.kittytune.data.local

/**
 * The playlist the notification's "Add to last playlist" action targets.
 *
 * The action needs the playlist *id* to add the track; the name is only there to tell the user
 * where the track went. The two used to be stored independently, and the most common path
 * (adding a single track to a playlist) only stored the id - so the action found an id with no
 * name and refused to add anything, telling the user "no playlist available" even though the
 * playlist was right there.
 *
 * These rules live here, away from Android, so they can be tested directly.
 */
object LastUsedPlaylist {

    /** The id stored when no playlist was ever used. */
    const val NO_PLAYLIST = -1L

    /** True when [playlistId] points at an actual playlist. */
    fun isValidId(playlistId: Long): Boolean = playlistId != NO_PLAYLIST

    /**
     * The name to show for the target playlist: the bookmarked one first, the playlist's own
     * name in the local library as a fallback.
     *
     * Returns null when neither is known - the action still adds the track, it just cannot name
     * the destination.
     */
    fun resolveTitle(bookmarkedTitle: String?, libraryTitle: String?): String? =
        bookmarkedTitle?.takeIf { it.isNotBlank() } ?: libraryTitle?.takeIf { it.isNotBlank() }
}
