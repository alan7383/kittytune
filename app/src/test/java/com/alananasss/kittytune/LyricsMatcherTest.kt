package com.alananasss.kittytune

import com.alananasss.kittytune.data.LyricsMatcher
import com.alananasss.kittytune.ui.player.lyrics.LyricLine
import com.alananasss.kittytune.ui.player.lyrics.LyricWord
import com.alananasss.kittytune.ui.player.lyrics.LyricsUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which provider results count as the track being played.
 *
 * What these protect: on SoundCloud the "artist" is the account that uploaded the file, so a
 * re-upload of a well known song carries an unrelated artist and a title padded with
 * `(Official Video)`-style noise. Requiring the title *and* the artist to match, then discarding
 * anything more than fifteen seconds off in duration, is what made those tracks come back with no
 * lyrics until the user edited the artist name by hand (issue #33). Loosening it too far is the
 * opposite failure — the lyrics of a different song, confidently displayed.
 */
class LyricsMatcherTest {

    private val target = LyricsMatcher.Target(
        title = "Get Lucky",
        artist = "Daft Punk",
        durationMs = 248_000L,
    )

    @Test
    fun `exact match is accepted`() {
        assertTrue(LyricsMatcher.isAcceptable("Get Lucky", "Daft Punk", target))
    }

    /** The re-upload case: right song, an account name instead of an artist. */
    @Test
    fun `title alone carries a mismatched uploader`() {
        assertTrue(LyricsMatcher.isAcceptable("Get Lucky", "xX_bootleg_uploads_Xx", target))
    }

    @Test
    fun `packaging noise in the title does not block the match`() {
        assertTrue(
            LyricsMatcher.isAcceptable(
                "Get Lucky (Official Video) [FREE DL] HQ",
                "Some Reupload Channel",
                target,
            )
        )
    }

    @Test
    fun `featured credits are ignored on both sides`() {
        assertTrue(
            LyricsMatcher.isAcceptable(
                "Get Lucky feat. Pharrell Williams",
                "Daft Punk ft. Nile Rodgers",
                target,
            )
        )
    }

    @Test
    fun `an unrelated song is rejected even from the right artist`() {
        assertFalse(LyricsMatcher.isAcceptable("Harder Better Faster Stronger", "Daft Punk", target))
    }

    @Test
    fun `an unrelated song from an unrelated artist is rejected`() {
        assertFalse(LyricsMatcher.isAcceptable("Bohemian Rhapsody", "Queen", target))
    }

    /** Accents differ between providers far more often than the words do. */
    @Test
    fun `diacritics do not change the verdict`() {
        val accented = LyricsMatcher.Target("Déjà Vu", "Beyoncé", 200_000L)
        assertTrue(LyricsMatcher.isAcceptable("Deja Vu", "Beyonce", accented))
    }

    @Test
    fun `word order in an artist credit does not matter`() {
        assertTrue(LyricsMatcher.similarity("Rodgers Nile", "Nile Rodgers") > 0.9f)
    }

    /** The whole point of separating the two: the exact title has to outrank the vague one. */
    @Test
    fun `the closer title scores higher`() {
        val exact = LyricsMatcher.score("Get Lucky", "Daft Punk", 248.0, target)
        val vague = LyricsMatcher.score("Get Lucky (Remix)", "Someone Else", 248.0, target)
        assertTrue("exact=$exact vague=$vague", exact > vague)
    }

    /**
     * Duration ranks, it no longer excludes: a provider entry with no duration used to be thrown
     * away or kept purely by luck, and a live version a minute longer than the studio cut is still
     * the same words.
     */
    @Test
    fun `a missing duration is not held against a candidate`() {
        val withDuration = LyricsMatcher.score("Get Lucky", "Daft Punk", 248.0, target)
        val withoutDuration = LyricsMatcher.score("Get Lucky", "Daft Punk", 0.0, target)
        assertTrue(withDuration > withoutDuration)
        assertTrue(LyricsMatcher.isAcceptable("Get Lucky", "Daft Punk", target))
        assertTrue("scored $withoutDuration", withoutDuration > 0.6f)
    }

    @Test
    fun `a far-off duration lowers the score without disqualifying the match`() {
        val close = LyricsMatcher.score("Get Lucky", "Daft Punk", 248.0, target)
        val far = LyricsMatcher.score("Get Lucky", "Daft Punk", 600.0, target)
        assertTrue(close > far)
        assertTrue(far > 0.5f)
    }

    @Test
    fun `empty fields never match`() {
        assertFalse(LyricsMatcher.isAcceptable("", "", target))
        assertFalse(LyricsMatcher.isAcceptable(null, null, target))
    }

    // --- the wrong song on screen (issue #33) -------------------------------------------------
    //
    // "When I play a song, it shows the lyrics for a completely different song, although if I do a
    // manual search, it will immediately show the correct song." The automatic lookup works through
    // progressively looser queries and takes the best usable candidate, so the only thing standing
    // between it and an unrelated lyric sheet is this comparison. It used to be a plain substring
    // test on the whole string, and a short title is a substring of almost anything.

    private val messy = LyricsMatcher.Target("fortuna 812 go with me", "xxxqs0", 180_000L)

    @Test
    fun `a short word inside a long title is not a match`() {
        // "go" sits inside the target, and used to score 0.9 for it.
        assertTrue(LyricsMatcher.similarity("Go", messy.title) < 0.35f)
        assertFalse(LyricsMatcher.isAcceptable("Go", "Someone Else", messy))
    }

    @Test
    fun `a short shared word out of several is not a match`() {
        assertFalse(LyricsMatcher.isAcceptable("Me", "Another Artist", messy))
        assertTrue(LyricsMatcher.similarity("Me", messy.title) < 0.35f)
    }

    /**
     * A lone word from a multi-word messy title with an unrelated artist must not be accepted.
     */
    @Test
    fun `one word out of a five word title from an unrelated artist is rejected`() {
        val partial = LyricsMatcher.similarity("Fortuna", messy.title)
        val whole = LyricsMatcher.similarity("fortuna 812 go with me", messy.title)
        assertFalse(LyricsMatcher.isAcceptable("Fortuna", "Another Artist", messy))
        assertTrue("partial=$partial whole=$whole", partial < whole)
    }

    @Test
    fun `right song plain lyrics outranks a different song by same artist with word sync`() {
        val daftPunkTarget = LyricsMatcher.Target("One More Time", "Daft Punk", 320_000L)
        // Candidate 1: Genius has the exact right song as plain text
        val rightSongScore = LyricsMatcher.score("One More Time", "Daft Punk", 320.0, daftPunkTarget)
        val rightSongTitleSim = LyricsMatcher.titleSimilarity("One More Time", daftPunkTarget)
        val rightSongRank = LyricsMatcher.rank(
            syncTier = LyricsMatcher.SYNC_TIER_PLAIN,
            matchScore = rightSongScore,
            titleSimilarity = rightSongTitleSim,
        )

        // Candidate 2: Musixmatch has another song by the same artist ("Another Time" or similar) with word sync
        val partialSongScore = LyricsMatcher.score("Another Time", "Daft Punk", 320.0, daftPunkTarget)
        val partialSongTitleSim = LyricsMatcher.titleSimilarity("Another Time", daftPunkTarget)
        val partialSongRank = LyricsMatcher.rank(
            syncTier = LyricsMatcher.SYNC_TIER_WORD,
            matchScore = partialSongScore,
            titleSimilarity = partialSongTitleSim,
        )

        assertTrue(
            "Exact song on Genius (plain, rank=$rightSongRank) must beat different song by same artist (synced, rank=$partialSongRank)",
            rightSongRank > partialSongRank,
        )
    }

    @Test
    fun `a substring that is not a word is not a match`() {
        // "man" inside "mango": the old rule read the strings, not the words.
        val mango = LyricsMatcher.Target("Mango Season", "Someone", 200_000L)
        assertTrue(LyricsMatcher.similarity("Man", mango.title) < 0.35f)
    }

    @Test
    fun `the real title still matches through the packaging`() {
        val packaged = LyricsMatcher.Target("Song Name (Official Video) [FREE]", "Uploader", 200_000L)
        assertTrue(LyricsMatcher.similarity("Song Name", packaged.title) >= 0.85f)
        assertTrue(LyricsMatcher.isAcceptable("Song Name", "The Actual Artist", packaged))
    }

    @Test
    fun `a remix of the right song still matches`() {
        val plain = LyricsMatcher.Target("Song Name", "Artist", 200_000L)
        assertTrue(LyricsMatcher.isAcceptable("Song Name Remix", "Artist", plain))
    }

    @Test
    fun `a distinctive single word still carries`() {
        // One word, but a word rather than a syllable, so it may identify a song on its own.
        val queen = LyricsMatcher.Target("Bohemian Rhapsody", "Queen", 355_000L)
        assertTrue(LyricsMatcher.similarity("Bohemian", queen.title) >= 0.7f)
    }

    @Test
    fun `non latin titles compare on their own words`() {
        val cyrillic = LyricsMatcher.Target("Заправка", "Артист", 200_000L)
        assertTrue(LyricsMatcher.isAcceptable("Заправка", "Артист", cyrillic))
        assertFalse(LyricsMatcher.isAcceptable("Другая песня", "Кто-то", cyrillic))
    }

    @Test
    fun `soundCloud track with channel uploader correctly matches candidate parsed artist and title`() {
        val soundCloudTrack = LyricsMatcher.Target(
            title = "Resonance",
            artist = "HOME",
            durationMs = 212_000L,
            alternativeTitles = listOf("HOME - Resonance", "Resonance"),
            alternativeArtists = listOf("Electronic Gems", "HOME"),
        )
        val rightSongScore = LyricsMatcher.score("Resonance", "HOME", 212.0, soundCloudTrack)
        val strangerSongScore = LyricsMatcher.score("Resonance", "Completely Different Artist", 212.0, soundCloudTrack)
        assertTrue("right song should score >= 0.95f, was $rightSongScore", rightSongScore >= 0.95f)
        assertTrue("stranger song should score < 0.78f, was $strangerSongScore", strangerSongScore < 0.78f)
        assertTrue(LyricsMatcher.isAcceptable("Resonance", "HOME", soundCloudTrack))
    }
}

/**
 * How much timing a provider result really carries.
 *
 * What these protect: a provider with the words but no timings still answers with a whole list of
 * lines — a Musixmatch subtitle whose entries carry no time, an LRC where every stamp is
 * `[00:00.00]`. Ranking on line count alone let that beat a genuinely synced result from the other
 * provider, which is the "switches to the version without synchronisation even though a
 * synchronised one exists" report in issue #33.
 */
class LyricsSyncTierTest {

    private fun line(text: String, startMs: Long, words: List<LyricWord> = emptyList()) =
        LyricLine(text = text, startTime = startMs, endTime = startMs + 2_000L, words = words)

    @Test
    fun `advancing line timings are line-synced`() {
        val lines = listOf(line("one", 0), line("two", 1_500), line("three", 3_000))
        assertEquals(LyricsMatcher.SYNC_TIER_LINE, LyricsMatcher.syncTier(lines, null))
    }

    @Test
    fun `word timings are word-synced`() {
        val lines = listOf(
            line("one two", 0, listOf(LyricWord("one", 0, 500), LyricWord("two", 500, 1_000))),
            line("three", 1_500),
        )
        assertEquals(LyricsMatcher.SYNC_TIER_WORD, LyricsMatcher.syncTier(lines, null))
    }

    /** The exact shape reported: many lines, every one of them stamped at zero. */
    @Test
    fun `lines that all start at zero are not synced`() {
        val lines = listOf(line("one", 0), line("two", 0), line("three", 0))
        assertEquals(LyricsMatcher.SYNC_TIER_PLAIN, LyricsMatcher.syncTier(lines, null))
    }

    @Test
    fun `a single timed line is not synced`() {
        assertEquals(LyricsMatcher.SYNC_TIER_PLAIN, LyricsMatcher.syncTier(listOf(line("only", 0)), null))
    }

    @Test
    fun `plain text alone is the lowest usable tier`() {
        assertEquals(LyricsMatcher.SYNC_TIER_PLAIN, LyricsMatcher.syncTier(emptyList(), "the words"))
    }

    @Test
    fun `nothing at all is not usable`() {
        assertEquals(LyricsMatcher.SYNC_TIER_NONE, LyricsMatcher.syncTier(emptyList(), null))
        assertEquals(LyricsMatcher.SYNC_TIER_NONE, LyricsMatcher.syncTier(emptyList(), "   "))
    }

    /** The comparison that decides the reported case: real sync has to outrank the fake kind. */
    @Test
    fun `genuine sync outranks a same-length untimed result`() {
        val untimed = List(20) { line("line $it", 0) }
        val synced = List(20) { line("line $it", it * 2_000L) }
        assertTrue(
            LyricsMatcher.syncTier(synced, null) > LyricsMatcher.syncTier(untimed, "the words")
        )
    }
}

/**
 * Which lyric line counts as the current one.
 *
 * What these protect: a word-synced result carries each line's real start and end, so the intervals
 * leave gaps over instrumental breaks and can overlap one another. Selecting the first interval that
 * contained the position picked an earlier line than the one being sung, and the view jumped
 * backwards through the song — the "jumps around across the entire text" report in issue #33.
 */
class LyricsActiveLineTest {

    private fun line(startMs: Long, endMs: Long) =
        LyricLine(text = "l", startTime = startMs, endTime = endMs)

    /** Contiguous, as LRC parsing produces. */
    private val contiguous = listOf(line(0, 1_000), line(1_000, 2_000), line(2_000, 3_000))

    /** Real word-sync: each line ends before the next begins. */
    private val gapped = listOf(line(0, 800), line(2_000, 2_600), line(5_000, 5_900))

    /** Overlapping, which Musixmatch richsync does produce. */
    private val overlapping = listOf(line(0, 3_000), line(1_000, 2_000), line(2_000, 4_000))

    @Test
    fun `before the first line there is no active line`() {
        assertEquals(-1, LyricsUtils.activeLineIndex(gapped, 0L - 1))
    }

    @Test
    fun `inside a line that line is active`() {
        assertEquals(1, LyricsUtils.activeLineIndex(contiguous, 1_500))
    }

    /** A gap keeps the line that was singing rather than clearing the highlight. */
    @Test
    fun `in a gap the last started line stays active`() {
        assertEquals(0, LyricsUtils.activeLineIndex(gapped, 1_400))
        assertEquals(1, LyricsUtils.activeLineIndex(gapped, 3_500))
    }

    /** The reported case: containment would have answered 0 here, jumping backwards. */
    @Test
    fun `overlapping intervals do not select an earlier line`() {
        assertEquals(1, LyricsUtils.activeLineIndex(overlapping, 1_200))
        assertEquals(2, LyricsUtils.activeLineIndex(overlapping, 2_500))
    }

    @Test
    fun `past the end the last line stays active`() {
        assertEquals(2, LyricsUtils.activeLineIndex(gapped, 90_000))
    }

    /** The property the fix is really about: it can never go backwards as the song plays on. */
    @Test
    fun `the active line never moves backwards`() {
        listOf(contiguous, gapped, overlapping).forEach { lines ->
            var previous = -1
            for (pos in 0L..6_000L step 25L) {
                val index = LyricsUtils.activeLineIndex(lines, pos)
                assertTrue("went back to $index from $previous at $pos", index >= previous)
                previous = index
            }
        }
    }
}
