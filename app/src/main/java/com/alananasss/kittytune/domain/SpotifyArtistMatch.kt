package com.alananasss.kittytune.domain

/**
 * Whether a shortcut to a Spotify artist leads to the artist a page is about.
 *
 * The app keeps a shortcut from a numeric id to a Spotify artist, so that an artist
 * the user follows on Spotify opens on the spot. Such a shortcut is only a shortcut:
 * it was also written by name searches, and a search that matched a substring once
 * pointed a SoundCloud artist at a Spotify artist of another name entirely. Tapping
 * the artist then opened somebody else's page, which is what this rule refuses.
 *
 * The name is the only thing there is to compare: the account the page is about is
 * gone by the time the shortcut is followed. When there is no name to compare — the
 * shortcut was written by the user following the artist — the shortcut is trusted.
 */
object SpotifyArtistMatch {

    /**
     * Whether the Spotify artist named [spotifyArtistName] is the artist [expectedName].
     *
     * An absent name on either side means there is nothing to compare, and the
     * shortcut is taken at face value rather than thrown away.
     */
    fun isSameArtist(expectedName: String?, spotifyArtistName: String?): Boolean {
        val expected = expectedName?.trim().orEmpty()
        val actual = spotifyArtistName?.trim().orEmpty()
        if (expected.isEmpty() || actual.isEmpty()) return true
        return actual.equals(expected, ignoreCase = true)
    }
}
