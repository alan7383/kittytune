package com.alananasss.kittytune

import com.alananasss.kittytune.ui.player.lyrics.LyricsUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Apple-style TTML, as served by BetterLyrics and YouLyPlus, is XML: its ampersands, angle
 * brackets and quotes arrive escaped (`&amp;`, `&lt;`, `&quot;`). Decoding those entities before
 * handing the document to the XML parser makes it invalid, the parse comes back empty, and the
 * player used to fall back to showing the raw `<tt>` markup as plain lyrics.
 */
class TtmlEntityParsingTest {

    private val ttmlWithEntities = """
        <?xml version="1.0" encoding="UTF-8"?>
        <tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal" itunes:timing="Line" xml:lang="en">
          <head><metadata/></head>
          <body dur="00:03:00.000"><div>
            <p begin="00:00:01.000" end="00:00:03.000">Rock &amp; roll &lt;3</p>
            <p begin="00:00:04.000" end="00:00:06.000">A &quot;quoted&quot; line &#8212; here</p>
          </div></body>
        </tt>
    """.trimIndent()

    @Test
    fun `TTML with XML entities parses into lyrics instead of markup`() {
        val lines = LyricsUtils.parseLyricsContent(ttmlWithEntities, 180_000L)

        assertEquals(2, lines.size)
        assertEquals("Rock & roll <3", lines[0].text)
        assertEquals(1_000L, lines[0].startTime)
        assertEquals("A \"quoted\" line — here", lines[1].text)
        assertEquals(4_000L, lines[1].startTime)
    }

    @Test
    fun `TTML root split across lines is still detected and parsed`() {
        val splitRoot = """
            <tt
                xmlns="http://www.w3.org/ns/ttml">
              <body><div>
                <p begin="00:00:01.000" end="00:00:03.000">First</p>
              </div></body>
            </tt>
        """.trimIndent()

        assertTrue(LyricsUtils.looksLikeTtml(splitRoot))
        assertEquals(listOf("First"), LyricsUtils.parseLyricsContent(splitRoot, 60_000L).map { it.text })
    }

    @Test
    fun `TTML prefixed by a BOM is still detected and parsed`() {
        val withBom = "\uFEFF" + ttmlWithEntities

        assertTrue(LyricsUtils.looksLikeTtml(withBom))
        assertEquals(2, LyricsUtils.parseLyricsContent(withBom, 180_000L).size)
    }

    @Test
    fun `malformed TTML yields no lyrics rather than raw markup`() {
        val broken = """<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="00:00:01.000">Broken"""

        assertTrue(LyricsUtils.looksLikeTtml(broken))
        assertTrue(LyricsUtils.parseLyricsContent(broken, 60_000L).isEmpty())
    }

    @Test
    fun `LRC still parses as before`() {
        val lrc = "[00:01.00]Hello\n[00:03.00]World"

        assertFalse(LyricsUtils.looksLikeTtml(lrc))
        assertEquals(listOf("Hello", "World"), LyricsUtils.parseLyricsContent(lrc, 60_000L).map { it.text })
    }
}
