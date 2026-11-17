package com.alananasss.kittytune

import com.alananasss.kittytune.data.musicimport.PortablePlaylistParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortablePlaylistParserTest {

    @Test
    fun `spotify csv keeps quoted titles and the track url`() {
        val csv = """
            Track name,Artist name,Album name,URL
            "Song, One",Artist One,Album One,https://open.spotify.com/track/abc123
            Song Two,Artist Two,Album Two,
        """.trimIndent()

        val file = PortablePlaylistParser.parse("my_playlist.csv", csv)

        assertEquals("my playlist", file.name)
        assertEquals(2, file.entries.size)
        assertEquals("Song, One", file.entries[0].title)
        assertEquals("Artist One", file.entries[0].artist)
        assertEquals("https://open.spotify.com/track/abc123", file.entries[0].url)
        assertEquals("Song Two", file.entries[1].title)
        assertNull(file.entries[1].url)
    }

    @Test
    fun `semicolon csv and french headers are understood`() {
        val csv = """
            Titre;Artiste;Album
            Dernière danse;Indila;Mini World
            Papaoutai;Stromae;Racine carrée
        """.trimIndent()

        val file = PortablePlaylistParser.parse("export.csv", csv)

        assertEquals(2, file.entries.size)
        assertEquals("Dernière danse", file.entries[0].title)
        assertEquals("Indila", file.entries[0].artist)
        assertEquals("Papaoutai", file.entries[1].title)
    }

    @Test
    fun `txt artist dash title and title by artist are both accepted`() {
        val txt = """
            Artist One - Song One
            Song Two by Artist Two
            3. Artist Three – Song Three
        """.trimIndent()

        val file = PortablePlaylistParser.parse("backup.txt", txt)

        assertEquals(3, file.entries.size)
        assertEquals("Song One", file.entries[0].title)
        assertEquals("Artist One", file.entries[0].artist)
        assertEquals("Song Two", file.entries[1].title)
        assertEquals("Artist Two", file.entries[1].artist)
        assertEquals("Song Three", file.entries[2].title)
        assertEquals("Artist Three", file.entries[2].artist)
    }

    @Test
    fun `url only lines are kept for direct resolution`() {
        val txt = """
            https://open.spotify.com/track/abc123
            https://www.deezer.com/track/3135556
        """.trimIndent()

        val file = PortablePlaylistParser.parse("links.txt", txt)

        assertEquals(2, file.entries.size)
        assertTrue(file.entries[0].url!!.contains("spotify"))
        assertEquals("", file.entries[0].artist)
        assertTrue(file.entries[1].url!!.contains("deezer"))
    }

    @Test
    fun `m3u uses the extinf label and the path`() {
        val m3u = """
            #EXTM3U
            #EXTINF:123,Artist One - Song One
            /music/song-one.mp3
            #EXTINF:240,Artist Two - Song Two
            https://example.com/song-two.mp3
        """.trimIndent()

        val file = PortablePlaylistParser.parse("playlist.m3u", m3u)

        assertEquals(2, file.entries.size)
        assertEquals("Song One", file.entries[0].title)
        assertEquals("Artist One", file.entries[0].artist)
        assertNull(file.entries[0].url)
        assertEquals("Song Two", file.entries[1].title)
        assertEquals("https://example.com/song-two.mp3", file.entries[1].url)
    }

    @Test
    fun `headerless csv falls back to title artist url columns`() {
        val csv = """
            Song One,Artist One,https://open.spotify.com/track/abc123
            Song Two,Artist Two,
        """.trimIndent()

        val file = PortablePlaylistParser.parse("rows.csv", csv)

        assertEquals(2, file.entries.size)
        assertEquals("Song One", file.entries[0].title)
        assertEquals("Artist One", file.entries[0].artist)
        assertEquals("https://open.spotify.com/track/abc123", file.entries[0].url)
    }
}
