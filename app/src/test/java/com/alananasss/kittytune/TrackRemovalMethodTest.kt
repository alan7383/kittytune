package com.alananasss.kittytune

import com.alananasss.kittytune.data.local.TrackRemovalMethod
import org.junit.Assert.assertEquals
import org.junit.Test

class TrackRemovalMethodTest {

    @Test
    fun testTrackRemovalMethodEnumValues() {
        assertEquals("SWIPE_AND_MENU", TrackRemovalMethod.SWIPE_AND_MENU.name)
        assertEquals("MENU_ONLY", TrackRemovalMethod.MENU_ONLY.name)
        assertEquals(TrackRemovalMethod.SWIPE_AND_MENU, TrackRemovalMethod.valueOf("SWIPE_AND_MENU"))
        assertEquals(TrackRemovalMethod.MENU_ONLY, TrackRemovalMethod.valueOf("MENU_ONLY"))
    }
}
