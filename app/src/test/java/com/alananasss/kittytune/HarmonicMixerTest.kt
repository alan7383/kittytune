/**
 * Developed by Jason-Marshall Fastner, Germany <jasonfastner@protonmail.com>
 * Questions, feedback, or beat-matching debates? Feel free to reach out via email!
 * 
 * Note: Cats always land on their feet, and with this engine, your transitions will too.
 */
package com.alananasss.kittytune

import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.player.CamelotKey
import com.alananasss.kittytune.ui.player.EnergyMode
import com.alananasss.kittytune.ui.player.HarmonicMixer
import org.junit.Assert.*
import org.junit.Test

class HarmonicMixerTest {

    private fun createTrack(id: Long, title: String): Track {
        return Track(
            id = id,
            title = title,
            artworkUrl = null,
            durationMs = 210000L,
            user = null
        )
    }

    @Test
    fun testPerfectMatchInHoldMode() {
        val currentTrack = createTrack(1L, "Current Song")
        val candidateTrack = createTrack(2L, "Next Song")

        val match = HarmonicMixer.scoreCandidate(
            currentBpm = 125.0f,
            currentKey = CamelotKey.KEY_8A,
            candidateTrack = candidateTrack,
            candidateBpm = 125.0f,
            candidateKey = CamelotKey.KEY_8A,
            mode = EnergyMode.HOLD
        )

        assertEquals("Identical BPM and Key in HOLD mode should achieve 100.0 score", 100.0f, match.score, 0.1f)
        assertTrue(match.isHarmonic)
        assertEquals(0, match.harmonicDistance)
        assertEquals(0f, match.bpmDelta, 0.01f)
    }

    @Test
    fun testBuildUpEnergyPreference() {
        val currBpm = 124.0f
        val currKey = CamelotKey.KEY_8A

        val candRising = createTrack(2L, "Rising Track")
        val candDropping = createTrack(3L, "Dropping Track")

        // Candidate 1: +2.0 BPM (+1.6%) with adjacent key (9A)
        val matchRising = HarmonicMixer.scoreCandidate(
            currentBpm = currBpm,
            currentKey = currKey,
            candidateTrack = candRising,
            candidateBpm = 126.0f,
            candidateKey = CamelotKey.KEY_9A,
            mode = EnergyMode.BUILD_UP
        )

        // Candidate 2: -2.0 BPM (-1.6%) with adjacent key (7A)
        val matchDropping = HarmonicMixer.scoreCandidate(
            currentBpm = currBpm,
            currentKey = currKey,
            candidateTrack = candDropping,
            candidateBpm = 122.0f,
            candidateKey = CamelotKey.KEY_7A,
            mode = EnergyMode.BUILD_UP
        )

        assertTrue(
            "BUILD_UP mode should score tempo-increasing tracks higher than tempo-decreasing tracks (Rising: ${matchRising.score}, Dropping: ${matchDropping.score})",
            matchRising.score > matchDropping.score
        )
    }

    @Test
    fun testWindDownEnergyPreference() {
        val currBpm = 124.0f
        val currKey = CamelotKey.KEY_8A

        val candRising = createTrack(2L, "Faster Track")
        val candDropping = createTrack(3L, "Mellower Track")

        // Candidate 1: +2.0 BPM with adjacent key (9A)
        val matchRising = HarmonicMixer.scoreCandidate(
            currentBpm = currBpm,
            currentKey = currKey,
            candidateTrack = candRising,
            candidateBpm = 126.0f,
            candidateKey = CamelotKey.KEY_9A,
            mode = EnergyMode.WIND_DOWN
        )

        // Candidate 2: -2.0 BPM with adjacent key (7A)
        val matchDropping = HarmonicMixer.scoreCandidate(
            currentBpm = currBpm,
            currentKey = currKey,
            candidateTrack = candDropping,
            candidateBpm = 122.0f,
            candidateKey = CamelotKey.KEY_7A,
            mode = EnergyMode.WIND_DOWN
        )

        assertTrue(
            "WIND_DOWN mode should score tempo-reducing tracks higher than tempo-increasing tracks (Dropping: ${matchDropping.score}, Rising: ${matchRising.score})",
            matchDropping.score > matchRising.score
        )
    }

