package com.alananasss.kittytune

import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.local.TrackRemovalMethod
import com.alananasss.kittytune.ui.player.MenuTiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackRemovalMethodTest {

    @Test
    fun testTrackRemovalMethodEnumValues() {
        assertEquals(3, TrackRemovalMethod.entries.size)
        assertEquals("SWIPE_AND_MENU", TrackRemovalMethod.SWIPE_AND_MENU.name)
        assertEquals("MENU_ONLY", TrackRemovalMethod.MENU_ONLY.name)
        assertEquals("DISABLED", TrackRemovalMethod.DISABLED.name)
        assertEquals(TrackRemovalMethod.SWIPE_AND_MENU, TrackRemovalMethod.valueOf("SWIPE_AND_MENU"))
        assertEquals(TrackRemovalMethod.MENU_ONLY, TrackRemovalMethod.valueOf("MENU_ONLY"))
        assertEquals(TrackRemovalMethod.DISABLED, TrackRemovalMethod.valueOf("DISABLED"))
    }

    @Test
    fun testTrackRemovalMethodCapabilities() {
        assertTrue(TrackRemovalMethod.SWIPE_AND_MENU.isSwipeEnabled)
        assertTrue(TrackRemovalMethod.SWIPE_AND_MENU.isRemovalAllowed)

        assertFalse(TrackRemovalMethod.MENU_ONLY.isSwipeEnabled)
        assertTrue(TrackRemovalMethod.MENU_ONLY.isRemovalAllowed)

        assertFalse(TrackRemovalMethod.DISABLED.isSwipeEnabled)
        assertFalse(TrackRemovalMethod.DISABLED.isRemovalAllowed)
    }

    @Test
    fun testPlaylistMenuCatalogueIncludesTrackRemoval() {
        val playlistTiles = MenuTiles.catalogue(PlayerPreferences.MENU_PLAYLIST)
        assertTrue(playlistTiles.any { it.id == "track_removal" })
    }
}
