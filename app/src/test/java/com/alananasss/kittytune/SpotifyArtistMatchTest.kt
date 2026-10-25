package com.alananasss.kittytune

import com.alananasss.kittytune.domain.SpotifyArtistMatch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A shortcut from a numeric id to a Spotify artist is only a shortcut.
 *
 * It was also written by name searches, and a search that matched a substring pointed
 * a SoundCloud artist at a Spotify artist of another name entirely: tapping
 * "KounnySlay" in the history opened "Slay", an artist with 560,000 monthly
 * listeners, while the SoundCloud artist had 39 followers. These cases pin the rule:
 * the shortcut has to name the same artist, or it is not followed.
 */
class SpotifyArtistMatchTest {

    @Test
    fun `an artist whose name is only a substring of another is another artist`() {
        // "Slay" is a substring of "KounnySlay", and a different artist.
        assertFalse(SpotifyArtistMatch.isSameArtist("KounnySlay", "Slay"))
    }

    @Test
    fun `the same name is the same artist`() {
        assertTrue(SpotifyArtistMatch.isSameArtist("KounnySlay", "KounnySlay"))
    }

    @Test
    fun `a name that only differs in case is the same artist`() {
        assertTrue(SpotifyArtistMatch.isSameArtist("KounnySlay", "kounnyslay"))
        assertTrue(SpotifyArtistMatch.isSameArtist("  KounnySlay  ", "KounnySlay"))
    }

    @Test
    fun `another name is another artist`() {
        assertFalse(SpotifyArtistMatch.isSameArtist("KounnySlay", "Bou"))
    }

    @Test
    fun `without a name to compare the shortcut is trusted`() {
        // The shortcut was written by the user following the artist: nothing to check.
        assertTrue(SpotifyArtistMatch.isSameArtist(null, "Slay"))
        assertTrue(SpotifyArtistMatch.isSameArtist("", "Slay"))
        assertTrue(SpotifyArtistMatch.isSameArtist("KounnySlay", null))
        assertTrue(SpotifyArtistMatch.isSameArtist("KounnySlay", ""))
    }
}
