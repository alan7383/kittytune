package com.alananasss.kittytune

import com.alananasss.kittytune.data.StreamResolver
import com.alananasss.kittytune.data.YoutubeFallbackMatcher as Matcher
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.zionhuang.innertube.models.Artist
import com.zionhuang.innertube.models.SongItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class YoutubeFallbackMatcherTest {
    private val tatu = Matcher.Recording("All The Things She Said", "t.A.T.u.", 214, restricted = true)
    private val remix = Matcher.Recording("Strongest (Alan Walker Remix)", "Ina Wroldsen", 210, restricted = true)
    private fun candidate(id: String, title: String, artist: String, duration: Long = 214) =
        Matcher.Candidate(id, title, listOf(artist), duration)
    private fun track(title: String = tatu.title, artist: String = tatu.artist, fullDuration: Long? = 214_000) =
        Track(76, title, null, 30_000, User(1, artist, null), policy = "SNIP", fullDuration = fullDuration)
    private fun song(id: String, title: String, artist: String, duration: Int = 214) =
        SongItem(id, title, listOf(Artist(artist, null)), duration = duration, thumbnail = "")

    @Test fun `exact artist beats a cover whose duration is closer`() {
        val choices = listOf(candidate("cover", tatu.title, "First To Eleven"), candidate("original", tatu.title, "t.A.T.u.", 218))
        assertEquals("original", Matcher.match(tatu, choices)?.id)
    }

    @Test fun `a different song with the same duration is rejected`() {
        assertNull(Matcher.match(tatu, listOf(candidate("wrong", "All About Us", "t.A.T.u."))))
    }

    @Test fun `requested named remix beats the original and other remixes`() {
        val choices = listOf(candidate("original", "Strongest", remix.artist, 210),
            candidate("other remix", "Strongest (Tiesto Remix)", remix.artist, 210),
            candidate("correct", remix.title, remix.artist, 216))
        assertEquals("correct", Matcher.match(remix, choices)?.id)
    }

    @Test fun `a remix is never silently substituted with the original`() {
        assertNull(Matcher.match(remix, listOf(candidate("wrong", "Strongest", remix.artist, 210))))
    }

    @Test fun `the original is never silently substituted with an unrequested remix`() {
        assertNull(Matcher.match(remix.copy(title = "Strongest"), listOf(candidate("wrong", remix.title, remix.artist, 210))))
    }

    @Test fun `recording qualifiers survive query construction`() {
        assertEquals("Strongest (Alan Walker Remix) Ina Wroldsen", Matcher.queries(remix).first())
        assertEquals("Song (Acoustic Version)", Matcher.searchTitle("Song (Acoustic Version) [Official Audio]"))
        assertEquals("Song (feat. Singer)", Matcher.searchTitle("Song (feat. Singer)"))
        assertEquals("Intro", Matcher.searchTitle("[Intro]"))
        assertTrue(Matcher.queries(tatu.copy(title = "[ ]")).isEmpty())
    }

    @Test fun `unknown snippet duration still requires the same song and artist`() {
        val recording = tatu.copy(durationSec = 30, snippetDuration = true)
        assertEquals("original", Matcher.match(recording, listOf(candidate("wrong", "Not Gonna Get Us", tatu.artist),
            candidate("preview", tatu.title, tatu.artist, 30), candidate("original", tatu.title, tatu.artist, 218)))?.id)
        assertNull(Matcher.match(recording, listOf(candidate("preview", tatu.title, tatu.artist, 30))))
    }

    @Test fun `live acoustic karaoke and slowed versions are rejected unless requested`() {
        for (edition in listOf("Live", "Acoustic", "Karaoke", "Sped Up", "Slowed", "Cover", "Extended", "8D")) {
            assertNull(edition, Matcher.match(tatu, listOf(candidate("wrong", "${tatu.title} ($edition)", tatu.artist))))
        }
    }

    @Test fun `case punctuation diacritics and topic channels do not change artist identity`() {
        assertEquals("original", Matcher.match(tatu, listOf(candidate("original", "ALL THE THINGS SHE SAID (Official Music Video)", "tatu - Topic", 218)))?.id)
        val requested = tatu.copy(artist = "Beyoncé")
        assertNotNull(Matcher.match(requested, listOf(candidate("original", tatu.title, "BeyonceVEVO"))))
    }

    @Test fun `label uploads may identify the artist in the video title`() {
        assertNotNull(Matcher.match(tatu, listOf(candidate("video", "t.A.T.u. - All The Things She Said (Official Video)", "Universal Music", 218))))
        assertNull(Matcher.match(tatu, listOf(candidate("cover", "Other Artist - All The Things She Said", "Universal Music"))))
    }

    @Test fun `known duration excludes teasers and long compilations`() {
        assertNull(Matcher.match(tatu, listOf(candidate("preview", tatu.title, tatu.artist, 30))))
        assertNull(Matcher.match(tatu, listOf(candidate("compilation", tatu.title, tatu.artist, 3600))))
    }

    @Test fun `short genuine tracks with known full duration remain usable`() {
        val recording = tatu.copy(title = "Intro", durationSec = 35)
        assertNotNull(Matcher.match(recording, listOf(candidate("short", "Intro", tatu.artist, 35))))
    }

    @Test fun `an unknown candidate duration cannot defeat an exact metadata match`() {
        assertEquals("exact", Matcher.match(tatu, listOf(candidate("exact", tatu.title, tatu.artist, 0),
            candidate("less exact", "${tatu.title} 2002", tatu.artist, 214)))?.id)
    }

    @Test fun `album metadata can identify the requested remix`() {
        val choice = candidate("correct", "Strongest", remix.artist, 216).copy(album = remix.title)
        assertEquals("correct", Matcher.match(remix, listOf(choice))?.id)
        assertNull(Matcher.match(remix.copy(title = "Strongest"), listOf(choice)))
    }

    @Test fun `selection does not depend on YouTube result order`() {
        val choices = listOf(candidate("wrong artist", tatu.title, "Other Artist"), candidate("cover", "${tatu.title} (Cover)", tatu.artist),
            candidate("wrong title", "All About Us", tatu.artist), candidate("correct", tatu.title, tatu.artist, 218))
        repeat(100) { assertEquals("correct", Matcher.match(tatu, choices.shuffled(Random(it)))?.id) }
    }

    @Test fun `empty first search page still tries the second query`() = runBlocking {
        val calls = mutableListOf<String>()
        val result = StreamResolver.findYoutubeFallbackSong(track()) { query ->
            calls += query
            if (calls.size == 1) emptyList() else listOf(song("correct", tatu.title, tatu.artist))
        }
        assertEquals(2, calls.size)
        assertEquals("correct", result?.id)
    }

    @Test fun `unrelated successful first response does not mask an exact second page`() = runBlocking {
        var call = 0
        val result = StreamResolver.findYoutubeFallbackSong(track(remix.title, remix.artist, null)) {
            if (++call == 1) listOf(song("wrong", "Strongest", remix.artist)) else listOf(song("correct", remix.title, remix.artist))
        }
        assertEquals("correct", result?.id)
    }

    @Test fun `search failure continues with another query but cancellation propagates`() = runBlocking {
        var call = 0
        assertEquals("correct", StreamResolver.findYoutubeFallbackSong(track()) {
            if (++call == 1) error("Unavailable") else listOf(song("correct", tatu.title, tatu.artist))
        }?.id)
        try {
            StreamResolver.findYoutubeFallbackSong(track()) { throw kotlinx.coroutines.CancellationException("cancelled") }
            fail("Cancellation must propagate")
        } catch (_: kotlinx.coroutines.CancellationException) { }
    }

    @Test fun `production song adapter uses metadata instead of duration alone`() {
        val selected = StreamResolver.matchSongCandidate(listOf(song("wrong", tatu.title, "Cover Artist"),
            song("correct", tatu.title, tatu.artist, 218)), 214, true, false, track())
        assertEquals("correct", selected?.id)
    }

    @Test fun `production video adapter also rejects same length covers`() {
        fun video(id: String, title: String, channel: String) = org.schabi.newpipe.extractor.stream.StreamInfoItem(
            0, "https://youtube.com/watch?v=$id", title, org.schabi.newpipe.extractor.stream.StreamType.VIDEO_STREAM
        ).apply { duration = 218; uploaderName = channel }
        val selected = StreamResolver.matchVideoCandidate(listOf(
            video("wrong", tatu.title, "Cover Artist"),
            video("correct", "t.A.T.u. – All The Things She Said (Official Video)", "Universal Music")
        ), 214, true, false, track())
        assertEquals("https://youtube.com/watch?v=correct", selected?.url)
    }

    @Test fun `presentation labels do not erase real song titles`() {
        val recording = tatu.copy(title = "Audio")
        assertNotNull(Matcher.match(recording, listOf(candidate("correct", "Audio", tatu.artist))))
        assertNull(Matcher.match(tatu, listOf(candidate("teaser", "${tatu.title} (Teaser)", tatu.artist, 0))))
    }
}
