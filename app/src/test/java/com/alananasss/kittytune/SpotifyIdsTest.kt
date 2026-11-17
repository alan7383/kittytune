package com.alananasss.kittytune

import com.alananasss.kittytune.data.spotify.SpotifyIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers [SpotifyIds], the single normalization point for Spotify references.
 *
 * The original bug: `PlaylistDetailScreen` pre-stripped `spotify:` from the route
 * with a `String.replace` chain, leaving `playlist:<id>`. The repository then sent
 * `spotify:playlist:playlist:<id>` upstream and rendered a name-less, empty shell
 * as "This playlist is empty". These tests pin the parsing contract so a half-strip
 * cannot pass validation again.
 */
class SpotifyIdsTest {

    private fun idOf(value: String, hint: SpotifyIds.EntityType? = null): String? =
        SpotifyIds.resolve(value, hint)

    private fun typeOf(value: String, hint: SpotifyIds.EntityType? = null): SpotifyIds.EntityType? =
        (SpotifyIds.parse(value, hint) as? SpotifyIds.Result.Resolved)?.type

    // ---- bare ids -----------------------------------------------------------

    @Test
    fun `bare id resolves to itself`() {
        assertEquals("37i9dQZF1E4z1g3aRBqnmA", idOf("37i9dQZF1E4z1g3aRBqnmA", SpotifyIds.EntityType.PLAYLIST))
        assertEquals("0c4u1LEZWEh5UfMQkzOt4Q", idOf("0c4u1LEZWEh5UfMQkzOt4Q", SpotifyIds.EntityType.ARTIST))
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals(
            "37i9dQZF1E4z1g3aRBqnmA",
            idOf("  37i9dQZF1E4z1g3aRBqnmA  ", SpotifyIds.EntityType.PLAYLIST)
        )
    }

    // ---- URNs ---------------------------------------------------------------

    @Test
    fun `urns resolve to the bare id and report their own type`() {
        assertEquals("37i9dQZF1E4z1g3aRBqnmA", idOf("spotify:playlist:37i9dQZF1E4z1g3aRBqnmA"))
        assertEquals(SpotifyIds.EntityType.PLAYLIST, typeOf("spotify:playlist:37i9dQZF1E4z1g3aRBqnmA"))
        assertEquals("4ZUgNgy1N2QEZ13sCIxzG2", idOf("spotify:album:4ZUgNgy1N2QEZ13sCIxzG2"))
        assertEquals(SpotifyIds.EntityType.ALBUM, typeOf("spotify:album:4ZUgNgy1N2QEZ13sCIxzG2"))
        assertEquals("2d3nRF1Ahx9AQ3LxAXH3mM", idOf("spotify:track:2d3nRF1Ahx9AQ3LxAXH3mM"))
        assertEquals(SpotifyIds.EntityType.TRACK, typeOf("spotify:track:2d3nRF1Ahx9AQ3LxAXH3mM"))
        assertEquals("0c4u1LEZWEh5UfMQkzOt4Q", idOf("spotify:artist:0c4u1LEZWEh5UfMQkzOt4Q"))
        assertEquals(SpotifyIds.EntityType.ARTIST, typeOf("spotify:artist:0c4u1LEZWEh5UfMQkzOt4Q"))
    }

    @Test
    fun `urns are case insensitive on the scheme and type`() {
        assertEquals("37i9dQZF1E4z1g3aRBqnmA", idOf("SPOTIFY:PLAYLIST:37i9dQZF1E4z1g3aRBqnmA"))
    }

    @Test
    fun `a malformed urn is rejected instead of half-parsed`() {
        assertNull(idOf("spotify:playlist"))
        assertNull(idOf("spotify:playlist:"))
        assertNull(idOf("spotify:"))
    }

    @Test
    fun `an unsupported urn type is rejected`() {
        assertNull(idOf("spotify:carrierpigeon:37i9dQZF1E4z1g3aRBqnmA"))
    }

    // ---- the regression: bare entity prefixes -------------------------------

    @Test
    fun `bare entity prefixes left by a pre-stripping caller resolve correctly`() {
        // This is the exact input the buggy screen produced.
        assertEquals("30KI6TULnUPhPnoHEQJi0s", idOf("playlist:30KI6TULnUPhPnoHEQJi0s"))
        assertEquals("4ZUgNgy1N2QEZ13sCIxzG2", idOf("album:4ZUgNgy1N2QEZ13sCIxzG2"))
        assertEquals("2d3nRF1Ahx9AQ3LxAXH3mM", idOf("track:2d3nRF1Ahx9AQ3LxAXH3mM"))
        assertEquals("0c4u1LEZWEh5UfMQkzOt4Q", idOf("artist:0c4u1LEZWEh5UfMQkzOt4Q"))
    }

