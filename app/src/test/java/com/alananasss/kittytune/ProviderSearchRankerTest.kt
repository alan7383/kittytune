package com.alananasss.kittytune

import com.alananasss.kittytune.data.search.ProviderSearchRanker
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.TrackPublisherMetadata
import com.alananasss.kittytune.domain.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProviderSearchRankerTest {

    @Test
    fun `unrelated provider results are removed`() {
        val ranked = ProviderSearchRanker.rankTracks(
            "Cine n-are dor",
            listOf(
                track("Canta, Canta Minha Gente", "Sambabook"),
                track("Cine N-are Dor Pe Vale", "Steliana Sima"),
                track("Cine n-are dor", "Nicolae Paliț"),
            ),
        )

        assertEquals(2, ranked.size)
        assertFalse(ranked.any { it.title == "Canta, Canta Minha Gente" })
    }

    @Test
    fun `an exact title comes before longer versions`() {
        val ranked = ProviderSearchRanker.rankTracks(
            "Cine n-are dor",
            listOf(
                track("Cine N-are Dor Pe Vale", "Steliana Sima"),
                track("Cine n-are dor", "Nicolae Paliț"),
            ),
        )

        assertEquals("Cine n-are dor", ranked.first().title)
    }

    @Test
    fun `artist and album terms are searchable too`() {
        val ranked = ProviderSearchRanker.rankTracks(
            "daft punk discovery",
            listOf(
                track("Around the World", "Daft Punk", "Discovery"),
                track("No Surprises", "Radiohead", "OK Computer"),
            ),
        )

        assertEquals(listOf("Around the World"), ranked.map { it.title })
    }

    @Test
    fun `single words match whole tokens rather than substrings`() {
        val ranked = ProviderSearchRanker.rankTracks(
            "are",
            listOf(
                track("Care", "Someone"),
                track("Are You", "Someone"),
            ),
        )

        assertEquals(listOf("Are You"), ranked.map { it.title })
    }

    @Test
    fun `a query without spaces finds a spaced title`() {
        val ranked = ProviderSearchRanker.rankTracks(
            "newyork",
            listOf(
                track("New York", "Someone"),
                track("Paris", "Someone Else"),
            ),
        )

        assertEquals(listOf("New York"), ranked.map { it.title })
    }

    @Test
    fun `nothing matching leaves the original list alone`() {
        val original = listOf(
            track("First", "Someone"),
            track("Second", "Someone Else"),
        )

        assertEquals(original, ProviderSearchRanker.rankTracks("completely different", original))
    }

    private fun track(title: String, artist: String, album: String? = null) = Track(
        id = "$title|$artist".hashCode().toLong(),
        title = title,
        artworkUrl = null,
        durationMs = 0L,
        user = User(id = artist.hashCode().toLong(), username = artist, avatarUrl = null),
        publisherMetadata = TrackPublisherMetadata(artist = artist, albumTitle = album),
        source = "deezer",
    )
}