    @Test
    fun testBpmDeviationPenaltyBeyondEightPercent() {
        val currBpm = 120.0f
        val currKey = CamelotKey.KEY_8A
        val track = createTrack(2L, "Candidate")

        // Within 8% (e.g. 125 BPM -> +4.1%)
        val matchWithinLimit = HarmonicMixer.scoreCandidate(
            currentBpm = currBpm,
            currentKey = currKey,
            candidateTrack = track,
            candidateBpm = 125.0f,
            candidateKey = currKey,
            mode = EnergyMode.HOLD
        )

        // Exceeding 8% limit (e.g. 140 BPM -> +16.7%)
        val matchFarLimit = HarmonicMixer.scoreCandidate(
            currentBpm = currBpm,
            currentKey = currKey,
            candidateTrack = track,
            candidateBpm = 140.0f,
            candidateKey = currKey,
            mode = EnergyMode.HOLD
        )

        assertTrue(
            "Track within 8% should score significantly higher than track far outside 8% (within: ${matchWithinLimit.score}, outside: ${matchFarLimit.score})",
            matchWithinLimit.score > matchFarLimit.score + 25f
        )
    }

    @Test
    fun testHarmonicKeyHierarchyScoring() {
        val currBpm = 125.0f
        val currKey = CamelotKey.KEY_8A // Am
        val track = createTrack(2L, "Candidate")

        val matchSame = HarmonicMixer.scoreCandidate(currBpm, currKey, track, currBpm, CamelotKey.KEY_8A, EnergyMode.HOLD)
        val matchRelative = HarmonicMixer.scoreCandidate(currBpm, currKey, track, currBpm, CamelotKey.KEY_8B, EnergyMode.HOLD) // C
        val matchAdjacent = HarmonicMixer.scoreCandidate(currBpm, currKey, track, currBpm, CamelotKey.KEY_9A, EnergyMode.HOLD) // Em
        val matchTwoStep = HarmonicMixer.scoreCandidate(currBpm, currKey, track, currBpm, CamelotKey.KEY_10A, EnergyMode.HOLD) // Bm
        val matchClash = HarmonicMixer.scoreCandidate(currBpm, currKey, track, currBpm, CamelotKey.KEY_2A, EnergyMode.HOLD) // Ebm

        assertTrue(matchSame.score >= matchRelative.score)
        assertTrue(matchRelative.score >= matchAdjacent.score)
        assertTrue(matchAdjacent.score > matchTwoStep.score)
        assertTrue(matchTwoStep.score > matchClash.score)
        assertFalse(matchClash.isHarmonic)
    }

    @Test
    fun testHalftimeDoubletimeHandling() {
        val currBpm = 140.0f
        val currKey = CamelotKey.KEY_8A
        val track = createTrack(2L, "Half-time Candidate")

        // 70 BPM track in a 140 BPM context (1:2 ratio)
        val matchHalftime = HarmonicMixer.scoreCandidate(
            currentBpm = currBpm,
            currentKey = currKey,
            candidateTrack = track,
            candidateBpm = 70.0f,
            candidateKey = currKey,
            mode = EnergyMode.HOLD
        )

        assertTrue("Half-time track (70 BPM to 140 BPM) should be recognized as compatible", matchHalftime.score >= 90.0f)
    }

    @Test
    fun testRankCandidates() {
        val currBpm = 128.0f
        val currKey = CamelotKey.KEY_8A

        val t1 = createTrack(1L, "Clash Track")
        val t2 = createTrack(2L, "Perfect Track")
        val t3 = createTrack(3L, "Adjacent Track")

        val candidates = listOf(
            Triple(t1, 150.0f, CamelotKey.KEY_2A), // Bad BPM + clash key
            Triple(t2, 128.0f, CamelotKey.KEY_8A), // Exact BPM + exact key
            Triple(t3, 129.0f, CamelotKey.KEY_9A)  // Near BPM + adjacent key
        )

        val ranked = HarmonicMixer.rankCandidates(currBpm, currKey, candidates, EnergyMode.HOLD)

        assertEquals(3, ranked.size)
        assertEquals("Perfect Track should be ranked #1", t2.id, ranked[0].track.id)
        assertEquals("Adjacent Track should be ranked #2", t3.id, ranked[1].track.id)
        assertEquals("Clash Track should be ranked #3", t1.id, ranked[2].track.id)
    }