    @Test
    fun `a bare entity prefix reports the matching type`() {
        assertEquals(SpotifyIds.EntityType.PLAYLIST, typeOf("playlist:30KI6TULnUPhPnoHEQJi0s"))
        assertEquals(SpotifyIds.EntityType.ALBUM, typeOf("album:4ZUgNgy1N2QEZ13sCIxzG2"))
    }

    // ---- internal app routes ------------------------------------------------

    @Test
    fun `internal route prefixes resolve to the bare id`() {
        assertEquals("37i9dQZF1E4z1g3aRBqnmA", idOf("spotify_playlist:37i9dQZF1E4z1g3aRBqnmA"))
        assertEquals("4ZUgNgy1N2QEZ13sCIxzG2", idOf("spotify_album:4ZUgNgy1N2QEZ13sCIxzG2"))
        assertEquals("0c4u1LEZWEh5UfMQkzOt4Q", idOf("spotify_artist:0c4u1LEZWEh5UfMQkzOt4Q"))
        assertEquals("2d3nRF1Ahx9AQ3LxAXH3mM", idOf("spotify_radio:2d3nRF1Ahx9AQ3LxAXH3mM"))
        assertEquals("0c4u1LEZWEh5UfMQkzOt4Q", idOf("station_spotify:0c4u1LEZWEh5UfMQkzOt4Q"))
        assertEquals("0c4u1LEZWEh5UfMQkzOt4Q", idOf("station_artist:0c4u1LEZWEh5UfMQkzOt4Q"))
        assertEquals("0c4u1LEZWEh5UfMQkzOt4Q", idOf("profile:0c4u1LEZWEh5UfMQkzOt4Q"))
    }

    @Test
    fun `internal routes report their implied type`() {
        assertEquals(SpotifyIds.EntityType.PLAYLIST, typeOf("spotify_playlist:37i9dQZF1E4z1g3aRBqnmA"))
        assertEquals(SpotifyIds.EntityType.ALBUM, typeOf("spotify_album:4ZUgNgy1N2QEZ13sCIxzG2"))
        assertEquals(SpotifyIds.EntityType.ARTIST, typeOf("spotify_artist:0c4u1LEZWEh5UfMQkzOt4Q"))
    }

    @Test
    fun `compound prefixes collapse instead of leaving a half stripped id`() {
        assertEquals(
            "37i9dQZF1E4z1g3aRBqnmA",
            idOf("spotify:user:spotify:playlist:37i9dQZF1E4z1g3aRBqnmA")
        )
        assertEquals(
            "37i9dQZF1E4z1g3aRBqnmA",
            idOf("spotify_playlist:spotify:playlist:37i9dQZF1E4z1g3aRBqnmA")
        )
        // The worst case the old chain could produce: a URN wrapped in a bare prefix.
        assertEquals("30KI6TULnUPhPnoHEQJi0s", idOf("playlist:spotify:playlist:30KI6TULnUPhPnoHEQJi0s"))
    }

    // ---- web URLs -----------------------------------------------------------

    @Test
    fun `web urls resolve to the last path segment`() {
        assertEquals(
            "2d3nRF1Ahx9AQ3LxAXH3mM",
            idOf("https://open.spotify.com/track/2d3nRF1Ahx9AQ3LxAXH3mM?si=90363223d38b4b0a")
        )
        assertEquals(
            "37i9dQZF1E4z1g3aRBqnmA",
            idOf("https://open.spotify.com/playlist/37i9dQZF1E4z1g3aRBqnmA")
        )
    }

    @Test
    fun `regionalized web urls drop the intl segment and the query`() {
        assertEquals(
            "2d3nRF1Ahx9AQ3LxAXH3mM",
            idOf("https://open.spotify.com/intl-fr/track/2d3nRF1Ahx9AQ3LxAXH3mM?si=R1n4MGNfSG6DrPKhf-EvEg&nd=1")
        )
    }

    @Test
    fun `embed web urls resolve`() {
        assertEquals(
            "4ZUgNgy1N2QEZ13sCIxzG2",
            idOf("https://open.spotify.com/embed/album/4ZUgNgy1N2QEZ13sCIxzG2")
        )
    }

