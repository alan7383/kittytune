package com.alananasss.kittytune

import com.alananasss.kittytune.data.search.ProviderLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderLinksTest {

    @Test
    fun `deezer links keep their exact object and id`() {
        assertEquals(
            ProviderLinks.Link(ProviderLinks.Provider.DEEZER, ProviderLinks.Kind.TRACK, "3135556"),
            ProviderLinks.parse("https://www.deezer.com/en/track/3135556?utm_source=share"),
        )
        assertEquals(
            ProviderLinks.Link(ProviderLinks.Provider.DEEZER, ProviderLinks.Kind.ALBUM, "302127"),
            ProviderLinks.parse("https://deezer.com/album/302127"),
        )
        assertEquals(
            ProviderLinks.Link(ProviderLinks.Provider.DEEZER, ProviderLinks.Kind.PLAYLIST, "908622995"),
            ProviderLinks.parse("deezer:playlist:908622995"),
        )
    }

    @Test
    fun `tidal links support browse paths and playlist uuids`() {
        assertEquals(
            ProviderLinks.Link(ProviderLinks.Provider.TIDAL, ProviderLinks.Kind.TRACK, "5966783"),
            ProviderLinks.parse("https://tidal.com/browse/track/5966783"),
        )
        assertEquals(
            ProviderLinks.Link(
                ProviderLinks.Provider.TIDAL,
                ProviderLinks.Kind.PLAYLIST,
                "1c9a3d3e-6c3a-4f0a-9c0e-1d7e5f3b2a10",
            ),
            ProviderLinks.parse("https://listen.tidal.com/playlist/1c9a3d3e-6c3a-4f0a-9c0e-1d7e5f3b2a10"),
        )
        assertEquals(
            ProviderLinks.Link(ProviderLinks.Provider.TIDAL, ProviderLinks.Kind.ARTIST, "7804"),
            ProviderLinks.parse("tidal:artist:7804"),
        )
    }

    @Test
    fun `qobuz links accept locale and slug paths`() {
        assertEquals(
            ProviderLinks.Link(ProviderLinks.Provider.QOBUZ, ProviderLinks.Kind.TRACK, "422042324"),
            ProviderLinks.parse("https://open.qobuz.com/track/422042324"),
        )
        assertEquals(
            ProviderLinks.Link(ProviderLinks.Provider.QOBUZ, ProviderLinks.Kind.ALBUM, "kji81v7j2ywkv"),
            ProviderLinks.parse("https://www.qobuz.com/us-en/album/good-girl-go-brooke-macarthur/kji81v7j2ywkv"),
        )
        assertEquals(
            ProviderLinks.Link(ProviderLinks.Provider.QOBUZ, ProviderLinks.Kind.ARTIST, "36819"),
            ProviderLinks.parse("qobuz:artist:36819"),
        )
    }

    @Test
    fun `qobuz playlists are recognised`() {
        assertEquals(
            ProviderLinks.Link(ProviderLinks.Provider.QOBUZ, ProviderLinks.Kind.PLAYLIST, "1578664"),
            ProviderLinks.parse("https://open.qobuz.com/playlist/1578664"),
        )
        assertEquals(
            ProviderLinks.Link(ProviderLinks.Provider.QOBUZ, ProviderLinks.Kind.PLAYLIST, "1578664"),
            ProviderLinks.parse("qobuz:playlist:1578664"),
        )
    }

    @Test
    fun `short links are recognised before the redirect`() {
        val short = "https://deezer.page.link/abc123"
        assertTrue(ProviderLinks.looksLikeProviderUrl(short))
        assertTrue(ProviderLinks.isShortLink(short))
        assertNull(ProviderLinks.parse(short))
    }

    @Test
    fun `ordinary search text is not a provider link`() {
        assertFalse(ProviderLinks.looksLikeProviderUrl("Cine n-are dor"))
        assertNull(ProviderLinks.parse("Cine n-are dor"))
        assertFalse(ProviderLinks.looksLikeProviderUrl("https://soundcloud.com/artist/track"))
    }

    @Test
    fun `a link that is still being typed is not a link yet`() {
        assertNull(ProviderLinks.parse("https://www.qobuz.com/us-en/album/good-girl-go/3"))
        assertNull(ProviderLinks.parse("qobuz:album:3"))
    }
}