    @Test
    fun test15CandidatesRanking() {
        val currBpm = 126.0f
        val currKey = CamelotKey.KEY_8A

        val candidates = (1..15).map { i ->
            val bpm = 120.0f + (i * 0.8f)
            val key = CamelotKey.entries[i % CamelotKey.entries.size]
            Triple(createTrack(i.toLong(), "Track $i"), bpm, key)
        }

        val ranked = HarmonicMixer.rankCandidates(currBpm, currKey, candidates, EnergyMode.HOLD)
        assertEquals(15, ranked.size)
        // Ensure sorted strictly descending by score
        for (i in 0 until ranked.size - 1) {
            assertTrue("Ranked candidate $i should score >= candidate ${i + 1}", ranked[i].score >= ranked[i + 1].score)
        }
    }

    @Test
    fun testCuePointsPreservedInTrackMatch() {
        val candTrack = createTrack(42L, "Drop Track")
        val match = HarmonicMixer.scoreCandidate(
            currentBpm = 128f,
            currentKey = CamelotKey.KEY_8A,
            candidateTrack = candTrack,
            candidateBpm = 128f,
            candidateKey = CamelotKey.KEY_8A,
            mixInPointMs = 16_000L,
            mixOutPointMs = 180_000L
        )

        assertEquals(16_000L, match.mixInPointMs)
        assertEquals(180_000L, match.mixOutPointMs)
    }

    @Test
    fun testDjConstantEnergyCurveMath() {
        fun equalPowerIn(edge0: Float, edge1: Float, x: Float): Float {
            val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
            return kotlin.math.sin(t * (Math.PI / 2.0).toFloat())
        }
        fun equalPowerOut(edge0: Float, edge1: Float, x: Float): Float {
            val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
            return kotlin.math.cos(t * (Math.PI / 2.0).toFloat())
        }

        // Test over 100 steps from 0.0 to 1.0 progress
        for (step in 0..100) {
            val progress = step / 100f
            val fadeOut = if (progress < 0.4f) 1.0f else equalPowerOut(0.4f, 1f, progress)
            val fadeIn = if (progress > 0.6f) 1.0f else equalPowerIn(0f, 0.6f, progress)

            val totalEnergy = fadeOut * fadeOut + fadeIn * fadeIn
            assertTrue(
                "DJ constant-energy curve sum of acoustic power must not drop below 1.0 at progress $progress (fadeOut=$fadeOut, fadeIn=$fadeIn, totalEnergy=$totalEnergy)",
                totalEnergy >= 0.99f
            )
        }
    }

    @Test
    fun testTransitionStyleAndEnergyLevel() {
        val currentTrack = createTrack(1L, "Current Club Track")
        val candidateHarmonic = createTrack(2L, "Harmonic Match")
        val candidateClash = createTrack(3L, "Tonal Clash")
        val candidateBuildUp = createTrack(4L, "Build Up Track")

        // 1. Harmonic match in HOLD mode -> HARMONIC_CLUB_BLEND (16 beats)
        val matchHarmonic = HarmonicMixer.scoreCandidate(
            currentBpm = 126f,
            currentKey = CamelotKey.KEY_8A,
            candidateTrack = candidateHarmonic,
            candidateBpm = 126.5f,
            candidateKey = CamelotKey.KEY_8A,
            mode = EnergyMode.HOLD
        )
        assertEquals(com.alananasss.kittytune.ui.player.TransitionStyle.HARMONIC_CLUB_BLEND, matchHarmonic.transitionStyle)
        assertTrue("Energy level should be between 1 and 10", matchHarmonic.energyLevel in 1..10)

        // 2. Big tonal clash -> POWER_SWAP (4 beats downbeat cut)
        val matchClash = HarmonicMixer.scoreCandidate(
            currentBpm = 126f,
            currentKey = CamelotKey.KEY_8A,
            candidateTrack = candidateClash,
            candidateBpm = 135f,
            candidateKey = CamelotKey.KEY_2B,
            mode = EnergyMode.HOLD
        )
        assertEquals(com.alananasss.kittytune.ui.player.TransitionStyle.POWER_SWAP, matchClash.transitionStyle)

        // 3. Build-up with tempo boost -> ENERGY_DROP_CUT (8 beats)
        val matchBuildUp = HarmonicMixer.scoreCandidate(
            currentBpm = 124f,
            currentKey = CamelotKey.KEY_8A,
            candidateTrack = candidateBuildUp,
            candidateBpm = 126.5f,
            candidateKey = CamelotKey.KEY_9A,
            mode = EnergyMode.BUILD_UP
        )
        assertEquals(com.alananasss.kittytune.ui.player.TransitionStyle.ENERGY_DROP_CUT, matchBuildUp.transitionStyle)
    }

