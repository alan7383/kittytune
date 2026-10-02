package com.alananasss.kittytune

import com.alananasss.kittytune.data.DataSaver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The one traffic switch: [DataSaver.lightArtwork] is the pure half of it — it rewrites
 * cover URLs to their light variants while the saver is active. The audio half (quality
 * pinning in StreamResolver) needs a Context and is exercised by manual QA instead.
 */
class DataSaverTest {

    // ─── SoundCloud ──────────────────────────────────────────────────────────────

    @Test
    fun `soundcloud original collapses to t200x200`() {
        assertEquals(
            "https://i1.sndcdn.com/artworks-000123456-t200x200.jpg",
            DataSaver.lightArtwork("https://i1.sndcdn.com/artworks-000123456-original.jpg")
        )
    }

    @Test
    fun `soundcloud t500x500 collapses to t200x200`() {
        assertEquals(
            "https://i1.sndcdn.com/artworks-000123456-t200x200.jpg",
            DataSaver.lightArtwork("https://i1.sndcdn.com/artworks-000123456-t500x500.jpg")
        )
    }

    @Test
    fun `soundcloud avatar large size is left alone`() {
        // "-large." is only ~100px already; rewriting it would be pointless churn
        val url = "https://i1.sndcdn.com/avatars-000987654-large.jpg"
        assertEquals(url, DataSaver.lightArtwork(url))
    }

    // ─── Deezer ──────────────────────────────────────────────────────────────────

    @Test
    fun `deezer 1000x1000 collapses to 200x200`() {
        assertEquals(
            "https://e-cdns-images.dzcdn.net/images/cover/ab12cd34/200x200-000000-80-0-0.jpg",
            DataSaver.lightArtwork("https://e-cdns-images.dzcdn.net/images/cover/ab12cd34/1000x1000-000000-80-0-0.jpg")
        )
    }

    @Test
    fun `deezer 500x500 collapses to 200x200`() {
        assertEquals(
            "https://e-cdns-images.dzcdn.net/images/cover/ab12cd34/200x200-000000-80-0-0.jpg",
            DataSaver.lightArtwork("https://e-cdns-images.dzcdn.net/images/cover/ab12cd34/500x500-000000-80-0-0.jpg")
        )
    }

    // ─── TIDAL ───────────────────────────────────────────────────────────────────

    @Test
    fun `tidal 1280x1280 collapses to 320x320`() {
        assertEquals(
            "https://resources.tidal.com/images/9f8e7d6c/320x320.jpg",
            DataSaver.lightArtwork("https://resources.tidal.com/images/9f8e7d6c/1280x1280.jpg")
        )
    }

    // ─── Apple Music ─────────────────────────────────────────────────────────────

    @Test
    fun `apple music any size collapses to 200x200bb`() {
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/Music112/v4/ab/200x200bb.jpg",
            DataSaver.lightArtwork("https://is1-ssl.mzstatic.com/image/thumb/Music112/v4/ab/600x600bb.jpg")
        )
    }

    // ─── YouTube ─────────────────────────────────────────────────────────────────

    @Test
    fun `youtube maxres collapses to hq`() {
        assertEquals(
            "https://i.ytimg.com/vi/abc123/hqdefault.jpg",
            DataSaver.lightArtwork("https://i.ytimg.com/vi/abc123/maxresdefault.jpg")
        )
    }

    @Test
    fun `youtube hq is already light enough`() {
        val url = "https://i.ytimg.com/vi/abc123/hqdefault.jpg"
        assertEquals(url, DataSaver.lightArtwork(url))
    }

    // ─── Passthroughs ────────────────────────────────────────────────────────────

    @Test
    fun `local files and unknown hosts pass through untouched`() {
        assertEquals("/storage/emulated/0/cover.jpg", DataSaver.lightArtwork("/storage/emulated/0/cover.jpg"))
        val picsum = "https://picsum.photos/200"
        assertEquals(picsum, DataSaver.lightArtwork(picsum))
        val unknown = "https://example.com/some-cover-500x500.jpg"
        assertEquals(unknown, DataSaver.lightArtwork(unknown))
    }

    @Test
    fun `null and blank urls stay null or blank`() {
        assertNull(DataSaver.lightArtwork(null))
        assertEquals("", DataSaver.lightArtwork(""))
    }
}
