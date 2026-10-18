package com.alananasss.kittytune

import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.track.TrackDetailOrder
import com.alananasss.kittytune.ui.track.TrackDetailsCache
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** A track's page comes back as it was left, and its lists open in the order that matters. */
class TrackDetailsCacheTest {

    private fun snapshot(takenAtMs: Long) = TrackDetailsCache.Snapshot(
        track = null, likers = emptyList(), reposters = emptyList(), playlists = emptyList(), playlistsPopular = emptyList(), related = emptyList(),
        likersNext = "next", repostersNext = null, playlistsNext = null, relatedNext = null,
        usersByFollowers = true, playlistsByLikes = true, takenAtMs = takenAtMs,
    )

    @Test
    fun `a page opened a few minutes ago comes back without asking again`() {
        TrackDetailsCache.put(901L, snapshot(takenAtMs = 1_000L))
        assertNotNull(TrackDetailsCache.get(901L, nowMs = 1_000L + 5 * 60_000L))
        assertEquals("next", TrackDetailsCache.get(901L, nowMs = 2_000L)?.likersNext)
    }

    @Test
    fun `after ten minutes it is asked for again, since likes keep arriving`() {
        TrackDetailsCache.put(902L, snapshot(takenAtMs = 1_000L))
        assertNull(TrackDetailsCache.get(902L, nowMs = 1_000L + TrackDetailsCache.TTL_MS))
    }

    @Test
    fun `a track never opened has nothing kept`() {
        assertNull(TrackDetailsCache.get(903L))
    }

    private fun user(id: Long, followers: Int) = User(id = id, username = "u$id", avatarUrl = null, followersCount = followers)

    private fun playlist(id: Long, likes: Int) = Playlist(
        id = id, title = "p$id", artworkUrl = null, calculatedArtworkUrl = null, trackCount = 5, user = null, likesCount = likes,
    )

    @Test
    fun `people open by followers, most first, and go back to the order they came in`() {
        val arrived = listOf(user(1, 10), user(2, 5_000), user(3, 300))
        assertEquals(listOf(2L, 3L, 1L), TrackDetailOrder.users(arrived, byFollowers = true).map { it.id })
        assertEquals(listOf(1L, 2L, 3L), TrackDetailOrder.users(arrived, byFollowers = false).map { it.id })
    }

    @Test
    fun `playlists open by likes`() {
        val arrived = listOf(playlist(1, 2), playlist(2, 90), playlist(3, 30))
        assertEquals(listOf(2L, 3L, 1L), TrackDetailOrder.playlists(arrived, byLikes = true).map { it.id })
        assertEquals(listOf(1L, 2L, 3L), TrackDetailOrder.playlists(arrived, byLikes = false).map { it.id })
    }
}
