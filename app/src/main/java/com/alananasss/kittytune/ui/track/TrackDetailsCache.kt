package com.alananasss.kittytune.ui.track

import com.alananasss.kittytune.core.BoundedCache
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User

/**
 * What the track page loaded, kept for a while (desktop PR #77).
 *
 * The page is made again each time it is opened, so someone who looked at a track's reposts, went back and
 * looked again waited for the same lists a second time. A page of a track opened a few minutes ago comes back
 * as it was left, sort and scrolled-in pages included; after [TTL_MS] it is asked for again, since likes and
 * reposts keep arriving.
 */
internal object TrackDetailsCache {

    class Snapshot(
        val track: Track?,
        val likers: List<User>,
        val reposters: List<User>,
        val playlists: List<Playlist>,
        val playlistsPopular: List<Playlist> = emptyList(),
        val related: List<Track>,
        val likersNext: String?,
        val repostersNext: String?,
        val playlistsNext: String?,
        val relatedNext: String?,
        val usersByFollowers: Boolean = true,
        val playlistsByLikes: Boolean = false,
        val takenAtMs: Long,
    )

    private val cache = BoundedCache<Long, Snapshot>(MAX_TRACKS)

    fun get(trackId: Long, nowMs: Long = System.currentTimeMillis()): Snapshot? =
        cache[trackId]?.takeIf { nowMs - it.takenAtMs < TTL_MS }

    fun put(trackId: Long, snapshot: Snapshot) {
        cache[trackId] = snapshot
    }

    /** A few tracks, not a library: the ones just looked at. */
    private const val MAX_TRACKS = 24
    const val TTL_MS = 10 * 60 * 1000L
}

/** The orders the lists on the track page open in. */
internal object TrackDetailOrder {

    /** The ones with the most followers first: who liked or reposted a track matters by who they are. */
    fun users(users: List<User>, byFollowers: Boolean): List<User> =
        if (byFollowers) users.sortedByDescending { it.followersCount } else users

    /** The ones the most people liked first. */
    fun playlists(playlists: List<Playlist>, byLikes: Boolean): List<Playlist> =
        if (byLikes) playlists.sortedByDescending { it.likesCount ?: 0 } else playlists
}
