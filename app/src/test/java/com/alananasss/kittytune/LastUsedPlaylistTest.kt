package com.alananasss.kittytune

import com.alananasss.kittytune.data.local.LastUsedPlaylist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The notification's "Add to last playlist" action used to need a playlist id *and* a name, and
 * only the id was stored when a single track was added - so the action found a playlist it could
 * not name and refused to add anything ("no playlist available yet").
 */
class LastUsedPlaylistTest {

    @Test
    fun `an unused playlist has no valid id`() {
        assertFalse(LastUsedPlaylist.isValidId(LastUsedPlaylist.NO_PLAYLIST))
        assertFalse(LastUsedPlaylist.isValidId(-1L))
        assertTrue(LastUsedPlaylist.isValidId(42L))
    }

    @Test
    fun `the bookmarked name wins over the library name`() {
        assertEquals(
            "Bookmarked",
            LastUsedPlaylist.resolveTitle("Bookmarked", "Library")
        )
    }

    @Test
    fun `a bookmark without a name falls back to the library name`() {
        assertEquals("Library", LastUsedPlaylist.resolveTitle(null, "Library"))
        assertEquals("Library", LastUsedPlaylist.resolveTitle("   ", "Library"))
    }

    @Test
    fun `a playlist with no name at all is still a usable destination`() {
        // No name means the action cannot say where the track went, but it must still add it.
        assertNull(LastUsedPlaylist.resolveTitle(null, null))
        assertNull(LastUsedPlaylist.resolveTitle("", "  "))
    }
}