    @Test
    fun testBpmNormalizationHardtekkAndElectronicTempos() {
        // Hardtekk (160.6 BPM) must remain 160.6 BPM (not cut in half to 80.3 BPM)
        assertEquals(160.6f, HarmonicMixer.normalizeBpm(160.6f), 0.01f)

        // Drum & Bass (174.0 BPM) must remain 174.0 BPM
        assertEquals(174.0f, HarmonicMixer.normalizeBpm(174.0f), 0.01f)

        // House (128.0 BPM) and Techno (140.0 BPM) must stay unchanged
        assertEquals(128.0f, HarmonicMixer.normalizeBpm(128.0f), 0.01f)
        assertEquals(140.0f, HarmonicMixer.normalizeBpm(140.0f), 0.01f)

        // Sub-90 BPM detections (e.g. 80.3 BPM) fold up into electronic range
        assertEquals(160.6f, HarmonicMixer.normalizeBpm(80.3f), 0.01f)
        assertEquals(130.0f, HarmonicMixer.normalizeBpm(65.0f), 0.01f)

        // Hyper-tempo (> 180 BPM) folds down
        assertEquals(130.0f, HarmonicMixer.normalizeBpm(260.0f), 0.01f)
    }

    @Test
    fun testHardtekkVersusHouseStrictPenalty() {
        val currentHouseTrack = createTrack(1L, "House 127")
        val hardtekkTrack = createTrack(2L, "Hardtekk 160")

        // 127 BPM House vs 160.6 BPM Hardtekk
        val match = HarmonicMixer.scoreCandidate(
            currentBpm = 127.0f,
            currentKey = CamelotKey.KEY_8A,
            candidateTrack = hardtekkTrack,
            candidateBpm = 160.6f,
            candidateKey = CamelotKey.KEY_8A, // Same key!
            mode = EnergyMode.HOLD
        )

        assertTrue(
            "Hardtekk 160.6 BPM mixed with House 127 BPM (delta ~26%) must be strictly penalized (Score <= 20, was ${match.score})",
            match.score <= 20.0f
        )
        assertTrue(
            "BPM delta ratio must exceed MAX_BPM_TOLERANCE_RATIO",
            kotlin.math.abs(match.bpmPercentDelta) > HarmonicMixer.MAX_BPM_TOLERANCE_RATIO
        )
    }

    @Test
    fun testHardtekkMatchesWithHardtekk() {
        val currTrack = createTrack(1L, "Hardtekk A")
        val nextTrack = createTrack(2L, "Hardtekk B")

        val match = HarmonicMixer.scoreCandidate(
            currentBpm = 160.6f,
            currentKey = CamelotKey.KEY_8A,
            candidateTrack = nextTrack,
            candidateBpm = 162.0f,
            candidateKey = CamelotKey.KEY_8A,
            mode = EnergyMode.HOLD
        )

        assertTrue(
            "Hardtekk 160.6 BPM matching with 162.0 BPM should get a high match score (was ${match.score})",
            match.score >= 90.0f
        )
    }

    @Test
    fun testDetectMixInQuantizesStrictlyTo16BeatPhrase() {
        // At 128 BPM: period = 468.75ms.
        // 16 beats = 7500ms (7.5s).
        val periodMs = 60_000f / 128f
        val firstBeatOffsetMs = 200L

        // Construct mock envelope: first 12 blocks (6000ms = 6.0s) are quiet, then body energy starts
        // 20 blocks * 500ms = 10s
        val env = FloatArray(20) { idx ->
            if (idx >= 12) 1.0f else 0.05f
        }

        val mixIn = com.alananasss.kittytune.audio.automix.BeatAnalyzer.detectMixIn(
            env = env,
            phraseAnchorMs = firstBeatOffsetMs,
            periodMs = periodMs
        )

        assertNotNull("Mix-in should be detected", mixIn)
        // 6000ms is candidateBlock. It must NOT snap to arbitrary beat (e.g. 6293ms).
        // It must snap to a 16-beat boundary: 200 + 1 * (16 * 468.75) = 7700ms!
        val phraseBeatsFromStart = ((mixIn!! - firstBeatOffsetMs) / periodMs).toInt()
        assertEquals("Drop-In must align to an exact multiple of 16 beats (4 bars)", 0, phraseBeatsFromStart % 16)
        assertEquals(7700L, mixIn)
    }
}
