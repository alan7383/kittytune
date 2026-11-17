package com.alananasss.kittytune

import com.alananasss.kittytune.domain.HistoryRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The listening history is what the user taps to go back where they were, so a row has
 * to carry the route of the page it describes.
 *
 * A trailer broke that rule: it is not a page of its own but the artist page with a
 * clip on top, so it wrapped the route it came from ("trailer:profile:1345345683").
 * The wrapped route carries no id, and the row was given a hash of it — an id that
 * names no SoundCloud user. The artist was written down twice, and the copy with the
 * hash pointed at nobody: tapping it fell through to the Spotify lookup, where a name
 * that is only a substring of the artist's name matched an unrelated artist.
 *
 * These cases pin the rule: a trailer is the page it came from, and an id is only ever
 * read from a route that carries one.
 */
class HistoryRouteTest {

    @Test
    fun `a trailer is unwrapped to the page it was started from`() {
        assertEquals("profile:1345345683", HistoryRoute.unwrap("trailer:profile:1345345683"))
    }

    @Test
    fun `a route that is not a trailer is left alone`() {
        assertEquals("profile:1345345683", HistoryRoute.unwrap("profile:1345345683"))
        assertEquals("likes", HistoryRoute.unwrap("likes"))
    }

    @Test
    fun `a trailer route carries the id of the page under it`() {
        // The id the trailer row used to be given was a hash of the whole route, which
        // names no user at all.
        assertEquals(1345345683L, HistoryRoute.numericId("trailer:profile:1345345683"))
        assertEquals(1345345683L, HistoryRoute.numericId("profile:1345345683"))
    }

    @Test
    fun `a route without a numeric id carries none`() {
        assertNull(HistoryRoute.numericId("spotify_artist:0c4u1LEZWEh5UfMQkzOt4Q"))
        assertNull(HistoryRoute.numericId("your_mix"))
        assertNull(HistoryRoute.numericId("likes"))
        assertNull(HistoryRoute.numericId("system_playlist:soundcloud:system-playlists:your-playback:123:2025"))
    }

    @Test
    fun `a soundcloud profile route names the artist it opens`() {
        assertEquals("profile:1345345683", HistoryRoute.soundCloudProfileId("profile:1345345683"))
        assertEquals("profile:1345345683", HistoryRoute.soundCloudProfileId("trailer:profile:1345345683"))
    }

    @Test
    fun `a route that is not a soundcloud profile names no artist`() {
        assertNull(HistoryRoute.soundCloudProfileId("spotify_artist:0c4u1LEZWEh5UfMQkzOt4Q"))
        assertNull(HistoryRoute.soundCloudProfileId("likes"))
        assertNull(HistoryRoute.soundCloudProfileId("profile:"))
        assertNull(HistoryRoute.soundCloudProfileId("profile:whoiam0001"))
    }
}
