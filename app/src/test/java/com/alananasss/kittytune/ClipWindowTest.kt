package com.alananasss.kittytune

import com.alananasss.kittytune.ui.player.ClipWindow
import org.junit.Test
import kotlin.test.assertEquals

/** A trailer shows each song as twenty seconds of it (round 2 of the tester's list, item 6). */
class ClipWindowTest {

    @Test
    fun `a song's trailer window is twenty seconds from a third of the way in`() {
        val window = ClipWindow.forTrailer(totalMs = 200_000L, lengthMs = 20_000L, startShare = 0.3)
        assertEquals(ClipWindow(60_000L, 80_000L), window)
        assertEquals(20_000L, window.lengthMs)
    }

    @Test
    fun `a song hardly longer than the window plays whole`() {
        assertEquals(ClipWindow(0L, 22_000L), ClipWindow.forTrailer(22_000L, 20_000L, 0.3))
        assertEquals(ClipWindow(0L, 25_000L), ClipWindow.forTrailer(25_000L, 20_000L, 0.3))
    }

    @Test
    fun `the window never runs past the end of the song`() {
        val window = ClipWindow.forTrailer(totalMs = 40_000L, lengthMs = 20_000L, startShare = 0.9)
        assertEquals(ClipWindow(20_000L, 40_000L), window)
    }

    @Test
    fun `times convert between the song and the window, held inside it`() {
        val window = ClipWindow(60_000L, 80_000L)
        assertEquals(0L, window.toShown(10_000L), "before the window is its start")
        assertEquals(5_000L, window.toShown(65_000L))
        assertEquals(20_000L, window.toShown(120_000L), "after the window is its end")
        assertEquals(70_000L, window.toSong(10_000L))
        assertEquals(80_000L, window.toSong(99_000L))
        assertEquals(window.toSong(window.toShown(73_000L)), 73_000L)
    }
}