    @Test
    fun `web urls report their type from the path`() {
        assertEquals(
            SpotifyIds.EntityType.PLAYLIST,
            typeOf("https://open.spotify.com/playlist/37i9dQZF1E4z1g3aRBqnmA")
        )
    }

    @Test
    fun `schemeless web urls resolve`() {
        assertEquals("37i9dQZF1E4z1g3aRBqnmA", idOf("open.spotify.com/playlist/37i9dQZF1E4z1g3aRBqnmA"))
    }

    @Test
    fun `a trailing slash does not confuse the url parser`() {
        assertEquals("37i9dQZF1E4z1g3aRBqnmA", idOf("https://open.spotify.com/playlist/37i9dQZF1E4z1g3aRBqnmA/"))
    }

    @Test
    fun `a non spotify host is rejected`() {
        assertNull(idOf("https://example.com/track/2d3nRF1Ahx9AQ3LxAXH3mM"))
        assertNull(idOf("https://evil.example.com/playlist/37i9dQZF1E4z1g3aRBqnmA"))
    }

    // ---- rejections ---------------------------------------------------------

    @Test
    fun `empty and blank references are rejected`() {
        assertNull(idOf(""))
        assertNull(idOf("   "))
    }

    @Test
    fun `a double stripped reference collapses to the bare id, never a doubled prefix`() {
        // The invariant that matters is what leaves this function: the id must be a
        // bare entity id. Collapsing is the right answer rather than rejection,
        // because the caller would otherwise have nothing to work with.
        assertEquals("30KI6TULnUPhPnoHEQJi0s", idOf("playlist:playlist:30KI6TULnUPhPnoHEQJi0s"))
    }

    @Test
    fun `a prefixed route with a malformed payload is rejected`() {
        // Nothing valid remains after stripping, so it must not be forwarded.
        assertNull(idOf("playlist:"))
        assertNull(idOf("playlist:not-an-id"))
        assertNull(idOf("playlist:playlist:"))
        assertNull(idOf("spotify_playlist:"))
    }

    @Test
    fun `ids containing separators are rejected`() {
        assertNull(idOf("30KI6TULnUPhPnoHEQJi0s/extra", SpotifyIds.EntityType.PLAYLIST))
        assertNull(idOf("30KI 6TULnUPhPnoHEQJi0s", SpotifyIds.EntityType.PLAYLIST))
    }

    @Test
    fun `a soundcloud numeric id is not a spotify id`() {
        // 22 digits would validate as base62, but Spotify ids are never all-digits
        // in practice; the lenient path is what handles these, not parse().
        assertNull(idOf("751398427"))
    }

    @Test
    fun `the lenient path does not validate, so numeric ids survive`() {
        // station_artist: routes carry a SoundCloud numeric id that is later fed to
        // a radio lookup. stripPrefixes must return it untouched.
        assertEquals("751398427", SpotifyIds.stripPrefixes("station_artist:751398427"))
        assertEquals("751398427", SpotifyIds.stripPrefixes("station:751398427"))
    }

    @Test
    fun `the lenient path collapses prefixes without validating`() {
        assertEquals("30KI6TULnUPhPnoHEQJi0s", SpotifyIds.stripPrefixes("playlist:30KI6TULnUPhPnoHEQJi0s"))
        assertEquals(
            "37i9dQZF1E4z1g3aRBqnmA",
            SpotifyIds.stripPrefixes("spotify:user:spotify:playlist:37i9dQZF1E4z1g3aRBqnmA")
        )
        assertEquals("", SpotifyIds.stripPrefixes(""))
        assertEquals("", SpotifyIds.stripPrefixes("   "))
    }

    @Test
    fun `a caller hint classifies a type-less route`() {
        // spotify_radio: hides its type, so the hint supplies it.
        assertEquals(
            SpotifyIds.EntityType.TRACK,
            typeOf("spotify_radio:2d3nRF1Ahx9AQ3LxAXH3mM", SpotifyIds.EntityType.TRACK)
        )
    }

    @Test
    fun `an explicit type in the reference wins over the hint`() {
        assertEquals(
            SpotifyIds.EntityType.ALBUM,
            typeOf("spotify:album:4ZUgNgy1N2QEZ13sCIxzG2", SpotifyIds.EntityType.PLAYLIST)
        )
    }

    @Test
    fun `expected length helper matches spotify issued ids`() {
        assertTrue(SpotifyIds.hasExpectedLength("37i9dQZF1E4z1g3aRBqnmA"))
    }
}