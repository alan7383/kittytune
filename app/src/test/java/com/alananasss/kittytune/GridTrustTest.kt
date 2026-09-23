package com.alananasss.kittytune

import com.alananasss.kittytune.data.local.BeatInfoEntity
import com.alananasss.kittytune.data.local.GridTrust
import com.alananasss.kittytune.data.local.gridTrust
import com.alananasss.kittytune.data.local.trustedAnchorMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The downbeat classifier is a hand-tuned template match and it is not uniformly reliable:
 * across 139 analysed tracks its margin was above 0.70 on 47 % and below 0.30 on 20 %. These
 * tests pin down that a cue point only claims as much precision as the evidence supports.
 */
class GridTrustTest {

    private fun row(
        downbeat: Float,
        phrase: Float,
        firstBeat: Long = 100,
        downbeatMs: Long = 600,
        phraseMs: Long = 2600,
    ) = BeatInfoEntity(
        songId = "t",
        bpm = 128f,
        firstBeatOffsetMs = firstBeat,
        confidence = 0.9f,
        downbeatOffsetMs = downbeatMs,
        phraseOffsetMs = phraseMs,
        downbeatConfidence = downbeat,
        phraseConfidence = phrase,
        analysisVersion = BeatInfoEntity.CURRENT_ANALYSIS_VERSION,
    )

    @Test
    fun `solid evidence earns a phrase claim`() {
        val r = row(downbeat = 0.93f, phrase = 0.60f)
        assertEquals(GridTrust.PHRASE, r.gridTrust)
        assertEquals(16, r.gridTrust.quantizeBeats)
        assertEquals(2600L, r.trustedAnchorMs)
    }

    @Test
    fun `a known downbeat with an unreadable arrangement drops to bars`() {
        // Happens on tracks with a clear backbeat but no sectional contrast — the phrase
        // position has no evidence even though beat 1 does.
        val r = row(downbeat = 0.85f, phrase = 0.05f)
        assertEquals(GridTrust.BAR, r.gridTrust)
        assertEquals(4, r.gridTrust.quantizeBeats)
        assertEquals("Bars are counted from the downbeat, not the phrase", 600L, r.trustedAnchorMs)
    }

    @Test
    fun `a guessed downbeat claims nothing beyond the beat`() {
        // The measured worst cases: margins of 0.00-0.14. Quantizing those to 16 beats puts
        // the drop somewhere arbitrary while looking exactly as authoritative as a real one.
        val r = row(downbeat = 0.08f, phrase = 0.90f)
        assertEquals(GridTrust.BEAT, r.gridTrust)
        assertEquals(1, r.gridTrust.quantizeBeats)
        assertEquals("Only the plain beat grid is trustworthy here", 100L, r.trustedAnchorMs)
    }

    @Test
    fun `a confident downbeat does not vouch for the phrase`() {
        // The two questions have different evidence and fail independently.
        assertEquals(GridTrust.BAR, row(downbeat = 1.0f, phrase = 0.0f).gridTrust)
        assertEquals(GridTrust.BEAT, row(downbeat = 0.0f, phrase = 1.0f).gridTrust)
    }

    @Test
    fun `rows from before confidence was recorded keep working`() {
        // analysisVersion 1 rows written before phraseConfidence existed have both at 0 but a
        // real phrase offset. Downgrading them would change behaviour for tracks nobody
        // complained about.
        val legacy = BeatInfoEntity(
            songId = "old",
            bpm = 128f,
            firstBeatOffsetMs = 100,
            confidence = 0.9f,
            downbeatOffsetMs = 600,
            phraseOffsetMs = 2600,
            downbeatConfidence = 0f,
            phraseConfidence = 0f,
        )
        assertEquals(GridTrust.PHRASE, legacy.gridTrust)
        assertEquals(2600L, legacy.trustedAnchorMs)
    }

    @Test
    fun `a row with no downbeat data at all falls back to the beat grid`() {
        val bare = BeatInfoEntity(
            songId = "bare",
            bpm = 128f,
            firstBeatOffsetMs = 100,
            confidence = 0.9f,
        )
        assertEquals(GridTrust.BEAT, bare.gridTrust)
        assertEquals(100L, bare.trustedAnchorMs)
    }

    @Test
    fun `each step down is a real subdivision of the one above`() {
        // Bars divide phrases and beats divide bars, so dropping a level never lands the cue
        // somewhere the higher level would have called wrong - only somewhere less ambitious.
        assertEquals(0, GridTrust.PHRASE.quantizeBeats % GridTrust.BAR.quantizeBeats)
        assertEquals(0, GridTrust.BAR.quantizeBeats % GridTrust.BEAT.quantizeBeats)
        assertTrue(GridTrust.PHRASE.quantizeBeats > GridTrust.BAR.quantizeBeats)
        assertTrue(GridTrust.BAR.quantizeBeats > GridTrust.BEAT.quantizeBeats)
    }
}
