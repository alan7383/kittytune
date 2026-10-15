package com.alananasss.kittytune.data.artist

import org.junit.Assert.assertEquals
import org.junit.Test

/** Which search results count as the artist's own music videos. */
class ArtistClipsTest {

    private fun clip(title: String, uploader: String, durationSec: Long = 220L) =
        Clip("https://youtube.com/watch?v=" + title.hashCode(), title, uploader, null, durationSec, 1_000L)

    @Test
    fun `keeps the artist's own videos and drops everyone else's`() {
        val found = listOf(
            clip("Rihanna - Umbrella (Official Music Video) ft. JAY-Z", "Rihanna"),
            clip("Shakira - Can't Remember to Forget You ft. Rihanna", "Shakira"),
            clip("Rihanna - Diamonds", "RihannaVEVO"),
            clip("Rihanna - Stay (Lyrics)", "Lyrics Channel"),
            clip("Umbrella", "Rihanna - Topic"),
            clip("Rihanna - Work (Teaser)", "Rihanna", durationSec = 30L),
            clip("Rihanna - Umbrella (Orange Version)", "Rihanna"),
        )
        assertEquals(
            listOf("Rihanna - Umbrella (Official Music Video) ft. JAY-Z", "Rihanna - Diamonds"),
            ArtistClips.pickClips(ArtistClips.namesOf("Rihanna"), found).map { it.title },
        )
    }

    @Test
    fun `a title starting with the artist counts even from another channel`() {
        val found = listOf(clip("9mice - NEW-YORK (клип)", "Some Label"), clip("NEW-YORK", "Some Label"))
        assertEquals(listOf("9mice - NEW-YORK (клип)"), ArtistClips.pickClips(ArtistClips.namesOf("9mice"), found).map { it.title })
    }

    @Test
    fun `a duo is looked for as each member, whatever joins their names`() {
        assertEquals(listOf("Kai Angel & 9mice", "Kai Angel", "9mice"), ArtistClips.namesOf("Kai Angel & 9mice"))
        assertEquals(listOf("A / B", "A", "B"), ArtistClips.namesOf("A / B"))
        assertEquals(listOf("A + B", "A", "B"), ArtistClips.namesOf("A + B"))
        assertEquals(listOf("A, B", "A", "B"), ArtistClips.namesOf("A, B"))
        assertEquals(listOf("Rihanna"), ArtistClips.namesOf("Rihanna"))
    }

    @Test
    fun `a duo keeps each member's own videos, not only the ones they made together`() {
        val found = listOf(
            clip("Kai Angel & 9mice - TV (Official Video)", "kai angel"),
            clip("Kai Angel - gladiator (Official Music Video)", "kai angel"),
            clip("9mice - u+me (official music video)", "9mice"),
            clip("Kai Angel - jennifer's body (текст)", "Blademp3"),
            clip("Kai Angel - PRADA PARTY (Official Music Video)", "viperrviperrviperr"),
            clip("Kai Angel - gladiator (slowed + reverb)", "Some Channel"),
            clip("Kai Angel & 9mice - Pitch Black (Visualizer)", "kai angel"),
            clip("Someone Else - Kai Angel type beat", "Beats"),
        )
        val titles = ArtistClips.pickClips(ArtistClips.namesOf("Kai Angel & 9mice"), found).map { it.title }
        assertEquals(
            listOf(
                "Kai Angel & 9mice - TV (Official Video)",
                "Kai Angel - gladiator (Official Music Video)",
                "9mice - u+me (official music video)",
                "Kai Angel - PRADA PARTY (Official Music Video)",
            ),
            titles,
        )
    }

    @Test
    fun `a video marked as one comes before a bare title`() {
        val found = listOf(clip("Rihanna - Diamonds", "Rihanna"), clip("Rihanna - Umbrella (Official Music Video)", "Rihanna"))
        assertEquals(
            listOf("Rihanna - Umbrella (Official Music Video)", "Rihanna - Diamonds"),
            ArtistClips.pickClips(listOf("Rihanna"), found).map { it.title },
        )
    }
}
