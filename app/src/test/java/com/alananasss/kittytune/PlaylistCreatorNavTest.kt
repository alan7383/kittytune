package com.alananasss.kittytune

import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.domain.playlistCreatorNavId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The creator of a playlist is not always an identity the app can open.
 *
 * A Spotify community playlist builds its header as
 * `User(id = 0L, username = <owner name>, avatarUrl = <cover>)` — no urn, no
 * permalink, no numeric id. The creator name used to be routed anyway, producing
 * `profile:<name>`, which the profile screen resolved by searching Spotify for an
 * artist of that name and opening an unrelated verified profile.
 *
 * These cases pin the rule: a link requires a real identifier.
 */
class PlaylistCreatorNavTest {

    @Test
    fun `a spotify community playlist creator is not linkable`() {
        // The exact shape built for a Spotify playlist: a name and nothing else.
        val creator = User(
            id = 0L,
            username = "Hiver >.<",
            avatarUrl = "https://i.scdn.co/image/cover"
        )
        assertNull(playlistCreatorNavId(creator))
    }

    @Test
    fun `a name-only creator is not linkable even with a valid username`() {
        assertNull(playlistCreatorNavId(User(id = 0L, username = "Someone", avatarUrl = null)))
    }

    @Test
    fun `a spotify artist urn links to the exact artist`() {
        val creator = User(
            id = 0L,
            username = "FACE",
            avatarUrl = null,
            urn = "spotify:artist:0c4u1LEZWEh5UfMQkzOt4Q"
        )
        assertEquals("spotify_artist:0c4u1LEZWEh5UfMQkzOt4Q", playlistCreatorNavId(creator))
    }

    @Test
    fun `a spotify artist urn wins over a hashed id`() {
        // Album headers hash the artist id into User.id; the urn is the real identity.
        val creator = User(
            id = 4_242_242L,
            username = "Hiver",
            avatarUrl = null,
            urn = "spotify:artist:0c4u1LEZWEh5UfMQkzOt4Q"
        )
        assertEquals("spotify_artist:0c4u1LEZWEh5UfMQkzOt4Q", playlistCreatorNavId(creator))
    }

    @Test
    fun `a real soundcloud user links to its profile`() {
        val creator = User(id = 751398427L, username = "lavendr", avatarUrl = null)
        assertEquals("profile:751398427", playlistCreatorNavId(creator))
    }

    @Test
    fun `a real soundcloud user links by id even when the urn is present`() {
        val creator = User(
            id = 751398427L,
            username = "lavendr",
            avatarUrl = null,
            urn = "soundcloud:users:751398427"
        )
        assertEquals("profile:751398427", playlistCreatorNavId(creator))
    }

    @Test
    fun `a null creator is not linkable`() {
        assertNull(playlistCreatorNavId(null))
    }

    @Test
    fun `a spotify urn with an empty id is not linkable`() {
        // Guards against linking to a route that would resolve to nothing.
        val creator = User(id = 0L, username = "Ghost", avatarUrl = null, urn = "spotify:artist:")
        assertNull(playlistCreatorNavId(creator))
    }

    @Test
    fun `a negative id is not treated as an identity`() {
        assertNull(playlistCreatorNavId(User(id = -1L, username = "Local", avatarUrl = null)))
    }
}