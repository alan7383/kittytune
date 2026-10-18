package com.alananasss.kittytune

import com.alananasss.kittytune.data.vk.VkMusicClient
import com.alananasss.kittytune.data.vk.VkPlaylistLink
import com.alananasss.kittytune.data.vk.VkPlaylistRef
import com.alananasss.kittytune.data.vk.VkTrack
import com.alananasss.kittytune.data.vk.VkTrackMatcher
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VkImportTest {

    @Test
    fun readsEveryShapeOfPlaylistLink() {
        assertEquals(VkPlaylistRef(-147845620, 2949), VkPlaylistLink.parse("https://vk.com/music/playlist/-147845620_2949"))
        assertEquals(VkPlaylistRef(-2000123, 456, "8f3a1c9d2b"), VkPlaylistLink.parse("https://vk.com/music/album/-2000123_456_8f3a1c9d2b"))
        assertEquals(VkPlaylistRef(123, 456, "abcdef0123"), VkPlaylistLink.parse("https://vk.com/audios123?z=audio_playlist123_456%2Fabcdef0123"))
        assertEquals(VkPlaylistRef(123, 456, "abcdef0123"), VkPlaylistLink.parse("https://m.vk.com/audio?act=audio_playlist123_456&access_hash=abcdef0123"))
        assertEquals(VkPlaylistRef(-1, 7), VkPlaylistLink.parse("vk.ru/music/playlist/-1_7"))
        assertEquals(VkPlaylistRef(-1, 7), VkPlaylistLink.parse("  https://m.vk.ru/music/playlist/-1_7?from=share  "))
    }

    @Test
    fun tellsOtherLinksApart() {
        assertNull(VkPlaylistLink.parse("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"))
        assertNull(VkPlaylistLink.parse("https://vk.com/id1"))
        assertFalse(VkPlaylistLink.isVkLink("https://notvk.com/music/playlist/-1_7"))
        assertTrue(VkPlaylistLink.isVkLink("https://vk.cc/abc"))
        assertTrue(VkPlaylistLink.isShortLink("vk.cc/abc"))
    }

    @Test
    fun undoesVkEscaping() {
        assertEquals("SLEEPYHΞAD, feelbad", VkMusicClient.decode("SLEEPYH&amp;#926;AD, feelbad"))
        assertEquals("Rock & Roll", VkMusicClient.decode("Rock &amp; Roll"))
        assertEquals("VK Музыка", VkMusicClient.decode("  <a href=\"/audios-1\">VK Музыка</a>"))
    }

    @Test
    fun aDifferentVersionIsNotTheSong() {
        val original = VkTrack("Перемен", "Кино", listOf("Кино"), "", 296)
        val remix = VkTrackMatcher.Candidate("Перемен (Remix)", "Кино", 296)
        val same = VkTrackMatcher.Candidate("Перемен", "Kino", 296)
        assertTrue(VkTrackMatcher.score(original, same) >= VkTrackMatcher.ACCEPT, "Transliterated artist, same recording")
        assertTrue(VkTrackMatcher.score(original, remix) < VkTrackMatcher.ACCEPT, "A remix must not stand in for the original")

        val slowed = VkTrack("Song", "Artist", listOf("Artist"), "Slowed", 200)
        assertTrue(VkTrackMatcher.score(slowed, VkTrackMatcher.Candidate("Song (Slowed)", "Artist", 201)) >= VkTrackMatcher.ACCEPT)
        assertTrue(VkTrackMatcher.score(slowed, VkTrackMatcher.Candidate("Song", "Artist", 160)) < VkTrackMatcher.ACCEPT)
    }

    @Test
    fun anotherSongWithTheSameTitleIsRejected() {
        val track = VkTrack("Love", "Artist One", listOf("Artist One"), "", 180)
        assertEquals(0f, VkTrackMatcher.score(track, VkTrackMatcher.Candidate("Love", "Somebody Else", 240)))
    }

    @Test
    fun aRenamedArtistWithTheSameRecordingIsAccepted() {
        val track = VkTrack("Фарфор", "ROMANOVSKAYA", listOf("ROMANOVSKAYA"), "", 177)
        assertTrue(VkTrackMatcher.score(track, VkTrackMatcher.Candidate("Фарфор", "ANNA", 177)) >= VkTrackMatcher.ACCEPT)
        assertTrue(VkTrackMatcher.score(track, VkTrackMatcher.Candidate("Фарфор", "ANNA", 200)) < VkTrackMatcher.ACCEPT)
    }

    @Test
    fun producerCreditsAreNotAVersion() {
        assertTrue(VkTrackMatcher.versionsOf("prod. wayziss").isEmpty())
        assertEquals(setOf("spedup"), VkTrackMatcher.versionsOf("Sped Up"))
        assertEquals(setOf("remix"), VkTrackMatcher.versionsOf("Ремикс"))
    }
}
