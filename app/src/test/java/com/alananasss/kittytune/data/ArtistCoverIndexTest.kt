package com.alananasss.kittytune.data

import com.alananasss.kittytune.data.stats.ArtistCoverIndex
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ArtistCoverIndexTest {
    @Test fun `global track rank wins over credit arrival order`() {
        val index = ArtistCoverIndex(listOf(1L to "first", 2L to "second"))
        assertEquals("first", index.coverFor(listOf(2L, 1L)))
    }
    @Test fun `a missing cover on the top track stays missing`() {
        assertNull(ArtistCoverIndex(listOf(1L to null, 2L to "second")).coverFor(listOf(2L, 1L)))
    }
    @Test fun `missing IDs and empty credits return no cover`() {
        val index = ArtistCoverIndex(listOf(1L to "cover"))
        assertNull(index.coverFor(emptyList()))
        assertNull(index.coverFor(listOf(-1L, 99L)))
    }
    @Test fun `duplicate credits and duplicate input tracks keep first rank`() {
        val index = ArtistCoverIndex(listOf(1L to "first", 1L to "later", 2L to "second"))
        assertEquals("first", index.coverFor(listOf(2L, 1L, 1L)))
    }
    @Test fun `shared tracks give collaborators the same ranked fallback`() {
        val index = ArtistCoverIndex(listOf(3L to "shared", 1L to "solo", 2L to "other"))
        assertEquals("shared", index.coverFor(listOf(1L, 3L)))
        assertEquals("shared", index.coverFor(listOf(2L, 3L)))
    }
    @Test fun `randomized credit sets exactly match the original nested scan`() {
        val random = Random(143)
        repeat(200) {
            val tracks = List(random.nextInt(0, 120)) {
                random.nextLong(-5, 120) to if (random.nextBoolean()) null else "cover$it"
            }
            val index = ArtistCoverIndex(tracks)
            repeat(10) {
                val credits = List(random.nextInt(0, 100)) { random.nextLong(-5, 150) }
                val expected = tracks.firstOrNull { track -> credits.any { it == track.first } }?.second
                assertEquals(expected, index.coverFor(credits))
            }
        }
    }
}
